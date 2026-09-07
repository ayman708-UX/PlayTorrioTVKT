package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VidGod Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidGod provider.
 * Leverages multi-worker clusters (Pulsar, Orion, Stellar) and cloud cache.
 */
class VidGodScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidGodScraper"
        private const val REDIS_URL = "https://vidnest-redis-fell-prism-rest.cloud.layerbase.dev/"
        private const val REDIS_AUTH = "Bearer ve8z9XSKatu74M7FjLU8eQ29"
        private const val ORIGIN = "https://vidgod.space"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private data class ServerConfig(
            val name: String,
            val key: String,
            val base: String,
            val param: String
        )

        private val SERVERS = listOf(
            ServerConfig("Pulsar", "pulsar", "https://vidnest-extractor.vividdubbing.workers.dev/api", "prime"),
            ServerConfig("Orion", "orion", "https://404-vidnest.lofiserver.workers.dev/api", "gama"),
            ServerConfig("Stellar", "stellar", "https://404-vidnest.lofiserver.workers.dev/api", "sigma")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun getFromCache(
            serverKey: String,
            type: String,
            tmdbId: Int,
            season: Int?,
            episode: Int?
        ): JSONObject? {
            try {
                val cacheKey = if (type == "tv") {
                    "$serverKey:$type:$tmdbId:${season ?: 1}:${episode ?: 1}"
                } else {
                    "$serverKey:$type:$tmdbId"
                }

                val jsonMedia = "application/json; charset=utf-8".toMediaType()
                val bodyArr = JSONArray().apply {
                    put("GET")
                    put(cacheKey)
                }

                val req = Request.Builder()
                    .url(REDIS_URL)
                    .header("Authorization", REDIS_AUTH)
                    .header("Content-Type", "application/json")
                    .header("Accept", "*/*")
                    .header("Origin", ORIGIN)
                    .header("Referer", "$ORIGIN/")
                    .header("User-Agent", UA)
                    .post(bodyArr.toString().toRequestBody(jsonMedia))
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val data = JSONObject(body)
                            val rawResult = data.opt("result")
                            val parsed: JSONObject? = when (rawResult) {
                                is JSONObject -> rawResult
                                is String -> if (rawResult.startsWith("{")) JSONObject(rawResult) else null
                                else -> null
                            }
                            if (parsed != null && parsed.has("streams") && parsed.getJSONArray("streams").length() > 0) {
                                return parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        private fun fetchFromWorker(
            srv: ServerConfig,
            type: String,
            tmdbId: Int,
            season: Int?,
            episode: Int?
        ): JSONObject? {
            try {
                val endpoint = if (type == "movie") {
                    "${srv.base}/movie/$tmdbId?server=${srv.param}"
                } else {
                    "${srv.base}/tv/$tmdbId/${season ?: 1}/${episode ?: 1}?server=${srv.param}"
                }

                val req = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Origin", ORIGIN)
                    .header("Referer", "$ORIGIN/")
                    .header("User-Agent", UA)
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val data = JSONObject(body)
                            if (data.has("streams") && data.getJSONArray("streams").length() > 0) {
                                return data
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
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

            val tasks = SERVERS.map { srv ->
                async {
                    var data = getFromCache(srv.key, mediaType, tmdbId, request.season, request.episode)
                    if (data == null) {
                        data = fetchFromWorker(srv, mediaType, tmdbId, request.season, request.episode)
                    }

                    val serverResults = mutableListOf<ScraperStreamResult>()
                    if (data != null && data.has("streams")) {
                        val streamsList = data.getJSONArray("streams")
                        for (i in 0 until streamsList.length()) {
                            val item = streamsList.optJSONObject(i) ?: continue
                            val streamUrl = item.optString("url")
                            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                val quality = item.optString("quality", "1080p").ifEmpty { "1080p" }

                                val headers = mapOf(
                                    "User-Agent" to UA,
                                    "Referer" to "$ORIGIN/",
                                    "Origin" to ORIGIN
                                )

                                serverResults.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = "VidGod · ${srv.name} · $quality",
                                        description = "VidGod Cloud Stream · $quality",
                                        url = streamUrl,
                                        quality = quality,
                                        headers = headers
                                    )
                                )
                            }
                        }
                    }
                    serverResults
                }
            }

            tasks.awaitAll().flatten()
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidGod: ${e.message}")
            emptyList()
        }
    }
}
