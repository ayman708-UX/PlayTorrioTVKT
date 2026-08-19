package com.playtorrio.tv.data.streaming

import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * MovieNight (movienig.ht) HTTP Stream Extractor.
 *
 * Fetches high-definition streams (4K 2160p, 1080p, 720p HLS) from MovieNight's
 * server infrastructure (Dallas, Austin, Helena, Seattle, etc.).
 */
object MovieNightExtractor {
    private const val TAG = "MovieNightExtractor"
    private const val BASE_URL = "https://movienig.ht"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private data class Server(val id: String, val label: String)

    private val PRIORITY_SERVERS = listOf(
        Server("dallas", "Dallas 4K"),
        Server("austin", "Austin"),
        Server("helena", "Helena"),
        Server("seattle", "Seattle 4K"),
        Server("vixsrc-1", "Newport Beach"),
        Server("tucson", "Tucson"),
        Server("salem", "Salem")
    )

    suspend fun extract(
        client: OkHttpClient,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        title: String? = null,
        year: Int? = null,
        imdbId: String? = null,
    ): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val isMovie = season == null
            var resolvedTitle = title
            var resolvedYear = year
            var resolvedImdbId = imdbId

            // Resolve missing title/year/imdb if needed
            if (resolvedTitle.isNullOrBlank() || resolvedImdbId.isNullOrBlank() || resolvedYear == null) {
                try {
                    if (isMovie) {
                        val details = TmdbClient.api.getMovieDetails(tmdbId, TmdbClient.API_KEY)
                        if (resolvedTitle.isNullOrBlank()) resolvedTitle = details.title
                        if (resolvedYear == null) resolvedYear = details.releaseDate?.take(4)?.toIntOrNull()
                        if (resolvedImdbId.isNullOrBlank()) {
                            val ext = TmdbClient.api.getMovieExternalIds(tmdbId, TmdbClient.API_KEY)
                            resolvedImdbId = ext.imdbId
                        }
                    } else {
                        val details = TmdbClient.api.getTvDetails(tmdbId, TmdbClient.API_KEY)
                        if (resolvedTitle.isNullOrBlank()) resolvedTitle = details.name
                        if (resolvedYear == null) resolvedYear = details.firstAirDate?.take(4)?.toIntOrNull()
                        if (resolvedImdbId.isNullOrBlank()) {
                            val ext = TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY)
                            resolvedImdbId = ext.imdbId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch extra TMDB details for $tmdbId: ${e.message}")
                }
            }

            val encTitle = URLEncoder.encode(resolvedTitle ?: "", "UTF-8")
            val yearQuery = if (resolvedYear != null) "&year=$resolvedYear" else ""
            val imdbQuery = if (!resolvedImdbId.isNullOrBlank()) "&imdbId=$resolvedImdbId" else ""
            val idToUse = if (tmdbId > 0) tmdbId.toString() else (resolvedImdbId ?: return@withContext null)

            Log.i(TAG, "Extracting MovieNight for \"$resolvedTitle\" (id: $idToUse, S:$season E:$episode)")

            // Query priority servers in parallel
            coroutineScope {
                val deferreds = PRIORITY_SERVERS.map { server ->
                    async {
                        try {
                            val s = season ?: 1
                            val e = episode ?: 1
                            val url = if (isMovie) {
                                "$BASE_URL/api/stream/v1/movie/$idToUse?title=$encTitle$yearQuery$imdbQuery&server=${server.id}&only=1"
                            } else {
                                "$BASE_URL/api/stream/v1/tv/$idToUse/$s/$e?title=$encTitle$yearQuery$imdbQuery&server=${server.id}&only=1"
                            }

                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", UA)
                                .header("Referer", "$BASE_URL/")
                                .header("Origin", BASE_URL)
                                .header("Accept", "text/event-stream")
                                .build()

                            val bodyStr = client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) resp.body?.string() else null
                            } ?: return@async null

                            if (bodyStr.contains("event: done")) {
                                val doneIdx = bodyStr.indexOf("event: done")
                                val dataIdx = bodyStr.indexOf("data: ", doneIdx)
                                if (dataIdx != -1) {
                                    val jsonStart = dataIdx + 6
                                    val jsonEnd = bodyStr.indexOf('\n', jsonStart)
                                    val jsonText = (if (jsonEnd != -1) bodyStr.substring(jsonStart, jsonEnd) else bodyStr.substring(jsonStart)).trim()
                                    val json = JSONObject(jsonText)
                                    val sources = json.optJSONArray("sources")
                                    if (sources != null && sources.length() > 0) {
                                        for (i in 0 until sources.length()) {
                                            val src = sources.getJSONObject(i)
                                            val rawUrl = src.optString("url")
                                            if (rawUrl.isNotBlank()) {
                                                Log.i(TAG, "MovieNight stream found from server ${server.label}: $rawUrl")
                                                return@async StreamResult(
                                                    url = rawUrl,
                                                    referer = "$BASE_URL/",
                                                    headers = mapOf(
                                                        "User-Agent" to UA,
                                                        "Referer" to "$BASE_URL/"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        null
                    }
                }

                // Return first successful result
                for (deferred in deferreds) {
                    val res = deferred.await()
                    if (res != null) {
                        return@coroutineScope res
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "MovieNight extraction failed: ${e.message}")
            null
        }
    }
}
