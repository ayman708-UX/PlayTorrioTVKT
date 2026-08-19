package com.playtorrio.tv.data.streaming

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class StreamResult(
    val url: String,
    val referer: String = "",
    val headers: Map<String, String>? = null
)

object StreamExtractorService {
    enum class SourceType { WEBVIEW, HTTP_API, HTTP_SCRAPER }

    data class Source(
        val index: Int,
        val name: String,
        val referer: String,
        val type: SourceType = SourceType.WEBVIEW
    )

    val SOURCES = listOf(
        Source(1, "VidEasy", "https://www.cineby.at/", SourceType.WEBVIEW),
        Source(2, "67movies", "https://67movies.nl/", SourceType.HTTP_SCRAPER),
        Source(3, "VsEmbed", "https://vsembed.ru/", SourceType.HTTP_SCRAPER),
        Source(4, "MovieNight", "https://movienig.ht/", SourceType.HTTP_SCRAPER),
        Source(5, "DownloadEverything", "https://downloadeverythingfromeverywhere.com/", SourceType.HTTP_SCRAPER),
        Source(6, "Movy", "https://www.movy.bz/", SourceType.HTTP_SCRAPER)
    )

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(
        context: Context,
        sourceIdx: Int,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        timeoutMs: Long = 25_000L,
        title: String? = null,
        year: Int? = null,
        imdbId: String? = null,
    ): StreamResult? {
        return withTimeoutOrNull(timeoutMs) {
            when (sourceIdx) {
                1 -> VidEasyWebViewExtractor.extract(context, tmdbId, season, episode)
                2 -> SixtySevenMoviesExtractor.extract(httpClient, tmdbId, season, episode)
                3 -> VsEmbedExtractor.extract(httpClient, tmdbId, season, episode)
                4 -> MovieNightExtractor.extract(httpClient, tmdbId, season, episode, title, year, imdbId)
                5 -> DownloadEverythingExtractor.extract(httpClient, tmdbId, season, episode, title, year, imdbId)
                6 -> MovyExtractor.extract(tmdbId, season, episode, title, year, imdbId)
                else -> null
            }
        }
    }
}
