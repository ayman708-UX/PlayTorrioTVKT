package com.playtorrio.tv.data.iptv

/** Raw scraped Xtream-Codes portal credentials (unverified). */
data class IptvPortal(
    val url: String,
    val username: String,
    val password: String,
    val source: String = "",
)

/** Portal that successfully authenticated against /player_api.php. */
data class VerifiedPortal(
    val portal: IptvPortal,
    val name: String,
    val expiry: String,
    val maxConnections: String,
    val activeConnections: String,
)

data class IptvCategory(
    val id: String,
    val name: String,
)

/**
 * Single playable stream entry.
 *  - `kind` = "live" / "vod" / "series"
 *  - For series, `streamId` is actually the series_id; resolution to episodes
 *    happens via a separate API call.
 */
data class IptvStream(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val containerExt: String, // "ts" / "mp4" / "mkv"
    val kind: String,
)

enum class IptvSection { LIVE, VOD, SERIES }

data class IptvEpisode(
    val id: String,
    val title: String,
    val containerExt: String,
    val season: Int,
    val episode: Int,
    val plot: String,
    val image: String,
)
