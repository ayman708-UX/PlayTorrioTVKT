package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin FSonic Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla FSonic provider.
 * Resolves movies from fsonic.net and fsharetv.co.
 */
class FSonicScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FSonicScraper"
        private const val BASE_URL = "https://www.fsonic.net"
        private const val FSHARE_BASE = "https://fsharetv.co"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        if (request.type == "tv" || request.type == "series" || request.season != null) {
            return@withContext emptyList()
        }

        try {
            val encodedTitle = URLEncoder.encode(request.title, "UTF-8")
            val searchReq = Request.Builder()
                .url("$BASE_URL/movie/search/$encodedTitle")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val watchSlug = httpClient.newCall(searchReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val yearStr = request.year?.toString().orEmpty()

                var slug: String? = null
                var idx = 0
                while (true) {
                    val found = html.indexOf("href=\"/watch/", idx)
                    if (found == -1) break
                    val start = found + 6
                    val end = html.indexOf('"', start)
                    if (end == -1) break
                    val link = html.substring(start, end)
                    if (slug == null) slug = link
                    if (yearStr.isNotEmpty() && link.contains(yearStr)) {
                        slug = link
                        break
                    }
                    idx = end
                }
                slug
            } ?: return@withContext emptyList()

            val watchReq = Request.Builder()
                .url("$BASE_URL$watchSlug")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val (token, trailer) = httpClient.newCall(watchReq).execute().use { res ->
                if (!res.isSuccessful) return@use Pair(null, null)
                val html = res.body?.string().orEmpty()
                val m = Regex("""init\('([^']+)',\s*(?:'[^']*',\s*)?'([^']+)'\)""").find(html)
                if (m != null) {
                    Pair(m.groupValues[1], m.groupValues[2])
                } else {
                    Pair(null, null)
                }
            }

            if (token == null) return@withContext emptyList()

            val jsonReq = Request.Builder()
                .url("$BASE_URL/api/source/$token?trailer=${trailer ?: ""}&type=watch")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$BASE_URL$watchSlug")
                .build()

            val results = mutableListOf<ScraperStreamResult>()
            val seen = mutableSetOf<String>()

            httpClient.newCall(jsonReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val json = JSONObject(body)
                if (json.optString("status") != "ok") return@use

                val data = json.optJSONObject("data") ?: return@use
                val file = data.optJSONObject("file") ?: return@use

                val allGroups = mutableListOf<org.json.JSONArray>()
                if (file.has("sources")) {
                    allGroups.add(file.getJSONArray("sources"))
                }
                if (file.has("alternatives")) {
                    val alt = file.getJSONArray("alternatives")
                    for (i in 0 until alt.length()) {
                        val item = alt.optJSONArray(i)
                        if (item != null) allGroups.add(item)
                    }
                }

                for (group in allGroups) {
                    for (i in 0 until group.length()) {
                        val item = group.optJSONObject(i) ?: continue
                        val rawSrc = item.optString("src")
                        if (rawSrc.isBlank()) continue
                        val srcUrl = if (rawSrc.startsWith("http")) rawSrc else "$FSHARE_BASE$rawSrc"

                        if (seen.add(srcUrl)) {
                            val quality = item.optString("quality", "1080").ifEmpty { "1080" }
                            val label = item.optString("label").trim()
                            val isHls = srcUrl.contains(".m3u8")

                            val titleParts = mutableListOf("FSonic")
                            if (label.isNotEmpty()) titleParts.add(label)
                            titleParts.add("${quality}p")

                            val descParts = mutableListOf("FSonic Stream")
                            if (label.isNotEmpty()) descParts.add(label)
                            descParts.add(if (isHls) "HLS" else "MP4")

                            val reqHeaders = HashMap(HEADERS).apply {
                                put("Referer", "$FSHARE_BASE/")
                            }

                            results.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = titleParts.joinToString(" · "),
                                    description = descParts.joinToString(" · "),
                                    url = srcUrl,
                                    quality = "${quality}p",
                                    headers = reqHeaders
                                )
                            )
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping FSonic: ${e.message}")
            emptyList()
        }
    }
}
