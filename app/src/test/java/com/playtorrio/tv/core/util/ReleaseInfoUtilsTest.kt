package com.playtorrio.tv.core.util

import com.playtorrio.tv.domain.model.ContentType
import com.playtorrio.tv.domain.model.MetaPreview
import com.playtorrio.tv.domain.model.PosterShape
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInfoUtilsTest {
    private val eastern = ZoneId.of("America/Detroit")

    @Test
    fun `catalog release filtering uses exact timestamp`() {
        val item = preview(released = "2026-07-15T15:00:00Z")
        val today = LocalDate.of(2026, 7, 15)

        assertTrue(
            item.isUnreleased(
                today = today,
                clock = Clock.fixed(Instant.parse("2026-07-15T14:59:59Z"), eastern)
            )
        )
        assertFalse(
            item.isUnreleased(
                today = today,
                clock = Clock.fixed(Instant.parse("2026-07-15T15:00:00Z"), eastern)
            )
        )
    }

    @Test
    fun `catalog date only release starts at utc midnight`() {
        val item = preview(released = "2026-07-15")
        val today = LocalDate.of(2026, 7, 14)

        assertTrue(
            item.isUnreleased(
                today = today,
                clock = Clock.fixed(Instant.parse("2026-07-14T23:59:59Z"), eastern)
            )
        )
        assertFalse(
            item.isUnreleased(
                today = today,
                clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), eastern)
            )
        )
    }

    // --- #2793: TMDB titles with no release information at all ---
    // A TMDB collection can contain announced-but-undated titles (e.g.
    // "Untitled Top Gun 3": release_date is empty on TMDB). Those map to
    // released == null and releaseInfo == null, which the date-based
    // isUnreleased() cannot flag — the strict TMDB collection path catches
    // them via hasNoReleaseInfo() instead.

    @Test
    fun `undated item is not caught by date-based isUnreleased`() {
        val item = preview(released = null)
        assertFalse(item.isUnreleased(today = LocalDate.of(2026, 7, 28)))
    }

    @Test
    fun `undated item is flagged by hasNoReleaseInfo`() {
        assertTrue(preview(released = null).hasNoReleaseInfo())
        assertTrue(preview(released = " ", releaseInfo = "").hasNoReleaseInfo())
    }

    @Test
    fun `dated items are not flagged by hasNoReleaseInfo`() {
        assertFalse(preview(released = "2022-05-27").hasNoReleaseInfo())
        assertFalse(preview(released = null, releaseInfo = "2022").hasNoReleaseInfo())
    }

    private fun preview(released: String?, releaseInfo: String? = null): MetaPreview = MetaPreview(
        id = "tt1",
        type = ContentType.MOVIE,
        name = "Title",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = releaseInfo,
        imdbRating = null,
        genres = emptyList(),
        released = released,
    )
}
