package com.playtorrio.tv.core.music

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.playtorrio.tv.core.music.model.MusicRow
import com.playtorrio.tv.core.music.model.MusicTrack
import com.playtorrio.tv.core.network.IPv4FirstDns
import com.playtorrio.tv.data.trailer.InAppYouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InnerTubeMusicService"
private const val SONGS_SEARCH_PARAMS = "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ=="

@Singleton
class InnerTubeMusicService @Inject constructor(
    private val inAppYouTubeExtractor: InAppYouTubeExtractor
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Audio stream cache: videoId -> Pair<streamUrl, timestamp>
    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours

    /**
     * Search songs on YouTube Music using InnerTube.
     */
    suspend fun searchSongs(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val contextJson = JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientName", "WEB_REMIX")
                    addProperty("clientVersion", "1.20240101.01.00")
                    addProperty("hl", "en")
                    addProperty("gl", "US")
                })
            }
            val payload = JsonObject().apply {
                add("context", contextJson)
                addProperty("query", query.trim())
                addProperty("params", SONGS_SEARCH_PARAMS)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search request failed with code: ${response.code}")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                parseSearchResults(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs for query '$query': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetch the default home shelves with top charts, trending, and curated playlists.
     */
    suspend fun getHomeShelves(): List<MusicRow> = coroutineScope {
        val categories = listOf(
            "Trending Hits" to "Top Hits",
            "Top Global Charts" to "Billboard Hot 100",
            "New Releases" to "New Music Friday",
            "Pop & Dance" to "Pop Dance Hits",
            "Hip-Hop & R&B" to "Hip Hop Hits",
            "Rock & Alternative" to "Rock Classics",
            "Lo-Fi & Chill" to "Lo-Fi Chill Beats"
        )

        val deferredRows = categories.map { (title, query) ->
            async(Dispatchers.IO) {
                val tracks = searchSongs(query)
                if (tracks.isNotEmpty()) {
                    MusicRow(title = title, tracks = tracks)
                } else null
            }
        }

        deferredRows.awaitAll().filterNotNull()
    }

    /**
     * Resolve playable audio stream URL for a given YouTube videoId.
     * Uses the exact same in-app YouTube trailer extractor that works reliably in the app.
     */
    suspend fun resolveAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        // Check in-memory cache
        val cached = streamCache[videoId]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached audio stream for $videoId")
            return@withContext cached.first
        }

        // Method 1: Use inAppYouTubeExtractor (same engine as working trailers)
        try {
            val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
            val source = inAppYouTubeExtractor.extractPlaybackSource(youtubeUrl)
            val streamUrl = source?.audioUrl ?: source?.videoUrl
            if (!streamUrl.isNullOrBlank()) {
                streamCache[videoId] = streamUrl to System.currentTimeMillis()
                Log.i(TAG, "Resolved audio via trailer extractor for $videoId: $streamUrl")
                return@withContext streamUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Trailer extractor method failed for $videoId: ${e.message}")
        }

        // Method 2: Dedicated InAppYouTubeExtractor audio stream extraction
        try {
            val audioUrl = inAppYouTubeExtractor.extractAudioStream(videoId)
            if (!audioUrl.isNullOrBlank()) {
                streamCache[videoId] = audioUrl to System.currentTimeMillis()
                Log.i(TAG, "Resolved audio via extractAudioStream for $videoId: $audioUrl")
                return@withContext audioUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractAudioStream fallback failed for $videoId: ${e.message}")
        }

        Log.e(TAG, "All audio resolution methods failed for $videoId")
        null
    }

    private fun parseSearchResults(jsonString: String): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return emptyList()

            for (section in contents) {
                val shelf = section.asJsonObject.getAsJsonObject("musicShelfRenderer") ?: continue
                val items = shelf.getAsJsonArray("contents") ?: continue

                for (item in items) {
                    val responsiveItem = item.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue

                    val title = parseFlexColumnText(responsiveItem, 0)
                    val subtitleRuns = parseFlexColumnRuns(responsiveItem, 1)

                    val artist = subtitleRuns.getOrNull(0) ?: "Unknown Artist"
                    val album = if (subtitleRuns.size > 2) subtitleRuns.getOrNull(2) else null
                    val durationText = if (subtitleRuns.isNotEmpty()) subtitleRuns.last() else null
                    val durationSeconds = parseDurationToSeconds(durationText)

                    val videoId = responsiveItem.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
                        ?: responsiveItem.getAsJsonObject("overlay")
                            ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                            ?.getAsJsonObject("content")
                            ?.getAsJsonObject("musicPlayButtonRenderer")
                            ?.getAsJsonObject("playNavigationEndpoint")
                            ?.getAsJsonObject("watchEndpoint")
                            ?.get("videoId")?.asString

                    if (!videoId.isNullOrBlank() && title.isNotBlank()) {
                        val thumbnails = responsiveItem.getAsJsonObject("thumbnail")
                            ?.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")
                            ?.getAsJsonArray("thumbnails")

                        val rawThumb = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString.orEmpty()
                        // Enhance thumbnail resolution to 544x544
                        val highResThumb = if (rawThumb.contains("=w") && rawThumb.contains("-h")) {
                            rawThumb.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
                        } else rawThumb

                        tracks.add(
                            MusicTrack(
                                id = videoId,
                                title = title,
                                artist = artist,
                                album = album,
                                durationText = durationText,
                                durationSeconds = durationSeconds,
                                thumbnailUrl = highResThumb,
                                backdropUrl = highResThumb
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing InnerTube search results: ${e.message}", e)
        }
        return tracks
    }

    private fun parseFlexColumnText(item: JsonObject, index: Int): String {
        return parseFlexColumnRuns(item, index).joinToString("")
    }

    private fun parseFlexColumnRuns(item: JsonObject, index: Int): List<String> {
        val flexColumns = item.getAsJsonArray("flexColumns") ?: return emptyList()
        if (index >= flexColumns.size()) return emptyList()
        val flexColumn = flexColumns[index].asJsonObject.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer") ?: return emptyList()
        val runs = flexColumn.getAsJsonObject("text")?.getAsJsonArray("runs") ?: return emptyList()
        return runs.mapNotNull { it.asJsonObject.get("text")?.asString }
    }

    private fun parseDurationToSeconds(durationText: String?): Long? {
        if (durationText.isNullOrBlank()) return null
        val parts = durationText.trim().split(":")
        return when (parts.size) {
            2 -> {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toLongOrNull() ?: return null
                min * 60 + sec
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toLongOrNull() ?: return null
                hr * 3600 + min * 60 + sec
            }
            else -> null
        }
    }
}
