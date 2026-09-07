package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin Purstream Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla Purstream provider.
 * Searches and queries api.purstream.club directly for multi-source streams.
 */
class PurstreamScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "PurstreamScraper"
        private const val DOMAIN = "https://purstream.club"
        private const val API_BASE = "https://api.purstream.club/api/v1"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "$DOMAIN/",
            "Origin" to DOMAIN,
            "X-Requested-With" to "XMLHttpRequest",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-site"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val mediaType = if (isTv) "tv" else "movie"

        try {
            val encodedTitle = URLEncoder.encode(request.title, "UTF-8")
            val searchUrl = "$API_BASE/search-bar/search/$encodedTitle"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val matchId = httpClient.newCall(searchReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val searchData = JSONObject(body)

                val items = searchData.optJSONObject("data")
                    ?.optJSONObject("items")
                    ?.optJSONObject("movies")
                    ?.optJSONArray("items") ?: return@use null

                if (items.length() == 0) return@use null

                val lowerTitle = request.title.lowercase()
                var match: JSONObject? = null

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val itemType = item.optString("type")
                    val itemTitle = item.optString("title").lowercase()
                    val releaseDate = item.optString("release_date")

                    if (itemType == mediaType && itemTitle == lowerTitle) {
                        if (request.year == null || releaseDate.startsWith(request.year.toString())) {
                            match = item
                            break
                        }
                    }
                }

                if (match == null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        if (item.optString("type") == mediaType) {
                            match = item
                            break
                        }
                    }
                }

                match?.optString("id")
            } ?: return@withContext emptyList()

            val streamUrl = if (isTv) {
                "$API_BASE/stream/$matchId/episode?season=${request.season ?: 1}&episode=${request.episode ?: 1}"
            } else {
                "$API_BASE/stream/$matchId"
            }

            val streamReq = Request.Builder()
                .url(streamUrl)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(streamReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val sJson = JSONObject(body)
                if (sJson.optString("type") != "success") return@use

                val sources = sJson.optJSONObject("data")
                    ?.optJSONObject("items")
                    ?.optJSONArray("sources") ?: return@use

                val reqHeaders = mapOf(
                    "User-Agent" to UA,
                    "Referer" to "$DOMAIN/"
                )

                for (i in 0 until sources.length()) {
                    val src = sources.optJSONObject(i) ?: continue
                    val sUrl = src.optString("stream_url")
                    if (sUrl.isBlank() || !sUrl.startsWith("http")) continue

                    val rawName = src.optString("source_name", "Purstream").ifEmpty { "Purstream" }
                    val cleanName = rawName.replace(Regex("""^\s*\|\s*"""), "")
                        .replace(Regex("""\s*\|\s*"""), " · ")
                        .trim()
                    val sQuality = src.optString("quality", "Auto").ifEmpty { "Auto" }
                    val titleQuality = if (sQuality != "Auto" && !cleanName.contains(sQuality)) " · $sQuality" else ""
                    val streamTitle = "Purstream · $cleanName$titleQuality"

                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = streamTitle,
                            description = "Purstream Multi-Audio HLS Stream",
                            url = sUrl,
                            quality = if (sQuality != "Auto") sQuality else "1080p",
                            headers = reqHeaders
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping Purstream: ${e.message}")
            emptyList()
        }
    }
}
