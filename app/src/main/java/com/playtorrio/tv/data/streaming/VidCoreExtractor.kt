package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VidCoreExtractor {
    private const val TAG = "VidCoreExtractor"
    private val API_BASES = listOf("https://www.vidcore.org", "https://vidcore.org")
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

            val seenUrls = mutableSetOf<String>()
            val skipped = mutableSetOf<String>()

            for (base in API_BASES) {
                Log.i(TAG, "Attempting VidCore base: $base (tmdb: $resolvedTmdbId)")
                val initialData = fetchSourcesRound(base, resolvedTmdbId, isMovie, seasonNumber, episodeNumber, null)
                if (initialData != null) {
                    extractAndAdd(initialData, base, seenUrls, skipped, onStreamFound)

                    for (round in 1..3) {
                        if (skipped.isEmpty() || skipped.size >= 12) break
                        val roundData = fetchSourcesRound(base, resolvedTmdbId, isMovie, seasonNumber, episodeNumber, skipped.joinToString(","))
                        if (roundData == null) break
                        extractAndAdd(roundData, base, seenUrls, skipped, onStreamFound)
                    }

                    if (seenUrls.isNotEmpty()) {
                        Log.i(TAG, "VidCore found ${seenUrls.size} streams from $base")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VidCore extraction failed for $title", e)
        }
    }

    private fun fetchSourcesRound(
        base: String,
        tmdbId: Int,
        isMovie: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        skip: String?
    ): JSONObject? {
        return try {
            val urlBuilder = "$base/api/sources".toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("id", tmdbId.toString())
            urlBuilder.addQueryParameter("type", if (isMovie) "movie" else "tv")
            if (!isMovie) {
                if (seasonNumber != null) urlBuilder.addQueryParameter("season", seasonNumber.toString())
                if (episodeNumber != null) urlBuilder.addQueryParameter("episode", episodeNumber.toString())
            }
            if (!skip.isNullOrBlank()) {
                urlBuilder.addQueryParameter("skip", skip)
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", UA)
                .header("Referer", "$base/embed/movie/$tmdbId")
                .header("Origin", base)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    JSONObject(body)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAndAdd(
        data: JSONObject,
        base: String,
        seenUrls: MutableSet<String>,
        skipped: MutableSet<String>,
        onStreamFound: (HttpStreamResult) -> Unit
    ) {
        val outerSources = data.optJSONArray("sources") ?: return
        for (i in 0 until outerSources.length()) {
            val o = outerSources.optJSONObject(i) ?: continue
            val label = o.optString("label").ifEmpty {
                o.optString("provider").ifEmpty {
                    o.optString("server").ifEmpty { "VidCore" }
                }
            }
            val rawLabel = o.optString("label")
            if (rawLabel.isNotEmpty()) skipped.add(rawLabel)

            val inners = mutableListOf<JSONObject>()
            val dataObj = o.optJSONObject("data")
            if (dataObj != null && dataObj.optJSONArray("sources") != null) {
                val sArr = dataObj.optJSONArray("sources")!!
                for (j in 0 until sArr.length()) sArr.optJSONObject(j)?.let { inners.add(it) }
            } else if (o.optJSONArray("sources") != null) {
                val sArr = o.optJSONArray("sources")!!
                for (j in 0 until sArr.length()) sArr.optJSONObject(j)?.let { inners.add(it) }
            } else if (o.optString("url").isNotEmpty()) {
                inners.add(o)
            }

            for (s in inners) {
                val streamUrl = s.optString("url")
                if (streamUrl.isBlank() || streamUrl in seenUrls || !streamUrl.startsWith("http")) continue
                seenUrls.add(streamUrl)

                val quality = s.optString("quality").ifEmpty { o.optString("quality").ifEmpty { "Auto" } }
                val isHls = streamUrl.contains(".m3u8")
                val isDash = streamUrl.contains(".mpd")

                val stream = HttpStreamResult(
                    sourceName = "VidCore ($label)",
                    title = "VidCore $label · $quality",
                    description = if (isHls) "VidCore Direct HLS Stream" else (if (isDash) "VidCore Direct DASH Stream" else "VidCore Direct Stream"),
                    url = streamUrl,
                    headers = mapOf(
                        "User-Agent" to UA,
                        "Referer" to "$base/"
                    ),
                    quality = quality
                )
                onStreamFound(stream)
            }
        }
    }
}
