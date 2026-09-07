package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class A111477Scraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "A111477Scraper"
        private const val SERVICE_ORIGIN = "https://st.111477.xyz"
        private const val DEFAULT_STREAM_HOST = "https://a.111477.xyz/"

        private val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Accept" to "application/json, text/plain, */*"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        fun generateManifestBaseUrl(
            host: String = DEFAULT_STREAM_HOST,
            sort: String = "file-desc",
            limit: Int = 3,
            tmdbKey: String? = null
        ): String {
            var config = host.trim()
            if (!config.endsWith("/")) config += "/"
            if (sort.isNotEmpty() && sort != "none") {
                config += "::sort=$sort"
            }
            if (limit > 0 && limit != 5) {
                config += "::limit=$limit"
            }
            if (!tmdbKey.isNullOrEmpty()) {
                config += "::tmdb=$tmdbKey"
            }
            val b64 = Base64.encodeToString(
                config.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            return "$SERVICE_ORIGIN/config/$b64"
        }
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

        val addonBase = generateManifestBaseUrl(limit = 3)
        val seenUrls = mutableSetOf<String>()

        for (id in targetIds) {
            val endpoint = if (isTv) {
                val s = request.season ?: 1
                val e = request.episode ?: 1
                "$addonBase/stream/series/$id:$s:$e.json"
            } else {
                "$addonBase/stream/movie/$id.json"
            }

            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
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

                                val rawTitle = item.optString("title").ifEmpty { "111477 • Direct Stream" }
                                val rawName = item.optString("name").ifEmpty { "111477" }

                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = if (rawTitle.isNotBlank()) rawTitle else rawName,
                                        description = "111477 • Direct CDN Stream",
                                        url = url,
                                        headers = headers
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
