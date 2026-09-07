package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class FlyStreamScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FlyStreamScraper"
        private const val API_BASE = "https://flystream.net"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val headers = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://flystream.net/",
            "Accept" to "application/json"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        )

        try {
            val randomViewer = UUID.randomUUID().toString().replace("-", "")
            val urlBuilder = "$API_BASE/api/streams".toHttpUrl().newBuilder()
                .addQueryParameter("type", if (isTv) "tv" else "movie")
                .addQueryParameter("viewerId", randomViewer)
                .addQueryParameter("title", request.title)

            if (tmdbId != null && tmdbId > 0) urlBuilder.addQueryParameter("tmdbId", tmdbId.toString())
            if (!request.imdbId.isNullOrBlank()) urlBuilder.addQueryParameter("imdb", request.imdbId)
            if (request.year != null) urlBuilder.addQueryParameter("year", request.year.toString())
            if (isTv && request.season != null) urlBuilder.addQueryParameter("season", request.season.toString())
            if (isTv && request.episode != null) urlBuilder.addQueryParameter("episode", request.episode.toString())

            val req = Request.Builder()
                .url(urlBuilder.build())
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val streams = json.optJSONArray("streams")
                    if (streams != null) {
                        for (i in 0 until streams.length()) {
                            val s = streams.getJSONObject(i)
                            val rawUrl = s.optString("url")
                            if (rawUrl.isBlank()) continue
                            val url = if (rawUrl.startsWith("/")) "$API_BASE$rawUrl" else rawUrl
                            if (!url.startsWith("http")) continue

                            val quality = s.optString("quality").ifEmpty { "Auto" }
                            val codec = s.optString("videoCodec").uppercase()
                            val size = s.optString("size")
                            val title = s.optString("name").ifEmpty { s.optString("title").ifEmpty { request.title } }

                            val descDetails = listOfNotNull(
                                quality.takeIf { it.isNotBlank() },
                                codec.takeIf { it.isNotBlank() },
                                size.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")

                            results.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = "FlyStream $title",
                                    description = if (descDetails.isNotBlank()) "FlyStream $descDetails" else "FlyStream Direct HLS Stream",
                                    url = url,
                                    quality = quality,
                                    headers = mapOf(
                                        "User-Agent" to UA,
                                        "Referer" to "https://flystream.net/"
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "FlyStreamScraper error: ${e.message}")
        }

        results
    }
}
