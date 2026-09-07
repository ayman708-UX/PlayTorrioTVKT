package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class XDownloaderScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "XDownloaderScraper"
        private const val BASE_URL = "https://www.films365.org"
        private val headers = mapOf(
            "Authorization" to "Bearer 79a02956be35835728a044b11e2ae793149d45fb2c89cb6d029ec01aac19bfdb",
            "Content-Type" to "application/json",
            "User-Agent" to "MovieDownloader/1.0"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val targetType = if (isTv) "tv" else "movie"

        try {
            val searchJson = JSONObject().apply { put("query", request.title) }
            val mediaType = "application/json".toMediaType()
            val searchReq = Request.Builder()
                .url("$BASE_URL/api/mobile/search")
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .post(searchJson.toString().toRequestBody(mediaType))
                .build()

            val searchRes = httpClient.newCall(searchReq).execute()
            if (!searchRes.isSuccessful) return@withContext emptyList()

            val json = JSONObject(searchRes.body?.string().orEmpty())
            val resultsObj = json.optJSONObject("results") ?: return@withContext emptyList()

            val items = resultsObj.optJSONArray("all")
                ?: resultsObj.optJSONArray("movies")
                ?: resultsObj.optJSONArray("tvs")
                ?: return@withContext emptyList()

            if (items.length() == 0) return@withContext emptyList()

            var matchedItemId: String? = null
            val cleanSearchTitle = request.title.lowercase().replace(Regex("[^a-z0-9]"), "")

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val itemType = item.optString("type")
                if (itemType.isNotEmpty() && !itemType.equals(targetType, ignoreCase = true)) continue

                val itemTitle = item.optString("title")
                val cleanItemTitle = itemTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (cleanItemTitle == cleanSearchTitle || cleanItemTitle.contains(cleanSearchTitle)) {
                    matchedItemId = item.optString("id").ifEmpty { item.optString("tmdbId") }
                    break
                }
            }

            if (matchedItemId == null) {
                matchedItemId = items.getJSONObject(0).optString("id").ifEmpty { items.getJSONObject(0).optString("tmdbId") }
            }

            if (matchedItemId.isNullOrBlank()) return@withContext emptyList()

            val detailsReq = Request.Builder()
                .url("$BASE_URL/api/mobile/details?id=$matchedItemId&type=$targetType")
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val detailsRes = httpClient.newCall(detailsReq).execute()
            if (!detailsRes.isSuccessful) return@withContext emptyList()

            val detailsJson = JSONObject(detailsRes.body?.string().orEmpty())
            val data = detailsJson.optJSONObject("data") ?: return@withContext emptyList()

            if (targetType == "movie") {
                val downloadUrl = data.optString("downloadUrl")
                val videoUrl = data.optString("videoUrl")
                val streamUrl = downloadUrl.ifEmpty { videoUrl }
                if (streamUrl.startsWith("http")) {
                    sources.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = "X-Downloader",
                            description = "X-Downloader Direct MP4 Stream",
                            url = streamUrl
                        )
                    )
                }
            } else {
                val seasons = data.optJSONArray("seasons")
                if (seasons != null && request.season != null) {
                    for (sIdx in 0 until seasons.length()) {
                        val sObj = seasons.getJSONObject(sIdx)
                        if (sObj.optInt("seasonNumber") == request.season) {
                            val episodes = sObj.optJSONArray("episodes")
                            if (episodes != null && request.episode != null) {
                                for (eIdx in 0 until episodes.length()) {
                                    val eObj = episodes.getJSONObject(eIdx)
                                    if (eObj.optInt("episodeNumber") == request.episode) {
                                        val epDownloadUrl = eObj.optString("downloadUrl")
                                        val epVideoUrl = eObj.optString("videoUrl")
                                        val streamUrl = epDownloadUrl.ifEmpty { epVideoUrl }
                                        if (streamUrl.startsWith("http")) {
                                            sources.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "X-Downloader",
                                                    description = "X-Downloader Direct MP4 Stream (S${request.season}E${request.episode})",
                                                    url = streamUrl
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "XDownloaderScraper error: ${e.message}")
        }

        sources
    }
}
