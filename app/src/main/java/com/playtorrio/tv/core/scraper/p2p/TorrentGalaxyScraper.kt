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

class TorrentGalaxyScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "TorrentGalaxyScraper"
        private const val BASE_URL = "https://torrentgalaxy.info"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private data class Candidate(
        val postUrl: String,
        val torrentName: String,
        val size: String,
        val seeders: Int,
        val resolution: String?
    )

    suspend fun scrape(request: ScraperMediaRequest): List<Stream> = withContext(Dispatchers.IO) {
        val queries = PlayTorrioP2PFilter.buildSearchQueries(request)
        val seenPostUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val candidates = java.util.Collections.synchronizedList(mutableListOf<Candidate>())

        val searchTasks = queries.map { query ->
            async {
                try {
                    searchCandidates(query, request, seenPostUrls, candidates)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "TGX search error for '$query': ${e.message}")
                }
            }
        }
        searchTasks.awaitAll()

        // Filtered candidates: sort by seeders descending and fetch detail pages for top 15
        val topCandidates = candidates.sortedByDescending { it.seeders }.take(15)
        val fetchTasks = topCandidates.map { candidate ->
            async {
                fetchDetailAndBuildStream(candidate)
            }
        }

        fetchTasks.awaitAll().filterNotNull()
    }

    private fun searchCandidates(
        query: String,
        request: ScraperMediaRequest,
        seenPostUrls: MutableSet<String>,
        candidates: MutableList<Candidate>
    ) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$BASE_URL/get-posts/keywords:$encodedQuery"

        val req = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val response = client.newCall(req).execute()
        if (!response.isSuccessful) {
            Log.d(TAG, "HTTP ${response.code} for $searchUrl")
            return
        }

        val html = response.body.string()
        val document = Jsoup.parse(html)
        val rows = document.select(".tgxtable .tgxtablerow")

        for (row in rows) {
            val titleNode = row.selectFirst(".tgxtablecell.clickable-row a[title]")
            val torrentName = titleNode?.attr("title")?.trim().orEmpty()
            val postUrlPath = titleNode?.attr("href")?.trim().orEmpty()

            if (torrentName.isEmpty() || postUrlPath.isEmpty()) continue

            // Filter bad matches BEFORE fetching detail page (removes spinoffs, other seasons, other episodes)
            if (!PlayTorrioP2PFilter.matches(torrentName, request)) {
                continue
            }

            val fullPostUrl = if (postUrlPath.startsWith("http")) postUrlPath else "$BASE_URL$postUrlPath"
            if (!seenPostUrls.add(fullPostUrl)) continue

            val sizeNode = row.selectFirst(".badge-secondary")
            val size = sizeNode?.text()?.trim() ?: "Unknown Size"

            val seedersNode = row.selectFirst("font[color=\"green\"] b")
            val seeders = seedersNode?.text()?.trim()?.replace(",", "")?.toIntOrNull() ?: 0

            val resolution = Regex("""\b(2160p|4k|1080p|720p|480p)\b""", RegexOption.IGNORE_CASE).find(torrentName)?.value?.uppercase()

            candidates.add(
                Candidate(
                    postUrl = fullPostUrl,
                    torrentName = torrentName,
                    size = size,
                    seeders = seeders,
                    resolution = resolution
                )
            )
        }
    }

    private fun fetchDetailAndBuildStream(candidate: Candidate): Stream? {
        return try {
            val req = Request.Builder()
                .url(candidate.postUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return null

            val html = response.body.string()
            val document = Jsoup.parse(html)
            val magnetNode = document.selectFirst("a[href^=magnet:]")
            val magnetUrl = magnetNode?.attr("href")?.trim().orEmpty()

            if (magnetUrl.isEmpty()) return null

            val match = Regex("""urn:btih:([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(magnetUrl)
            val infoHash = match?.groupValues?.getOrNull(1)?.lowercase() ?: return null

            val trMatches = Regex("""&tr=([^&]+)""").findAll(magnetUrl)
            val trackers = trMatches.mapNotNull { m ->
                runCatching {
                    "tracker:${URLDecoder.decode(m.groupValues[1], "UTF-8")}"
                }.getOrNull()
            }.toList()

            Stream(
                name = "PlayTorrio",
                title = "${candidate.torrentName}\n${candidate.size} 👥 ${candidate.seeders}",
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
                quality = candidate.resolution
            )
        } catch (e: Exception) {
            Log.d(TAG, "Detail fetch error for ${candidate.postUrl}: ${e.message}")
            null
        }
    }
}
