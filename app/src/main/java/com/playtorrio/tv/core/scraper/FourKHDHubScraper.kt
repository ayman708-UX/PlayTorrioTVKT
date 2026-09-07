package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class FourKHDHubScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FourKHDHubScraper"
        private const val BASE_URL = "https://4khdhub.one"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val headers = mapOf("User-Agent" to UA)

        private val httpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        @Volatile
        private var resolvedBaseUrl: String? = null
        @Volatile
        private var resolvedBaseUrlExpiry: Long = 0

        private fun getBaseUrl(): String {
            val now = System.currentTimeMillis()
            if (resolvedBaseUrl != null && now < resolvedBaseUrlExpiry) {
                return resolvedBaseUrl!!
            }

            var current = BASE_URL
            try {
                val nonRedirectClient = httpClient.newBuilder().followRedirects(false).build()
                for (i in 0 until 5) {
                    val req = Request.Builder().url(current).head().apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
                    nonRedirectClient.newCall(req).execute().use { res ->
                        val loc = res.header("Location")
                        if (!loc.isNullOrBlank()) {
                            current = if (loc.startsWith("http")) loc else "$current$loc"
                        }
                    }
                }
            } catch (_: Exception) {}

            val parsedUri = java.net.URI(current)
            val base = "${parsedUri.scheme}://${parsedUri.host}"
            resolvedBaseUrl = base
            resolvedBaseUrlExpiry = now + 3600_000L
            return base
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        try {
            val baseUrl = getBaseUrl()
            val query = URLEncoder.encode(request.title, "UTF-8")
            val searchUrl = "$baseUrl/?s=$query"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val html = httpClient.newCall(searchReq).execute().use { it.body?.string().orEmpty() }
            if (html.isBlank()) return@withContext emptyList()

            val doc = Jsoup.parse(html)
            val posts = doc.select("article, .post-item, .result-item, .movies-list .item, a[href*='/movies/'], a[href*='/series/']")
            var detailUrl: String? = null

            for (post in posts) {
                val link = if (post.tagName() == "a") post.attr("href") else post.selectFirst("a")?.attr("href")
                val title = post.text().ifEmpty { post.attr("title") }
                if (!link.isNullOrBlank() && link.startsWith("http")) {
                    if (title.contains(request.title, ignoreCase = true)) {
                        detailUrl = link
                        break
                    }
                }
            }

            if (detailUrl == null) {
                val firstLink = doc.selectFirst(".content-area a[href*='/'], .search-results a[href*='/']")?.attr("href")
                if (!firstLink.isNullOrBlank() && firstLink.startsWith("http")) {
                    detailUrl = firstLink
                }
            }

            if (detailUrl != null) {
                val detailReq = Request.Builder()
                    .url(detailUrl)
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val detailHtml = httpClient.newCall(detailReq).execute().use { it.body?.string().orEmpty() }
                val detailDoc = Jsoup.parse(detailHtml)

                val links = detailDoc.select("a[href*='hubcloud'], a[href*='drive'], a[href*='download'], a[href*='fastdl'], a.btn-download")
                for (link in links) {
                    val href = link.attr("href")
                    if (href.startsWith("http")) {
                        val text = link.text().ifEmpty { "4KHDHub Link" }
                        results.add(
                            ScraperStreamResult(
                                name = "PlayTorrioHTTP",
                                title = "4KHDHub $text",
                                description = "4KHDHub • $text",
                                url = href,
                                headers = headers
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "FourKHDHubScraper error: ${e.message}")
        }

        results
    }
}
