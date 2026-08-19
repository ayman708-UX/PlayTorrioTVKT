package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object MultiEmbedExtractor {
    private const val TAG = "MultiEmbedExtractor"
    private const val EMBED_BASE = "https://www.2embed.cc"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun extract(
        title: String,
        isMovie: Boolean,
        year: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        onStreamFound: (HttpStreamResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val resolvedTmdbId = tmdbId ?: TmdbIdResolver.resolveTmdbId(
                imdbId = imdbId,
                title = title,
                isMovie = isMovie,
                year = year
            ) ?: return@withContext

            val s = seasonNumber ?: 1
            val e = episodeNumber ?: 1

            val embedPath = if (!isMovie) {
                "/embedtv/$resolvedTmdbId&s=$s&e=$e"
            } else {
                if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) "/embed/$imdbId" else "/embed/$resolvedTmdbId"
            }

            val req = Request.Builder()
                .url("$EMBED_BASE$embedPath")
                .header("User-Agent", UA)
                .header("Referer", "$EMBED_BASE/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext

            val serverUrls = mutableListOf<String>()
            val onclickMatcher = Pattern.compile("onclick=\"go\\('([^']+)'\\)\"").matcher(html)
            while (onclickMatcher.find()) {
                val url = onclickMatcher.group(1)
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    serverUrls.add(url)
                }
            }

            val dataSrcMatcher = Pattern.compile("data-src=\"([^\"]+)\"").matcher(html)
            if (dataSrcMatcher.find()) {
                val dUrl = dataSrcMatcher.group(1)
                if (!dUrl.isNullOrBlank() && dUrl.startsWith("http") && dUrl !in serverUrls) {
                    serverUrls.add(0, dUrl)
                }
            }

            var emittedCount = 0
            for (sUrl in serverUrls) {
                if (emittedCount >= 6) break
                if (sUrl.contains("/xps")) {
                    val pImdb = imdbId ?: ""
                    val pTmdb = resolvedTmdbId.toString()

                    val xpsPageUrl = if (!isMovie) {
                        "https://play.xpass.top/e/tv/$pTmdb/$s/$e?autostart=true"
                    } else {
                        "https://play.xpass.top/e/movie/$pImdb?autostart=true"
                    }

                    try {
                        val xpsReq = Request.Builder()
                            .url(xpsPageUrl)
                            .header("User-Agent", UA)
                            .header("Referer", "https://streamsrcs.2embed.cc/")
                            .build()

                        val xpsHtml = httpClient.newCall(xpsReq).execute().use { r ->
                            if (r.isSuccessful) r.body?.string() else null
                        } ?: continue

                        val dataMatcher = Pattern.compile("var data\\s*=\\s*(\\{.*?\\});").matcher(xpsHtml)
                        var playlistPath: String? = null
                        if (dataMatcher.find()) {
                            try {
                                val dObj = JSONObject(dataMatcher.group(1) ?: "")
                                playlistPath = dObj.optString("playlist")
                            } catch (_: Exception) {}
                        }

                        if (playlistPath.isNullOrBlank()) {
                            val plMatcher = Pattern.compile("\"playlist\"\\s*:\\s*\"([^\"]+)\"").matcher(xpsHtml)
                            if (plMatcher.find()) {
                                playlistPath = plMatcher.group(1)
                            }
                        }

                        if (!playlistPath.isNullOrBlank()) {
                            val playlistUrl = if (playlistPath.startsWith("http")) {
                                playlistPath
                            } else {
                                "https://play.xpass.top${if (playlistPath.startsWith("/")) "" else "/"}$playlistPath"
                            }

                            val plReq = Request.Builder()
                                .url(playlistUrl)
                                .header("User-Agent", UA)
                                .header("Referer", xpsPageUrl)
                                .header("Origin", "https://play.xpass.top")
                                .header("Accept", "application/json,*/*")
                                .build()

                            httpClient.newCall(plReq).execute().use { plResp ->
                                if (plResp.isSuccessful) {
                                    val plBody = plResp.body?.string() ?: ""
                                    val plJson = JSONObject(plBody)
                                    val playlists = plJson.optJSONArray("playlist")
                                    if (playlists != null) {
                                        for (pIdx in 0 until playlists.length()) {
                                            val pItem = playlists.optJSONObject(pIdx) ?: continue
                                            val srcList = pItem.optJSONArray("sources") ?: continue
                                            for (srcIdx in 0 until srcList.length()) {
                                                val src = srcList.optJSONObject(srcIdx) ?: continue
                                                val file = src.optString("file")
                                                if (file.startsWith("http") &&
                                                    !file.contains("/error") &&
                                                    !file.contains(".txt") &&
                                                    (file.contains(".m3u8") || file.contains(".mp4") || file.contains("/playlist/"))
                                                ) {
                                                    val label = src.optString("label").ifEmpty { "Auto" }
                                                    val stream = HttpStreamResult(
                                                        sourceName = "2embed (XPS)",
                                                        title = "2embed · $label",
                                                        description = "2embed Multi-CDN Stream",
                                                        url = file,
                                                        headers = mapOf(
                                                            "User-Agent" to UA,
                                                            "Referer" to "https://play.xpass.top/"
                                                        ),
                                                        quality = label
                                                    )
                                                    onStreamFound(stream)
                                                    emittedCount++
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MultiEmbed XPS error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MultiEmbed extraction failed for $title", e)
        }
    }
}
