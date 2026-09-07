package com.playtorrio.tv.core.subtitles

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playtorrio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WyzieSubtitleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "WyzieSubtitleProvider"
        private const val ENDPOINT = "https://sub.wyzie.io/search"
        private const val API_KEY = "wyzie-2q1gc0ypd8mkisqcw0ijt1b9zjytj7ex"
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
        movieName: String,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<Subtitle> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Subtitle>()

        val urlBuilder = ENDPOINT.toHttpUrlOrNull()?.newBuilder() ?: return@withContext results
        urlBuilder.addQueryParameter("source", "all")
        urlBuilder.addQueryParameter("key", API_KEY)

        if (!imdbId.isNullOrBlank()) {
            val formattedImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            urlBuilder.addQueryParameter("id", formattedImdb)
        } else {
            urlBuilder.addQueryParameter("query", movieName)
        }

        if (season != null) urlBuilder.addQueryParameter("season", season.toString())
        if (episode != null) urlBuilder.addQueryParameter("episode", episode.toString())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("x-api-key", API_KEY)
            .header("Authorization", "Bearer $API_KEY")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.d(TAG, "HTTP ${response.code} from Wyzie")
                return@withContext results
            }

            val body = response.body?.string() ?: return@withContext results
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val items: List<Map<String, Any?>> = gson.fromJson(body, listType) ?: return@withContext results

            for (map in items) {
                val url = map["url"]?.toString()?.trim()
                if (url.isNullOrBlank()) continue

                val rawLang = (map["language"] ?: map["lang"] ?: "en").toString().lowercase()
                val display = map["display"]?.toString()
                val language = if (!display.isNullOrBlank()) {
                    display
                } else {
                    ISO3_TO_NAME[rawLang] ?: if (rawLang.length <= 3) rawLang.uppercase() else rawLang
                }

                val release = map["release"]?.toString()
                val isHi = map["isHearingImpaired"] == true || map["hi"] == true
                var label = if (!release.isNullOrBlank()) release else "Wyzie"
                if (isHi) label = "$label [CC]"

                val subId = "wyzie-${map["id"]?.toString() ?: url.hashCode().toString()}"

                results.add(
                    Subtitle(
                        id = subId,
                        url = url,
                        lang = language,
                        addonName = "PlayTorrioSub",
                        addonLogo = null
                    )
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Wyzie search error: ${e.message}")
        }

        results
    }
}
