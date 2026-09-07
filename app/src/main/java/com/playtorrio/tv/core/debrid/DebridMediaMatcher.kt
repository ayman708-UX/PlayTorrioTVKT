package com.playtorrio.tv.core.debrid

import com.playtorrio.tv.core.scraper.p2p.ParseTorrentTitle

object DebridMediaMatcher {

    private val VIDEO_EXTS = setOf(
        ".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".m4v", ".flv", ".wmv", ".iso"
    )

    private val AUDIO_EXTS = setOf(
        ".mp3", ".m4b", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav", ".wma"
    )

    private val JUNK_REGEX = Regex(
        """(?:\b|_)(?:sample|trailer|extras?|bonus|featurettes?|deleted[-._ ]scenes?|behind[-._ ]the[-._ ]scenes|preview|interview)(?:\b|_)""",
        RegexOption.IGNORE_CASE
    )

    fun isFileMatch(
        rawPath: String,
        targetSeason: Int,
        targetEpisode: Int,
        episodeTitle: String? = null
    ): Boolean {
        return computeFileMatchScore(
            rawPath = rawPath,
            sizeBytes = 1024L * 1024L * 1000L,
            season = targetSeason,
            episode = targetEpisode,
            episodeTitle = episodeTitle
        ) > 500.0
    }

    fun computeFileMatchScore(
        rawPath: String,
        sizeBytes: Long,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        filename: String? = null
    ): Double {
        var score = 0.0
        val lowerPath = rawPath.lowercase()

        // 1. Strict Penalty for Samples, Trailers, and Extras
        if (JUNK_REGEX.containsMatchIn(lowerPath) ||
            lowerPath.endsWith(".sample") ||
            lowerPath.contains("sample.")
        ) {
            score -= 5000.0
        }

        // 2. File size weight: larger files are full releases, not short clips
        val sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0)
        score += (sizeGB * 25.0).coerceIn(0.0, 100.0)

        if (sizeBytes < 25L * 1024L * 1024L && !AUDIO_EXTS.any { lowerPath.endsWith(it) }) {
            score -= 2000.0 // Stubs, previews, or sample files
        }

        // 3. Season & Episode Matching
        if (season != null && episode != null) {
            val parsed = ParseTorrentTitle.parsePath(rawPath)
            val parsedSeason = parsed.season
            val parsedEpisode = parsed.episode
            val episodeRange = parsed.episodeRange

            // Exact Season & Episode Match
            if (parsedSeason == season && parsedEpisode == episode) {
                score += 1500.0
            }
            // Multi-Episode File Range Match (e.g. S01E01-E04 for Ep 2)
            else if (parsedSeason == season &&
                episodeRange != null &&
                episode in episodeRange.first..episodeRange.second
            ) {
                score += 1400.0
            }
            // Single-Season Pack (Season omitted in filename, but episode matches)
            else if (parsedSeason == null && parsedEpisode == episode) {
                score += 1250.0
            }
            // Range match without explicit season
            else if (parsedSeason == null &&
                episodeRange != null &&
                episode in episodeRange.first..episodeRange.second
            ) {
                score += 1200.0
            }
            // Secondary fallback patterns for uncommon naming schemes
            else {
                val normalized = ParseTorrentTitle.normalizeTorrentTitle(rawPath).lowercase()
                val sStr = season.toString().padStart(2, '0')
                val eStr = episode.toString().padStart(2, '0')

                val fallbackRegexes = listOf(
                    Regex("""s0*$season[-._ x]*e0*$episode""", RegexOption.IGNORE_CASE),
                    Regex("""0*$season[xX]0*$episode""", RegexOption.IGNORE_CASE),
                    Regex("""s0*$season[-._ x]+0*$episode""", RegexOption.IGNORE_CASE),
                    Regex("""(?:season|saison)[-._ x]*0*$season.*(?:episode|ep|e)[-._ x]*0*$episode""", RegexOption.IGNORE_CASE),
                    Regex("""\b0*$season[-._ ]+0*$episode\b""")
                )

                val matchedFallback = fallbackRegexes.any { it.containsMatchIn(normalized) }

                if (matchedFallback ||
                    normalized.contains("s${sStr}e$eStr") ||
                    normalized.contains("${season}x$eStr") ||
                    normalized.contains("${sStr}x$eStr")
                ) {
                    score += 1100.0
                }
            }
        }

