package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin FlaxMovies Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla FlaxMovies provider.
 * Resolves multi-CDN streams (Airflix, Cinevaro, Vaplayer, Vidnest Alfa).
 */
class FlaxMoviesScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FlaxMoviesScraper"
        private const val BASE_URL = "https://flaxmovies.xyz"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val DEFAULT_WORKERS = listOf(
            "https://freakyniki.elaxo.lol",
            "https://vidlove.nabilekson.workers.dev"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private fun getWorkerUrls(embedUrl: String): List<String> {
            val foundUrls = mutableListOf<String>()
            try {
                val req = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", UA)
                    .header("Referer", "$BASE_URL/")
                    .header("Origin", BASE_URL)
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()
                        val directMatch = Regex("""WORKER_URL\s*[:=]\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                            .find(html)
                        if (directMatch != null) {
                            val wUrl = directMatch.groupValues[1].replace(Regex("/+$"), "")
                            if (!foundUrls.contains(wUrl)) foundUrls.add(wUrl)
                        }

                        val scriptMatches = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            .findAll(html)

                        for (sm in scriptMatches) {
                            var scriptUrl = sm.groupValues[1]
                            if (scriptUrl.startsWith("//")) {
                                scriptUrl = "https:$scriptUrl"
                            } else if (scriptUrl.startsWith("/")) {
                                scriptUrl = "$BASE_URL$scriptUrl"
                            } else if (!scriptUrl.startsWith("http")) {
                                scriptUrl = "$BASE_URL/$scriptUrl"
                            }

                            if (scriptUrl.contains("jsdelivr") || scriptUrl.contains("google") || scriptUrl.contains("cloudflare")) {
                                continue
                            }

                            try {
                                val sReq = Request.Builder()
                                    .url(scriptUrl)
                                    .header("User-Agent", UA)
                                    .header("Referer", embedUrl)
                                    .build()

                                httpClient.newCall(sReq).execute().use { sRes ->
                                    if (sRes.isSuccessful) {
                                        val sBody = sRes.body?.string().orEmpty()
                                        val m = Regex("""WORKER_URL\s*[:=]\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                                            .find(sBody)
                                        if (m != null) {
                                            val wUrl = m.groupValues[1].replace(Regex("/+$"), "")
                                            if (!foundUrls.contains(wUrl)) foundUrls.add(wUrl)
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}

            for (d in DEFAULT_WORKERS) {
                if (!foundUrls.contains(d)) foundUrls.add(d)
            }
            return foundUrls
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

            val embedUrl = if (isTv) {
                "$BASE_URL/embed/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$BASE_URL/embed/movie/$tmdbId"
            }

            val workerUrls = getWorkerUrls(embedUrl)
            val query = if (isTv) {
                "tmdb_id=$tmdbId&tmdbId=$tmdbId&season=${request.season ?: 1}&episode=${request.episode ?: 1}"
            } else {
                "tmdb_id=$tmdbId&tmdbId=$tmdbId"
            }

            val results = mutableListOf<ScraperStreamResult>()
            val seenUrls = mutableSetOf<String>()

            for (worker in workerUrls) {
                try {
                    val url = "$worker/?$query"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", UA)
                        .header("Referer", "$BASE_URL/")
                        .header("Origin", BASE_URL)
                        .header("Accept", "application/json, text/plain, */*")
                        .build()

                    httpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.startsWith("{")) {
                                val json = JSONObject(body)
                                if (json.has("streams")) {
                                    val streams = json.getJSONArray("streams")
                                    for (i in 0 until streams.length()) {
                                        val st = streams.getJSONObject(i)
                                        val streamUrl = st.optString("url")
                                        if (streamUrl.isNotBlank() && streamUrl.startsWith("http") && seenUrls.add(streamUrl)) {
                                            val provider = st.optString("provider", "CDN").ifEmpty { "CDN" }
                                            val resolution = st.optString("resolution", "1080p").ifEmpty { "1080p" }

                                            val streamHeaders = mapOf(
                                                "User-Agent" to UA,
                                                "Referer" to "$BASE_URL/",
                                                "Origin" to BASE_URL
                                            )

                                            results.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "FlaxMovies · $provider · $resolution",
                                                    description = "FlaxMovies Multi-CDN Stream · $resolution",
                                                    url = streamUrl,
                                                    quality = resolution,
                                                    headers = streamHeaders
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (results.isNotEmpty()) break
                } catch (_: Exception) {}
            }
            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping FlaxMovies: ${e.message}")
            emptyList()
        }
    }
}
