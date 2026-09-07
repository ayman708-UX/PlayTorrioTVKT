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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "AllDebridResolver"

@Singleton
class AllDebridDirectDebridResolver @Inject constructor(
    private val dataStore: DebridSettingsDataStore,
    @Named("directDebrid") private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    suspend fun resolve(
        stream: Stream,
        season: Int?,
        episode: Int?
    ): DirectDebridResolveResult {
        val apiKey = dataStore.settings.first().allDebridApiKey.trim()
        if (apiKey.isBlank()) return DirectDebridResolveResult.MissingApiKey

        val magnet = stream.clientResolve?.magnetUri?.takeIf { it.isNotBlank() }
            ?: DebridMagnetBuilder.fromStream(stream)
            ?: buildMagnetUri(stream.clientResolve)
            ?: return DirectDebridResolveResult.Stale

        return try {
            // 1. Upload magnet
            val uploadBody = FormBody.Builder()
                .add("magnets[]", magnet)
                .build()

            val uploadReq = Request.Builder()
                .url("https://api.alldebrid.com/v4/magnet/upload")
                .header("Authorization", "Bearer $apiKey")
                .post(uploadBody)
                .build()

            val uploadRes = okHttpClient.newCall(uploadReq).execute()
            if (!uploadRes.isSuccessful) return DirectDebridResolveResult.Stale

            val uploadJson = gson.fromJson(uploadRes.body?.string(), JsonObject::class.java) ?: return DirectDebridResolveResult.Stale
            if (uploadJson.get("status")?.asString != "success") return DirectDebridResolveResult.Stale

            val dataObj = uploadJson.getAsJsonObject("data") ?: return DirectDebridResolveResult.Stale
            val magnetsArr = dataObj.getAsJsonArray("magnets") ?: return DirectDebridResolveResult.Stale
            if (magnetsArr.size() == 0) return DirectDebridResolveResult.Stale

            val firstMag = magnetsArr.get(0).asJsonObject
            if (firstMag.has("error")) {
                Log.w(TAG, "AllDebrid magnet error: ${firstMag.get("error")}")
                return DirectDebridResolveResult.Stale
            }

            val magnetId = firstMag.get("id")?.asLong ?: return DirectDebridResolveResult.Stale

            // 2. Poll status
            var attempts = 0
            var isReady = false
            while (attempts < 25) {
                val statusBody = FormBody.Builder()
                    .add("id", magnetId.toString())
                    .build()

                val statusReq = Request.Builder()
                    .url("https://api.alldebrid.com/v4.1/magnet/status")
                    .header("Authorization", "Bearer $apiKey")
                    .post(statusBody)
                    .build()

                val statusRes = okHttpClient.newCall(statusReq).execute()
                if (statusRes.isSuccessful) {
                    val statusJson = gson.fromJson(statusRes.body?.string(), JsonObject::class.java)
                    val stData = statusJson?.getAsJsonObject("data")
                    val mags = stData?.get("magnets")
                    val magObj = if (mags?.isJsonArray == true && mags.asJsonArray.size() > 0) {
                        mags.asJsonArray.get(0).asJsonObject
                    } else if (mags?.isJsonObject == true) {
                        mags.asJsonObject
                    } else null

                    val statusCode = magObj?.get("statusCode")?.asInt ?: -1
                    if (statusCode == 4) {
                        isReady = true
                        break
                    }
                    if (statusCode >= 5) {
                        Log.w(TAG, "AllDebrid magnet failed with statusCode $statusCode")
                        return DirectDebridResolveResult.Stale
                    }
                }
                delay(1500L)
                attempts++
            }

            if (!isReady) {
                Log.w(TAG, "AllDebrid magnet not ready after polling timeout")
                return DirectDebridResolveResult.NotCached
            }

            // 3. Get files
            val filesBody = FormBody.Builder()
                .add("id[]", magnetId.toString())
                .build()

            val filesReq = Request.Builder()
                .url("https://api.alldebrid.com/v4/magnet/files")
                .header("Authorization", "Bearer $apiKey")
                .post(filesBody)
                .build()

            val filesRes = okHttpClient.newCall(filesReq).execute()
            if (!filesRes.isSuccessful) return DirectDebridResolveResult.Stale

            val filesJson = gson.fromJson(filesRes.body?.string(), JsonObject::class.java) ?: return DirectDebridResolveResult.Stale
            val filesData = filesJson.getAsJsonObject("data") ?: return DirectDebridResolveResult.Stale
            val filesMagnets = filesData.getAsJsonArray("magnets") ?: return DirectDebridResolveResult.Stale
            if (filesMagnets.size() == 0) return DirectDebridResolveResult.Stale

            val filesObj = filesMagnets.get(0).asJsonObject
            val tree = filesObj.getAsJsonArray("files") ?: return DirectDebridResolveResult.Stale

            data class FlatFile(val path: String, val size: Long, val link: String)
            val flatFiles = mutableListOf<FlatFile>()

            fun flatten(nodes: com.google.gson.JsonArray, prefix: String) {
                for (node in nodes) {
                    if (!node.isJsonObject) continue
                    val obj = node.asJsonObject
                    val name = obj.get("n")?.asString.orEmpty()
                    val children = obj.get("e")
                    if (children != null && children.isJsonArray) {
                        flatten(children.asJsonArray, if (prefix.isEmpty()) name else "$prefix/$name")
                    } else {
                        val path = if (prefix.isEmpty()) name else "$prefix/$name"
                        val size = obj.get("s")?.asLong ?: 0L
                        val link = obj.get("l")?.asString.orEmpty()
                        if (link.isNotBlank() && path.lowercase().hasDebridVideoExtension()) {
                            flatFiles.add(FlatFile(path, size, link))
                        }
                    }
                }
            }

            flatten(tree, "")
            if (flatFiles.isEmpty()) return DirectDebridResolveResult.Stale

            // 4. Select file with DebridMediaMatcher
            val targetSeason = season ?: stream.clientResolve?.season
            val targetEpisode = episode ?: stream.clientResolve?.episode

            val picked = DebridMediaMatcher.pickMediaFile(
                files = flatFiles,
                fileIndex = stream.clientResolve?.fileIdx,
                filename = stream.behaviorHints?.filename ?: stream.title ?: stream.name,
                season = targetSeason,
                episode = targetEpisode,
                episodeTitle = stream.title,
                name = { it.path },
                size = { it.size }
            ) ?: return DirectDebridResolveResult.Stale

            if (picked.link.isBlank()) return DirectDebridResolveResult.Stale

            // 5. Unlock link
            val unlockBody = FormBody.Builder()
                .add("link", picked.link)
                .build()

            val unlockReq = Request.Builder()
                .url("https://api.alldebrid.com/v4/link/unlock")
                .header("Authorization", "Bearer $apiKey")
                .post(unlockBody)
                .build()

            val unlockRes = okHttpClient.newCall(unlockReq).execute()
            if (!unlockRes.isSuccessful) return DirectDebridResolveResult.Stale

            val unlockJson = gson.fromJson(unlockRes.body?.string(), JsonObject::class.java) ?: return DirectDebridResolveResult.Stale
            val unData = unlockJson.getAsJsonObject("data") ?: return DirectDebridResolveResult.Stale
            val dlLink = unData.get("link")?.asString?.takeIf { it.isNotBlank() } ?: return DirectDebridResolveResult.Stale

            val filename = unData.get("filename")?.asString?.takeIf { it.isNotBlank() }
                ?: picked.path.substringAfterLast('/')
            val filesize = unData.get("filesize")?.asLong ?: picked.size

            DirectDebridResolveResult.Success(
                url = dlLink,
                filename = filename,
                videoSize = filesize
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "AllDebrid resolve error: ${e.message}", e)
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
