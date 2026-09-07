package com.playtorrio.tv.core.debrid

import com.playtorrio.tv.domain.model.DebridSettings
import com.playtorrio.tv.domain.model.Stream
import com.playtorrio.tv.domain.model.StreamBehaviorHints
import com.playtorrio.tv.domain.model.StreamDebridCacheState
import com.playtorrio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebridStreamFormatterTest {
    @Test
    fun `formats cached local debrid torrents with tv default formatter`() {
        val formatter = DebridStreamFormatter(DebridStreamTemplateEngine())
        val stream = Stream(
            name = "Original torrent",
            title = "movie",
            description = null,
            url = null,
            ytId = null,
            infoHash = "abc123",
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = null,
                countryWhitelist = null,
                proxyHeaders = null,
                filename = "Movie.2026.1080p.mkv"
            ),
            addonName = "Addon",
            addonLogo = null,
            debridCacheStatus = StreamDebridCacheStatus(
                providerId = DebridProviders.TORBOX_ID,
                providerName = "Torbox",
                state = StreamDebridCacheState.CACHED,
                cachedName = "Movie.2026.1080p.mkv",
                cachedSize = 1_500_000_000L
            )
        )

        val formatted = formatter.format(stream, DebridSettings())

        assertEquals("FHD TB Instant", formatted.name)
        assertTrue(formatted.description.orEmpty().contains("Ready (TB)"))
        assertTrue(formatted.description.orEmpty().contains("Movie.2026.1080p.mkv"))
    }
}
