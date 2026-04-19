package com.playtorrio.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.model.TmdbMedia
import com.playtorrio.tv.data.stremio.BoardRow
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val movies: List<TmdbMedia> = emptyList(),
    val tvShows: List<TmdbMedia> = emptyList(),
    val trendingMovies: List<TmdbMedia> = emptyList(),
    val trendingTv: List<TmdbMedia> = emptyList(),
    val logos: Map<Int, String> = emptyMap(),
    val error: String? = null,
    val addonRows: List<BoardRow> = emptyList(),
    val isLoadingAddonSearch: Boolean = false
)

class SearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY
    private var searchJob: Job? = null

    init {
        loadTrending()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val movies = api.getPopularMovies(key).results.take(20)
                val tv = api.getPopularTv(key).results.take(20)
                _uiState.value = _uiState.value.copy(
                    trendingMovies = movies,
                    trendingTv = tv
                )
                fetchLogos(movies + tv)
            } catch (_: Exception) {}
        }
    }

    private fun fetchLogos(items: List<TmdbMedia>) {
        items.forEach { media ->
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
                    if (logo != null) {
                        val current = _uiState.value.logos.toMutableMap()
                        current[media.id] = "https://image.tmdb.org/t/p/w300${logo.filePath}"
                        _uiState.value = _uiState.value.copy(logos = current)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                movies = emptyList(),
                tvShows = emptyList(),
                addonRows = emptyList(),
                isLoading = false,
                isLoadingAddonSearch = false
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true, isLoadingAddonSearch = true)
            try {
                // Normalize: strip special chars that trip up TMDB search
                val normalized = newQuery
                    .replace("'", "")
                    .replace("'", "")
                    .replace(":", " ")
                    .replace("  ", " ")
                    .trim()
                val moviesResult = api.searchMovies(key, normalized).results
                val tvResult = api.searchTv(key, normalized).results
                _uiState.value = _uiState.value.copy(
                    movies = moviesResult,
                    tvShows = tvResult,
                    isLoading = false,
                    error = null
                )
                fetchLogos(moviesResult + tvResult)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }

            // Addon search in parallel
            try {
                val addons = StremioAddonRepository.getAddons()
                if (addons.isNotEmpty()) {
                    val addonRows = StremioService.search(addons, newQuery)
                    _uiState.value = _uiState.value.copy(
                        addonRows = addonRows,
                        isLoadingAddonSearch = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingAddonSearch = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingAddonSearch = false)
            }
        }
    }
}
