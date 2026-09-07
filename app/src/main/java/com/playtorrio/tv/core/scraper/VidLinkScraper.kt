package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VidLink Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidLink provider.
 * Encrypts TMDB query via enc-dec.app and extracts direct streams from vidlink.pro.
 */
class VidLinkScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidLinkScraper"
        private const val BASE_URL = "https://vidlink.pro"
        private const val ENC_API = "https://enc-dec.app/api/enc-vidlink"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Origin" to BASE_URL,
            "Referer" to "$BASE_URL/",
            "Accept" to "application/json, text/plain, */*"
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
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = mediaType,
                year = request.year
            ) ?: return@withContext emptyList()

            val encReq = Request.Builder()
                .url("$ENC_API?text=$tmdbId")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            val encKey = httpClient.newCall(encReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val encData = JSONObject(body)
                if (encData.optInt("status") == 200) {
                    encData.optString("result")
                } else null
            } ?: return@withContext emptyList()

            val endpoint = if (isTv) {
                "$BASE_URL/api/b/tv/$encKey/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$BASE_URL/api/b/movie/$encKey"
            }

            val req = Request.Builder()
                .url(endpoint)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val data = JSONObject(body)
                if (!data.has("stream")) return@use

                val stream = data.getJSONObject("stream")

                val playlistUrl = stream.optString("playlist")
                if (playlistUrl.isNotBlank()) {
                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = "VidLink · Master HLS · 1080p",
                            description = "VidLink HLS Stream",
                            url = playlistUrl,
                            quality = "1080p",
                            headers = HEADERS
                        )
                    )
                }

                if (stream.has("qualities")) {
                    val qualities = stream.optJSONObject("qualities")
                    if (qualities != null) {
                        for (q in listOf("1080", "720", "480", "360")) {
                            val qObj = qualities.optJSONObject(q)
                            val qUrl = qObj?.optString("url")
                            if (!qUrl.isNullOrBlank()) {
                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = "VidLink · ${q}p",
                                        description = "VidLink MP4 Stream",
                                        url = qUrl,
                                        quality = "${q}p",
                                        headers = HEADERS
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidLink: ${e.message}")
            emptyList()
        }
    }
}
