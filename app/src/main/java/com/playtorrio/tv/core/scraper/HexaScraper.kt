package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin Hexa Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla Hexa provider.
 * Resolves HLS and MP4 sources from hexa.su / flixer.su via enc-dec.app.
 */
class HexaScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "HexaScraper"
        private const val API_BASE = "https://enc-dec.app/api"
        private val DOMAINS = listOf("hexa.su", "flixer.su")
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private fun randomApiKey(): String {
            val rng = SecureRandom()
            val bytes = ByteArray(32)
            rng.nextBytes(bytes)
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        private fun getChallengeToken(): String? {
            try {
                val req = Request.Builder()
                    .url("$API_BASE/enc-hexa")
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val data = JSONObject(body)
                        if (data.optInt("status") == 200) {
                            val result = data.optJSONObject("result")
                            return result?.optString("token")
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

            val capToken = getChallengeToken() ?: return@withContext emptyList()
            val apiKey = randomApiKey()

            var decrypted: JSONObject? = null

            for (domain in DOMAINS) {
                try {
                    val url = if (isTv) {
                        "https://theemoviedb.$domain/api/tmdb/tv/$tmdbId/season/${request.season ?: 1}/episode/${request.episode ?: 1}/images"
                    } else {
                        "https://theemoviedb.$domain/api/tmdb/movie/$tmdbId/images"
                    }

                    val encReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", UA)
                        .header("Referer", "https://$domain/")
                        .header("Accept", "text/plain")
                        .header("X-Fingerprint-Lite", "e9136c41504646444")
                        .header("X-Api-Key", apiKey)
                        .header("X-Cap-Token", capToken)
                        .build()

                    val encBody = httpClient.newCall(encReq).execute().use { res ->
                        if (res.isSuccessful) res.body?.string().orEmpty() else ""
                    }

                    if (encBody.isEmpty()) continue

                    val decPayload = JSONObject().apply {
                        put("text", encBody)
                        put("key", apiKey)
                    }

                    val jsonMedia = "application/json; charset=utf-8".toMediaType()
                    val decReq = Request.Builder()
                        .url("$API_BASE/dec-hexa")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", UA)
                        .post(decPayload.toString().toRequestBody(jsonMedia))
                        .build()

                    httpClient.newCall(decReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val data = JSONObject(body)
                            if (data.optInt("status") == 200) {
                                val result = data.optJSONObject("result")
                                if (result != null && result.has("sources") && result.getJSONArray("sources").length() > 0) {
                                    decrypted = result
                                }
                            }
                        }
                    }

                    if (decrypted != null) break
                } catch (_: Exception) {}
            }

            if (decrypted == null || !decrypted!!.has("sources")) return@withContext emptyList()

            val sources = decrypted!!.getJSONArray("sources")
            val results = mutableListOf<ScraperStreamResult>()

            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i)
                val streamUrl = src.optString("url")
                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                    val sName = src.optString("name").ifEmpty { src.optString("server", "Server") }
                    val quality = src.optString("quality", "1080p").ifEmpty { "1080p" }

                    val headers = mapOf(
                        "User-Agent" to UA,
                        "Referer" to "https://${DOMAINS.first()}/"
                    )

                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = "Hexa · $sName · $quality",
                            description = "Hexa Stream · $quality",
                            url = streamUrl,
                            quality = quality,
                            headers = headers
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping Hexa: ${e.message}")
            emptyList()
        }
    }
}
