package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class DownloadEverythingScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "DownloadEverythingScraper"
        private const val SLAVE_URL = "https://slave.downloadeverythingfromeverywhere.com/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Origin" to "https://downloadeverythingfromeverywhere.com",
            "Referer" to "https://downloadeverythingfromeverywhere.com/",
            "Content-Type" to "application/json"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        )

        try {
            val payload = JSONObject().apply {
                put("mode", if (isTv) "series" else "movie")
                put("title", request.title)
                if (request.year != null) put("year", request.year.toString())
                if (tmdbId != null) put("tmdb_id", tmdbId)
                if (!request.imdbId.isNullOrBlank()) put("imdb_id", request.imdbId)
                if (isTv && request.season != null) put("season", request.season)
                if (isTv && request.episode != null) put("episode", request.episode)
            }

            val mediaType = "application/json".toMediaType()
            val req = Request.Builder()
                .url(SLAVE_URL)
                .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim().orEmpty()
                        if (trimmed.isEmpty()) continue
                        try {
                            val parsed = JSONObject(trimmed)
                            if (parsed.optString("t") == "hit") {
                                val site = parsed.optString("site").ifEmpty { "DownloadEverything" }
                                val links = parsed.optJSONArray("links")
                                if (links != null) {
                                    for (i in 0 until links.length()) {
                                        val lObj = links.getJSONObject(i)
                                        val rawUrl = lObj.optString("url")
                                        if (rawUrl.isBlank()) continue

                                        if (rawUrl.contains("111477.xyz") || rawUrl.contains("vadapav.mov")) continue

                                        var directUrl: String? = null
                                        var provider = site
                                        var streamHeaders = mapOf("User-Agent" to UA)

                                        if (rawUrl.contains("hakunaymatata.com")) {
                                            directUrl = rawUrl
                                            provider = "Moviebox"
                                            streamHeaders = mapOf("User-Agent" to "Lavf/60.16.100")
                                        } else if (rawUrl.contains("pixeldrain.")) {
                                            val m = Regex("""pixeldrain\.(?:dev|com)/(?:u|l)/([a-zA-Z0-9_-]+)""").find(rawUrl)
                                            if (m != null) {
                                                directUrl = "https://pixeldrain.com/api/file/${m.groupValues[1]}"
                                                provider = "Pixeldrain"
                                            }
                                        } else if (rawUrl.endsWith(".mp4", ignoreCase = true) || rawUrl.endsWith(".mkv", ignoreCase = true)) {
                                            directUrl = rawUrl
                                        }

                                        if (!directUrl.isNullOrBlank()) {
                                            val name = lObj.optString("name").ifEmpty { "Direct Stream" }
                                            val quality = if (name.contains("2160") || name.contains("4k", ignoreCase = true)) "4K"
                                            else if (name.contains("1080")) "1080p"
                                            else if (name.contains("720")) "720p" else "Auto"

                                            sources.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "[DLE - $provider] $quality",
                                                    description = "$provider • $quality • $name",
                                                    url = directUrl,
                                                    quality = quality,
                                                    headers = streamHeaders
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DownloadEverything error: ${e.message}")
        }

        sources
    }
}
