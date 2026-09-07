package com.playtorrio.tv.core.debrid

import android.util.Log
import com.playtorrio.tv.data.local.DebridSettingsDataStore
import com.playtorrio.tv.data.remote.api.TorboxApi
import com.playtorrio.tv.data.remote.dto.TorboxCreateTorrentDataDto
import com.playtorrio.tv.domain.model.Stream
import com.playtorrio.tv.domain.model.StreamClientResolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TorboxResolver"

@Singleton
class TorboxDirectDebridResolver @Inject constructor(
    private val dataStore: DebridSettingsDataStore,
    private val api: TorboxApi,
    private val fileSelector: TorboxFileSelector
) {
    suspend fun resolve(
        stream: Stream,
        season: Int?,
        episode: Int?
    ): DirectDebridResolveResult {
        val resolve = stream.clientResolve ?: return DirectDebridResolveResult.Error
        val apiKey = dataStore.settings.first().torboxApiKey.trim()
        if (apiKey.isBlank()) return DirectDebridResolveResult.MissingApiKey
        val magnet = resolve.magnetUri?.takeIf { it.isNotBlank() }
            ?: buildMagnetUri(resolve)
            ?: return DirectDebridResolveResult.Stale
        val authorization = "Bearer $apiKey"

        return try {
            Log.d(TAG, "resolve: createTorrent hash=${resolve.infoHash?.take(12)}...")
            val createStartMs = System.currentTimeMillis()
            val create = api.createTorrent(
                authorization = authorization,
                magnet = magnet.toTextPart(),
                addOnlyIfCached = "false".toTextPart(),
                allowZip = "false".toTextPart()
            )
            Log.d(TAG, "resolve: createTorrent done in ${System.currentTimeMillis() - createStartMs}ms code=${create.code()}")
            val rawTorrentId = create.extractTorrentId()
            val torrentId = if (rawTorrentId != null) {
                rawTorrentId
            } else {
                // Fallback: check if the torrent already exists in user's cloud library by hash
                val hash = resolve.infoHash?.trim()?.lowercase()
                if (!hash.isNullOrBlank()) {
                    try {
                        val cloudList = api.listCloudTorrents(authorization)
                        cloudList.body()?.data?.firstOrNull { it.hash?.equals(hash, ignoreCase = true) == true }?.id?.toString()?.toIntOrNull()
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            } ?: return create.toFailureForCreate()

            Log.d(TAG, "resolve: getTorrent id=$torrentId - polling status")
            var torrentData: com.playtorrio.tv.data.remote.dto.TorboxTorrentDataDto? = null
            for (attempt in 0..15) {
                val torrent = api.getTorrent(
                    authorization = authorization,
                    id = torrentId,
                    bypassCache = true
                )
                if (torrent.isSuccessful) {
                    val data = torrent.body()?.data
                    if (data != null) {
                        if (data.downloadState?.equals("error", ignoreCase = true) == true) {
                            Log.e(TAG, "resolve: torrent $torrentId download failed with error state")
                            return DirectDebridResolveResult.Error
                        }
                        if (!data.files.isNullOrEmpty()) {
                            torrentData = data
                            val state = data.downloadState?.lowercase()
                            val finished = data.downloadFinished == true
                            if (finished || state == "cached" || state == "completed") {
                                break
                            }
                        }
                    }
                }
                delay(2000)
            }

            val files = torrentData?.files.orEmpty()
            if (files.isEmpty()) {
                Log.w(TAG, "resolve: torrent $torrentId has no files after polling")
                return DirectDebridResolveResult.Stale
            }
            val file = fileSelector.selectFile(files, resolve, season, episode)
                ?: return DirectDebridResolveResult.Stale
            val fileId = file.id ?: return DirectDebridResolveResult.Stale

            Log.d(TAG, "resolve: requestDownloadLink torrentId=$torrentId fileId=$fileId")
            var downloadUrl: String? = null
            for (attempt in 0..10) {
                val linkStartMs = System.currentTimeMillis()
                val link = api.requestDownloadLink(
                    authorization = authorization,
                    token = apiKey,
                    torrentId = torrentId,
                    fileId = fileId,
                    zipLink = false,
                    redirect = false,
                    appendName = false
                )
                Log.d(TAG, "resolve: requestDownloadLink done in ${System.currentTimeMillis() - linkStartMs}ms code=${link.code()}")
                if (link.isSuccessful) {
                    val url = link.body()?.data?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        downloadUrl = url
                        break
                    }
                }
                delay(2000)
            }
            if (downloadUrl == null) {
                Log.w(TAG, "resolve: failed to get download link for torrent $torrentId file $fileId")
                return DirectDebridResolveResult.Stale
            }

            DirectDebridResolveResult.Success(
                url = downloadUrl,
                filename = file.displayName().takeIf { it.isNotBlank() },
                videoSize = file.size
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "resolve: failed with ${error::class.simpleName}: ${error.message}")
            DirectDebridResolveResult.Error
        }
    }

    private fun Response<com.playtorrio.tv.data.remote.dto.TorboxEnvelopeDto<TorboxCreateTorrentDataDto>>.extractTorrentId(): Int? {
        if (!isSuccessful) return null
        val body = body()
        if (body?.success == false) return null
        return body?.data?.resolvedTorrentId()
    }

    private fun Response<com.playtorrio.tv.data.remote.dto.TorboxEnvelopeDto<TorboxCreateTorrentDataDto>>.toFailureForCreate(): DirectDebridResolveResult {
        return when (code()) {
            401, 403 -> DirectDebridResolveResult.Error
            409 -> DirectDebridResolveResult.NotCached
            else -> DirectDebridResolveResult.Stale
        }
    }

    private fun buildMagnetUri(resolve: StreamClientResolve): String? {
        val hash = resolve.infoHash?.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(hash)
            resolve.sources
                ?.filter { it.isNotBlank() }
                ?.forEach { source ->
                    append("&tr=")
                    append(java.net.URLEncoder.encode(source, "UTF-8"))
                }
        }
    }

    private fun String.toTextPart(): RequestBody {
        return toRequestBody("text/plain".toMediaType())
    }
}

sealed class DirectDebridResolveResult {
    data class Success(
        val url: String,
        val filename: String?,
        val videoSize: Long?
    ) : DirectDebridResolveResult()

    data object MissingApiKey : DirectDebridResolveResult()
    data object NotCached : DirectDebridResolveResult()
    data object Stale : DirectDebridResolveResult()
    data object Error : DirectDebridResolveResult()
}
