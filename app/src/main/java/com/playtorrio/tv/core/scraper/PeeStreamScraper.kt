package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin PeeStream Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla PeeStream provider.
 * Connects to providers.peestream.in via SSE scrape pipeline and fallback search.
 */
class PeeStreamScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "PeeStreamScraper"
        private const val BASE_URL = "https://providers.peestream.in"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val mediaType = if (isTv) "tv" else "movie"

        try {
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = mediaType,
                year = request.year
            ) ?: return@withContext emptyList()

            val results = mutableListOf<ScraperStreamResult>()
            val seenUrls = mutableSetOf<String>()

            // 1. Try SSE scrape route
            try {
                val scrapeUrlBuilder = "$BASE_URL/scrape".toHttpUrlOrNull()?.newBuilder()
                if (scrapeUrlBuilder != null) {
                    scrapeUrlBuilder.addQueryParameter("type", mediaType)
                    scrapeUrlBuilder.addQueryParameter("tmdbId", tmdbId.toString())
                    scrapeUrlBuilder.addQueryParameter("title", request.title)
                    if (request.year != null) scrapeUrlBuilder.addQueryParameter("releaseYear", request.year.toString())
                    if (!request.imdbId.isNullOrEmpty()) scrapeUrlBuilder.addQueryParameter("imdbId", request.imdbId)
                    if (isTv) {
                        scrapeUrlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                        scrapeUrlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
                    }

                    val sseReq = Request.Builder()
                        .url(scrapeUrlBuilder.build())
                        .header("User-Agent", UA)
                        .header("Accept", "text/event-stream")
                        .header("Referer", "$BASE_URL/")
                        .build()

                    httpClient.newCall(sseReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val events = body.split("\n\n")
                            for (ev in events) {
                                if (ev.contains("event: completed")) {
                                    val match = Regex("""data:\s*(.+)""").find(ev)
                                    val jsonStr = match?.groupValues?.get(1)?.trim().orEmpty()
                                    if (jsonStr.startsWith("{")) {
                                        try {
                                            val parsed = JSONObject(jsonStr)
                                            if (parsed.has("stream")) {
                                                val stream = parsed.getJSONObject("stream")
                                                val streamUrl = stream.optString("playlist").ifEmpty {
                                                    stream.optString("url").ifEmpty {
                                                        stream.optString("file")
                                                    }
                                                }

                                                if (streamUrl.isNotBlank() && streamUrl.startsWith("http") && seenUrls.add(streamUrl)) {
                                                    val sourceId = parsed.optString("sourceId", "Poseidon").ifEmpty { "Poseidon" }
                                                    val quality = stream.optString("quality", "1080p").ifEmpty { "1080p" }

                                                    var reqHeaders: Map<String, String>? = null
                                                    if (stream.has("headers")) {
                                                        val hObj = stream.optJSONObject("headers")
                                                        if (hObj != null) {
                                                            val map = mutableMapOf<String, String>()
                                                            val keys = hObj.keys()
                                                            while (keys.hasNext()) {
                                                                val k = keys.next()
                                                                map[k] = hObj.optString(k)
                                                            }
                                                            reqHeaders = map
                                                        }
                                                    }
                                                    if (reqHeaders == null) {
                                                        reqHeaders = mapOf("User-Agent" to UA)
                                                    }

                                                    results.add(
                                                        ScraperStreamResult(
                                                            name = "PlayTorrioHTTP",
                                                            title = "PeeStream · $sourceId · $quality",
                                                            description = "PeeStream Multi-Server Stream · $quality",
                                                            url = streamUrl,
                                                            quality = quality,
                                                            headers = reqHeaders
                                                        )
                                                    )
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Fallback search route if SSE didn't return streams
            if (results.isEmpty()) {
                try {
                    val searchUrlBuilder = "$BASE_URL/api/search".toHttpUrlOrNull()?.newBuilder()
                    if (searchUrlBuilder != null) {
                        searchUrlBuilder.addQueryParameter("q", request.title)
                        searchUrlBuilder.addQueryParameter("type", mediaType)
                        searchUrlBuilder.addQueryParameter("tmdbId", tmdbId.toString())
                        if (isTv) {
                            searchUrlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                            searchUrlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
                        }

                        val sReq = Request.Builder()
                            .url(searchUrlBuilder.build())
                            .header("User-Agent", UA)
                            .header("Accept", "application/json")
                            .header("Referer", "$BASE_URL/")
                            .build()

                        httpClient.newCall(sReq).execute().use { sRes ->
                            if (sRes.isSuccessful) {
                                val body = sRes.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val data = JSONObject(body)
                                    if (data.has("results")) {
                                        val resultsArr = data.getJSONArray("results")
                                        for (i in 0 until resultsArr.length()) {
                                            val rObj = resultsArr.optJSONObject(i) ?: continue
                                            val pName = rObj.optString("providerName").ifEmpty {
                                                rObj.optString("provider", "PeeStream")
                                            }
                                            if (rObj.has("streams")) {
                                                val stArr = rObj.getJSONArray("streams")
                                                for (j in 0 until stArr.length()) {
                                                    val st = stArr.optJSONObject(j) ?: continue
                                                    val streamUrl = st.optString("url")
                                                    if (streamUrl.isNotBlank() && streamUrl.startsWith("http") && seenUrls.add(streamUrl)) {
                                                        val stName = st.optString("name", pName).ifEmpty { pName }
                                                        val quality = st.optString("quality", "1080p").ifEmpty { "1080p" }

                                                        val streamHeaders = mapOf("User-Agent" to UA)

                                                        results.add(
                                                            ScraperStreamResult(
                                                                name = "PlayTorrioHTTP",
                                                                title = "PeeStream · $stName · $quality",
                                                                description = "PeeStream Stream · $quality",
                                                                url = streamUrl,
                                                                quality = quality,
                                                                headers = streamHeaders
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
                    }
                } catch (_: Exception) {}
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping PeeStream: ${e.message}")
            emptyList()
        }
    }
}
