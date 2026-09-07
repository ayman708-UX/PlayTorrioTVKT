package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VidAPI Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidAPI provider.
 * Resolves HLS and MP4 streams from streamdata.vaplayer.ru.
 */
class VidApiScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidApiScraper"
        private const val API_BASE = "https://streamdata.vaplayer.ru/api.php"
        private const val EMBED_ORIGIN = "https://nextgencloudfabric.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val VERIFY_HEADERS = mapOf(
            "User-Agent" to UA,
            "Referer" to "$EMBED_ORIGIN/",
            "Origin" to EMBED_ORIGIN
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun extractStreamUrls(obj: Any?, urls: MutableSet<String>) {
            if (obj == null) return
            when (obj) {
                is String -> {
                    if (obj.startsWith("http") && (obj.contains(".m3u8") || obj.contains(".mp4") || obj.contains(".mpd"))) {
                        urls.add(obj)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        extractStreamUrls(obj.opt(i), urls)
                    }
                }
                is JSONObject -> {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        extractStreamUrls(obj.opt(k), urls)
                    }
                }
            }
        }
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

            val urlBuilder = API_BASE.toHttpUrlOrNull()?.newBuilder() ?: return@withContext emptyList()
            urlBuilder.addQueryParameter("tmdb", tmdbId.toString())
            urlBuilder.addQueryParameter("type", mediaType)
            if (isTv) {
                urlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                urlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "*/*")
                .header("Origin", EMBED_ORIGIN)
                .header("Referer", "$EMBED_ORIGIN/")
                .header("User-Agent", UA)
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (body.isEmpty()) return@use

                val streamUrls = mutableSetOf<String>()
                if (body.startsWith("{")) {
                    extractStreamUrls(JSONObject(body), streamUrls)
                } else if (body.startsWith("[")) {
                    extractStreamUrls(JSONArray(body), streamUrls)
                }

                for (sUrl in streamUrls) {
                    val isHls = sUrl.contains(".m3u8")
                    val isDash = sUrl.contains(".mpd")
                    val format = if (isHls) "HLS" else if (isDash) "DASH" else "MP4"

                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = "VidAPI · $format · 1080p",
                            description = "VidAPI Stream · $format",
                            url = sUrl,
                            quality = "1080p",
                            headers = VERIFY_HEADERS
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidAPI: ${e.message}")
            emptyList()
        }
    }
}
