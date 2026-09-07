package com.playtorrio.tv.core.scraper

import com.playtorrio.tv.domain.model.ProxyHeaders
import com.playtorrio.tv.domain.model.Stream
import com.playtorrio.tv.domain.model.StreamBehaviorHints

data class ScraperMediaRequest(
    val type: String, // "movie", "tv", "series"
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null
) {
    val isMovie: Boolean get() = type.equals("movie", ignoreCase = true)
    val isSeries: Boolean get() = type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)
}

data class ScraperStreamResult(
    val name: String = "PlayTorrioHTTP",
    val title: String,
    val description: String? = null,
    val url: String,
    val quality: String? = null,
    val headers: Map<String, String>? = null,
    val behaviorHints: Map<String, Any?>? = null
) {
    fun toDomainStream(): Stream {
        val finalHeaders = headers ?: emptyMap()
        return Stream(
            name = name,
            title = title,
            description = description,
            url = url,
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = false,
                bingeGroup = null,
                countryWhitelist = null,
                proxyHeaders = if (finalHeaders.isNotEmpty()) {
                    ProxyHeaders(request = finalHeaders, response = null)
                } else null
            ),
            addonName = "PlayTorrioHTTP",
            addonLogo = null,
            quality = quality
        )
    }
}

interface StreamScraper {
    val name: String get() = "PlayTorrioHTTP"
    suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult>
}
