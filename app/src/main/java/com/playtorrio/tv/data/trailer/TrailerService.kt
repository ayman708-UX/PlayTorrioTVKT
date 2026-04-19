package com.playtorrio.tv.data.trailer

import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.model.TmdbVideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TrailerService"

object TrailerService {
    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY

    // Cache: tmdbId -> TrailerPlaybackSource (null sentinel = tried and failed)
    private val cache = ConcurrentHashMap<String, TrailerPlaybackSource?>()
    private val NEGATIVE = TrailerPlaybackSource(videoUrl = "")

    suspend fun getTrailer(
        tmdbId: Int,
        isMovie: Boolean
    ): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        val cacheKey = "${if (isMovie) "m" else "t"}_$tmdbId"

        cache[cacheKey]?.let { cached ->
            return@withContext if (cached === NEGATIVE) null else cached
        }

        try {
            // 1) Get video keys from TMDB
            val response = if (isMovie) {
                api.getMovieVideos(tmdbId, key)
            } else {
                api.getTvVideos(tmdbId, key)
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB videos request failed (${response.code()}) for $cacheKey")
                return@withContext null
            }

            val results = response.body()?.results.orEmpty()
            val candidates = rankCandidates(results)
            Log.i(TAG, "TMDB trailer candidates for $cacheKey: ${candidates.size}")

            // 2) Try each YouTube candidate until one works
            for (candidate in candidates) {
                val ytKey = candidate.key?.trim() ?: continue
                if (ytKey.isBlank()) continue

                Log.i(TAG, "Trying YouTube key: ${ytKey.take(6)}...")
                val youtubeUrl = "https://www.youtube.com/watch?v=$ytKey"
                val source = YouTubeExtractor.extractPlaybackSource(youtubeUrl)
                if (source != null) {
                    cache[cacheKey] = source
                    Log.i(TAG, "Trailer found for $cacheKey (audio=${!source.audioUrl.isNullOrBlank()})")
                    return@withContext source
                } else {
                    Log.w(TAG, "YouTube extraction failed for key: ${ytKey.take(6)}...")
                }
            }

            // No trailer found
            cache[cacheKey] = NEGATIVE
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trailer for $cacheKey: ${e.message}")
            null
        }
    }

    private fun rankCandidates(results: List<TmdbVideoResult>): List<TmdbVideoResult> {
        return results
            .filter { (it.site ?: "").equals("YouTube", ignoreCase = true) }
            .filter { !it.key.isNullOrBlank() }
            .filter {
                val t = it.type?.trim()?.lowercase()
                t == "trailer" || t == "teaser"
            }
            .sortedWith(
                compareBy<TmdbVideoResult> {
                    when (it.type?.trim()?.lowercase()) {
                        "trailer" -> 0; "teaser" -> 1; else -> 2
                    }
                }.thenBy { if (it.official == true) 0 else 1 }
                    .thenByDescending { it.size ?: 0 }
            )
    }
}
