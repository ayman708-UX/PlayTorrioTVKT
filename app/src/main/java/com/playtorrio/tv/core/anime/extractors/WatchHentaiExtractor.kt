package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

class WatchHentaiExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "WatchHentaiExtractor"
        private const val ORIGIN = "https://watchhentai.net"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val STOPWORDS = setOf(
            "a", "an", "the", "of", "and", "or", "to", "in", "on", "at",
            "for", "with", "by", "from", "is", "it", "no", "wa", "ga", "ni",
            "o", "wo", "de", "mo", "ka", "ya", "na", "e", "he", "te", "ne",
            "animation", "anime", "motion", "ova", "ona", "tv", "special",
            "version", "edition", "dubbed", "subbed", "sub", "dub",
            "uncensored", "censored", "episode", "ep", "season", "side", "part", "arc"
        )
    }

    private fun tokenize(s: String): Set<String> {
        return s.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 1 && !STOPWORDS.contains(it) }
            .toSet()
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int
    ): AnimeStreamResult? = withContext(Dispatchers.IO) {
        for (query in titleCandidates) {
            val clean = query.trim()
            if (clean.isBlank()) continue

            try {
                val searchUrl = "$ORIGIN/?s=${URLEncoder.encode(clean, "UTF-8")}"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$ORIGIN/")
                    .build()

                var html = ""
                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) html = res.body?.string().orEmpty()
                }
                if (html.isBlank()) continue

                val doc = Jsoup.parse(html)
                val searchContainer = doc.selectFirst(".csearch_results, .search-page, #content, .content") ?: doc
                val items = searchContainer.select(".result-item, article.item, .item")

                var bestUrl: String? = null
                var bestScore = 0.0
                val targetTokens = tokenize(clean)

                for (el in items) {
                    val aTag = el.selectFirst("h3 a, .title a, a") ?: continue
                    val title = aTag.text()
                    val href = aTag.attr("href")
                    if (href.isBlank()) continue

                    val itemTokens = tokenize(title)
                    val score = jaccardSimilarity(targetTokens, itemTokens)
                    if (score > bestScore) {
                        bestScore = score
                        bestUrl = href
                    }
                }

                if (bestScore < 0.35 || bestUrl.isNullOrBlank()) continue

                val seriesReq = Request.Builder()
                    .url(bestUrl!!)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", searchUrl)
                    .build()

                var seriesHtml = ""
                client.newCall(seriesReq).execute().use { res ->
                    if (res.isSuccessful) seriesHtml = res.body?.string().orEmpty()
                }

                val seriesDoc = Jsoup.parse(seriesHtml)
                val epLinks = seriesDoc.select("a[href*=/videos/], a[href*=-episode-]")
                var epUrl: String? = null

                val targetEpPattern = Regex("""-episode-$episodeNumber(?:\b|-)""", RegexOption.IGNORE_CASE)
                for (link in epLinks) {
                    val h = link.attr("href")
                    if (targetEpPattern.containsMatchIn(h)) {
                        epUrl = h
                        break
                    }
                }

                if (epUrl == null && epLinks.isNotEmpty()) {
                    epUrl = epLinks.first()?.attr("href")
                }

                if (epUrl.isNullOrBlank()) continue

                val videoReq = Request.Builder()
                    .url(epUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", bestUrl!!)
                    .build()

                var videoHtml = ""
                client.newCall(videoReq).execute().use { res ->
                    if (res.isSuccessful) videoHtml = res.body?.string().orEmpty()
                }

                val iframeMatch = Regex("""src=['"](https?://watchhentai\.net/jwplayer/\?source=[^'"]+)['"]""").find(videoHtml)
                    ?: Regex("""data-litespeed-src=['"](https?://watchhentai\.net/jwplayer/\?source=[^'"]+)['"]""").find(videoHtml)
                val jwUrl = iframeMatch?.groupValues?.get(1) ?: continue

                val jwReq = Request.Builder()
                    .url(jwUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", epUrl)
                    .build()

                var jwHtml = ""
                client.newCall(jwReq).execute().use { res ->
                    if (res.isSuccessful) jwHtml = res.body?.string().orEmpty()
                }

                val directMatch = Regex("""file\s*:\s*['"](https?://[^'"]+\.mp4[^'"]*)['"]""").find(jwHtml)
                    ?: Regex("""src=['"](https?://[^'"]+\.mp4[^'"]*)['"]""").find(jwHtml)
                    ?: Regex("""['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").find(jwHtml)

                val stream = directMatch?.groupValues?.get(1)
                if (!stream.isNullOrBlank()) {
                    return@withContext AnimeStreamResult(
                        streamUrl = stream,
                        serverName = "WatchHentai",
                        category = "SUB",
                        quality = "1080p",
                        isDirectMp4 = stream.contains(".mp4"),
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to jwUrl,
                            "Origin" to ORIGIN
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "WatchHentai error: ${e.message}")
            }
        }
        null
    }
}
