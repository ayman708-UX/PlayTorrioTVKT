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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VidFast Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidFast provider.
 * Resolves movies and TV shows via vidfast.vc and enc-dec.app decryption pipeline.
 */
class VidFastScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidFastScraper"
        private const val DOMAIN = "https://vidfast.vc"
        private const val API_BASE = "https://enc-dec.app/api"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val DEFAULT_HEADERS = mapOf(
            "User-Agent" to UA,
            "Referer" to "$DOMAIN/",
            "Origin" to DOMAIN,
            "X-Requested-With" to "XMLHttpRequest"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

            val embedUrl = if (isTv) {
                "$DOMAIN/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}/"
            } else {
                "$DOMAIN/movie/$tmdbId/"
            }

            val pageReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", UA)
                .header("Referer", "$DOMAIN/")
                .build()

            val rawToken = httpClient.newCall(pageReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val m = Regex("""\\"(?:en|token)\\":\\"(.*?)\\"""").find(html)
                    ?: Regex(""""(?:en|token)":"(.*?)"""").find(html)
                m?.groupValues?.get(1)
            } ?: return@withContext emptyList()

            val encReq = Request.Builder()
                .url("$API_BASE/enc-vidfast?text=${URLEncoder.encode(rawToken, "UTF-8")}")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            val (serversUrl, streamUrl, csrfToken) = httpClient.newCall(encReq).execute().use { res ->
                if (!res.isSuccessful) return@use Triple(null, null, null)
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use Triple(null, null, null)
                val data = JSONObject(body)
                if (data.optInt("status") != 200) return@use Triple(null, null, null)
                val result = data.optJSONObject("result") ?: return@use Triple(null, null, null)
                Triple(
                    result.optString("servers").ifEmpty { null },
                    result.optString("stream").ifEmpty { null },
                    result.optString("token").ifEmpty { null }
                )
            }

            if (serversUrl == null || streamUrl == null || csrfToken == null) return@withContext emptyList()

            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            val serversEncReq = Request.Builder()
                .url(serversUrl)
                .apply { DEFAULT_HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("X-CSRF-Token", csrfToken)
                .post("".toRequestBody(jsonMedia))
                .build()

            val serversEncBody = httpClient.newCall(serversEncReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (serversEncBody.isEmpty()) return@withContext emptyList()

            val decServersPayload = JSONObject().apply {
                put("text", serversEncBody)
            }

            val decServersReq = Request.Builder()
                .url("$API_BASE/dec-vidfast")
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .post(decServersPayload.toString().toRequestBody(jsonMedia))
                .build()

            val serversList = httpClient.newCall(decServersReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val data = JSONObject(body)
                data.optJSONArray("result")
            } ?: return@withContext emptyList()

            val tasks = (0 until serversList.length()).map { i ->
                async {
                    try {
                        val srv = serversList.optJSONObject(i) ?: return@async null
                        val srvName = srv.optString("name", "Server").ifEmpty { "Server" }
                        val srvData = srv.optString("data")
                        if (srvData.isEmpty()) return@async null

                        val sEncReq = Request.Builder()
                            .url("$streamUrl/$srvData")
                            .apply { DEFAULT_HEADERS.forEach { (k, v) -> header(k, v) } }
                            .header("X-CSRF-Token", csrfToken)
                            .post("".toRequestBody(jsonMedia))
                            .build()

                        val sEncBody = httpClient.newCall(sEncReq).execute().use { res ->
                            if (res.isSuccessful) res.body?.string().orEmpty() else ""
                        }

                        if (sEncBody.isEmpty()) return@async null

                        val decStreamPayload = JSONObject().apply {
                            put("text", sEncBody)
                        }

                        val decStreamReq = Request.Builder()
                            .url("$API_BASE/dec-vidfast")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", UA)
                            .post(decStreamPayload.toString().toRequestBody(jsonMedia))
                            .build()

                        httpClient.newCall(decStreamReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val decData = JSONObject(body)
                                    if (decData.optInt("status") == 200 && decData.has("result")) {
                                        val finalUrl = decData.getJSONObject("result").optString("url")
                                        if (finalUrl.isNotBlank()) {
                                            val is4K = srvName.contains("2160", ignoreCase = true) ||
                                                    srvName.contains("4k", ignoreCase = true) ||
                                                    finalUrl.contains("2160p")
                                            val qualityLabel = if (is4K) "4K" else "1080p"

                                            val headers = mapOf(
                                                "User-Agent" to UA,
                                                "Referer" to "$DOMAIN/",
                                                "Origin" to DOMAIN
                                            )

                                            return@async ScraperStreamResult(
                                                name = "PlayTorrioHTTP",
                                                title = "VidFast · $srvName · $qualityLabel",
                                                description = "VidFast Multi-CDN Stream · $qualityLabel",
                                                url = finalUrl,
                                                quality = qualityLabel,
                                                headers = headers
                                            )
                                        }
                                    }
                                }
                            }
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            tasks.awaitAll().filterNotNull()
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidFast: ${e.message}")
            emptyList()
        }
    }
}
