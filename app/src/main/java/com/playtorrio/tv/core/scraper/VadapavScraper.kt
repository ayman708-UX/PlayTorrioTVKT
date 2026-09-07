package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VadapavScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VadapavScraper"
        private const val ADDON_BASE = "https://stremio.vadapav.mov"

        private val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Accept" to "application/json, text/plain, */*"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val targetIds = mutableListOf<String>()

        if (!request.imdbId.isNullOrBlank() && request.imdbId.startsWith("tt")) {
            targetIds.add(request.imdbId)
        }

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        )

        if (tmdbId != null && tmdbId > 0) {
            targetIds.add("tmdb:$tmdbId")
        }

        if (targetIds.isEmpty()) return@withContext emptyList()
        val seenUrls = mutableSetOf<String>()

        for (id in targetIds) {
            val endpoint = if (isTv) {
                val s = request.season ?: 1
                val e = request.episode ?: 1
                "$ADDON_BASE/stream/series/$id:$s:$e.json"
            } else {
                "$ADDON_BASE/stream/movie/$id.json"
            }

            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val streams = json.optJSONArray("streams")
                        if (streams != null && streams.length() > 0) {
                            for (i in 0 until streams.length()) {
                                val item = streams.getJSONObject(i)
                                val url = item.optString("url")
                                if (url.isBlank() || !url.startsWith("http") || seenUrls.contains(url)) {
                                    continue
                                }
                                seenUrls.add(url)

                                val rawTitle = item.optString("title").ifEmpty { "vadapav.mov • Direct Stream" }
                                val rawName = item.optString("name").ifEmpty { "vadapav.mov" }

                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = if (rawTitle.isNotBlank()) rawTitle else rawName,
                                        description = rawTitle,
                                        url = url,
                                        headers = defaultHeaders
                                    )
                                )
                            }
                            if (results.isNotEmpty()) return@withContext results
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed fetching from $endpoint: ${e.message}")
            }
        }

        results
    }
}
