package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin Dulo Stream Scraper for PlayTorrioHTTP.
 * Extracts direct multi-CDN HLS streams via Dulo SSE endpoint (/api/source).
 * Ported 1-to-1 from Vyla Dulo provider and dulo_client.dart.
 */
class DuloScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "DuloScraper"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val DOMAINS = listOf(
            "https://dulo.gd",
            "https://dulo.cx"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        @Volatile
        private var cachedSessionCookie: String? = null
        @Volatile
        private var sessionExpiry: Long = 0L

        private val PLAYBACK_HEADERS = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://d.dulo.gd/",
            "Origin" to "https://d.dulo.gd"
        )

        private fun getSessionCookie(domain: String): String? {
            val now = System.currentTimeMillis()
            if (cachedSessionCookie != null && now < sessionExpiry) {
                return cachedSessionCookie
            }

            try {
                val req = Request.Builder()
                    .url("$domain/api/session")
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .header("Referer", "$domain/")
                    .header("Origin", domain)
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val setCookieHeaders = response.headers("set-cookie")
                        for (setCookie in setCookieHeaders) {
                            val match = Regex("(__Host-amri_session=[^;]+)").find(setCookie)
                            val cookieVal = match?.value ?: setCookie.substringBefore(";")
                            if (cookieVal.contains("__Host-amri_session")) {
                                cachedSessionCookie = cookieVal
                                sessionExpiry = now + (6 * 3600 * 1000L) // 6 hours
                                return cookieVal
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Session fetch error ($domain): ${e.message}")
            }
            return null
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        val payload = JSONObject().apply {
            put("type", mediaType)
            put("tmdbId", tmdbId)
            if (isTv) {
                put("season", request.season ?: 1)
                put("episode", request.episode ?: 1)
            }
        }

        val results = mutableListOf<ScraperStreamResult>()
        val seenUrls = mutableSetOf<String>()

        for (domain in DOMAINS) {
            val cookie = getSessionCookie(domain)
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val reqBody = payload.toString().toRequestBody(jsonMedia)

            val reqBuilder = Request.Builder()
                .url("$domain/api/source")
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Referer", "$domain/")
                .header("Origin", domain)
                .post(reqBody)

            if (!cookie.isNullOrEmpty()) {
                reqBuilder.header("Cookie", cookie)
            }

            try {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.startsWith("data:")) {
                                val jsonPart = trimmed.substring(5).trim()
                                if (jsonPart.isNotEmpty() && jsonPart.startsWith("{")) {
                                    try {
                                        val data = JSONObject(jsonPart)
                                        if (data.has("sources")) {
                                            val rawSources = data.getJSONArray("sources")
                                            for (i in 0 until rawSources.length()) {
                                                val item = rawSources.getJSONObject(i)
                                                val sUrl = item.optString("url")
                                                if (sUrl.isNotBlank() && sUrl.startsWith("http") && seenUrls.add(sUrl)) {
                                                    val sTitle = item.optString("title", "Source").ifEmpty { "Source" }
                                                    val sQuality = item.optString("quality", "1080p").ifEmpty { "1080p" }
                                                    val qualityLabel = if (sQuality != "Auto") sQuality else "1080p"

                                                    results.add(
                                                        ScraperStreamResult(
                                                            name = "PlayTorrioHTTP",
                                                            title = "Dulo · $sTitle · $qualityLabel",
                                                            description = "Dulo Multi-CDN HLS Stream · $qualityLabel",
                                                            url = sUrl,
                                                            quality = qualityLabel,
                                                            headers = PLAYBACK_HEADERS
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Ignore incomplete SSE progress chunks
                                    }
                                }
                            } else if (trimmed.startsWith("event: complete")) {
                                break
                            }
                        }
                    } else if (response.code == 401 || response.code == 403) {
                        cachedSessionCookie = null
                        sessionExpiry = 0L
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "SSE extraction error ($domain): ${e.message}")
            }

            if (results.isNotEmpty()) break
        }

        results
    }
}
