package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VixSrc Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VixSrc provider.
 * Resolves tokenized HLS playlists from vixsrc.to.
 */
class VixSrcScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VixSrcScraper"
        private const val BASE_URL = "https://vixsrc.to"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun extract(reg: Regex, text: String): String? {
            val m = reg.find(text) ?: return null
            var v = m.groupValues[1]
            v = v.replace("\\u0026", "&").replace("\\/", "/").replace("\\", "")
            return v
        }

        private val LANG_MAP = mapOf(
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "ru" to "Russian",
            "ja" to "Japanese",
            "hi" to "Hindi"
        )
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val mediaType = if (isTv) "tv" else "movie"

        try {
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = mediaType,
                year = request.year
            ) ?: return@withContext emptyList()

            val apiUrl = if (isTv) {
                "$BASE_URL/api/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$BASE_URL/api/movie/$tmdbId"
            }

            val apiReq = Request.Builder()
                .url(apiUrl)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val embedUrl = httpClient.newCall(apiReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val apiData = JSONObject(body)
                val rawEmbed = apiData.optString("src")
                if (rawEmbed.isNotBlank()) {
                    if (rawEmbed.startsWith("http")) rawEmbed else "$BASE_URL$rawEmbed"
                } else null
            } ?: return@withContext emptyList()

            val embedReq = Request.Builder()
                .url(embedUrl)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val html = httpClient.newCall(embedReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (html.isEmpty()) return@withContext emptyList()

            val token = extract(Regex("""token["']\s*:\s*["']([^"']+)"""), html)
            val expires = extract(Regex("""expires["']\s*:\s*["']([^"']+)"""), html)
            var playlist = extract(Regex("""url["']\s*:\s*["']([^"']+)"""), html)

            if (token == null || expires == null || playlist == null) return@withContext emptyList()

            if (playlist.startsWith("/")) {
                playlist = "$BASE_URL$playlist"
            }

            val lang = extract(Regex("""lang(?:uage)?["']\s*:\s*["']([a-z]{2,5})""", RegexOption.IGNORE_CASE), html) ?: "en"
            val delim = if (playlist.contains("?")) "&" else "?"
            val rawMasterUrl = "$playlist${delim}token=$token&expires=$expires&h=1&lang=$lang"

            val isForeign = lang.isNotEmpty() && !lang.equals("en", ignoreCase = true)
            val langName = LANG_MAP[lang.lowercase()] ?: lang.uppercase()

            val title = if (isForeign) {
                "VixSrc · Master HLS · $langName · 1080p"
            } else {
                "VixSrc · Master HLS · 1080p"
            }

            val desc = if (isForeign) {
                "VixSrc Master Stream · $langName"
            } else {
                "VixSrc Master Stream"
            }

            val reqHeaders = HashMap(HEADERS).apply {
                put("Referer", embedUrl)
            }

            listOf(
                ScraperStreamResult(
                    name = "PlayTorrioHTTP",
                    title = title,
                    description = desc,
                    url = rawMasterUrl,
                    quality = "1080p",
                    headers = reqHeaders
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VixSrc: ${e.message}")
            emptyList()
        }
    }
}
