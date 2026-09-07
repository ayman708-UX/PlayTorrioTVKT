package com.playtorrio.tv.core.debrid

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.playtorrio.tv.core.scraper.p2p.ParseTorrentTitle
import com.playtorrio.tv.data.local.DebridSettingsDataStore
import com.playtorrio.tv.domain.model.Stream
import com.playtorrio.tv.domain.model.StreamClientResolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "DebridLinkResolver"

@Singleton
class DebridLinkDirectDebridResolver @Inject constructor(
    private val dataStore: DebridSettingsDataStore,
    @Named("directDebrid") private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun resolve(
        stream: Stream,
        season: Int?,
        episode: Int?
    ): DirectDebridResolveResult {
        val apiKey = dataStore.settings.first().debridLinkApiKey.trim()
        if (apiKey.isBlank()) return DirectDebridResolveResult.MissingApiKey

        val magnet = stream.clientResolve?.magnetUri?.takeIf { it.isNotBlank() }
            ?: DebridMagnetBuilder.fromStream(stream)
            ?: buildMagnetUri(stream.clientResolve)
            ?: return DirectDebridResolveResult.Stale

        return try {
            // 1. Add magnet
            val addPayload = mapOf("url" to magnet, "async" to true)
            val addBody = gson.toJson(addPayload).toRequestBody(jsonMediaType)

            val addReq = Request.Builder()
                .url("https://debrid-link.com/api/v2/seedbox/add")
                .header("Authorization", "Bearer $apiKey")
                .post(addBody)
                .build()

            val addRes = okHttpClient.newCall(addReq).execute()
            if (!addRes.isSuccessful) return DirectDebridResolveResult.Stale

            val addJson = gson.fromJson(addRes.body?.string(), JsonObject::class.java) ?: return DirectDebridResolveResult.Stale
            if (addJson.get("success")?.asBoolean != true) {
                Log.w(TAG, "Debrid-Link add error: ${addJson.get("error")}")
                return DirectDebridResolveResult.Stale
            }

            val torrent = addJson.getAsJsonObject("value") ?: return DirectDebridResolveResult.Stale
            val torrentId = torrent.get("id")?.asString?.takeIf { it.isNotBlank() } ?: return DirectDebridResolveResult.Stale

            data class DLFile(val name: String, val size: Long, val downloadUrl: String)

            fun extractFiles(torObj: JsonObject): List<DLFile> {
                val filesArr = torObj.getAsJsonArray("files") ?: return emptyList()
                val list = mutableListOf<DLFile>()
                for (elem in filesArr) {
                    if (!elem.isJsonObject) continue
                    val f = elem.asJsonObject
                    val name = f.get("name")?.asString.orEmpty()
                    val size = f.get("size")?.asLong ?: 0L
                    val dl = f.get("downloadUrl")?.asString.orEmpty()
                    if (name.lowercase().hasDebridVideoExtension()) {
                        list.add(DLFile(name, size, dl))
                    }
                }
                return list
            }

            var files = extractFiles(torrent)
            var ready = files.isNotEmpty() && files.all { it.downloadUrl.isNotBlank() }

            // 2. Poll if not yet ready
            var attempts = 0
            while (!ready && attempts < 25) {
                delay(1500L)
                val listReq = Request.Builder()
                    .url("https://debrid-link.com/api/v2/seedbox/list?ids=$torrentId")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val listRes = okHttpClient.newCall(listReq).execute()
                if (listRes.isSuccessful) {
                    val listJson = gson.fromJson(listRes.body?.string(), JsonObject::class.java)
                    if (listJson?.get("success")?.asBoolean == true) {
                        val valArr = listJson.getAsJsonArray("value")
                        if (valArr != null && valArr.size() > 0) {
                            val firstTor = valArr.get(0).asJsonObject
                            files = extractFiles(firstTor)
                            ready = files.isNotEmpty() && files.all { it.downloadUrl.isNotBlank() }
                        }
                    }
                }
                attempts++
            }

            if (files.isEmpty()) return DirectDebridResolveResult.Stale
            if (!ready) {
                Log.w(TAG, "Debrid-Link torrent not ready after polling timeout")
                return DirectDebridResolveResult.NotCached
            }

            // 3. Match file using DebridMediaMatcher
            val targetSeason = season ?: stream.clientResolve?.season
            val targetEpisode = episode ?: stream.clientResolve?.episode

            val picked = DebridMediaMatcher.pickMediaFile(
                files = files,
                fileIndex = stream.clientResolve?.fileIdx,
                filename = stream.behaviorHints?.filename ?: stream.title ?: stream.name,
                season = targetSeason,
                episode = targetEpisode,
                episodeTitle = stream.title,
                name = { it.name },
                size = { it.size }
            ) ?: return DirectDebridResolveResult.Stale

            if (picked.downloadUrl.isBlank()) return DirectDebridResolveResult.Stale

            DirectDebridResolveResult.Success(
                url = picked.downloadUrl,
                filename = picked.name,
                videoSize = picked.size
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Debrid-Link resolve error: ${e.message}", e)
            DirectDebridResolveResult.Error
        }
    }

    private fun buildMagnetUri(resolve: StreamClientResolve?): String? {
        val hash = resolve?.infoHash?.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(hash)
            resolve.sources?.filter { it.isNotBlank() }?.forEach { source ->
                val tracker = source.removePrefix("tracker:")
                if (tracker.isNotBlank()) {
                    append("&tr=")
                    append(java.net.URLEncoder.encode(tracker, "UTF-8"))
                }
            }
        }
    }
}
