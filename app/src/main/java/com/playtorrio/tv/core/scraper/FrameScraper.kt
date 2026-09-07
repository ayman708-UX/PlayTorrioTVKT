package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin Frame Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla Frame provider.
 * Resolves multi-provider streams (Zephyr, Atlas, Luna, Volt, Echo, Rift, Quill) via api.peestream.in.
 */
class FrameScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FrameScraper"
        private const val BASE_URL = "https://api.peestream.in"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val PROVIDERS = listOf(
            Pair("vaplayer", "Zephyr"),
            Pair("castle", "Atlas"),
            Pair("hera", "Luna"),
            Pair("multivid", "Volt"),
            Pair("netmirror", "Echo"),
            Pair("vidsuper-castle", "Rift"),
            Pair("vidsuper-vixsrc", "Quill")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
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

            val tasks = PROVIDERS.map { (pId, pName) ->
                async {
                    val subResults = mutableListOf<ScraperStreamResult>()
                    try {
                        val httpUrlBuilder = "$BASE_URL/api/search".toHttpUrlOrNull()?.newBuilder()
                            ?: return@async subResults

                        httpUrlBuilder.addQueryParameter("q", tmdbId.toString())
                        httpUrlBuilder.addQueryParameter("type", mediaType)
                        httpUrlBuilder.addQueryParameter("provider", pId)
                        if (isTv) {
                            httpUrlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                            httpUrlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
                        }

                        val req = Request.Builder()
                            .url(httpUrlBuilder.build())
                            .header("User-Agent", UA)
                            .header("Accept", "application/json")
                            .header("Referer", "https://peestream.in/")
                            .build()

                        httpClient.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val json = JSONObject(body)
                                    if (json.has("results")) {
                                        val resultsArr = json.getJSONArray("results")
                                        for (i in 0 until resultsArr.length()) {
                                            val resObj = resultsArr.getJSONObject(i)
                                            if (resObj.has("streams")) {
                                                val streamsArr = resObj.getJSONArray("streams")
                                                for (j in 0 until streamsArr.length()) {
                                                    val st = streamsArr.getJSONObject(j)
                                                    val sUrl = st.optString("url")
                                                    if (sUrl.isNotBlank() && sUrl.startsWith("http")) {
                                                        val quality = st.optString("quality", "1080p").ifEmpty { "1080p" }
                                                        val isHls = st.optString("type") == "m3u8" || sUrl.contains(".m3u8")
                                                        val headers = mapOf("User-Agent" to UA)

                                                        subResults.add(
                                                            ScraperStreamResult(
                                                                name = "PlayTorrioHTTP",
                                                                title = "FRAME $pName · $quality",
                                                                description = "FRAME Stream · ${if (isHls) "HLS" else "MP4"}",
                                                                url = sUrl,
                                                                quality = quality,
                                                                headers = headers
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    subResults
                }
            }

            tasks.awaitAll().flatten()
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping Frame: ${e.message}")
            emptyList()
        }
    }
}
