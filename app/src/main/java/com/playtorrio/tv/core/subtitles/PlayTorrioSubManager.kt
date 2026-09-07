package com.playtorrio.tv.core.subtitles

import android.util.Log
import com.playtorrio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayTorrioSubManager @Inject constructor() {
    companion object {
        private const val TAG = "PlayTorrioSubManager"
        const val ADDON_NAME = "PlayTorrioSub"
    }

    private val wyzieProvider = WyzieSubtitleProvider()
    private val openSubtitlesProvider = OpenSubtitlesSubtitleProvider()
    private val subtitleCatProvider = SubtitleCatSubtitleProvider()

    suspend fun fetchSubtitles(
        type: String,
        title: String,
        imdbId: String? = null,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        videoHash: String? = null,
        videoSize: Long? = null,
        onSubtitlesBatch: (suspend (List<Subtitle>) -> Unit)? = null
    ): List<Subtitle> = withContext(Dispatchers.IO) {
        val allSubtitles = mutableListOf<Subtitle>()
        val seenUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        val tasks = listOf(
            async {
                try {
                    val res = withTimeoutOrNull(8000L) {
                        wyzieProvider.search(
                            movieName = title,
                            imdbId = imdbId,
                            season = season,
                            episode = episode
                        )
                    } ?: emptyList()
                    val unique = res.filter { seenUrls.add(it.url) }
                    if (unique.isNotEmpty()) {
                        onSubtitlesBatch?.invoke(unique)
                    }
                    unique
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.d(TAG, "Wyzie failed: ${e.message}")
                    emptyList()
                }
            },
            async {
                try {
                    val cleanImdb = imdbId?.takeIf { it.isNotBlank() } ?: return@async emptyList()
                    val res = withTimeoutOrNull(8000L) {
                        openSubtitlesProvider.search(
                            type = type,
                            imdbId = cleanImdb,
                            season = season,
                            episode = episode,
                            videoHash = videoHash,
                            videoSize = videoSize
                        )
                    } ?: emptyList()
                    val unique = res.filter { seenUrls.add(it.url) }
                    if (unique.isNotEmpty()) {
                        onSubtitlesBatch?.invoke(unique)
                    }
                    unique
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.d(TAG, "OpenSubtitles failed: ${e.message}")
                    emptyList()
                }
            },
            async {
                try {
                    val res = withTimeoutOrNull(10000L) {
                        subtitleCatProvider.search(
                            title = title,
                            year = year,
                            season = season,
                            episode = episode
                        )
                    } ?: emptyList()
                    val unique = res.filter { seenUrls.add(it.url) }
                    if (unique.isNotEmpty()) {
                        onSubtitlesBatch?.invoke(unique)
                    }
                    unique
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.d(TAG, "SubtitleCat failed: ${e.message}")
                    emptyList()
                }
            }
        )

        val results = tasks.awaitAll().flatten()
        allSubtitles.addAll(results)
        Log.d(TAG, "PlayTorrioSub resolved ${allSubtitles.size} subtitles")
        allSubtitles
    }
}
