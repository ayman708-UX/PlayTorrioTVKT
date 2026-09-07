package com.playtorrio.tv.core.iptv.model

/**
 * Raw scraped Xtream-Codes portal credentials (unverified).
 */
data class IptvPortal(
    val url: String,
    val username: String,
    val password: String,
    val source: String = ""
) {
    val key: String get() = "$url|$username|$password".lowercase()
    val credKey: String get() = "$username|$password".lowercase()
}

/**
 * Portal that successfully authenticated against /player_api.php.
 */
data class VerifiedPortal(
    val portal: IptvPortal,
    val name: String,
    val expiry: String,
    val maxConnections: String = "1",
    val activeConnections: String = "0"
) {
    val key: String get() = portal.key
    val credKey: String get() = portal.credKey
}

data class IptvCategory(
    val id: String,
    val name: String
)

enum class IptvSection {
    LIVE, VOD, SERIES
}

/**
 * Single playable stream entry. [kind] = "live" / "vod" / "series".
 */
data class IptvStream(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val containerExt: String,
    val kind: String,
    val epgChannelId: String = ""
)

/**
 * Single EPG programme entry returned by Xtream `get_short_epg`.
 */
data class EpgEntry(
    val title: String,
    val description: String,
    val start: Long,
    val stop: Long
) {
    val isNow: Boolean get() {
        val now = System.currentTimeMillis()
        return now in start..stop
    }
}

data class IptvEpisode(
    val id: String,
    val title: String,
    val containerExt: String,
    val season: Int,
    val episode: Int,
    val plot: String = "",
    val image: String = ""
)

/**
 * A single alive stream found while resolving a HardcodedChannel.
 */
data class ChannelHit(
    val portal: VerifiedPortal,
    val stream: IptvStream,
    val streamUrl: String
)

data class ScrapePage(
    val portals: List<IptvPortal>,
    val nextAfter: String? = null
) {
    val hasMore: Boolean get() = !nextAfter.isNullOrEmpty()
}

data class AliveProgress(
    val checked: Int = 0,
    val total: Int = 0,
    val alive: Int = 0
)

enum class CatalogSource(val label: String, val description: String) {
    REDDIT("Reddit", "Live scrapers from Reddit IPTV subreddits"),
    CLOUD_VAULT("Cloud Vault", "High-speed cloud database with 9,000+ live IPTV servers")
}

data class M3uChannel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val tvgId: String = "",
    val tvgName: String = ""
)

data class M3uPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val count: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
