package com.playtorrio.tv.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.stremio.BoardRow
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.model.TmdbMedia
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.trailer.TrailerPlaybackSource
import com.playtorrio.tv.data.trailer.TrailerService
import com.playtorrio.tv.data.stremio.StremioMetaPreview
import com.playtorrio.tv.data.watch.WatchProgress
import com.playtorrio.tv.data.watch.WatchProgressStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeRow(
    val title: String,
    val items: List<TmdbMedia>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val featured: TmdbMedia? = null,
    val featuredLogoUrl: String? = null,
    val rows: List<HomeRow> = emptyList(),
    val networkLogos: Map<Int, String?> = emptyMap(),
    val error: String? = null,
    val trailerSource: TrailerPlaybackSource? = null,
    val isTrailerPlaying: Boolean = false,
    val addonRows: List<BoardRow> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val continueWatchingEditMode: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY

    private val logoCache = mutableMapOf<String, String?>()
    private var trailerJob: Job? = null
    private val failedTrailerIds = mutableSetOf<Int>()

    companion object {
        val NETWORK_IDS = listOf(213, 2739, 49, 1024, 2552, 453, 174, 4330, 67, 88, 3353, 318, 4, 1399, 2222)
    }

    init {
        loadContent()
        refreshContinueWatching()
    }

    fun refreshAddonRows() {
        viewModelScope.launch {
            try {
                val addons = StremioAddonRepository.getAddons()
                val boardRows = if (addons.isNotEmpty()) {
                    StremioService.loadBoard(addons)
                } else {
                    emptyList()
                }
                _uiState.value = _uiState.value.copy(addonRows = boardRows)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(addonRows = emptyList())
            }
        }
    }

    /**
     * Resolves an addon catalog item click.
     * For tt-prefixed (IMDB) IDs → looks up TMDB and returns "detail/{tmdbId}/{isMovie}".
     * For non-IMDB IDs → returns null (caller should navigate to stremio_detail).
     */
    suspend fun resolveImdbToTmdbRoute(item: StremioMetaPreview): String? {
        val id = item.id
        if (!id.startsWith("tt")) return null
        return try {
            val findResult = api.findByExternalId(id, key)
            val movie = findResult.movieResults.firstOrNull()
            val tv = findResult.tvResults.firstOrNull()
            when {
                movie != null -> "detail/${movie.id}/true"
                tv != null -> "detail/${tv.id}/false"
                else -> null
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Failed to resolve IMDB $id to TMDB", e)
            null
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                val trendingDeferred = async { api.getTrending(key) }
                val popularMoviesDeferred = async { api.getPopularMovies(key) }
                val popularTvDeferred = async { api.getPopularTv(key) }
                val topMoviesDeferred = async { api.getTopRatedMovies(key) }
                val topTvDeferred = async { api.getTopRatedTv(key) }

                val trending = trendingDeferred.await()
                val popularMovies = popularMoviesDeferred.await()
                val popularTv = popularTvDeferred.await()
                val topMovies = topMoviesDeferred.await()
                val topTv = topTvDeferred.await()

                val featured = trending.results.firstOrNull { it.backdropPath != null }

                val rows = listOf(
                    HomeRow("Trending Now", trending.results),
                    HomeRow("Popular Movies", popularMovies.results),
                    HomeRow("Popular TV Shows", popularTv.results),
                    HomeRow("Top Rated Movies", topMovies.results),
                    HomeRow("Top Rated TV Shows", topTv.results)
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    featured = featured,
                    rows = rows
                )

                featured?.let { fetchLogo(it) }

                // Fetch network logos in background (non-blocking — UI shows after rows load)
                // Load addon catalog rows in background
                launch { refreshAddonRows() }

                launch {
                    val logos = NETWORK_IDS.map { id ->
                        async {
                            id to try {
                                api.getNetworkDetails(id, key).logoUrl
                            } catch (_: Exception) { null }
                        }
                    }.associate { it.await() }
                    _uiState.value = _uiState.value.copy(networkLogos = logos)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load content"
                )
            }
        }
    }

    fun onItemFocused(item: TmdbMedia) {
        if (item.backdropPath != null) {
            val current = _uiState.value

            // While trailer is playing the overlay covers all content — any focus
            // callbacks are recomposition artifacts, not real user navigation.
            if (current.isTrailerPlaying) {
                Log.i("HomeViewModel", "onItemFocused BLOCKED (trailer playing) id=${item.id}")
                return
            }

            // Skip if already focused on same item while a trailer fetch is in flight
            if (current.featured?.id == item.id && current.featured?.isMovie == item.isMovie
                && trailerJob?.isActive == true
            ) {
                Log.i("HomeViewModel", "onItemFocused BLOCKED (same item, job active) id=${item.id}")
                return
            }

            Log.i("HomeViewModel", "onItemFocused id=${item.id} title=${item.displayTitle}")

            val cacheKey = "${if (item.isMovie) "m" else "t"}_${item.id}"
            val cachedLogo = logoCache[cacheKey]

            // Stop any playing trailer and cancel pending trailer fetch
            trailerJob?.cancel()
            _uiState.value = _uiState.value.copy(
                featured = item,
                featuredLogoUrl = cachedLogo,
                trailerSource = null,
                isTrailerPlaying = false
            )

            if (!logoCache.containsKey(cacheKey)) {
                fetchLogo(item)
            }

            // Start timer for trailer (respects user preference)
            if (!AppPreferences.trailerAutoplay) return
            val delayMs = AppPreferences.trailerDelaySec.coerceIn(3, 10) * 1000L
            trailerJob = viewModelScope.launch {
                delay(delayMs)
                // Still focused on same item?
                if (_uiState.value.featured?.id == item.id && item.id !in failedTrailerIds) {
                    Log.i("HomeViewModel", "Fetching trailer for ${item.displayTitle}")
                    val source = TrailerService.getTrailer(item.id, item.isMovie)
                    Log.i("HomeViewModel", "Trailer result for ${item.displayTitle}: ${source != null}")
                    // Still same item after extraction?
                    if (_uiState.value.featured?.id == item.id && source != null) {
                        Log.i("HomeViewModel", "Playing trailer for ${item.displayTitle}")
                        _uiState.value = _uiState.value.copy(
                            trailerSource = source,
                            isTrailerPlaying = true
                        )
                    } else if (source == null) {
                        Log.i("HomeViewModel", "Trailer extraction returned null for ${item.displayTitle}")
                    }
                }
            }
        }
    }

    fun onTrailerFailed() {
        val id = _uiState.value.featured?.id
        if (id != null) failedTrailerIds.add(id)
        Log.w("HomeViewModel", "Trailer playback failed for id=$id, won't retry")
        cancelTrailer()
    }

    fun onTrailerEnded() {
        // Just clear trailer — keep featured item and logo so home doesn't reset
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false
        )
    }

    fun cancelTrailer() {
        // Don't cancel while trailer is actively playing — unfocus events from the
        // content row fire when the trailer overlay steals focus.
        if (_uiState.value.isTrailerPlaying) return
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false
        )
    }

    /** Force-cancel trailer even if playing — used when leaving the screen. */
    fun forceStopTrailer() {
        trailerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            trailerSource = null,
            isTrailerPlaying = false
        )
    }

    // Focus position — survives trailer dismissal and navigation pop
    var lastFocusedRowIndex: Int = 0
        private set
    var lastFocusedItemIndex: Int = 0
        private set

    fun saveFocusPosition(rowIndex: Int, itemIndex: Int) {
        lastFocusedRowIndex = rowIndex
        lastFocusedItemIndex = itemIndex
    }

    /** Reload continue-watching from disk; called on screen resume. */
    fun refreshContinueWatching() {
        _uiState.value = _uiState.value.copy(
            continueWatching = WatchProgressStore.load(),
            // Auto-exit edit mode if the list became empty
            continueWatchingEditMode = _uiState.value.continueWatchingEditMode
                && WatchProgressStore.load().isNotEmpty()
        )
    }

    fun toggleContinueWatchingEditMode() {
        if (_uiState.value.continueWatching.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            continueWatchingEditMode = !_uiState.value.continueWatchingEditMode
        )
    }

    fun removeContinueWatching(key: String) {
        val list = WatchProgressStore.remove(key)
        _uiState.value = _uiState.value.copy(
            continueWatching = list,
            continueWatchingEditMode = list.isNotEmpty() && _uiState.value.continueWatchingEditMode
        )
    }

    private fun fetchLogo(media: TmdbMedia) {
        val cacheKey = "${if (media.isMovie) "m" else "t"}_${media.id}"
        viewModelScope.launch {
            try {
                val images = if (media.isMovie) {
                    api.getMovieImages(media.id, key)
                } else {
                    api.getTvImages(media.id, key)
                }
                val logo = images.logos
                    ?.filter { it.language == "en" || it.language == null }
                    ?.maxByOrNull { it.voteAverage }
                val logoUrl = logo?.url
                logoCache[cacheKey] = logoUrl
                if (_uiState.value.featured?.id == media.id) {
                    _uiState.value = _uiState.value.copy(featuredLogoUrl = logoUrl)
                }
            } catch (_: Exception) {
                logoCache[cacheKey] = null
            }
        }
    }
}
