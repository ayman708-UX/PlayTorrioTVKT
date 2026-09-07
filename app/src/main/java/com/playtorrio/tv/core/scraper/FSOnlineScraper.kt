package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin FSOnline Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla FSOnline provider.
 * Resolves FileSuN streams from www3.fsonline.app.
 */
class FSOnlineScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FSOnlineScraper"
        private const val ORIGIN = "https://www3.fsonline.app"
        private const val AJAX_URL = "$ORIGIN/wp-admin/admin-ajax.php"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Origin" to ORIGIN,
            "Referer" to "$ORIGIN/"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val query = if (request.year != null) "${request.title} ${request.year}" else request.title

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchReq = Request.Builder()
                .url("$ORIGIN/?s=$encodedQuery")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val targetPageUrl = httpClient.newCall(searchReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val typeFolder = if (isTv) "seriale" else "film"
                val linkRegex = Regex("""href=["'](https?://www3\.fsonline\.app/$typeFolder/([^"'/]+)/)["']""", RegexOption.IGNORE_CASE)
                val m = linkRegex.find(html) ?: return@use null
                if (isTv) {
                    val slug = m.groupValues[2].replace(Regex("""-\d{4}$"""), "")
                    "$ORIGIN/episoade/$slug-sezonul-${request.season ?: 1}-episodul-${request.episode ?: 1}/"
                } else {
                    m.groupValues[1]
                }
            } ?: return@withContext emptyList()

            val pageReq = Request.Builder()
                .url(targetPageUrl)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val movieId = httpClient.newCall(pageReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val m = Regex("""movie-id=['"]([^'"]+)['"]""").find(html)
                    ?: Regex("""movie-id=([^ >]+)""").find(html)
                m?.groupValues?.get(1)
            } ?: return@withContext emptyList()

            val formBody = FormBody.Builder()
                .add("action", "lazy_player")
                .add("movieID", movieId)
                .build()

            val ajaxReq = Request.Builder()
                .url(AJAX_URL)
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", targetPageUrl)
                .post(formBody)
                .build()

            val ajaxHtml = httpClient.newCall(ajaxReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (ajaxHtml.isEmpty()) return@withContext emptyList()

            val results = mutableListOf<ScraperStreamResult>()
            var idx = 0

            while (true) {
                val found = ajaxHtml.indexOf("data-vs=\"", idx)
                if (found == -1) break
                val embedStart = found + 9
                val embedEnd = ajaxHtml.indexOf('"', embedStart)
                if (embedEnd == -1) break
                val embedUrl = ajaxHtml.substring(embedStart, embedEnd)

                val spanStart = ajaxHtml.indexOf("<span>", embedEnd)
                val spanEnd = ajaxHtml.indexOf("</span>", spanStart)
                val serverLabel = if (spanStart != -1 && spanEnd != -1) {
                    ajaxHtml.substring(spanStart + 6, spanEnd).trim().lowercase()
                } else ""

                if (serverLabel.contains("filesun")) {
                    try {
                        val rReq = Request.Builder()
                            .url(embedUrl)
                            .header("Referer", ORIGIN)
                            .header("User-Agent", UA)
                            .build()

                        httpClient.newCall(rReq).execute().use { rRes ->
                            if (rRes.isSuccessful) {
                                val rBody = rRes.body?.string().orEmpty()
                                val m3u8Match = Regex("""file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(rBody)
                                    ?: Regex("""["']?file["']?:\s*["'](https?://[^"']+)["']""").find(rBody)

                                if (m3u8Match != null) {
                                    val streamUrl = m3u8Match.groupValues[1].replace(Regex("""\\/"""), "/")
                                    val reqHeaders = mapOf(
                                        "Referer" to embedUrl,
                                        "Origin" to "https://player.fsonline.app",
                                        "User-Agent" to UA
                                    )

                                    results.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = "FSOnline · FileSuN · 1080p",
                                            description = "FSOnline HLS Stream",
                                            url = streamUrl,
                                            quality = "1080p",
                                            headers = reqHeaders
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                idx = if (spanEnd != -1) spanEnd else embedEnd
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping FSOnline: ${e.message}")
            emptyList()
        }
    }
}
