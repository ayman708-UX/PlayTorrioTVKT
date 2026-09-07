package com.playtorrio.tv.core.subtitles

import android.util.Log
import com.playtorrio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class SubtitleCatSubtitleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .dns(com.playtorrio.tv.core.network.IPv4FirstDns())
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "SubtitleCatProvider"
        private const val ORIGIN = "https://www.subtitlecat.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val LANG_MAP = mapOf(
            "af" to "Afrikaans", "sq" to "Albanian", "ar" to "Arabic", "hy" to "Armenian",
            "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian", "bn" to "Bengali",
            "bs" to "Bosnian", "bg" to "Bulgarian", "ca" to "Catalan", "zh-cn" to "Chinese (S)",
            "zh-tw" to "Chinese (T)", "hr" to "Croatian", "cs" to "Czech", "da" to "Danish",
            "nl" to "Dutch", "en" to "English", "eo" to "Esperanto", "et" to "Estonian",
            "tl" to "Filipino", "fi" to "Finnish", "fr" to "French", "gl" to "Galician",
            "ka" to "Georgian", "de" to "German", "el" to "Greek", "gu" to "Gujarati",
            "he" to "Hebrew", "iw" to "Hebrew", "hi" to "Hindi", "hu" to "Hungarian",
            "is" to "Icelandic", "id" to "Indonesian", "in" to "Indonesian", "ga" to "Irish",
            "it" to "Italian", "ja" to "Japanese", "kn" to "Kannada", "ko" to "Korean",
            "la" to "Latin", "lv" to "Latvian", "lt" to "Lithuanian", "mk" to "Macedonian",
            "ms" to "Malay", "mt" to "Maltese", "mr" to "Marathi", "no" to "Norwegian",
            "fa" to "Persian", "pl" to "Polish", "pt" to "Portuguese", "pt-br" to "Portuguese (BR)",
            "ro" to "Romanian", "ru" to "Russian", "sr" to "Serbian", "sk" to "Slovak",
            "sl" to "Slovenian", "es" to "Spanish", "sw" to "Swahili", "sv" to "Swedish",
            "ta" to "Tamil", "te" to "Telugu", "th" to "Thai", "tr" to "Turkish",
            "uk" to "Ukrainian", "ur" to "Urdu", "vi" to "Vietnamese", "cy" to "Welsh"
        )
    }

    suspend fun search(
        title: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<Subtitle> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Subtitle>()

        val cleanTitle = title.replace(Regex("""\s+"""), " ").trim()
        if (cleanTitle.isBlank() || (cleanTitle.startsWith("tt") && cleanTitle.substring(2).takeWhile { it.isDigit() }.length >= 5)) {
            return@withContext results
        }

        val query = if (season != null && episode != null) {
            val s = season.toString().padStart(2, '0')
            val e = episode.toString().padStart(2, '0')
            "$cleanTitle S${s}E$e"
        } else if (year != null && year > 0) {
            "$cleanTitle $year"
        } else {
            cleanTitle
        }

        val searchUrl = "$ORIGIN/index.php?search=${java.net.URLEncoder.encode(query, "UTF-8")}"

        try {
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext results

            val html = res.body?.string() ?: return@withContext results
            val doc = Jsoup.parse(html)
            val searchLinks = doc.select("a[href^=subs/]")

            val detailUrls = searchLinks.mapNotNull { a ->
                val href = a.attr("href")
                if (href.endsWith(".html")) "$ORIGIN/$href" else null
            }.distinct().take(4) // Take top 4 detail pages

            val seenUrls = mutableSetOf<String>()

            for (detailUrl in detailUrls) {
                try {
                    val detailReq = Request.Builder()
                        .url(detailUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()

                    val detailRes = client.newCall(detailReq).execute()
                    if (!detailRes.isSuccessful) continue

                    val detailHtml = detailRes.body?.string() ?: continue
                    val detailDoc = Jsoup.parse(detailHtml)

                    // Select direct download links: <a id="download_code" href="/subs/...">
                    val dlAnchors = detailDoc.select("a[id^=download_][href*=.srt]")
                    for (a in dlAnchors) {
                        val href = a.attr("href")
                        if (href.isBlank()) continue

                        val fullUrl = if (href.startsWith("http")) href else "$ORIGIN$href"
                        if (!seenUrls.add(fullUrl)) continue

                        val langCode = a.id().removePrefix("download_").lowercase()
                        val languageLabel = LANG_MAP[langCode] ?: langCode.uppercase()
                        val subId = "subtitlecat-$langCode-${fullUrl.hashCode()}"

                        results.add(
                            Subtitle(
                                id = subId,
                                url = fullUrl,
                                lang = languageLabel,
                                addonName = "PlayTorrioSub",
                                addonLogo = null
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error parsing detail page $detailUrl: ${e.message}")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "SubtitleCat search error: ${e.message}")
        }

        results
    }
}
