package com.playtorrio.tv.data.torrent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

object TorrentSearchService {

    private const val TAG = "TorrentSearch"

    suspend fun search(request: TorrentSearchRequest): List<TorrentResult> = coroutineScope {
        val allResults = mutableListOf<TorrentResult>()
        // Normalize title for search queries (strip special chars that break indexer search)
        val searchTitle = normalizeTitle(request.title)

        if (request.isMovie) {
            // Movies: 2 searches (uindex + knaben)
            val query = "$searchTitle ${request.year ?: ""}".trim()
            val uindex = async { searchUindex(query) }
            val knaben = async { searchKnaben(query) }
            allResults.addAll(uindex.await())
            allResults.addAll(knaben.await())
        } else {
            // TV: 4 searches (episode + season pack on both sources)
            val sNum = String.format("S%02d", request.seasonNumber ?: 1)
            val eNum = String.format("E%02d", request.episodeNumber ?: 1)
            val episodeQuery = "$searchTitle ${sNum}${eNum}"
            val seasonQuery = "$searchTitle ${sNum}"

            val uEpisode = async { searchUindex(episodeQuery) }
            val uSeason = async { searchUindex(seasonQuery) }
            val kEpisode = async { searchKnaben(episodeQuery) }
            val kSeason = async { searchKnaben(seasonQuery) }

            allResults.addAll(uEpisode.await())
            allResults.addAll(uSeason.await())
            allResults.addAll(kEpisode.await())
            allResults.addAll(kSeason.await())
        }

        // Filter and deduplicate
        Log.d(TAG, "Raw results: ${allResults.size}, title='${request.title}', normalized='$searchTitle'")
        val filtered = filterResults(allResults, request)
        Log.d(TAG, "After filter: ${filtered.size}")
        deduplicateResults(filtered)
            .sortedByDescending { it.seeders }
    }

    // ── UINDEX SCRAPER ──

