package com.playtorrio.tv.ui.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.model.*
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.stremio.StremioStream
import com.playtorrio.tv.data.torrent.TorrentResult
import com.playtorrio.tv.data.torrent.TorrentSearchRequest
import com.playtorrio.tv.data.torrent.TorrentSearchService
import com.playtorrio.tv.data.trailer.TrailerPlaybackSource
import com.playtorrio.tv.data.trailer.TrailerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    // Common
    val title: String = "",
    val tagline: String? = null,
    val overview: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val logoUrl: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val year: String? = null,
    val status: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val productionCompanies: List<ProductionCompany> = emptyList(),
    val isMovie: Boolean = true,
    val mediaId: Int? = null,
    // Movie specific
    val runtime: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    // TV specific
    val yearRange: String? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val createdBy: List<Creator> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    // Credits
    val cast: List<CastInfo> = emptyList(),
    val directors: List<CrewInfo> = emptyList(),
    val writers: List<CrewInfo> = emptyList(),
    // Related
    val similar: List<TmdbMedia> = emptyList(),
    val recommendations: List<TmdbMedia> = emptyList(),
    // Season detail (when viewing episodes)
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    // Torrent overlay
    val showTorrentOverlay: Boolean = false,
    val torrentResults: List<TorrentResult> = emptyList(),
    val isLoadingTorrents: Boolean = false,
    val torrentSearchLabel: String = "",   // "S01E01" or movie name
    val torrentSeasonNumber: Int? = null,
    val torrentEpisodeNumber: Int? = null,
    val torrentEpisodeTitle: String? = null,
    // Streaming splash
    val showStreamingSplash: Boolean = false,
    val streamingSeasonNumber: Int? = null,
    val streamingEpisodeNumber: Int? = null,
    val streamingEpisodeTitle: String? = null,
    // Stremio
    val imdbId: String? = null,
    val stremioStreams: List<StremioStream> = emptyList(),
    val isLoadingStremioStreams: Boolean = false,
    // Trailer
    val trailerSource: TrailerPlaybackSource? = null,
    val isTrailerPlaying: Boolean = false,
    val isLoadingTrailer: Boolean = false
)

data class CastInfo(
    val id: Int,
    val name: String,
    val character: String?,
    val profileUrl: String?,
    val episodeCount: Int? = null
)

data class CrewInfo(
    val id: Int,
    val name: String,
    val job: String?,
    val profileUrl: String?
)

class DetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY
    private var trailerJob: Job? = null

    fun playTrailer() {
        val state = _uiState.value
        val mediaId = state.mediaId ?: return
        if (state.isTrailerPlaying || state.isLoadingTrailer) return
        trailerJob?.cancel()
        trailerJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTrailer = true)
            try {
                val source = TrailerService.getTrailer(mediaId, state.isMovie)
                if (source != null) {
                    _uiState.value = _uiState.value.copy(
                        trailerSource = source,
                        isTrailerPlaying = true,
                        isLoadingTrailer = false
                    )
                } else {
                    Log.i("DetailViewModel", "No trailer found for $mediaId")
                    _uiState.value = _uiState.value.copy(isLoadingTrailer = false)
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Trailer fetch failed", e)
                _uiState.value = _uiState.value.copy(isLoadingTrailer = false)
            }
        }
    }

    fun onTrailerEnded() {
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false
        )
    }

    fun onTrailerFailed() {
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false
        )
    }

    fun cancelTrailer() {
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false,
            isLoadingTrailer = false
        )
    }

    fun load(mediaId: Int, isMovie: Boolean) {
        viewModelScope.launch {
            try {
                if (isMovie) loadMovie(mediaId) else loadTv(mediaId)
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Failed to load details", e)
                _uiState.value = DetailUiState(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadMovie(id: Int) {
        val detailDeferred = viewModelScope.async { api.getMovieDetails(id, key) }
        val creditsDeferred = viewModelScope.async { api.getMovieCredits(id, key) }
        val similarDeferred = viewModelScope.async { runCatching { api.getSimilarMovies(id, key) }.getOrNull() }
        val recsDeferred = viewModelScope.async { runCatching { api.getMovieRecommendations(id, key) }.getOrNull() }
        val imagesDeferred = viewModelScope.async { runCatching { api.getMovieImages(id, key) }.getOrNull() }
        val externalIdsDeferred = viewModelScope.async { runCatching { api.getMovieExternalIds(id, key) }.getOrNull() }

        val detail = detailDeferred.await()
        val credits = creditsDeferred.await()
        val similar = similarDeferred.await()
        val recs = recsDeferred.await()
        val images = imagesDeferred.await()
        val externalIds = externalIdsDeferred.await()

        val logoUrl = images?.logos
            ?.filter { it.language == "en" || it.language == null }
            ?.maxByOrNull { it.voteAverage }?.url

        val directors = credits.crew
            ?.filter { it.job == "Director" }
            ?.map { CrewInfo(it.id, it.name ?: "Unknown", it.job, it.profileUrl) }
            ?: emptyList()

        val writers = credits.crew
            ?.filter { it.job == "Screenplay" || it.job == "Writer" || it.job == "Story" }
            ?.distinctBy { it.id }
            ?.map { CrewInfo(it.id, it.name ?: "Unknown", it.job, it.profileUrl) }
            ?: emptyList()

        val cast = credits.cast
            ?.sortedBy { it.order ?: 999 }
            ?.take(30)
            ?.map { CastInfo(it.id, it.name ?: "Unknown", it.character, it.profileUrl) }
            ?: emptyList()

        _uiState.value = DetailUiState(
            isLoading = false,
            title = detail.displayTitle,
            tagline = detail.tagline?.takeIf { it.isNotBlank() },
            overview = detail.overview,
            backdropUrl = detail.backdropUrl,
            posterUrl = detail.posterUrl,
            logoUrl = logoUrl,
            voteAverage = detail.voteAverage,
            voteCount = detail.voteCount,
            year = detail.year,
            status = detail.status,
            genres = detail.genres ?: emptyList(),
            productionCompanies = detail.productionCompanies ?: emptyList(),
            isMovie = true,
            mediaId = id,
            runtime = detail.runtimeFormatted,
            budget = detail.budget?.takeIf { it > 0 },
            revenue = detail.revenue?.takeIf { it > 0 },
            cast = cast,
            directors = directors,
            writers = writers,
            similar = similar?.results ?: emptyList(),
            recommendations = recs?.results ?: emptyList(),
            imdbId = externalIds?.imdbId
        )
    }

    private suspend fun loadTv(id: Int) {
        val detailDeferred = viewModelScope.async { api.getTvDetails(id, key) }
        val creditsDeferred = viewModelScope.async { runCatching { api.getTvCredits(id, key) }.getOrNull() }
        val similarDeferred = viewModelScope.async { runCatching { api.getSimilarTv(id, key) }.getOrNull() }
        val recsDeferred = viewModelScope.async { runCatching { api.getTvRecommendations(id, key) }.getOrNull() }
        val imagesDeferred = viewModelScope.async { runCatching { api.getTvImages(id, key) }.getOrNull() }
        val externalIdsDeferred = viewModelScope.async { runCatching { api.getTvExternalIds(id, key) }.getOrNull() }

        val detail = detailDeferred.await()
        val credits = creditsDeferred.await()
        val similar = similarDeferred.await()
        val recs = recsDeferred.await()
        val images = imagesDeferred.await()
        val externalIds = externalIdsDeferred.await()

        val logoUrl = images?.logos
            ?.filter { it.language == "en" || it.language == null }
            ?.maxByOrNull { it.voteAverage }?.url

        val directors = credits?.crew
            ?.filter { it.department == "Directing" }
            ?.sortedByDescending { it.totalEpisodeCount ?: 0 }
            ?.take(5)
            ?.map { CrewInfo(it.id, it.name ?: "Unknown", it.mainJob, it.profileUrl) }
            ?: emptyList()

        val writers = credits?.crew
            ?.filter { it.department == "Writing" }
            ?.sortedByDescending { it.totalEpisodeCount ?: 0 }
            ?.take(5)
            ?.map { CrewInfo(it.id, it.name ?: "Unknown", it.mainJob, it.profileUrl) }
            ?: emptyList()

        val cast = credits?.cast
            ?.sortedBy { it.order ?: 999 }
            ?.take(30)
            ?.map { CastInfo(it.id, it.name ?: "Unknown", it.mainCharacter, it.profileUrl, it.totalEpisodeCount) }
            ?: emptyList()

        val validSeasons = detail.seasons
            ?.filter { it.seasonNumber > 0 }
            ?: emptyList()

        _uiState.value = DetailUiState(
            isLoading = false,
            title = detail.displayTitle,
            tagline = detail.tagline?.takeIf { it.isNotBlank() },
            overview = detail.overview,
            backdropUrl = detail.backdropUrl,
            posterUrl = detail.posterUrl,
            logoUrl = logoUrl,
            voteAverage = detail.voteAverage,
            voteCount = detail.voteCount,
            year = detail.year,
            yearRange = detail.yearRange,
            status = detail.status,
            genres = detail.genres ?: emptyList(),
            productionCompanies = detail.productionCompanies ?: emptyList(),
            isMovie = false,
            mediaId = id,
            numberOfSeasons = detail.numberOfSeasons,
            numberOfEpisodes = detail.numberOfEpisodes,
            createdBy = detail.createdBy ?: emptyList(),
            seasons = validSeasons,
            cast = cast,
            directors = directors,
            writers = writers,
            similar = similar?.results ?: emptyList(),
            recommendations = recs?.results ?: emptyList(),
            selectedSeason = validSeasons.firstOrNull()?.seasonNumber ?: 1,
            imdbId = externalIds?.imdbId
        )

        // Auto-load first season episodes
        if (validSeasons.isNotEmpty()) {
            loadSeason(id, validSeasons.first().seasonNumber)
        }
    }

    fun selectSeason(tvId: Int, seasonNumber: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber, isLoadingEpisodes = true)
        viewModelScope.launch {
            loadSeason(tvId, seasonNumber)
        }
    }

    private suspend fun loadSeason(tvId: Int, seasonNumber: Int) {
        _uiState.value = _uiState.value.copy(isLoadingEpisodes = true)
        try {
            val season = api.getTvSeason(tvId, seasonNumber, key)
            _uiState.value = _uiState.value.copy(
                episodes = season.episodes ?: emptyList(),
                isLoadingEpisodes = false
            )
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Failed to load season $seasonNumber", e)
            _uiState.value = _uiState.value.copy(episodes = emptyList(), isLoadingEpisodes = false)
        }
    }

    // ── TORRENT OVERLAY ──

    fun searchTorrentsForMovie() {
        val state = _uiState.value
        val label = "${state.title} (${state.year ?: ""})"
        _uiState.value = state.copy(
            showTorrentOverlay = true,
            isLoadingTorrents = true,
            torrentResults = emptyList(),
            torrentSearchLabel = label,
            torrentSeasonNumber = null,
            torrentEpisodeNumber = null,
            torrentEpisodeTitle = null,
            stremioStreams = emptyList(),
            isLoadingStremioStreams = true
        )
        loadStremioStreamsForMovie()
        viewModelScope.launch {
            try {
                val results = TorrentSearchService.search(
                    TorrentSearchRequest(
                        title = state.title,
                        year = state.year,
                        isMovie = true
                    )
                )
                _uiState.value = _uiState.value.copy(
                    torrentResults = results,
                    isLoadingTorrents = false
                )
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Torrent search failed", e)
                _uiState.value = _uiState.value.copy(isLoadingTorrents = false)
            }
        }
    }

    fun searchTorrentsForEpisode(episode: Episode) {
        val state = _uiState.value
        val sNum = state.selectedSeason
        val eNum = episode.episodeNumber
        val label = "${state.title} S${String.format("%02d", sNum)}E${String.format("%02d", eNum)}"
        _uiState.value = state.copy(
            showTorrentOverlay = true,
            isLoadingTorrents = true,
            torrentResults = emptyList(),
            torrentSearchLabel = label,
            torrentSeasonNumber = sNum,
            torrentEpisodeNumber = eNum,
            torrentEpisodeTitle = episode.name,
            stremioStreams = emptyList(),
            isLoadingStremioStreams = true
        )
        loadStremioStreamsForEpisode(sNum, eNum)
        viewModelScope.launch {
            try {
                val results = TorrentSearchService.search(
                    TorrentSearchRequest(
                        title = state.title,
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                        isMovie = false
                    )
                )
                _uiState.value = _uiState.value.copy(
                    torrentResults = results,
                    isLoadingTorrents = false
                )
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Torrent search failed", e)
                _uiState.value = _uiState.value.copy(isLoadingTorrents = false)
            }
        }
    }

    private fun loadStremioStreamsForMovie() {
        viewModelScope.launch {
            try {
                val imdbId = ensureImdbId() ?: run {
                    _uiState.value = _uiState.value.copy(isLoadingStremioStreams = false)
                    return@launch
                }
                val addons = StremioAddonRepository.getAddons()
                val streams = StremioService.getStreams(addons, "movie", imdbId)
                _uiState.value = _uiState.value.copy(
                    stremioStreams = streams,
                    isLoadingStremioStreams = false
                )
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Stremio movie streams failed", e)
                _uiState.value = _uiState.value.copy(isLoadingStremioStreams = false)
            }
        }
    }

    private fun loadStremioStreamsForEpisode(season: Int, episode: Int) {
        viewModelScope.launch {
            try {
                val imdbId = ensureImdbId() ?: run {
                    _uiState.value = _uiState.value.copy(isLoadingStremioStreams = false)
                    return@launch
                }
                val videoId = "$imdbId:$season:$episode"
                val addons = StremioAddonRepository.getAddons()
                val streams = StremioService.getStreams(addons, "series", videoId)
                _uiState.value = _uiState.value.copy(
                    stremioStreams = streams,
                    isLoadingStremioStreams = false
                )
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Stremio episode streams failed", e)
                _uiState.value = _uiState.value.copy(isLoadingStremioStreams = false)
            }
        }
    }

    private suspend fun ensureImdbId(): String? {
        val state = _uiState.value
        state.imdbId?.takeIf { it.startsWith("tt") }?.let { return it }
        val id = state.mediaId ?: return null
        val fetched = runCatching {
            if (state.isMovie) api.getMovieExternalIds(id, key).imdbId
            else api.getTvExternalIds(id, key).imdbId
        }.getOrNull()?.takeIf { it.startsWith("tt") }
        if (!fetched.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(imdbId = fetched)
        }
        return fetched
    }

    fun dismissTorrentOverlay() {
        _uiState.value = _uiState.value.copy(
            showTorrentOverlay = false,
            torrentResults = emptyList(),
            torrentSearchLabel = "",
            stremioStreams = emptyList(),
            isLoadingStremioStreams = false
        )
    }

    fun showStreamingSplashForMovie() {
        _uiState.value = _uiState.value.copy(
            showStreamingSplash = true,
            streamingSeasonNumber = null,
            streamingEpisodeNumber = null,
            streamingEpisodeTitle = null
        )
    }

    fun showStreamingSplashForEpisode(episode: Episode) {
        val sNum = _uiState.value.selectedSeason
        _uiState.value = _uiState.value.copy(
            showStreamingSplash = true,
            streamingSeasonNumber = sNum,
            streamingEpisodeNumber = episode.episodeNumber,
            streamingEpisodeTitle = episode.name
        )
    }

    fun dismissStreamingSplash() {
        _uiState.value = _uiState.value.copy(showStreamingSplash = false)
    }
}
