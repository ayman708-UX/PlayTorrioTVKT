package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VidCoreScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidCoreScraper"
        private val API_BASES = listOf("https://www.vidcore.org", "https://vidcore.org")
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        val seenUrls = mutableSetOf<String>()

        for (base in API_BASES) {
            try {
                val urlBuilder = "$base/api/sources".toHttpUrl().newBuilder()
                    .addQueryParameter("id", tmdbId.toString())
                    .addQueryParameter("type", mediaType)

                if (isTv) {
                    if (request.season != null) urlBuilder.addQueryParameter("season", request.season.toString())
                    if (request.episode != null) urlBuilder.addQueryParameter("episode", request.episode.toString())
                }

                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "$base/embed/movie/$tmdbId")
                    .addHeader("Origin", base)
                    .addHeader("Accept", "application/json")
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string().orEmpty())
                        val outerSources = json.optJSONArray("sources")
                        if (outerSources != null) {
                            for (i in 0 until outerSources.length()) {
                                val o = outerSources.getJSONObject(i)
                                val label = o.optString("label").ifEmpty {
                                    o.optString("provider").ifEmpty { o.optString("server").ifEmpty { "VidCore" } }
                                }
                                val quality = o.optString("quality").ifEmpty { "Auto" }

                                var inners = o.optJSONArray("sources")
                                if (inners == null && o.has("data")) {
                                    inners = o.optJSONObject("data")?.optJSONArray("sources")
                                }

                                if (inners != null) {
                                    for (j in 0 until inners.length()) {
                                        val s = inners.getJSONObject(j)
                                        val sUrl = s.optString("url")
                                        if (sUrl.isNotBlank() && sUrl.startsWith("http") && seenUrls.add(sUrl)) {
                                            val sQuality = s.optString("quality").ifEmpty { quality }
                                            results.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "VidCore $label · $sQuality",
                                                    description = "VidCore Direct Stream Source",
                                                    url = sUrl,
                                                    quality = sQuality,
                                                    headers = mapOf(
                                                        "User-Agent" to UA,
                                                        "Referer" to "$base/"
                                                    )
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    val sUrl = o.optString("url")
                                    if (sUrl.isNotBlank() && sUrl.startsWith("http") && seenUrls.add(sUrl)) {
                                        results.add(
                                            ScraperStreamResult(
                                                name = "PlayTorrioHTTP",
                                                title = "VidCore $label · $quality",
                                                description = "VidCore Direct Stream Source",
                                                url = sUrl,
                                                quality = quality,
                                                headers = mapOf(
                                                    "User-Agent" to UA,
                                                    "Referer" to "$base/"
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.d(TAG, "VidCore error on $base: ${e.message}")
            }
        }

        results
    }
}
