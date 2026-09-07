package com.playtorrio.tv.core.scraper.p2p

import com.playtorrio.tv.core.scraper.ScraperMediaRequest

object PlayTorrioP2PFilter {

    /**
     * Builds search queries for torrent sites (Knaben, TorrentGalaxy).
     * For TV series episodes:
     * 1. Season pack search: e.g. "The Walking Dead s01"
     * 2. Specific episode search: e.g. "The Walking Dead s01e01"
     * 3. Specific episode variant: e.g. "The Walking Dead s01ep1"
     * This guarantees finding both full season packs and specific individual episode releases.
     */
    fun buildSearchQueries(request: ScraperMediaRequest): List<String> {
        val queries = mutableListOf<String>()
        val trimmedTitle = request.title.trim()

        if (request.isSeries && request.season != null) {
            val s = request.season.toString().padStart(2, '0')
            // 1. Full season pack query: e.g. "The Walking Dead s01"
            queries.add("$trimmedTitle s$s")

            if (request.episode != null) {
                val e = request.episode.toString().padStart(2, '0')
                // 2. Standard scene episode query: e.g. "The Walking Dead s01e01"
                queries.add("$trimmedTitle s${s}e$e")
                // 3. Alternate episode query: e.g. "The Walking Dead s01ep1"
                queries.add("$trimmedTitle s${s}ep${request.episode}")
            }
        } else if (request.isMovie) {
            if (request.year != null) {
                queries.add("$trimmedTitle ${request.year}")
            }
            queries.add(trimmedTitle)
        } else {
            queries.add(trimmedTitle)
        }

        return queries.distinct()
    }

    /**
     * Normalizes a title for exact matching by removing leading release tags,
     * replacing '&' with 'and', and stripping all non-alphanumeric characters.
     */
    fun cleanTitle(raw: String): String {
        return raw.lowercase()
            .replace(Regex("""^\[[^\]]+\]\s*"""), "")
            .replace(Regex("""^\([^\)]+\)\s*"""), "")
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Filters scraped torrent titles matching the V3 logic:
     * 1. Filters out spinoffs and unrelated shows (e.g. "Dead City", "Daryl Dixon", "Fear The Walking Dead").
     * 2. Filters out wrong seasons (e.g. Season 7 when Season 2 is requested).
     * 3. Filters out wrong episodes while keeping:
     *    - The exact requested episode
     *    - Episode ranges covering the requested episode
     *    - Full season packs (where episode is null)
     */
    fun matches(torrentName: String, request: ScraperMediaRequest): Boolean {
        if (torrentName.isBlank()) return false

        val searchTitleClean = cleanTitle(request.title)
        if (searchTitleClean.isEmpty()) return true

        val isSearchTitleAnId = searchTitleClean.startsWith("tt") &&
            searchTitleClean.substring(2).all { it.isDigit() }

        val parsed = ParseTorrentTitle.parse(torrentName)
        val cleanParsedTitle = cleanTitle(parsed.title)

        // 1. Exact show/movie match filter (filters out spinoffs like Dead City, Daryl Dixon, Fear The Walking Dead)
        if (!isSearchTitleAnId && cleanParsedTitle.isNotEmpty() && cleanParsedTitle != searchTitleClean) {
            return false
        }

        // 2. Series specific filtering
        if (request.isSeries) {
            val parsedSeason = parsed.season
            val parsedEpisode = parsed.episode
            val targetSeason = request.season
            val targetEpisode = request.episode

            // If season is specified in request and detected in torrent, they must match
            if (targetSeason != null && parsedSeason != null && parsedSeason != targetSeason) {
                return false
            }

            // Episode matching:
            // Only include if:
            // - It matches the specific episode
            // - OR it's within an episode range covering the episode
            // - OR it's a full season pack (parsedEpisode == null)
            if (targetEpisode != null && parsedEpisode != null) {
                if (parsedEpisode != targetEpisode) {
                    val range = parsed.episodeRange
                    if (range == null || targetEpisode !in range.first..range.second) {
                        return false
                    }
                }
            }
        } else if (request.isMovie) {
            if (request.year != null && parsed.year != null) {
                if (Math.abs(parsed.year - request.year) > 1) {
                    return false
                }
            }
        }

        return true
    }
}
