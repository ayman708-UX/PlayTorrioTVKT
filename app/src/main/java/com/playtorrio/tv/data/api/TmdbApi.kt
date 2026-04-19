package com.playtorrio.tv.data.api

import com.playtorrio.tv.data.model.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    // ── Find by external ID (IMDB → TMDB) ──
    @GET("find/{externalId}")
    suspend fun findByExternalId(
        @Path("externalId") externalId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") externalSource: String = "imdb_id"
    ): TmdbFindResponse

    // ── Home feed ──
    @GET("trending/all/day")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    // ── Genres ──
    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("api_key") apiKey: String
    ): GenreResponse

    @GET("genre/tv/list")
    suspend fun getTvGenres(
        @Query("api_key") apiKey: String
    ): GenreResponse

    // ── Images ──
    @GET("movie/{id}/images")
    suspend fun getMovieImages(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") languages: String = "en,null"
    ): TmdbImagesResponse

    @GET("tv/{id}/images")
    suspend fun getTvImages(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") languages: String = "en,null"
    ): TmdbImagesResponse

    // ── Videos ──
    @GET("movie/{id}/videos")
    suspend fun getMovieVideos(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): Response<TmdbVideosResponse>

    @GET("tv/{id}/videos")
    suspend fun getTvVideos(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): Response<TmdbVideosResponse>

    // ── Movie details ──
    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): MovieDetail

    @GET("movie/{id}/credits")
    suspend fun getMovieCredits(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): CreditsResponse

    @GET("movie/{id}/similar")
    suspend fun getSimilarMovies(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("movie/{id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    // ── TV details ──
    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): TvDetail

    @GET("tv/{id}/aggregate_credits")
    suspend fun getTvCredits(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): TvCreditsResponse

    @GET("tv/{id}/similar")
    suspend fun getSimilarTv(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("tv/{id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("tv/{id}/season/{season}")
    suspend fun getTvSeason(
        @Path("id") id: Int,
        @Path("season") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): SeasonDetail

    // ── Person ──
    @GET("person/{id}")
    suspend fun getPersonDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): PersonDetail

    @GET("movie/{id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): com.playtorrio.tv.data.model.ExternalIds

    @GET("tv/{id}/external_ids")
    suspend fun getTvExternalIds(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): com.playtorrio.tv.data.model.ExternalIds

    @GET("person/{id}/combined_credits")
    suspend fun getPersonCredits(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): PersonCreditsResponse

    @GET("person/{id}/images")
    suspend fun getPersonImages(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): PersonImagesResponse

    // ── Company / Studio ──
    @GET("company/{id}")
    suspend fun getCompanyDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): CompanyDetail

    @GET("discover/movie")
    suspend fun discoverMoviesByCompany(
        @Query("api_key") apiKey: String,
        @Query("with_companies") companyId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("discover/tv")
    suspend fun discoverTvByCompany(
        @Query("api_key") apiKey: String,
        @Query("with_companies") companyId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    // ── Network ──
    @GET("network/{id}")
    suspend fun getNetworkDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): CompanyDetail

    @GET("discover/tv")
    suspend fun discoverTvByNetwork(
        @Query("api_key") apiKey: String,
        @Query("with_networks") networkId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("discover/movie")
    suspend fun discoverMoviesByNetwork(
        @Query("api_key") apiKey: String,
        @Query("with_networks") networkId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    // ── Search ──
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse<TmdbMedia>
}
