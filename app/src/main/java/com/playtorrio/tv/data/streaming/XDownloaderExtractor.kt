package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object XDownloaderExtractor {
    private const val TAG = "XDownloaderExtractor"
    private const val BASE_URL = "https://www.films365.org"
    private const val AUTH_TOKEN = "Bearer 79a02956be35835728a044b11e2ae793149d45fb2c89cb6d029ec01aac19bfdb"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun extract(
        title: String,
        isMovie: Boolean,
        year: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        onStreamFound: (HttpStreamResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val targetType = if (isMovie) "movie" else "tv"
            val searchPayload = JSONObject().put("query", title).toString()
            val searchReq = Request.Builder()
                .url("$BASE_URL/api/mobile/search")
                .header("Authorization", AUTH_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "MovieDownloader/1.0")
                .post(searchPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val searchBody = httpClient.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext

            val searchJson = JSONObject(searchBody)
            val resultsObj = searchJson.optJSONObject("results") ?: return@withContext
            val items = resultsObj.optJSONArray("all")
                ?: resultsObj.optJSONArray("movies")
                ?: resultsObj.optJSONArray("tvs")
                ?: return@withContext

            if (items.length() == 0) return@withContext

            var matchedItem: JSONObject? = null
            val cleanSearchTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val itemType = item.optString("type")
                if (itemType.isNotEmpty() && itemType != targetType) continue

                val itemTitle = item.optString("title")
                val cleanItemTitle = itemTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (cleanItemTitle == cleanSearchTitle || cleanItemTitle.contains(cleanSearchTitle)) {
                    matchedItem = item
                    break
                }
            }

            if (matchedItem == null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    if (item.optString("type") == targetType) {
                        matchedItem = item
                        break
                    }
                }
            }

            if (matchedItem == null) {
                matchedItem = items.optJSONObject(0)
            }

            val itemId = matchedItem?.optString("id")?.ifEmpty { matchedItem.optString("tmdbId") }
            if (itemId.isNullOrBlank()) return@withContext

            val detailsReq = Request.Builder()
                .url("$BASE_URL/api/mobile/details?id=$itemId&type=$targetType")
                .header("Authorization", AUTH_TOKEN)
                .header("User-Agent", "MovieDownloader/1.0")
                .build()

            val detailsBody = httpClient.newCall(detailsReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext

            val detailsJson = JSONObject(detailsBody)
            val data = detailsJson.optJSONObject("data") ?: return@withContext

            if (isMovie) {
                val dlUrl = data.optString("downloadUrl")
                val vidUrl = data.optString("videoUrl")
                val streamUrl = if (dlUrl.isNotBlank()) dlUrl else vidUrl

                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                    val stream = HttpStreamResult(
                        sourceName = "X-Downloader",
                        title = "X-Downloader",
                        description = "X-Downloader Direct MP4 Stream",
                        url = streamUrl
                    )
                    onStreamFound(stream)
                }
            } else {
                val seasons = data.optJSONArray("seasons")
                if (seasons != null && seasonNumber != null) {
                    for (sIdx in 0 until seasons.length()) {
                        val sObj = seasons.optJSONObject(sIdx) ?: continue
                        if (sObj.optInt("seasonNumber") == seasonNumber) {
                            val episodes = sObj.optJSONArray("episodes")
                            if (episodes != null && episodeNumber != null) {
                                for (eIdx in 0 until episodes.length()) {
                                    val eObj = episodes.optJSONObject(eIdx) ?: continue
                                    if (eObj.optInt("episodeNumber") == episodeNumber) {
                                        val epDl = eObj.optString("downloadUrl")
                                        val epVid = eObj.optString("videoUrl")
                                        val epUrl = if (epDl.isNotBlank()) epDl else epVid
                                        if (epUrl.isNotBlank() && epUrl.startsWith("http")) {
                                            val stream = HttpStreamResult(
                                                sourceName = "X-Downloader",
                                                title = "X-Downloader",
                                                description = "X-Downloader Direct MP4 Stream (S${seasonNumber}E${episodeNumber})",
                                                url = epUrl
                                            )
                                            onStreamFound(stream)
                                        }
                                        break
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "XDownloader extraction failed for $title", e)
        }
    }
}
