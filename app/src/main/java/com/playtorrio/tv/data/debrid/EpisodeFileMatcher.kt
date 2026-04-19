package com.playtorrio.tv.data.debrid

internal object EpisodeFileMatcher {

    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "mov", "wmv", "flv",
        "webm", "m4v", "ts", "m2ts", "vob"
    )

    fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun isEpisodeMatch(name: String, season: Int, episode: Int): Boolean {
        return matchTier(name, season, episode) > 0
    }

    /**
     * Returns a match score (higher = stronger). 0 means no match.
     *
     *   4 = SxxExx / xxXxx (season + episode together)
     *   3 = "Episode N" / "Ep N" / "E N" (episode-only with explicit prefix)
     *   2 = padded bare number "01", "001" (cannot collide with "1" inside 1080p, 5.1, etc.)
     *   1 = bare number after dash/dot before extension, e.g. " - 1.mkv"
     */
    private fun matchTier(name: String, season: Int, episode: Int): Int {
        val t = name.lowercase()
        // Tier 4: SxxExx / xxXxx
        if (Regex("s0*${season}[ ._-]*e0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 4
        if (Regex("\\b0*${season}x0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 4
        // Tier 3: explicit episode prefix without season
        if (Regex("\\bepisode[ ._-]*0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 3
        if (Regex("\\bep\\.?[ ._-]*0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 3
        if (Regex("\\be0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 3
        // Tier 2: padded number "01", "001" (must have leading zero)
        if (Regex("\\b0+${episode}\\b").containsMatchIn(t)) return 2
        // Tier 1: bare number right before file extension, " - 1.mkv" / ".1.mkv" / "_1.mkv"
        if (Regex("[\\s._\\-\u2013]0*${episode}\\.[a-z0-9]{2,5}$", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 1
        return 0
    }

    /**
     * Picks the best file from a list. For episodes, prefers the file whose name matches
     * the S/E pattern (largest if multiple). Falls back to the largest video file.
     *
     * @param files list of (id, name, sizeBytes)
     * @return the chosen file id, or null if no video file found.
     */
    fun <T> pickFile(
        files: List<Triple<T, String, Long>>,
        isMovie: Boolean,
        season: Int?,
        episode: Int?
    ): T? {
        val videos = files.filter { isVideoFile(it.second) }
        if (videos.isEmpty()) return null

        if (!isMovie && season != null && episode != null) {
            val scored = videos.map { it to matchTier(it.second, season, episode) }
                .filter { it.second > 0 }
            if (scored.isNotEmpty()) {
                val best = scored.maxOf { it.second }
                return scored.filter { it.second == best }
                    .maxByOrNull { it.first.third }!!
                    .first.first
            }
        }
        return videos.maxByOrNull { it.third }!!.first
    }
}
