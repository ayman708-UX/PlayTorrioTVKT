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
 * Pure-Kotlin LMScript Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla LMScript provider.
 * Resolves movies from lmscript.xyz.
 */
class LMScriptScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "LMScriptScraper"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        if (request.type == "tv" || request.type == "series" || request.season != null) {
            return@withContext emptyList()
        }

        try {
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = "movie",
                year = request.year
            )

            val encodedTitle = URLEncoder.encode(request.title, "UTF-8")
            val url = "https://lmscript.xyz/v1/movies?filters[q]=$encodedTitle&expand=streams"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use emptyList()

                val data = JSONObject(body)
                if (!data.has("items")) return@use emptyList()
                val items = data.getJSONArray("items")
                if (items.length() == 0) return@use emptyList()

                var movie: JSONObject? = null

                if (tmdbId != null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val prefix = it.optString("tmdb_prefix")
                        val id = it.optString("tmdb_id")
                        if (prefix == tmdbId.toString() || id == tmdbId.toString()) {
                            movie = it
                            break
                        }
                    }
                }

                if (movie == null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        if (it.optString("title").equals(request.title, ignoreCase = true)) {
                            movie = it
                            break
                        }
                    }
                }

                if (movie == null) {
                    movie = items.optJSONObject(0)
                }

                if (movie == null || !movie.has("streams")) return@use emptyList()
                val streams = movie.optJSONObject("streams") ?: return@use emptyList()

                val results = mutableListOf<ScraperStreamResult>()
                val keys = streams.keys()

                while (keys.hasNext()) {
                    val quality = keys.next()
                    val streamUrl = streams.optString(quality)
                    if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                        val isHls = streamUrl.contains(".m3u8")
                        val headers = mapOf("User-Agent" to UA)

                        results.add(
                            ScraperStreamResult(
                                name = "PlayTorrioHTTP",
                                title = "LMScript · $quality",
                                description = "LMScript Stream · ${if (isHls) "HLS" else "MP4"}",
                                url = streamUrl,
                                quality = quality,
                                headers = headers
                            )
                        )
                    }
                }

                results
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping LMScript: ${e.message}")
            emptyList()
        }
    }
}
