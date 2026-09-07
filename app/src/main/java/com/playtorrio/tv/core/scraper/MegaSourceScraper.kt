package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin MegaSource Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla MegaSource provider.
 * Resolves streams via megasource.wasmer.app Stremio manifest pipeline.
 */
class MegaSourceScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MegaSourceScraper"
        private const val CONFIG =
            "W3siaWQiOiJkZWZhdWx0IiwibmFtZSI6Ik1lZ2FTb3VyY2UgZGVmYXVsdCIsInVybCI6Imh0dHBzOi8vZ2l0aHViLmNvbS96b3JldS9tZWdhc291cmNlX3NjcmFwZXJzL3Jhdy9yZWZzL2hlYWRzL21haW4vZGVmYXVsdF9zY3JhcGVyLnB5IiwiZGVzY3JpcHRpb24iOiJEZWZhdWx0IHNjcmFwZXIgaG9zdGVkIG9uIEdpdEh1Yi4ifV0"
        private const val BASE_URL = "https://megasource.wasmer.app/$CONFIG"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"

        try {
            var resolvedImdbId = request.imdbId
            if (resolvedImdbId == null || !resolvedImdbId.startsWith("tt")) {
                val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                    imdbId = request.imdbId,
                    title = request.title,
                    type = if (isTv) "tv" else "movie",
                    year = request.year
                )

                if (tmdbId != null) {
                    try {
                        val endpoint = if (isTv) {
                            "https://api.themoviedb.org/3/tv/$tmdbId/external_ids?api_key=b3556f3b206e16f82df4d1f6fd4545e6"
                        } else {
                            "https://api.themoviedb.org/3/movie/$tmdbId?api_key=b3556f3b206e16f82df4d1f6fd4545e6"
                        }

                        val req = Request.Builder().url(endpoint).build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                val json = JSONObject(body)
                                val imdb = json.optString("imdb_id")
                                if (imdb.isNotBlank()) resolvedImdbId = imdb
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (resolvedImdbId == null || !resolvedImdbId!!.startsWith("tt")) return@withContext emptyList()

            val path = if (isTv) {
                "/stream/series/$resolvedImdbId:${request.season ?: 1}:${request.episode ?: 1}.json"
            } else {
                "/stream/movie/$resolvedImdbId.json"
            }

            val req = Request.Builder()
                .url("$BASE_URL$path")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val data = JSONObject(body)
                if (!data.has("streams")) return@use

                val streams = data.getJSONArray("streams")
                for (i in 0 until streams.length()) {
                    val st = streams.optJSONObject(i) ?: continue
                    val streamUrl = st.optString("url")
                    if (streamUrl.isBlank() || !streamUrl.startsWith("http")) continue

                    val stTitle = st.optString("title", "Stream").ifEmpty { "Stream" }
                    val titleLabel = "MegaSource · $stTitle"

                    var reqHeaders: Map<String, String>? = null
                    val bHints = st.optJSONObject("behaviorHints")
                    if (bHints != null) {
                        val pHeaders = bHints.optJSONObject("proxyHeaders")
                        val reqMap = pHeaders?.optJSONObject("request")
                        if (reqMap != null) {
                            val map = mutableMapOf<String, String>()
                            val keys = reqMap.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                map[k] = reqMap.optString(k)
                            }
                            reqHeaders = map
                        }
                    }

                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = titleLabel,
                            description = "MegaSource HLS Stream",
                            url = streamUrl,
                            quality = "1080p",
                            headers = reqHeaders
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping MegaSource: ${e.message}")
            emptyList()
        }
    }
}
