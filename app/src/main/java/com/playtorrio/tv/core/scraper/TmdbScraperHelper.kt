package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TmdbScraperHelper {
    private const val TAG = "TmdbScraperHelper"
    private const val API_KEY = "b3556f3b206e16f82df4d1f6fd4545e6"
    private const val TMDB_DIRECT = "https://api.themoviedb.org/3"
    private const val TMDB_PROXY = "https://db.speedracelight.com/3"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Int>()

    private fun cleanString(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    suspend fun resolveTmdbId(
        imdbId: String?,
        title: String,
        type: String,
        year: Int?
    ): Int? = withContext(Dispatchers.IO) {
        val cacheKey = "${imdbId.orEmpty()}|$title|$type|${year ?: ""}"
        cache[cacheKey]?.let { return@withContext it }

        var cleanId = imdbId?.trim().orEmpty()
        cleanId = cleanId.replace(Regex("^(tmdb|movie|tv|imdb):", RegexOption.IGNORE_CASE), "")
        if (cleanId.contains(":")) {
            cleanId = cleanId.substringBefore(":")
        }

        val isTv = type.equals("tv", ignoreCase = true) || type.equals("series", ignoreCase = true)
        val endpoint = if (isTv) "tv" else "movie"

        if (cleanId.isNotEmpty()) {
            // 1. Direct numeric ID
            cleanId.toIntOrNull()?.let { id ->
                cache[cacheKey] = id
                return@withContext id
            }

            // 2. Query TMDB Find API for tt IMDB IDs
            if (cleanId.startsWith("tt", ignoreCase = true)) {
                // Official TMDB Find
                try {
                    val url = "$TMDB_DIRECT/find/$cleanId?api_key=$API_KEY&external_source=imdb_id"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val json = JSONObject(body)
                            val arr = if (isTv) json.optJSONArray("tv_results") else json.optJSONArray("movie_results")
                            if (arr != null && arr.length() > 0) {
                                val id = arr.getJSONObject(0).optInt("id", -1)
                                if (id > 0) {
                                    cache[cacheKey] = id
                                    return@withContext id
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Direct TMDB find failed for $cleanId: ${e.message}")
                }

                // Proxy TMDB Find
                try {
                    val url = "$TMDB_PROXY/find/$cleanId?external_source=imdb_id"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val json = JSONObject(body)
                            val arr = if (isTv) json.optJSONArray("tv_results") else json.optJSONArray("movie_results")
                            if (arr != null && arr.length() > 0) {
                                val id = arr.getJSONObject(0).optInt("id", -1)
                                if (id > 0) {
                                    cache[cacheKey] = id
                                    return@withContext id
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Proxy TMDB find failed for $cleanId: ${e.message}")
                }
            }
        }

        // 3. Search by title, type, and year
        if (title.isNotBlank()) {
            val targetCleanTitle = cleanString(title)
            val encodedTitle = URLEncoder.encode(title, "UTF-8")

            // Search via official TMDB
            try {
                val url = "$TMDB_DIRECT/search/$endpoint?api_key=$API_KEY&query=$encodedTitle"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            var bestMatchId: Int? = null
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val itemTitle = item.optString("title").ifEmpty {
                                    item.optString("name").ifEmpty {
                                        item.optString("original_title").ifEmpty { item.optString("original_name") }
                                    }
                                }
                                val itemCleanTitle = cleanString(itemTitle)
                                val dateStr = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
                                val itemYear = dateStr.take(4).toIntOrNull()

                                val titleMatch = itemCleanTitle == targetCleanTitle ||
                                        itemCleanTitle.contains(targetCleanTitle) ||
                                        targetCleanTitle.contains(itemCleanTitle)

                                if (titleMatch) {
                                    if (year != null && itemYear != null) {
                                        if (itemYear == year || abs(itemYear - year) <= 1) {
                                            val id = item.optInt("id", -1)
                                            if (id > 0) {
                                                cache[cacheKey] = id
                                                return@withContext id
                                            }
                                        }
                                    } else {
                                        if (bestMatchId == null) {
                                            val id = item.optInt("id", -1)
                                            if (id > 0) bestMatchId = id
                                        }
                                    }
                                }
                            }
                            if (bestMatchId != null) {
                                cache[cacheKey] = bestMatchId
                                return@withContext bestMatchId
                            }
                            val firstId = results.getJSONObject(0).optInt("id", -1)
                            if (firstId > 0) {
                                cache[cacheKey] = firstId
                                return@withContext firstId
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct TMDB search failed for $title: ${e.message}")
            }

            // Search via Speedrace proxy
            try {
                val url = "$TMDB_PROXY/search/$endpoint?query=$encodedTitle"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val itemTitle = item.optString("title").ifEmpty {
                                    item.optString("name").ifEmpty {
                                        item.optString("original_title").ifEmpty { item.optString("original_name") }
                                    }
                                }
                                val itemCleanTitle = cleanString(itemTitle)
                                val dateStr = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
                                val itemYear = dateStr.take(4).toIntOrNull()

                                if (itemCleanTitle == targetCleanTitle || itemCleanTitle.contains(targetCleanTitle)) {
                                    if (year == null || itemYear == null || itemYear == year || abs(itemYear - year) <= 1) {
                                        val id = item.optInt("id", -1)
                                        if (id > 0) {
                                            cache[cacheKey] = id
                                            return@withContext id
                                        }
                                    }
                                }
                            }
                            val firstId = results.getJSONObject(0).optInt("id", -1)
                            if (firstId > 0) {
                                cache[cacheKey] = firstId
                                return@withContext firstId
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Proxy TMDB search failed for $title: ${e.message}")
            }
        }

        null
    }
}
