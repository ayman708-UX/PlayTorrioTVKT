package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.model.AnimeStreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

class AniNekoExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "AniNekoExtractor"
        private const val BASE_URL = "https://anineko.to"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private fun cleanTitle(t: String): String =
        t.lowercase().replace(Regex("""[^a-z0-9]"""), "")

    private fun decodeEntities(str: String): String {
        return str
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun search(query: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        try {
            val url = "$BASE_URL/browser?keyword=${URLEncoder.encode(query, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val html = res.body?.string().orEmpty()
                    val doc = Jsoup.parse(html)
                    val links = doc.select("a[href*=/watch/]")
                    val seenSlugs = mutableSetOf<String>()

                    for (link in links) {
                        val href = link.attr("href")
                        val slugMatch = Regex("""/watch/([^/?#"]+)""").find(href)
                        val slug = slugMatch?.groupValues?.get(1) ?: continue
                        if (seenSlugs.add(slug)) {
                            val text = link.text().trim().ifBlank { slug }
                            results.add(slug to text)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private data class HlsExtractResult(
        val url: String,
        val tracks: List<AnimeStreamTrack> = emptyList()
    )

    private fun extractHls(embedUrl: String): HlsExtractResult? {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/")
                .build()

            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val html = res.body?.string().orEmpty()
                    val m = Regex("""const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)

                    val match = m?.groupValues?.get(1)
                    if (!match.isNullOrBlank()) {
                        val tracks = mutableListOf<AnimeStreamTrack>()
                        val trackRegex = Regex("""<track[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                        for (tr in trackRegex.findAll(html)) {
                            val trackTag = tr.value
                            val src = tr.groupValues[1]
                            if (src.isNotBlank() && !trackTag.contains("thumbnails", ignoreCase = true) && tracks.none { it.url == src }) {
                                val label = Regex("""label=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trackTag)?.groupValues?.get(1) ?: "Subtitles"
                                val srclang = Regex("""srclang=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trackTag)?.groupValues?.get(1) ?: "en"
                                tracks.add(AnimeStreamTrack(url = decodeEntities(src), label = label, lang = srclang))
                            }
                        }
                        return HlsExtractResult(url = decodeEntities(match), tracks = tracks)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        val targetCat = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"

        try {
            var seriesSlug: String? = null

            // 1. Direct slug probe
            for (title in titleCandidates) {
                val potentialSlug = title
                    .lowercase()
                    .replace(Regex("""[^a-z0-9]+"""), "-")
                    .trim('-')
                if (potentialSlug.isBlank()) continue

                try {
                    val probeUrl = "$BASE_URL/watch/$potentialSlug/ep-$episodeNumber"
                    val probeReq = Request.Builder()
                        .url(probeUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$BASE_URL/watch/$potentialSlug")
                        .build()

                    client.newCall(probeReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            if (body.contains("nv-watch-page")) {
                                seriesSlug = potentialSlug
                            }
                        }
                    }
                    if (seriesSlug != null) break
                } catch (_: Exception) {}
            }

            // 2. Search fallback
            if (seriesSlug == null) {
                for (title in titleCandidates) {
                    val searchList = search(title)
                    if (searchList.isEmpty()) continue

                    val targetClean = cleanTitle(title)
                    for ((slug, text) in searchList) {
                        val sClean = cleanTitle(slug)
                        val tClean = cleanTitle(text)
                        if (sClean == targetClean || tClean == targetClean || tClean.contains(targetClean)) {
                            seriesSlug = slug
                            break
                        }
                    }
                    if (seriesSlug != null) break
                }
            }

            if (seriesSlug == null) return@withContext results

            // 3. Fetch watch page
            val watchUrl = "$BASE_URL/watch/$seriesSlug/ep-$episodeNumber"
            val watchReq = Request.Builder()
                .url(watchUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/watch/$seriesSlug")
                .build()

            var watchHtml = ""
            client.newCall(watchReq).execute().use { res ->
                if (res.isSuccessful) {
                    watchHtml = res.body?.string().orEmpty()
                }
            }

            if (watchHtml.isBlank()) return@withContext results

            val doc = Jsoup.parse(watchHtml)
            val byAudio = mutableMapOf("sub" to mutableListOf<String>(), "dub" to mutableListOf<String>())

            // Parse iframe if present
            val iframeSrc = doc.selectFirst("iframe")?.attr("src")
            if (!iframeSrc.isNullOrBlank()) {
                val decodedIframe = decodeEntities(iframeSrc)
                val hls = extractHls(decodedIframe)
                if (hls != null) {
                    byAudio[targetCat]?.add(hls.url)
                }
            }

            // Parse tabs to map tab classes to sub/dub
            val dubTabs = mutableSetOf<String>()
            val tabButtons = doc.select(".nv-server-tab, .tab")
            for (tab in tabButtons) {
                val tabText = tab.text().lowercase()
                val tabId = tab.attr("data-id").lowercase()
                if (tabText.contains("dub") || tabId.contains("dub")) {
                    for (c in tab.classNames()) {
                        if (c.startsWith("tab_")) {
                            dubTabs.add(c)
                        }
                    }
                }
            }

            // Parse server-video buttons
            val serverButtons = doc.select("button.server-video, .server-video")
            for (btn in serverButtons) {
                val video = btn.attr("data-video")
                if (video.isBlank()) continue
                val videoUrl = decodeEntities(video)
                val tabAttr = btn.attr("data-tab")
                val btnText = btn.text().lowercase()

                val isDub = dubTabs.contains(tabAttr) ||
                    tabAttr.contains("dub", ignoreCase = true) ||
                    btnText.contains("dub")
                val cat = if (isDub) "dub" else "sub"

                val list = byAudio.getOrPut(cat) { mutableListOf() }
                if (!list.contains(videoUrl)) {
                    list.add(videoUrl)
                }
            }

            val targetUrls = byAudio[targetCat].orEmpty()
            for (u in targetUrls) {
                var finalUrl = u
                var tracks = emptyList<AnimeStreamTrack>()
                if (!finalUrl.contains(".m3u8")) {
                    val resolved = extractHls(finalUrl)
                    if (resolved != null) {
                        finalUrl = resolved.url
                        tracks = resolved.tracks
                    }
                }

                if (finalUrl.contains(".m3u8") || finalUrl.startsWith("http")) {
                    results.add(
                        AnimeStreamResult(
                            streamUrl = finalUrl,
                            serverName = "AniNeko",
                            category = targetCat.uppercase(),
                            quality = "1080p",
                            tracks = tracks,
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "$BASE_URL/",
                                "Origin" to BASE_URL
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniNeko extraction error: ${e.message}")
        }

        results
    }
}
