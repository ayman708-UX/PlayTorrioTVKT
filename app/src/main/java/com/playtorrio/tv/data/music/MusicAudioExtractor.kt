package com.playtorrio.tv.data.music

import android.util.Log
import com.playtorrio.tv.data.trailer.TrailerPlaybackSource
import com.playtorrio.tv.data.trailer.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "MusicAudioExtractor"
private val VIDEO_ID_REGEX = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")

object MusicAudioExtractor {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Searches YouTube for "{songName} {artistName} lyrics",
     * extracts the first video ID, then uses [YouTubeExtractor] to get a playable source.
     */
    suspend fun extract(songName: String, artistName: String): TrailerPlaybackSource? =
        withContext(Dispatchers.IO) {
            try {
                val query = "$songName $artistName lyrics"
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.youtube.com/results?search_query=$encoded"

                Log.i(TAG, "Searching YT: $query")
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val html = http.newCall(request).execute().use { it.body?.string() ?: "" }
                val videoId = VIDEO_ID_REGEX.findAll(html)
                    .map { it.groupValues[1] }
                    .distinct()
                    .firstOrNull()

                if (videoId == null) {
                    Log.i(TAG, "No videoId found for: $query")
                    return@withContext null
                }

                Log.i(TAG, "Found videoId=$videoId, extracting audio...")
                YouTubeExtractor.extractPlaybackSource(
                    "https://www.youtube.com/watch?v=$videoId",
                    audioOnly = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Extract failed: ${e.message}")
                null
            }
        }
}
