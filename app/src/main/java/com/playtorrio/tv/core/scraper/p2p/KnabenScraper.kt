package com.playtorrio.tv.core.scraper.p2p

import android.util.Log
import com.playtorrio.tv.core.scraper.ScraperMediaRequest
import com.playtorrio.tv.domain.model.Stream
import com.playtorrio.tv.domain.model.StreamBehaviorHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KnabenScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "KnabenScraper"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    suspend fun scrape(request: ScraperMediaRequest): List<Stream> = withContext(Dispatchers.IO) {
        val queries = PlayTorrioP2PFilter.buildSearchQueries(request)
        val seenInfoHashes = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val allStreams = java.util.Collections.synchronizedList(mutableListOf<Stream>())

        val tasks = queries.map { query ->
            async {
                try {
                    scrapeQuery(query, request, seenInfoHashes, allStreams)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Knaben scrape query '$query' error: ${e.message}")
                }
            }
        }
        tasks.awaitAll()

        allStreams.toList()
    }

    private fun scrapeQuery(
        query: String,
        request: ScraperMediaRequest,
        seenInfoHashes: MutableSet<String>,
        allStreams: MutableList<Stream>
    ) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://knaben.org/search/$encodedQuery/0/1/seeders"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            Log.d(TAG, "HTTP ${response.code} for $url")
            return
        }

        val html = response.body.string()
        val document = Jsoup.parse(html)
        val rows = document.select("table tbody tr")

        for (row in rows) {
            val tdList = row.select("td")
            if (tdList.size < 6) continue

            val titleNode = row.selectFirst(".text-wrap.w-100 > a")
            val torrentName = titleNode?.text()?.trim().orEmpty()

            val magnetNode = row.selectFirst("a[href^=magnet:]")
            val magnetUrl = magnetNode?.attr("href").orEmpty()

            if (torrentName.isEmpty() || magnetUrl.isEmpty()) continue

            // Filter bad matches using V3 logic (removes spinoffs, other seasons, other episodes)
            if (!PlayTorrioP2PFilter.matches(torrentName, request)) {
                continue
            }

            val size = tdList[2].text().trim()
            val seedersText = tdList[4].text().trim().replace(",", "")
            val seeders = seedersText.toIntOrNull() ?: 0

            // Extract infoHash from magnet
            val match = Regex("""urn:btih:([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(magnetUrl)
            val infoHash = match?.groupValues?.getOrNull(1)?.lowercase() ?: continue

            if (!seenInfoHashes.add(infoHash)) continue

            // Extract trackers
            val trMatches = Regex("""&tr=([^&]+)""").findAll(magnetUrl)
            val trackers = trMatches.mapNotNull { m ->
                runCatching {
                    "tracker:${URLDecoder.decode(m.groupValues[1], "UTF-8")}"
                }.getOrNull()
            }.toList()

            val resolution = Regex("""\b(2160p|4k|1080p|720p|480p)\b""", RegexOption.IGNORE_CASE).find(torrentName)?.value?.uppercase()

            allStreams.add(
                Stream(
                    name = "PlayTorrio",
                    title = "$torrentName\n$size 👥 $seeders",
                    description = null,
                    url = null,
                    ytId = null,
                    infoHash = infoHash,
                    fileIdx = null,
                    externalUrl = null,
                    behaviorHints = StreamBehaviorHints(
                        bingeGroup = "playtorrio-$infoHash"
                    ),
                    addonName = "PlayTorrio",
                    addonLogo = null,
                    sources = trackers.ifEmpty { null },
                    quality = resolution
                )
            )
        }
    }
}
