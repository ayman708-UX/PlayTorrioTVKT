package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RiveStreamScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "RiveStreamScraper"
        private const val API_BASE = "https://scrapper.rivestream.app"
        private const val REFERER = "https://www.rivestream.app/"
        private const val ORIGIN = "https://www.rivestream.app"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Referer" to REFERER,
            "Origin" to ORIGIN,
            "Accept" to "application/json, text/plain, */*"
        )

        private val fallbackProviders = listOf(
            "apex", "pulse", "solstice", "quasar", "primevids",
            "flowcast", "citadel", "guru", "asiacloud", "horizon", "hindicast"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        ) ?: return@withContext emptyList()

        val providers = fallbackProviders
        val cbValue = System.currentTimeMillis() / 3000000L

        val tasks = providers.map { provider ->
            async {
                val cbParam = if (provider == "primevids" || provider == "citadel") "&cb=$cbValue" else ""
                val endpoint = if (isTv) {
                    val s = request.season ?: 1
                    val e = request.episode ?: 1
                    "$API_BASE/api/provider?provider=$provider&id=$tmdbId&season=$s&episode=$e$cbParam"
                } else {
                    "$API_BASE/api/provider?provider=$provider&id=$tmdbId$cbParam"
                }

                val list = mutableListOf<ScraperStreamResult>()
                try {
                    val req = Request.Builder()
                        .url(endpoint)
                        .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val json = JSONObject(res.body?.string().orEmpty())
                            val data = json.optJSONObject("data")
                            val sources = data?.optJSONArray("sources")
                            if (sources != null) {
                                for (i in 0 until sources.length()) {
                                    val src = sources.getJSONObject(i)
                                    val rawUrl = src.optString("url").trim()
                                    if (rawUrl.isBlank() || !rawUrl.startsWith("http")) continue

                                    val srcName = src.optString("source").ifEmpty { provider }
                                    val quality = src.optString("quality").ifEmpty { "Auto" }
                                    val format = src.optString("format").ifEmpty { "HLS" }.uppercase()

                                    list.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = "[Rive - $srcName] $quality",
                                            description = "$srcName • $quality • $format",
                                            url = rawUrl,
                                            quality = quality,
                                            headers = defaultHeaders
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Rive provider $provider failed: ${e.message}")
                }
                list
            }
        }

        val allResults = tasks.awaitAll().flatten()
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<ScraperStreamResult>()
        for (r in allResults) {
            if (seen.add(r.url)) {
                deduped.add(r)
            }
        }
        deduped
    }
}
