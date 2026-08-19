package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class HttpStreamResult(
    val sourceName: String,
    val title: String,
    val description: String? = null,
    val url: String,
    val headers: Map<String, String>? = null,
    val quality: String? = null
)

object PlayTorrioHttpService {
    private const val TAG = "PlayTorrioHttpService"

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Executes all 8 HTTP scrapers in parallel, emitting streams in real time as they are discovered.
     */
    fun searchLive(
        scope: CoroutineScope,
        title: String,
        isMovie: Boolean,
        year: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        onStreamFound: (HttpStreamResult) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            Log.i(TAG, "══════════════════════════════════════════════════════════════")
            Log.i(TAG, "🚀 STARTING REAL-TIME HTTP SCRAPING: \"$title\"")
            Log.i(TAG, "   Type: ${if (isMovie) "Movie" else "TV (S${seasonNumber}E${episodeNumber})"}, Year: $year, TMDB: $tmdbId, IMDB: $imdbId")
            Log.i(TAG, "══════════════════════════════════════════════════════════════")

            val seenUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            val safeEmit: (HttpStreamResult) -> Unit = { stream ->
                if (stream.url.isNotBlank() && seenUrls.add(stream.url)) {
                    Log.i(TAG, "✨ [STREAM FOUND] ${stream.sourceName} -> ${stream.title} | ${stream.quality ?: "Auto"} | ${stream.url}")
                    scope.launch(Dispatchers.Main) {
                        onStreamFound(stream)
                    }
                }
            }

            // Launch all 8 extractors in parallel child jobs
            val jobs = listOf(
                // 1. MovieNight (multi-server SSE)
                launch {
                    try {
                        Log.i(TAG, "▶ [MovieNight] Starting extraction...")
                        MovieNightExtractor.extractLive(
                            client = httpClient,
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            season = seasonNumber,
                            episode = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId ?: 0,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [MovieNight] Error: ${e.message}", e)
                    }
                },
                // 2. DownloadEverything (slave NDJSON API)
                launch {
                    try {
                        Log.i(TAG, "▶ [DownloadEverything] Starting extraction...")
                        DownloadEverythingExtractor.extractLive(
                            client = httpClient,
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            season = seasonNumber,
                            episode = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId ?: 0,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [DownloadEverything] Error: ${e.message}", e)
                    }
                },
                // 3. FlyStream (flystream.net direct HLS)
                launch {
                    try {
                        Log.i(TAG, "▶ [FlyStream] Starting extraction...")
                        FlyStreamExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [FlyStream] Error: ${e.message}", e)
                    }
                },
                // 4. Videasy (speedracelight encrypted provider API)
                launch {
                    try {
                        Log.i(TAG, "▶ [Videasy] Starting extraction...")
                        VideasyExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Videasy] Error: ${e.message}", e)
                    }
                },
                // 5. MultiEmbed (2embed.cc XPS chain)
                launch {
                    try {
                        Log.i(TAG, "▶ [MultiEmbed] Starting extraction...")
                        MultiEmbedExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [MultiEmbed] Error: ${e.message}", e)
                    }
                },
                // 6. VidCore (vidcore.org sources)
                launch {
                    try {
                        Log.i(TAG, "▶ [VidCore] Starting extraction...")
                        VidCoreExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [VidCore] Error: ${e.message}", e)
                    }
                },
                // 7. VidSrc (data.vidsrcme.ru / vidsrc.me)
                launch {
                    try {
                        Log.i(TAG, "▶ [VidSrc] Starting extraction...")
                        VidSrcExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [VidSrc] Error: ${e.message}", e)
                    }
                },
                // 8. X-Downloader (films365.org direct MP4)
                launch {
                    try {
                        Log.i(TAG, "▶ [X-Downloader] Starting extraction...")
                        XDownloaderExtractor.extract(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [X-Downloader] Error: ${e.message}", e)
                    }
                },
                // 9. Movy (all 14 city servers: Miami 4K, Seattle, Denver, Chicago, Dallas, etc.)
                launch {
                    try {
                        Log.i(TAG, "▶ [Movy] Starting extraction...")
                        MovyExtractor.extractLive(
                            title = title,
                            isMovie = isMovie,
                            year = year,
                            season = seasonNumber,
                            episode = episodeNumber,
                            imdbId = imdbId,
                            tmdbId = tmdbId ?: 0,
                            onStreamFound = safeEmit
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Movy] Error: ${e.message}", e)
                    }
                }
            )

            jobs.joinAll()
            Log.i(TAG, "🏁 FINISHED HTTP SCRAPING: Found ${seenUrls.size} unique streams for \"$title\"")
        }
    }
}
