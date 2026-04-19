package com.playtorrio.tv.data.subtitle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ExternalSubtitle(
    val id: String,
    val url: String,
    val language: String,
    val displayName: String,
    val format: String,
    val source: String,          // "wyzie" or "levrx"
    val isHearingImpaired: Boolean = false,
    val fileName: String? = null,
    val downloadCount: Int = 0
)

object SubtitleService {

    private const val TAG = "SubtitleService"
    private const val WYZIE_KEY = "wyzie-0d7ef784cd5aa6b812766fb07931accb"

    suspend fun fetchSubtitles(
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null
    ): List<ExternalSubtitle> = coroutineScope {
        val wyzie = async { fetchWyzie(tmdbId, season, episode) }
        val levrx = async { fetchLevrx(tmdbId, season, episode) }

        val all = mutableListOf<ExternalSubtitle>()
        all.addAll(wyzie.await())
        all.addAll(levrx.await())

        // Sort: English first, then by download count
        all.sortedWith(
            compareByDescending<ExternalSubtitle> { it.language == "en" }
                .thenByDescending { it.downloadCount }
        )
    }

    private suspend fun fetchWyzie(
        tmdbId: Int, season: Int?, episode: Int?
    ): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder("https://sub.wyzie.ru/search?id=$tmdbId&key=$WYZIE_KEY")
            if (season != null && episode != null) {
                sb.append("&season=$season&episode=$episode")
            }
            val json = fetchUrl(sb.toString())
            if (json.isBlank()) return@withContext emptyList()

            val arr = JSONArray(json)
            val results = mutableListOf<ExternalSubtitle>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawUrl = obj.optString("url", "")
                if (rawUrl.isBlank()) continue

                // Append key to download URL
                val downloadUrl = if (rawUrl.contains("?")) "$rawUrl&key=$WYZIE_KEY"
                else "$rawUrl?key=$WYZIE_KEY"

                results.add(ExternalSubtitle(
                    id = obj.optString("id", "$i"),
                    url = downloadUrl,
                    language = obj.optString("language", "unknown"),
                    displayName = obj.optString("display", obj.optString("language", "Unknown")),
                    format = obj.optString("format", "srt"),
                    source = "wyzie",
                    isHearingImpaired = obj.optBoolean("isHearingImpaired", false),
                    fileName = obj.optString("fileName", null),
                    downloadCount = obj.optInt("downloadCount", 0)
                ))
            }
            Log.d(TAG, "Wyzie: ${results.size} subtitles")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Wyzie fetch failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchLevrx(
        tmdbId: Int, season: Int?, episode: Int?
    ): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder("https://api.levrx.de/search?id=$tmdbId")
            if (season != null && episode != null) {
                sb.append("/$season/$episode")
            }
            val json = fetchUrl(sb.toString())
            if (json.isBlank()) return@withContext emptyList()

            // Levrx returns: {"id":"...", "subtitles":[{"category":"Arabic","flag":"...","urls":["url1","url2"]}, ...]}
            val root = org.json.JSONObject(json)
            val subtitlesArr = root.optJSONArray("subtitles")
                ?: return@withContext emptyList()

            val results = mutableListOf<ExternalSubtitle>()
            var idx = 0
            for (i in 0 until subtitlesArr.length()) {
                val category = subtitlesArr.getJSONObject(i)
                val language = category.optString("category", "Unknown")
                val urlsArr = category.optJSONArray("urls") ?: continue

                for (j in 0 until urlsArr.length()) {
                    val rawUrl = urlsArr.optString(j, "")
                    if (rawUrl.isBlank()) continue

                    val format = when {
                        rawUrl.contains(".srt", true) -> "srt"
                        rawUrl.contains(".vtt", true) -> "vtt"
                        rawUrl.contains(".ass", true) || rawUrl.contains(".ssa", true) -> "ass"
                        else -> "srt"
                    }

                    results.add(ExternalSubtitle(
                        id = "levrx_${idx++}",
                        url = rawUrl,
                        language = language,
                        displayName = "$language ${j + 1}",
                        format = format,
                        source = "levrx",
                        isHearingImpaired = false,
                        fileName = null,
                        downloadCount = 0
                    ))
                }
            }
            Log.d(TAG, "Levrx: ${results.size} subtitles from ${subtitlesArr.length()} categories")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Levrx fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun fetchUrl(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
            else ""
        } finally {
            conn.disconnect()
        }
    }
}
