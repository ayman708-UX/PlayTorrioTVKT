package com.playtorrio.tv.core.anime.model

import com.playtorrio.tv.domain.model.ContentType
import com.playtorrio.tv.domain.model.MetaPreview
import com.playtorrio.tv.domain.model.PosterShape

data class AnimeMedia(
    val id: Int,
    val idMal: Int? = null,
    val titleRomaji: String = "",
    val titleEnglish: String = "",
    val titleNative: String = "",
    val titleUserPreferred: String = "",
    val coverImageExtraLarge: String = "",
    val coverImageLarge: String = "",
    val coverImageMedium: String = "",
    val coverColor: String? = null,
    val bannerImage: String = "",
    val format: String = "TV",
    val status: String = "FINISHED",
    val totalEpisodes: Int = 0,
    val durationMinutes: Int = 24,
    val genres: List<String> = emptyList(),
    val averageScore: Int = 0,
    val meanScore: Int = 0,
    val popularity: Int = 0,
    val favourites: Int = 0,
    val season: String = "",
    val seasonYear: Int = 0,
    val description: String = "",
    val studioName: String = "",
    val trailerUrl: String? = null,
    val trailerSite: String? = null,
    val nextAiring: AnimeNextAiring? = null,
    val characters: List<AnimeCharacter> = emptyList(),
    val relations: List<AnimeRelation> = emptyList(),
    val recommendations: List<AnimeMedia> = emptyList(),
    val isAdult: Boolean = false,
    val slug: String = "",
    val isArabic: Boolean = false
) {
    val displayTitle: String
        get() {
            fun isValid(s: String): Boolean = s.isNotBlank() && !s.equals("null", ignoreCase = true)
            if (isValid(titleEnglish)) return titleEnglish
            if (isValid(titleUserPreferred)) return titleUserPreferred
            if (isValid(titleRomaji)) return titleRomaji
            if (isValid(titleNative)) return titleNative
            return if (id > 0) "Anime #$id" else "Anime"
        }

    val coverUrl: String
        get() {
            fun isValid(s: String): Boolean = s.isNotBlank() && !s.equals("null", ignoreCase = true)
            if (isValid(coverImageExtraLarge)) return coverImageExtraLarge
            if (isValid(coverImageLarge)) return coverImageLarge
            if (isValid(coverImageMedium)) return coverImageMedium
            return ""
        }

    val backdropUrl: String
        get() {
            fun isValid(s: String): Boolean = s.isNotBlank() && !s.equals("null", ignoreCase = true)
            if (isValid(bannerImage)) return bannerImage
            return coverUrl
        }

    val formattedScore: String
        get() {
            if (averageScore <= 0) return "N/A"
            return String.format("%.1f", averageScore / 10.0)
        }

    val formattedFormat: String
        get() = when (format.uppercase()) {
            "TV" -> "TV Series"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "SPECIAL" -> "Special"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            else -> format
        }

    val formattedStatus: String
        get() = when (status.uppercase()) {
            "RELEASING" -> "Airing"
            "FINISHED" -> "Completed"
            "NOT_YET_RELEASED" -> "Upcoming"
            "CANCELLED" -> "Cancelled"
            "HIATUS" -> "On Hiatus"
            else -> status
        }

    val formattedSeasonYear: String
        get() {
            val s = if (season.isNotBlank()) {
                season.lowercase().replaceFirstChar { it.uppercase() }
            } else ""
            return if (seasonYear > 0) {
                if (s.isNotBlank()) "$s $seasonYear" else "$seasonYear"
            } else s
        }

    fun toMetaPreview(): MetaPreview {
        val release = if (seasonYear > 0) seasonYear.toString() else formattedSeasonYear
        val score = if (averageScore > 0) (averageScore / 10.0f) else null
        return MetaPreview(
            id = "anime_$id",
            name = displayTitle,
            type = if (format.equals("MOVIE", ignoreCase = true)) ContentType.MOVIE else ContentType.SERIES,
            poster = coverUrl.ifBlank { null },
            posterShape = PosterShape.POSTER,
            background = backdropUrl.ifBlank { null },
            logo = null,
            description = description.ifBlank { null },
            releaseInfo = release.ifBlank { null },
            imdbRating = score,
            genres = genres
        )
    }
}

data class AnimeNextAiring(
    val episode: Int,
    val timeUntilAiringSeconds: Long,
    val airingAtTimestamp: Long
)

data class AnimeCharacter(
    val id: Int,
    val nameFull: String,
    val nameNative: String = "",
    val imageLarge: String = "",
    val role: String = ""
)

data class AnimeRelation(
    val relationType: String,
    val id: Int,
    val title: String,
    val format: String,
    val status: String,
    val coverUrl: String
)

data class AnimeEpisode(
    val number: Int,
    val title: String = "",
    val thumbnail: String = "",
    val description: String = "",
    val isFiller: Boolean = false,
    val encodedHref: String = "",
    val watchPath: String = ""
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank() && !title.equals("null", ignoreCase = true) && !title.equals("Episode $number", ignoreCase = true)) {
                return "EP $number: $title"
            }
            return "Episode $number"
        }
}

data class AnimeStreamTrack(
    val url: String,
    val label: String,
    val lang: String = "en",
    val kind: String = "subtitles",
    val isDefault: Boolean = false
)

data class AnimeStreamResult(
    val streamUrl: String,
    val serverName: String,
    val category: String = "SUB", // "SUB" or "DUB"
    val quality: String = "Auto",
    val headers: Map<String, String> = emptyMap(),
    val tracks: List<AnimeStreamTrack> = emptyList(),
    val introStart: Int? = null,
    val introEnd: Int? = null,
    val outroStart: Int? = null,
    val outroEnd: Int? = null,
    val isDirectMp4: Boolean = false
)
