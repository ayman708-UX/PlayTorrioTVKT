package com.playtorrio.tv.data.debrid

import com.playtorrio.tv.data.AppPreferences

object DebridResolver {

    /**
     * Resolves a magnet URI to a direct HTTPS download URL via the active debrid provider.
     *
     * Returns:
     *  - A direct URL string on success.
     *  - null if debrid is disabled, source is not a magnet, or resolution fails/is uncached.
     *
     * Caller falls back to TorrServer when null is returned.
     */
    suspend fun resolve(
        source: String,
        isMovie: Boolean = true,
        season: Int? = null,
        episode: Int? = null,
    ): String? {
        if (!AppPreferences.debridEnabled) return null
        if (!source.startsWith("magnet:", ignoreCase = true)) return null
        return when (AppPreferences.debridProvider) {
            "realdebrid" -> RealDebridClient.resolve(source, isMovie, season, episode)
            "torbox"     -> TorBoxClient.resolve(source, isMovie, season, episode)
            else         -> null
        }
    }
}
