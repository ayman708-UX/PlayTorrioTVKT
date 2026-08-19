package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VidSrcExtractor {
    private const val TAG = "VidSrcExtractor"
    private const val API_BASE = "https://data.vidsrcme.ru"
    private const val EMBED_BASE = "https://vidsrc.me"
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

            var foundAny = false

            // 1. Query API endpoint
            try {
                val urlBuilder = "$API_BASE/api.php".toHttpUrlOrNull()?.newBuilder() ?: return@withContext
                urlBuilder.addQueryParameter("type", if (isMovie) "movie" else "tv")
                urlBuilder.addQueryParameter("tmdb", resolvedTmdbId.toString())
                urlBuilder.addQueryParameter("stream_urls", "")
                if (!isMovie) {
                    if (seasonNumber != null) urlBuilder.addQueryParameter("season", seasonNumber.toString())
                    if (episodeNumber != null) urlBuilder.addQueryParameter("episode", episodeNumber.toString())
                }

                val req = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", UA)
                    .header("Referer", "https://cloudorchestranova.com/")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.optString("status_code") == "200") {
                            val data = json.optJSONObject("data")
                            val rawStreamUrls = data?.optJSONArray("stream_urls")
                            if (rawStreamUrls != null) {
                                for (i in 0 until rawStreamUrls.length()) {
                                    val streamUrl = rawStreamUrls.optString(i).trim()
                                    if (streamUrl.startsWith("http")) {
                                        val isHls = streamUrl.contains(".m3u8")
                                        val stream = HttpStreamResult(
                                            sourceName = "VidSrc",
                                            title = if (rawStreamUrls.length() > 1) "VidSrc Server ${i + 1}" else "VidSrc",
                                            description = if (isHls) "VidSrc Direct HLS Stream" else "VidSrc Direct Stream Source",
                                            url = streamUrl,
                                            headers = mapOf(
                                                "User-Agent" to UA,
                                                "Referer" to "https://cloudorchestranova.com/"
                                            )
                                        )
                                        onStreamFound(stream)
                                        foundAny = true
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "VidSrc API error", e)
            }

            // 2. Fallback to embed page
            if (!foundAny) {
                val embedUrl = if (!isMovie) {
                    "$EMBED_BASE/embed/tv/$resolvedTmdbId/${seasonNumber ?: 1}/${episodeNumber ?: 1}"
                } else {
                    "$EMBED_BASE/embed/movie/$resolvedTmdbId"
                }

                val embedReq = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", UA)
                    .header("Referer", "$EMBED_BASE/")
                    .build()

                httpClient.newCall(embedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val matcher = Pattern.compile("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").matcher(html)
                        while (matcher.find()) {
                            val streamUrl = matcher.group(0)
                            if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                                val stream = HttpStreamResult(
                                    sourceName = "VidSrc",
                                    title = "VidSrc Direct",
                                    description = "VidSrc Direct HLS Stream",
                                    url = streamUrl,
                                    headers = mapOf(
                                        "User-Agent" to UA,
                                        "Referer" to "$EMBED_BASE/"
                                    )
                                )
                                onStreamFound(stream)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VidSrc extraction failed for $title", e)
        }
    }
}
