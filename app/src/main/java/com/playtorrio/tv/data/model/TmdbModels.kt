package com.playtorrio.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExternalIds(
    @Json(name = "imdb_id") val imdbId: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbFindResponse(
    @Json(name = "movie_results") val movieResults: List<TmdbMedia> = emptyList(),
    @Json(name = "tv_results") val tvResults: List<TmdbMedia> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbResponse<T>(
    val page: Int,
    val results: List<T>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)

@JsonClass(generateAdapter = true)
data class TmdbMedia(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "origin_country") val originCountry: List<String>? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    val cardBackdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val isMovie: Boolean get() = title != null || mediaType == "movie"
}

@JsonClass(generateAdapter = true)
data class TmdbImage(
    @Json(name = "file_path") val filePath: String,
    @Json(name = "iso_639_1") val language: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @Json(name = "vote_average") val voteAverage: Double = 0.0
) {
    val url: String get() = "https://image.tmdb.org/t/p/w500$filePath"
}

@JsonClass(generateAdapter = true)
data class TmdbImagesResponse(
    val id: Int,
    val logos: List<TmdbImage>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class GenreResponse(
    val genres: List<TmdbGenre>
)

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    val id: Int,
    val results: List<TmdbVideoResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbVideoResult(
    @Json(name = "key") val key: String? = null,
    @Json(name = "site") val site: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "official") val official: Boolean? = null,
    @Json(name = "size") val size: Int? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)

// ══════════════════════════════════════════════════════════════
// MOVIE DETAIL
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class ProductionCompany(
    val id: Int,
    val name: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null
) {
    val logoUrl: String? get() = logoPath?.let { "https://image.tmdb.org/t/p/w200$it" }
}

@JsonClass(generateAdapter = true)
data class MovieDetail(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenre>? = null,
    @Json(name = "production_companies") val productionCompanies: List<ProductionCompany>? = null,
    @Json(name = "spoken_languages") val spokenLanguages: List<SpokenLanguage>? = null
) {
    val displayTitle: String get() = title ?: "Unknown"
    val year: String? get() = releaseDate?.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    val runtimeFormatted: String?
        get() = runtime?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}

@JsonClass(generateAdapter = true)
data class SpokenLanguage(
    @Json(name = "english_name") val englishName: String? = null,
    @Json(name = "iso_639_1") val iso: String? = null,
    val name: String? = null
)

// ══════════════════════════════════════════════════════════════
// TV DETAIL
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class TvDetail(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "last_air_date") val lastAirDate: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int>? = null,
    val status: String? = null,
    val type: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenre>? = null,
    val seasons: List<SeasonSummary>? = null,
    @Json(name = "production_companies") val productionCompanies: List<ProductionCompany>? = null,
    @Json(name = "created_by") val createdBy: List<Creator>? = null,
    val networks: List<ProductionCompany>? = null
) {
    val displayTitle: String get() = name ?: "Unknown"
    val year: String? get() = firstAirDate?.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    val yearRange: String?
        get() {
            val start = firstAirDate?.take(4) ?: return null
            val end = if (status == "Ended" || status == "Canceled") lastAirDate?.take(4) else "Present"
            return "$start–${end ?: "Present"}"
        }
}

@JsonClass(generateAdapter = true)
data class Creator(
    val id: Int,
    val name: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

@JsonClass(generateAdapter = true)
data class SeasonSummary(
    val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null
) {
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
}

// ══════════════════════════════════════════════════════════════
// SEASON DETAIL + EPISODES
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class SeasonDetail(
    val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    val episodes: List<Episode>? = null
)

@JsonClass(generateAdapter = true)
data class Episode(
    val id: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null
) {
    val stillUrl: String? get() = stillPath?.let { "https://image.tmdb.org/t/p/w400$it" }
    val runtimeFormatted: String?
        get() = runtime?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}

// ══════════════════════════════════════════════════════════════
// CREDITS (MOVIE)
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class CreditsResponse(
    val id: Int,
    val cast: List<CastMember>? = null,
    val crew: List<CrewMember>? = null
)

@JsonClass(generateAdapter = true)
data class CastMember(
    val id: Int,
    val name: String? = null,
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int? = null,
    @Json(name = "known_for_department") val knownForDepartment: String? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

@JsonClass(generateAdapter = true)
data class CrewMember(
    val id: Int,
    val name: String? = null,
    val job: String? = null,
    val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

// ══════════════════════════════════════════════════════════════
// CREDITS (TV — aggregate)
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class TvCreditsResponse(
    val id: Int,
    val cast: List<TvCastMember>? = null,
    val crew: List<TvCrewMember>? = null
)

@JsonClass(generateAdapter = true)
data class TvCastMember(
    val id: Int,
    val name: String? = null,
    val roles: List<TvRole>? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int? = null,
    @Json(name = "total_episode_count") val totalEpisodeCount: Int? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
    val mainCharacter: String? get() = roles?.maxByOrNull { it.episodeCount ?: 0 }?.character
}

@JsonClass(generateAdapter = true)
data class TvRole(
    val character: String? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class TvCrewMember(
    val id: Int,
    val name: String? = null,
    val jobs: List<TvJob>? = null,
    val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "total_episode_count") val totalEpisodeCount: Int? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
    val mainJob: String? get() = jobs?.maxByOrNull { it.episodeCount ?: 0 }?.job
}

@JsonClass(generateAdapter = true)
data class TvJob(
    val job: String? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null
)

// ══════════════════════════════════════════════════════════════
// PERSON
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class PersonDetail(
    val id: Int,
    val name: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @Json(name = "place_of_birth") val placeOfBirth: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "known_for_department") val knownForDepartment: String? = null,
    val gender: Int? = null,
    val popularity: Double? = null,
    @Json(name = "also_known_as") val alsoKnownAs: List<String>? = null
) {
    val profileUrl: String? get() = profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val age: Int?
        get() {
            val birth = birthday ?: return null
            val end = deathday ?: java.time.LocalDate.now().toString()
            return try {
                val by = birth.take(4).toInt()
                val ey = end.take(4).toInt()
                ey - by
            } catch (_: Exception) { null }
        }
}

@JsonClass(generateAdapter = true)
data class PersonCreditsResponse(
    val id: Int,
    val cast: List<PersonCastCredit>? = null,
    val crew: List<PersonCrewCredit>? = null
)

@JsonClass(generateAdapter = true)
data class PersonCastCredit(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val character: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    val popularity: Double? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val isMovie: Boolean get() = mediaType == "movie"
}

@JsonClass(generateAdapter = true)
data class PersonCrewCredit(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val job: String? = null,
    val department: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    val popularity: Double? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val isMovie: Boolean get() = mediaType == "movie"
}

@JsonClass(generateAdapter = true)
data class PersonImagesResponse(
    val id: Int,
    val profiles: List<PersonImage>? = null
)

@JsonClass(generateAdapter = true)
data class PersonImage(
    @Json(name = "file_path") val filePath: String,
    val width: Int = 0,
    val height: Int = 0,
    @Json(name = "vote_average") val voteAverage: Double = 0.0
) {
    val url: String get() = "https://image.tmdb.org/t/p/w500$filePath"
}

// ══════════════════════════════════════════════════════════════
// COMPANY / STUDIO
// ══════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class CompanyDetail(
    val id: Int,
    val name: String? = null,
    val description: String? = null,
    val headquarters: String? = null,
    val homepage: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null,
    @Json(name = "parent_company") val parentCompany: ParentCompany? = null
) {
    val logoUrl: String? get() = logoPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

@JsonClass(generateAdapter = true)
data class ParentCompany(
    val id: Int,
    val name: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null
)
