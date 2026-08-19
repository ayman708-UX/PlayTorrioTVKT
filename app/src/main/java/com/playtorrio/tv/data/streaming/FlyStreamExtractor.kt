package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Random
import java.util.concurrent.TimeUnit

object FlyStreamExtractor {
    private const val TAG = "FlyStreamExtractor"
    private const val API_BASE = "https://flystream.net"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun generateViewerId(): String {
        val random = Random()
        val sb = StringBuilder(32)
        for (i in 0 until 32) {
            sb.append(Integer.toHexString(random.nextInt(16)))
        }
        return sb.toString()
    }

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
            )

            val urlBuilder = "$API_BASE/api/streams".toHttpUrlOrNull()?.newBuilder() ?: return@withContext
            urlBuilder.addQueryParameter("type", if (isMovie) "movie" else "tv")
            urlBuilder.addQueryParameter("viewerId", generateViewerId())
            urlBuilder.addQueryParameter("title", title)
            if (resolvedTmdbId != null && resolvedTmdbId > 0) {
                urlBuilder.addQueryParameter("tmdbId", resolvedTmdbId.toString())
            }
            if (!imdbId.isNullOrBlank()) {
                urlBuilder.addQueryParameter("imdb", imdbId)
            }
            if (year != null && year > 0) {
                urlBuilder.addQueryParameter("year", year.toString())
            }
            if (!isMovie) {
                if (seasonNumber != null) urlBuilder.addQueryParameter("season", seasonNumber.toString())
                if (episodeNumber != null) urlBuilder.addQueryParameter("episode", episodeNumber.toString())
            }

            val targetUrl = urlBuilder.build()
            Log.i(TAG, "Querying FlyStream API: $targetUrl")

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://flystream.net/")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                Log.i(TAG, "FlyStream HTTP response code: ${resp.code}")
                if (!resp.isSuccessful) return@withContext
                val body = resp.body?.string() ?: return@withContext
                val json = JSONObject(body)
                val streams = json.optJSONArray("streams") ?: return@withContext
                Log.i(TAG, "FlyStream returned ${streams.length()} streams")

                for (i in 0 until streams.length()) {
                    val s = streams.optJSONObject(i) ?: continue
                    val rawUrl = s.optString("url")
                    if (rawUrl.isBlank()) continue
                    val streamUrl = if (rawUrl.startsWith("/")) "$API_BASE$rawUrl" else rawUrl
                    if (!streamUrl.startsWith("http")) continue

                    val quality = s.optString("quality").ifEmpty { "Auto" }
                    val codec = s.optString("videoCodec")
                    val size = s.optString("size")
                    val name = s.optString("name").ifEmpty { s.optString("title").ifEmpty { title } }

                    val descParts = mutableListOf<String>()
                    if (quality.isNotEmpty()) descParts.add(quality)
                    if (codec.isNotEmpty()) descParts.add(codec.uppercase())
                    if (size.isNotEmpty()) descParts.add(size)

                    val stream = HttpStreamResult(
                        sourceName = "FlyStream",
                        title = "FlyStream · $name",
                        description = if (descParts.isNotEmpty()) descParts.joinToString(" · ") else "FlyStream Direct HLS Stream",
                        url = streamUrl,
                        headers = mapOf(
                            "User-Agent" to UA,
                            "Referer" to "https://flystream.net/"
                        ),
                        quality = quality
                    )
                    onStreamFound(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FlyStream extraction failed for $title", e)
        }
    }
}
