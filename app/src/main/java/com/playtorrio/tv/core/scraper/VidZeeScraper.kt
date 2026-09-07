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

/**
 * Pure-Kotlin VidZee Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidZee provider.
 * Resolves cloud streams from core.vidzee.wtf across services (dcloud, ipcloud, tik).
 */
class VidZeeScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidZeeScraper"
        private const val BASE_URL = "https://core.vidzee.wtf"
        private const val PLAYER_URL = "https://player.vidzee.wtf"
        private val SERVICES = listOf("dcloud", "ipcloud", "tik")
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "$PLAYER_URL/",
            "Origin" to PLAYER_URL
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
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

            val tasks = SERVICES.map { service ->
                async {
                    try {
                        val directEndpoint = if (isTv) {
                            "$BASE_URL/streams/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}?s=$service"
                        } else {
                            "$BASE_URL/streams/movie/$tmdbId?s=$service"
                        }

                        val req = Request.Builder()
                            .url(directEndpoint)
                            .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                            .build()

                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val data = JSONObject(body)
                                    val streamUrl = data.optString("url")
                                    if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                        val rawLang = data.optString("language").trim()
                                        val isHls = streamUrl.contains(".m3u8")
                                        val hasValidLang = rawLang.isNotEmpty() && !rawLang.equals("auto", ignoreCase = true)

                                        val titleParts = mutableListOf("VidZee", service)
                                        if (hasValidLang) titleParts.add(rawLang)
                                        titleParts.add("1080p")

                                        val desc = if (hasValidLang) {
                                            "VidZee Stream · $rawLang · ${if (isHls) "HLS" else "MP4"}"
                                        } else {
                                            "VidZee Stream · ${if (isHls) "HLS" else "MP4"}"
                                        }

                                        val reqHeaders = mapOf(
                                            "User-Agent" to UA,
                                            "Referer" to "$PLAYER_URL/",
                                            "Origin" to PLAYER_URL
                                        )

                                        return@async ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = titleParts.joinToString(" · "),
                                            description = desc,
                                            url = streamUrl,
                                            quality = "1080p",
                                            headers = reqHeaders
                                        )
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
            Log.d(TAG, "Error scraping VidZee: ${e.message}")
            emptyList()
        }
    }
}
