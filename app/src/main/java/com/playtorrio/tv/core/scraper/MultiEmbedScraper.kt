package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MultiEmbedScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MultiEmbedScraper"
        private const val EMBED_BASE = "https://www.2embed.cc"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val headers = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://www.2embed.cc/",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        ) ?: return@withContext emptyList()

        try {
            val s = request.season ?: 1
            val e = request.episode ?: 1
            val embedPath = if (isTv) {
                "/embedtv/$tmdbId&s=$s&e=$e"
            } else {
                if (!request.imdbId.isNullOrBlank() && request.imdbId.startsWith("tt")) "/embed/${request.imdbId}" else "/embed/$tmdbId"
            }

            val embedReq = Request.Builder()
                .url("$EMBED_BASE$embedPath")
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val html = httpClient.newCall(embedReq).execute().use { it.body?.string().orEmpty() }
            if (html.isBlank()) return@withContext emptyList()

            val onclickRegex = Regex("""onclick=["']go\('([^']+)'\)["']""")
            val serverUrls = mutableListOf<String>()
            onclickRegex.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (url.startsWith("http")) serverUrls.add(url)
            }

            val datasrcMatch = Regex("""data-src="([^"]+)"""").find(html)
            if (datasrcMatch != null) {
                val dUrl = datasrcMatch.groupValues[1]
                if (dUrl.startsWith("http") && !serverUrls.contains(dUrl)) {
                    serverUrls.add(0, dUrl)
                }
            }

            for (sUrl in serverUrls) {
                if (results.size >= 6) break
                if (sUrl.contains("/xps")) {
                    val pImdb = request.imdbId.orEmpty()
                    val pTmdb = tmdbId.toString()
                    val xpsPageUrl = if (isTv) {
                        "https://play.xpass.top/e/tv/$pTmdb/$s/$e?autostart=true"
                    } else {
                        "https://play.xpass.top/e/movie/$pImdb?autostart=true"
                    }

                    try {
                        val xpsReq = Request.Builder()
                            .url(xpsPageUrl)
                            .addHeader("User-Agent", UA)
                            .addHeader("Referer", "https://streamsrcs.2embed.cc/")
                            .build()

                        val xpsHtml = httpClient.newCall(xpsReq).execute().use { it.body?.string().orEmpty() }
                        var playlistPath: String? = null

                        val dataMatch = Regex("""var data\s*=\s*(\{.*?\});""").find(xpsHtml)
                        if (dataMatch != null) {
                            try {
                                val dataObj = JSONObject(dataMatch.groupValues[1])
                                playlistPath = dataObj.optString("playlist")
                            } catch (_: Exception) {}
                        }

                        if (playlistPath.isNullOrBlank()) {
                            playlistPath = Regex(""""playlist"\s*:\s*"([^"]+)"""").find(xpsHtml)?.groupValues?.get(1)
                        }

                        if (!playlistPath.isNullOrBlank()) {
                            val playlistUrl = if (playlistPath.startsWith("http")) playlistPath else "https://play.xpass.top/${playlistPath.trimStart('/')}"
                            val plReq = Request.Builder()
                                .url(playlistUrl)
                                .addHeader("User-Agent", UA)
                                .addHeader("Referer", xpsPageUrl)
                                .addHeader("Origin", "https://play.xpass.top")
                                .addHeader("Accept", "application/json,*/*")
                                .build()

                            httpClient.newCall(plReq).execute().use { plRes ->
                                if (plRes.isSuccessful) {
                                    val plJson = JSONObject(plRes.body?.string().orEmpty())
                                    val playlists = plJson.optJSONArray("playlist")
                                    if (playlists != null) {
                                        for (pIdx in 0 until playlists.length()) {
                                            val plItem = playlists.getJSONObject(pIdx)
                                            val srcList = plItem.optJSONArray("sources")
                                            if (srcList != null) {
                                                for (srcIdx in 0 until srcList.length()) {
                                                    val src = srcList.getJSONObject(srcIdx)
                                                    val file = src.optString("file")
                                                    if (file.isNotBlank() && file.startsWith("http") && !file.contains("/error") && !file.contains(".txt")) {
                                                        val label = src.optString("label").ifEmpty { "Auto" }
                                                        results.add(
                                                            ScraperStreamResult(
                                                                name = "PlayTorrioHTTP",
                                                                title = "2embed XPS · $label",
                                                                description = "2embed Multi-CDN Stream",
                                                                url = file,
                                                                quality = label,
                                                                headers = mapOf(
                                                                    "User-Agent" to UA,
                                                                    "Referer" to "https://play.xpass.top/"
                                                                )
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
                    } catch (e: Exception) {
                        Log.d(TAG, "MultiEmbed XPS error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "MultiEmbed error: ${e.message}")
        }

        results
    }
}
