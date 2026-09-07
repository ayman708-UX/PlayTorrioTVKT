package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MovieNightScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MovieNightScraper"
        private const val BASE = "https://movienig.ht"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val headers = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://movienig.ht/",
            "Origin" to "https://movienig.ht",
            "Accept" to "text/event-stream"
        )

        private val priorityServers = listOf(
            mapOf("id" to "dallas", "label" to "Dallas 4K"),
            mapOf("id" to "austin", "label" to "Austin"),
            mapOf("id" to "helena", "label" to "Helena"),
            mapOf("id" to "seattle", "label" to "Seattle 4K"),
            mapOf("id" to "vixsrc-1", "label" to "Newport Beach"),
            mapOf("id" to "tucson", "label" to "Tucson"),
            mapOf("id" to "salem", "label" to "Salem")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        )

        val idToUse = tmdbId?.toString() ?: request.imdbId
        if (idToUse.isNullOrBlank()) return@withContext emptyList()

        val encTitle = URLEncoder.encode(request.title, "UTF-8")
        val yearQuery = if (request.year != null) "&year=${request.year}" else ""
        val imdbQuery = if (!request.imdbId.isNullOrBlank()) "&imdbId=${request.imdbId}" else ""

        val serverTasks = priorityServers.map { server ->
            async {
                val serverId = server["id"]!!
                val serverLabel = server["label"]!!
                val results = mutableListOf<ScraperStreamResult>()

                try {
                    val url = if (isTv) {
                        val s = request.season ?: 1
                        val e = request.episode ?: 1
                        "$BASE/api/stream/v1/tv/$idToUse/$s/$e?title=$encTitle$yearQuery$imdbQuery&server=$serverId&only=1"
                    } else {
                        "$BASE/api/stream/v1/movie/$idToUse?title=$encTitle$yearQuery$imdbQuery&server=$serverId&only=1"
                    }

                    val req = Request.Builder()
                        .url(url)
                        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                    httpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string().orEmpty()
                            if (bodyStr.contains("event: done")) {
                                val doneIdx = bodyStr.indexOf("event: done")
                                val dataIdx = bodyStr.indexOf("data: ", doneIdx)
                                if (dataIdx != -1) {
                                    val jsonStart = dataIdx + 6
                                    val jsonEnd = bodyStr.indexOf("\n", jsonStart)
                                    val jsonText = (if (jsonEnd != -1) bodyStr.substring(jsonStart, jsonEnd) else bodyStr.substring(jsonStart)).trim()
                                    val json = JSONObject(jsonText)
                                    val sources = json.optJSONArray("sources")
                                    if (sources != null) {
                                        for (i in 0 until sources.length()) {
                                            val src = sources.getJSONObject(i)
                                            val rawUrl = src.optString("url")
                                            if (rawUrl.isBlank() || !rawUrl.startsWith("http")) continue

                                            val quality = src.optString("quality").ifEmpty { "Auto" }
                                            val titleQuality = if (quality != "Auto") " ($quality)" else ""

                                            results.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "MovieNight $serverLabel$titleQuality",
                                                    description = "MovieNight · $serverLabel · Quality: $quality HLS",
                                                    url = rawUrl,
                                                    quality = quality,
                                                    headers = mapOf(
                                                        "User-Agent" to UA,
                                                        "Referer" to "https://movienig.ht/"
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "MovieNight error on $serverId: ${e.message}")
                }

                results
            }
        }

        serverTasks.awaitAll().flatten()
    }
}
