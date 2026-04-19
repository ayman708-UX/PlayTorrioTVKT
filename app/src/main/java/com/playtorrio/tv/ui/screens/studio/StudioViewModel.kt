package com.playtorrio.tv.ui.screens.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class NetworkFilter { ALL, SHOWS, MOVIES }

data class StudioUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val company: CompanyDetail? = null,
    // Separate source lists (for filter counts + SHOWS/MOVIES views)
    val shows: List<TmdbMedia> = emptyList(),
    val movies: List<TmdbMedia> = emptyList(),
    // Single ordered list used for ALL mode — items appended in load order
    val allItems: List<TmdbMedia> = emptyList(),
    val showsPage: Int = 0,
    val showsTotalPages: Int = 1,
    val moviesPage: Int = 0,
    val moviesTotalPages: Int = 1,
    val filter: NetworkFilter = NetworkFilter.ALL,
    val isNetworkMode: Boolean = false,
    val loadedId: Int = 0
) {
    val displayedItems: List<TmdbMedia> get() = when (filter) {
        NetworkFilter.ALL -> allItems
        NetworkFilter.SHOWS -> shows
        NetworkFilter.MOVIES -> movies
    }
    val hasMoreShows: Boolean get() = showsPage < showsTotalPages
    val hasMoreMovies: Boolean get() = moviesPage < moviesTotalPages
    val hasMore: Boolean get() = if (isNetworkMode) when (filter) {
        NetworkFilter.ALL -> hasMoreShows || hasMoreMovies
        NetworkFilter.SHOWS -> hasMoreShows
        NetworkFilter.MOVIES -> hasMoreMovies
    } else false
    // Alias for studio mode screen compatibility
    val tvShows: List<TmdbMedia> get() = shows
}

class StudioViewModel : ViewModel() {

    private fun mergeUnique(existing: List<TmdbMedia>, incoming: List<TmdbMedia>): List<TmdbMedia> {
        return (existing + incoming).distinctBy { "${if (it.isMovie) "mv" else "tv"}_${it.id}" }
    }

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState

    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY

    fun load(companyId: Int) {
        viewModelScope.launch {
            _uiState.value = StudioUiState(isLoading = true, loadedId = companyId)
            try {
                val detailDeferred = async { api.getCompanyDetails(companyId, key) }
                val moviesDeferred = async { runCatching { api.discoverMoviesByCompany(key, companyId) }.getOrNull() }
                val tvDeferred = async { runCatching { api.discoverTvByCompany(key, companyId) }.getOrNull() }

                val detail = detailDeferred.await()
                val moviesResp = moviesDeferred.await()
                val tvResp = tvDeferred.await()
                val initialShows = (tvResp?.results ?: emptyList()).distinctBy { "tv_${it.id}" }
                val initialMovies = (moviesResp?.results ?: emptyList()).distinctBy { "mv_${it.id}" }

                _uiState.value = StudioUiState(
                    isLoading = false,
                    company = detail,
                    shows = initialShows,
                    movies = initialMovies,
                    showsPage = 1,
                    showsTotalPages = tvResp?.totalPages ?: 1,
                    moviesPage = 1,
                    moviesTotalPages = moviesResp?.totalPages ?: 1,
                    loadedId = companyId
                )
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to load studio", e)
                _uiState.value = StudioUiState(isLoading = false, error = e.message, loadedId = companyId)
            }
        }
    }

    fun loadNetwork(networkId: Int) {
        viewModelScope.launch {
            _uiState.value = StudioUiState(isLoading = true, isNetworkMode = true, loadedId = networkId)
            try {
                val detailDeferred = async { api.getNetworkDetails(networkId, key) }
                val showsDeferred = async { runCatching { api.discoverTvByNetwork(key, networkId, page = 1) }.getOrNull() }
                val moviesDeferred = async { runCatching { api.discoverMoviesByNetwork(key, networkId, page = 1) }.getOrNull() }

                val detail = detailDeferred.await()
                val showsResp = showsDeferred.await()
                val moviesResp = moviesDeferred.await()
                val initialShows = (showsResp?.results ?: emptyList()).distinctBy { "tv_${it.id}" }
                val initialMovies = (moviesResp?.results ?: emptyList()).distinctBy { "mv_${it.id}" }

                _uiState.value = StudioUiState(
                    isLoading = false,
                    company = detail,
                    shows = initialShows,
                    movies = initialMovies,
                    // ALL mode: shows first, then movies — but all appended in order
                    allItems = initialShows + initialMovies,
                    showsPage = if (showsResp != null) 1 else 0,
                    showsTotalPages = showsResp?.totalPages ?: 1,
                    moviesPage = if (moviesResp != null) 1 else 0,
                    moviesTotalPages = moviesResp?.totalPages ?: 1,
                    filter = NetworkFilter.ALL,
                    isNetworkMode = true,
                    loadedId = networkId
                )
            } catch (e: Exception) {
                Log.e("StudioViewModel", "Failed to load network", e)
                _uiState.value = StudioUiState(
                    isLoading = false, error = e.message,
                    isNetworkMode = true, loadedId = networkId
                )
            }
        }
    }

    fun setFilter(filter: NetworkFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun loadMore() {
        val s = _uiState.value
        if (!s.isNetworkMode || s.isLoadingMore || !s.hasMore) return
        _uiState.value = s.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val loadShows = when (s.filter) {
                    NetworkFilter.SHOWS -> true
                    NetworkFilter.MOVIES -> false
                    NetworkFilter.ALL -> s.hasMoreShows
                }
                if (loadShows) {
                    val page = s.showsPage + 1
                    val resp = api.discoverTvByNetwork(key, s.loadedId, page = page)
                    val incoming = resp.results.distinctBy { "tv_${it.id}" }
                    val newShows = mergeUnique(_uiState.value.shows, incoming)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        shows = newShows,
                        allItems = mergeUnique(_uiState.value.allItems, incoming),
                        showsPage = page,
                        showsTotalPages = resp.totalPages
                    )
                } else {
                    val page = s.moviesPage + 1
                    val resp = api.discoverMoviesByNetwork(key, s.loadedId, page = page)
                    val incoming = resp.results.distinctBy { "mv_${it.id}" }
                    val newMovies = mergeUnique(_uiState.value.movies, incoming)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        movies = newMovies,
                        allItems = mergeUnique(_uiState.value.allItems, incoming),
                        moviesPage = page,
                        moviesTotalPages = resp.totalPages
                    )
                }
            } catch (e: Exception) {
                Log.e("StudioViewModel", "loadMore failed", e)
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
}