    private suspend fun searchUindex(query: String): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://uindex.org/search.php?search=$encoded&c=0&sort=seeders&order=DESC"
            val html = fetchHtml(url)
            parseUindex(html)
        } catch (e: Exception) {
            Log.e(TAG, "uindex search failed: $query", e)
            emptyList()
        }
    }

    private fun parseUindex(html: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()

        // Match each <tr> in the results table
        val rowPattern = Pattern.compile(
            "<tr>\\s*<td class=\"sr-col-cat\">(.*?)</td>\\s*<td class=\"sr-col-name\">(.*?)</td>\\s*<td class=\"sr-col-size\">(.*?)</td>\\s*<td class=\"sr-col-uploaded\"[^>]*>(.*?)</td>\\s*<td class=\"sr-col-seeders\">\\s*<span class=\"sr-seed\">(\\d+)</span>\\s*</td>\\s*<td class=\"sr-col-leechers\">\\s*<span class=\"sr-leech\">(\\d+)</span>\\s*</td>\\s*</tr>",
            Pattern.DOTALL
        )
        val matcher = rowPattern.matcher(html)

        while (matcher.find()) {
            val nameCell = matcher.group(2) ?: continue
            val size = matcher.group(3)?.trim() ?: ""
            val seeders = matcher.group(5)?.toIntOrNull() ?: 0
            val leechers = matcher.group(6)?.toIntOrNull() ?: 0

            // Extract magnet link
            val magnetMatch = Pattern.compile("href=\"(magnet:\\?[^\"]+)\"").matcher(nameCell)
            val magnet = if (magnetMatch.find()) {
                magnetMatch.group(1)?.replace("&amp;", "&") ?: continue
            } else continue

            // Extract torrent name
            val nameMatch = Pattern.compile("class=\"sr-torrent-link\"[^>]*title=\"([^\"]+)\"").matcher(nameCell)
            val name = if (nameMatch.find()) {
                nameMatch.group(1) ?: continue
            } else continue

            results.add(TorrentResult(
                name = name,
                magnetLink = magnet,
                size = size,
                seeders = seeders,
                leechers = leechers,
                source = "uindex"
            ))
        }

        return results
    }

    // ── KNABEN SCRAPER ──

    private suspend fun searchKnaben(query: String): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val url = "https://knaben.org/search/$encoded/0/1/seeders"
            val html = fetchHtml(url)
            parseKnaben(html)
        } catch (e: Exception) {
            Log.e(TAG, "knaben search failed: $query", e)
            emptyList()
        }
    }

    private fun parseKnaben(html: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()

        // Split into rows by <tr
        val rows = html.split("<tr ").drop(1) // skip header

        for (row in rows) {
            try {
                // Extract magnet link
                val magnetMatch = Pattern.compile("href=\"(magnet:\\?[^\"]+)\"").matcher(row)
                val magnet = if (magnetMatch.find()) {
                    magnetMatch.group(1)?.replace("&amp;", "&") ?: continue
                } else continue

                // Extract name from the first <a with title= and magnet href
                val nameMatch = Pattern.compile("<a\\s+title=\"([^\"]+)\"\\s+href=\"magnet:").matcher(row)
                val name = if (nameMatch.find()) {
                    nameMatch.group(1) ?: continue
                } else continue

                // Extract TDs after the name cell: size, date, seeders, leechers
                // Pattern: <td ...>SIZE</td> <td ...>DATE</td> <td>SEEDERS</td> <td>LEECHERS</td>
                val tdPattern = Pattern.compile("<td[^>]*>([^<]*)</td>")
                val tdMatcher = tdPattern.matcher(row)
                val tdValues = mutableListOf<String>()
                while (tdMatcher.find()) {
                    tdValues.add(tdMatcher.group(1)?.trim() ?: "")
                }

                // Find size (contains GB, MB, TB)
                val sizeValue = tdValues.firstOrNull {
                    it.contains("GB", true) || it.contains("MB", true) || it.contains("TB", true)
                } ?: ""

                // Find seeders/leechers - they're plain numbers in td
                val numbers = tdValues.filter { it.matches(Regex("^\\d+$")) }
                val seeders = numbers.getOrNull(0)?.toIntOrNull() ?: 0
                val leechers = numbers.getOrNull(1)?.toIntOrNull() ?: 0

                results.add(TorrentResult(
                    name = name,
                    magnetLink = magnet,
                    size = sizeValue,
                    seeders = seeders,
                    leechers = leechers,
                    source = "knaben"
                ))
            } catch (_: Exception) {
                continue
            }
        }

        return results
    }

    // ── SMART FILTERING ──

    private fun filterResults(
        results: List<TorrentResult>,
        request: TorrentSearchRequest
    ): List<TorrentResult> {
        val normalizedTitle = normalizeTitle(request.title).lowercase()

        return results.filter { result ->
            val normalizedName = normalizeTitle(result.name).lowercase()

            // Core check: the full title must appear as a contiguous phrase
            val titleIndex = normalizedName.indexOf(normalizedTitle)
            if (titleIndex < 0) return@filter false

            if (request.isMovie) {
                // For movies: check year is present
                val yearMatch = request.year == null || normalizedName.contains(request.year!!)
                // Filter out TV show patterns (S01E01, etc.)
                val hasTvPattern = Regex("s\\d{2}e\\d{2}", RegexOption.IGNORE_CASE).containsMatchIn(normalizedName)
                yearMatch && !hasTvPattern
            } else {
                val sNum = String.format("S%02d", request.seasonNumber ?: 1)
                val eNum = String.format("E%02d", request.episodeNumber ?: 1)
                val seasonEpisode = "$sNum$eNum"

                // Title must appear BEFORE the season marker
                // (prevents matching episode descriptions that mention the show title)
                val seasonIdx = normalizedName.indexOf(sNum.lowercase())
                if (seasonIdx >= 0 && titleIndex > seasonIdx) return@filter false

                // Must be the correct show (not a spinoff)
                if (!isCorrectShow(normalizedName, normalizedTitle)) return@filter false

                // Check if it's the exact episode, an episode range containing it, or a season pack
                val hasExactEpisode = normalizedName.contains(seasonEpisode, ignoreCase = true)
                val hasEpisodeInRange = isEpisodeInRange(normalizedName, request.seasonNumber ?: 1, request.episodeNumber ?: 1)
                val isSeasonPack = isSeasonPack(normalizedName, sNum, request)

                hasExactEpisode || hasEpisodeInRange || isSeasonPack
            }
        }.map { result ->
            // Mark season packs (anything without the exact S02E03 match)
            if (!request.isMovie) {
                val sNum = String.format("S%02d", request.seasonNumber ?: 1)
                val eNum = String.format("E%02d", request.episodeNumber ?: 1)
                val hasExact = result.name.contains("$sNum$eNum", ignoreCase = true)
                val hasRange = isEpisodeInRange(result.name, request.seasonNumber ?: 1, request.episodeNumber ?: 1)
                val isPack = !hasExact && !hasRange
                result.copy(isSeasonPack = isPack)
            } else result
        }
    }

    private fun isCorrectShow(resultName: String, searchTitle: String): Boolean {
        // Detect spinoffs by checking if there are extra title words between
        // the matching show name and the season marker.
        // e.g. "the walking dead daryl dixon s01" has "daryl dixon" extra -> spinoff
        // vs "the walking dead s01" -> correct

        val titleWords = searchTitle.split(" ").filter { it.length > 1 }

        // Find where the title ends in the result name
        var searchStart = 0
        for (word in titleWords) {
            val idx = resultName.indexOf(word, searchStart, ignoreCase = true)
            if (idx < 0) return false
            searchStart = idx + word.length
        }

        // Get the text between end of title match and the season marker
        val seasonMatch = Regex("s\\d{2}", RegexOption.IGNORE_CASE).find(resultName, searchStart)
        if (seasonMatch != null) {
            val gap = resultName.substring(searchStart, seasonMatch.range.first).trim()
            // Gap should be empty or just dots/dashes/spaces
            val gapWords = gap.replace(Regex("[.\\-_]"), " ").trim().split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
            // If there are significant words in the gap, it's probably a spinoff
            if (gapWords.isNotEmpty()) {
                // Allow common words like "the", "and", "of" etc
                val stopWords = setOf("the", "and", "of", "a", "an", "in", "on", "at", "to")
                val significantGapWords = gapWords.filter { it.lowercase() !in stopWords }
                if (significantGapWords.isNotEmpty()) return false
            }
        }

        return true
    }

    private fun isSeasonPack(name: String, seasonCode: String, request: TorrentSearchRequest): Boolean {
        val hasSeasonCode = name.contains(seasonCode, ignoreCase = true)
        if (!hasSeasonCode) return false

        // It's a season pack if it has the season code but no specific episode
        val hasSpecificEpisode = Regex("${seasonCode}E\\d{2}", RegexOption.IGNORE_CASE).containsMatchIn(name)
        if (hasSpecificEpisode) return false

        // Or if it says "COMPLETE" or "Season"
        val hasPackIndicator = name.contains("COMPLETE", ignoreCase = true) ||
                name.contains("Season", ignoreCase = true) ||
                name.contains("FULL", ignoreCase = true)

        // If it just has S01 without any episode number, it's a season pack
        return !hasSpecificEpisode
    }

    /** Detect multi-episode ranges like S02E01-03, S02E01-E03, S02e01-03 */
    private fun isEpisodeInRange(name: String, season: Int, episode: Int): Boolean {
        val sCode = String.format("S%02d", season)
        // Match patterns: S02E01-03, S02E01-E03, S02e01-03, S02e01-e03
        val rangeRegex = Regex(
            "${sCode}E(\\d{2})\\s*-\\s*E?(\\d{2})",
            RegexOption.IGNORE_CASE
        )
        rangeRegex.findAll(name).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (episode in start..end) return true
        }
        return false
    }

    // ── DEDUPLICATION ──

    private fun deduplicateResults(results: List<TorrentResult>): List<TorrentResult> {
        // Deduplicate by magnet hash (btih)
        val seen = mutableSetOf<String>()
        return results.filter { result ->
            val hash = extractInfoHash(result.magnetLink)?.lowercase()
            if (hash != null && hash in seen) false
            else {
                if (hash != null) seen.add(hash)
                true
            }
        }
    }

    private fun extractInfoHash(magnet: String): String? {
        val match = Regex("btih:([a-fA-F0-9]+)").find(magnet)
        return match?.groupValues?.get(1)
    }

    // ── UTILS ──

    private fun normalizeTitle(title: String): String {
        return title
            .replace("'", "")   // Marvel's → Marvels
            .replace("\u2019", "")  // curly apostrophe
            .replace(":", " ")  // Daredevil: Born Again → Daredevil  Born Again
            .replace(",", "")
            .replace("&", "and")
            .replace(Regex("[._\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun fetchHtml(url: String): String {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }
}
