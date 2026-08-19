package com.playtorrio.tv.data.streaming

import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TmdbIdResolver {
    private const val TAG = "TmdbIdResolver"
    private const val TMDB_KEY = TmdbClient.API_KEY
    private const val TMDB_DIRECT = "https://api.themoviedb.org/3"
    private const val TMDB_PROXY = "https://db.speedracelight.com/3"

    private val cache = ConcurrentHashMap<String, Int>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun cleanString(s: String): String {
        return s.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    suspend fun resolveTmdbId(
        imdbId: String? = null,
        title: String,
        isMovie: Boolean,
        year: Int? = null
    ): Int? = withContext(Dispatchers.IO) {
        val cacheKey = "${imdbId ?: ""}|$title|$isMovie|${year ?: ""}"
        cache[cacheKey]?.let { return@withContext it }

        var cleanId = (imdbId ?: "").trim()
        cleanId = cleanId.replace(Regex("^(tmdb|movie|tv|imdb):", RegexOption.IGNORE_CASE), "")
        if (cleanId.contains(":")) {
            cleanId = cleanId.split(":")[0]
        }

        val endpoint = if (isMovie) "movie" else "tv"

        // 1. Direct numeric ID
        if (cleanId.matches(Regex("^\\d+$"))) {
            val id = cleanId.toIntOrNull()
            if (id != null) {
                cache[cacheKey] = id
                return@withContext id
            }
        }

        // 2. Query TMDB Find API for tt IMDB IDs
        if (cleanId.startsWith("tt")) {
            try {
                val url = "$TMDB_DIRECT/find/$cleanId?api_key=$TMDB_KEY&external_source=imdb_id"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = if (isMovie) json.optJSONArray("movie_results") else json.optJSONArray("tv_results")
                        if (results != null && results.length() > 0) {
                            val id = results.getJSONObject(0).optInt("id")
                            if (id > 0) {
                                cache[cacheKey] = id
                                return@withContext id
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // Backup find query via Speedrace proxy
            try {
                val url = "$TMDB_PROXY/find/$cleanId?external_source=imdb_id"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = if (isMovie) json.optJSONArray("movie_results") else json.optJSONArray("tv_results")
                        if (results != null && results.length() > 0) {
                            val id = results.getJSONObject(0).optInt("id")
                            if (id > 0) {
                                cache[cacheKey] = id
                                return@withContext id
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Search by title and year
        if (title.isNotBlank()) {
            val targetCleanTitle = cleanString(title)

            try {
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val url = "$TMDB_DIRECT/search/$endpoint?api_key=$TMDB_KEY&query=$encodedTitle"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            var bestMatchId: Int? = null

                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val itemTitle = item.optString("title").ifEmpty {
                                    item.optString("name").ifEmpty {
                                        item.optString("original_title").ifEmpty {
                                            item.optString("original_name")
                                        }
                                    }
                                }
                                val itemCleanTitle = cleanString(itemTitle)
                                val dateStr = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
                                val itemYear = if (dateStr.length >= 4) dateStr.substring(0, 4).toIntOrNull() else null

                                val titleMatch = itemCleanTitle == targetCleanTitle ||
                                        itemCleanTitle.contains(targetCleanTitle) ||
                                        targetCleanTitle.contains(itemCleanTitle)

                                if (titleMatch) {
                                    if (year != null && itemYear != null) {
                                        if (itemYear == year || kotlin.math.abs(itemYear - year) <= 1) {
                                            val id = item.optInt("id")
                                            if (id > 0) {
                                                cache[cacheKey] = id
                                                return@withContext id
                                            }
                                        }
                                    } else if (bestMatchId == null) {
                                        val id = item.optInt("id")
                                        if (id > 0) bestMatchId = id
                                    }
                                }
                            }

                            if (bestMatchId != null) {
                                cache[cacheKey] = bestMatchId
                                return@withContext bestMatchId
                            }

                            val fallbackId = results.getJSONObject(0).optInt("id")
                            if (fallbackId > 0) {
                                cache[cacheKey] = fallbackId
                                return@withContext fallbackId
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search TMDB error", e)
            }
        }

        null
    }
}