        // 4. Episode Title Semantic Match (Extra confidence layer)
        if (!episodeTitle.isNullOrBlank()) {
            val cleanEpTitle = episodeTitle
                .replace(Regex("""[^\w\s]+"""), "")
                .lowercase()
                .trim()
            val cleanPath = rawPath
                .replace(Regex("""[^\w\s]+"""), " ")
                .lowercase()

            if (cleanEpTitle.length >= 4 && cleanPath.contains(cleanEpTitle)) {
                score += 600.0
            }
        }

        // 5. Filename substring match for movies / general files
        if (!filename.isNullOrBlank()) {
            val cleanName = filename.lowercase().trim()
            if (lowerPath.contains(cleanName)) {
                score += 300.0
            }
        }

        return score
    }

    fun <T> pickMediaFile(
        files: List<T>,
        fileIndex: Int? = null,
        filename: String? = null,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        name: (T) -> String,
        size: (T) -> Long
    ): T? {
        if (files.isEmpty()) return null

        val mediaFiles = files.filter { f ->
            val n = name(f).lowercase()
            VIDEO_EXTS.any { n.endsWith(it) } || AUDIO_EXTS.any { n.endsWith(it) }
        }

        val candidates = mediaFiles.ifEmpty { files }

        // 1. Season & Episode match (Scored candidate ranking)
        if (season != null && episode != null) {
            // If explicit fileIndex is provided and matches, honor it
            if (fileIndex != null && fileIndex >= 0 && fileIndex < files.size) {
                val f = files[fileIndex]
                if (isFileMatch(name(f), season, episode, episodeTitle = episodeTitle)) {
                    return f
                }
            }

            // Rank all candidates by match score
            val scoredList = candidates.sortedByDescending { f ->
                computeFileMatchScore(
                    rawPath = name(f),
                    sizeBytes = size(f),
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    filename = filename
                )
            }

            val bestCandidate = scoredList.firstOrNull() ?: return null
            val bestScore = computeFileMatchScore(
                rawPath = name(bestCandidate),
                sizeBytes = size(bestCandidate),
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                filename = filename
            )

            if (bestScore > 0.0) {
                return bestCandidate
            }
        }

        // 2. Match by exact filename/title first
        if (!filename.isNullOrBlank()) {
            val cleanName = filename.lowercase().trim()
            val exactMatch = candidates.firstOrNull { f ->
                val fName = name(f).lowercase()
                val baseName = fName.substringAfterLast('/').substringAfterLast('\\')
                baseName == cleanName || fName == cleanName
            }
            if (exactMatch != null) {
                return exactMatch
            }
        }

        // 3. Explicit fileIndex provided and valid (when season/episode not specified)
        if (fileIndex != null && fileIndex >= 0 && fileIndex < files.size) {
            return files[fileIndex]
        }

        // 4. Match by substring filename/title
        if (!filename.isNullOrBlank()) {
            val cleanName = filename.lowercase().trim()
            val nameMatches = candidates.filter { f ->
                val fName = name(f).lowercase()
                val baseName = fName.substringAfterLast('/').substringAfterLast('\\')
                fName.contains(cleanName) || cleanName.contains(baseName) || baseName.contains(cleanName)
            }
            if (nameMatches.isNotEmpty()) {
                return nameMatches.maxByOrNull { size(it) }
            }
        }

        // 4. Fallback: select largest media candidate
        return candidates.maxByOrNull { size(it) }
    }

    /**
     * Extracts season and episode numbers from video ID formats:
     * - "tt1520211:2:12" -> Pair(2, 12)
     * - "tmdb:1399:2:12" -> Pair(2, 12)
     * - "kitsu:1234:12" -> Pair(1, 12)
     * - "series:12" -> Pair(null, 12)
     */
    fun extractSeasonAndEpisode(videoId: String?): Pair<Int?, Int?> {
        if (videoId.isNullOrBlank()) return Pair(null, null)
        val parts = videoId.split(":")
        val last = parts.lastOrNull()?.toIntOrNull() ?: return Pair(null, null)
        val secondLast = if (parts.size >= 2) parts[parts.size - 2].toIntOrNull() else null
        return if (secondLast != null && secondLast in 1..99) {
            Pair(secondLast, last)
        } else if (secondLast != null && secondLast >= 100) {
            Pair(1, last)
        } else {
            Pair(null, last)
        }
    }
}
