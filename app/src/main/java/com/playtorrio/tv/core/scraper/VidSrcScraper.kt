package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VidSrcScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidSrcScraper"
        private const val API_BASE = "https://data.vidsrcme.ru"
        private const val EMBED_BASE = "https://vidsrc.me"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val headers = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://cloudorchestranova.com/",
            "Accept" to "application/json"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        try {
            val urlBuilder = "$API_BASE/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("type", mediaType)
                .addQueryParameter("tmdb", tmdbId.toString())
                .addQueryParameter("stream_urls", "")

            if (isTv) {
                if (request.season != null) urlBuilder.addQueryParameter("season", request.season.toString())
                if (request.episode != null) urlBuilder.addQueryParameter("episode", request.episode.toString())
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string().orEmpty())
                    if (json.optString("status_code") == "200") {
                        val data = json.optJSONObject("data")
                        val streamUrls = data?.optJSONArray("stream_urls")
                        if (streamUrls != null) {
                            for (i in 0 until streamUrls.length()) {
                                val url = streamUrls.getString(i).trim()
                                if (url.startsWith("http")) {
                                    sources.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = if (streamUrls.length() > 1) "VidSrc ${i + 1}" else "VidSrc",
                                            description = if (url.contains(".m3u8")) "VidSrc Direct HLS Stream" else "VidSrc Direct Stream Source",
                                            url = url,
                                            headers = mapOf(
                                                "User-Agent" to UA,
                                                "Referer" to "https://cloudorchestranova.com/"
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fallback: Query VidSrc embed
            if (sources.isEmpty()) {
                val embedUrl = if (isTv) {
                    "$EMBED_BASE/embed/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}"
                } else {
                    "$EMBED_BASE/embed/movie/$tmdbId"
                }

                val embedReq = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://vidsrc.me/")
                    .build()

                httpClient.newCall(embedReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val html = res.body?.string().orEmpty()
                        val m3u8Matches = Regex("""https?://[^"'\s]+\.m3u8[^"'\s]*""").findAll(html)
                        for (m in m3u8Matches) {
                            val url = m.value
                            sources.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = "VidSrc Direct",
                                    description = "VidSrc Direct HLS Stream",
                                    url = url,
                                    headers = mapOf(
                                        "User-Agent" to UA,
                                        "Referer" to "https://vidsrc.me/"
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "VidSrcScraper error: ${e.message}")
        }

        sources
    }
}
