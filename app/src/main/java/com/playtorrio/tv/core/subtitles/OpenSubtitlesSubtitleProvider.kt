package com.playtorrio.tv.core.subtitles

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playtorrio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OpenSubtitlesSubtitleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "OpenSubtitlesProvider"
        private val ENDPOINTS = listOf(
            "https://opensubtitles.stremio.homes",
            "https://opensubtitles-v3.strem.io",
            "https://opensubtitles.strem.io"
        )
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val ISO3_TO_NAME = mapOf(
            "ara" to "Arabic", "ar" to "Arabic",
            "eng" to "English", "en" to "English",
            "spa" to "Spanish", "es" to "Spanish",
            "fre" to "French", "fra" to "French", "fr" to "French",
            "ger" to "German", "deu" to "German", "de" to "German",
            "ita" to "Italian", "it" to "Italian",
            "jpn" to "Japanese", "ja" to "Japanese",
            "kor" to "Korean", "ko" to "Korean",
            "rus" to "Russian", "ru" to "Russian",
            "por" to "Portuguese", "pt" to "Portuguese",
            "chi" to "Chinese", "zho" to "Chinese", "zh" to "Chinese",
            "hin" to "Hindi", "hi" to "Hindi",
            "tur" to "Turkish", "tr" to "Turkish",
            "ind" to "Indonesian", "id" to "Indonesian",
            "vie" to "Vietnamese", "vi" to "Vietnamese",
            "tha" to "Thai", "th" to "Thai",
            "pol" to "Polish", "pl" to "Polish",
            "dut" to "Dutch", "nld" to "Dutch", "nl" to "Dutch",
            "swe" to "Swedish", "sv" to "Swedish",
            "nor" to "Norwegian", "no" to "Norwegian",
            "dan" to "Danish", "da" to "Danish",
            "fin" to "Finnish", "fi" to "Finnish",
            "heb" to "Hebrew", "he" to "Hebrew",
            "ces" to "Czech", "cs" to "Czech",
            "ell" to "Greek", "el" to "Greek",
            "hun" to "Hungarian", "hu" to "Hungarian",
            "ron" to "Romanian", "ro" to "Romanian",
            "ukr" to "Ukrainian", "uk" to "Ukrainian",
            "per" to "Persian", "fas" to "Persian", "fa" to "Persian"
        )
    }

    suspend fun search(
        type: String,
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
        videoHash: String? = null,
        videoSize: Long? = null
    ): List<Subtitle> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Subtitle>()
        if (imdbId.isBlank()) return@withContext results

        val cleanImdbId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val isSeries = season != null && episode != null
        val canonicalType = if (isSeries) "series" else if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()
        val queryId = if (isSeries) "$cleanImdbId:$season:$episode" else cleanImdbId

        val extraParams = mutableListOf<String>()
        if (!videoHash.isNullOrBlank()) extraParams.add("videoHash=$videoHash")
        if (videoSize != null && videoSize > 0) extraParams.add("videoSize=$videoSize")
        val extraPath = if (extraParams.isNotEmpty()) "/${extraParams.joinToString("&")}" else ""

        val seenUrls = mutableSetOf<String>()

        for (base in ENDPOINTS) {
            try {
                val url = "$base/subtitles/$canonicalType/$queryId$extraPath.json"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val root: Map<String, Any?> = gson.fromJson(body, mapType) ?: continue
                val subsList = root["subtitles"] as? List<*> ?: continue

                for (item in subsList) {
                    if (item !is Map<*, *>) continue
                    val subUrl = item["url"]?.toString()?.trim()
                    if (subUrl.isNullOrBlank() || !seenUrls.add(subUrl)) continue

                    val rawLang = (item["lang"] ?: "en").toString().lowercase()
                    val language = ISO3_TO_NAME[rawLang] ?: if (rawLang.length <= 3) rawLang.uppercase() else rawLang
                    val subId = "opensub-${item["id"]?.toString() ?: subUrl.hashCode().toString()}"

                    results.add(
                        Subtitle(
                            id = subId,
                            url = subUrl,
                            lang = language,
                            addonName = "PlayTorrioSub",
                            addonLogo = null
                        )
                    )
                }

                if (results.isNotEmpty()) {
                    break // First responding endpoint is sufficient
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.d(TAG, "Endpoint $base failed: ${e.message}")
            }
        }

        results
    }
}
