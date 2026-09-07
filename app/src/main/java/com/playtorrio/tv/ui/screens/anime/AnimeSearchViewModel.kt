package com.playtorrio.tv.ui.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.anime.metadata.AnilistService
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.data.local.LayoutPreferenceDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimeSearchUiState(
    val query: String = "",
    val isAdult: Boolean = false,
    val selectedGenre: String? = null,
    val selectedFormat: String? = null,
    val selectedStatus: String? = null,
    val selectedSort: String = "TRENDING_DESC",
    val isLoading: Boolean = false,
    val searchResults: List<AnimeMedia> = emptyList(),
    val discoveryTrending: List<AnimeMedia> = emptyList(),
    val discoveryPopular: List<AnimeMedia> = emptyList(),
    val discoveryTopRated: List<AnimeMedia> = emptyList(),
    val focusedPosterBackdropExpandEnabled: Boolean = true,
    val posterCardWidthDp: Int = 126,
    val posterCardCornerRadiusDp: Int = 12
)

@HiltViewModel
class AnimeSearchViewModel @Inject constructor(
    private val anilistService: AnilistService,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeSearchUiState())
    val uiState: StateFlow<AnimeSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeLayoutPreferences()
        loadInitialDiscovery()
    }

    private fun observeLayoutPreferences() {
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropExpandEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(focusedPosterBackdropExpandEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardWidthDp.collectLatest { width ->
                _uiState.update { it.copy(posterCardWidthDp = width) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardCornerRadiusDp.collectLatest { radius ->
                _uiState.update { it.copy(posterCardCornerRadiusDp = radius) }
            }
        }
    }

    private fun loadInitialDiscovery() {
        viewModelScope.launch {
            try {
                val trending = anilistService.fetchTrendingAnime(page = 1, perPage = 20)
                val popular = anilistService.fetchPopularThisSeason(page = 1, perPage = 20)
                val topRated = anilistService.fetchTopRated(page = 1, perPage = 20)
                _uiState.update {
                    it.copy(
                        discoveryTrending = trending,
                        discoveryPopular = popular,
                        discoveryTopRated = topRated
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        triggerSearchDebounced()
    }

    fun toggleAdult() {
        _uiState.update { it.copy(isAdult = !it.isAdult) }
        triggerSearchImmediate()
    }

    fun selectGenre(genre: String?) {
        val autoAdult = genre.equals("Hentai", ignoreCase = true)
        _uiState.update {
            it.copy(
                selectedGenre = if (it.selectedGenre == genre) null else genre,
                isAdult = if (autoAdult) true else it.isAdult
            )
        }
        triggerSearchImmediate()
    }

    fun selectFormat(format: String?) {
        _uiState.update {
            it.copy(selectedFormat = if (it.selectedFormat == format) null else format)
        }
        triggerSearchImmediate()
    }

    fun selectStatus(status: String?) {
        _uiState.update {
            it.copy(selectedStatus = if (it.selectedStatus == status) null else status)
        }
        triggerSearchImmediate()
    }

    fun selectSort(sort: String) {
        _uiState.update { it.copy(selectedSort = sort) }
        triggerSearchImmediate()
    }

    private fun triggerSearchDebounced() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            executeSearch()
        }
    }

    private fun triggerSearchImmediate() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch()
        }
    }

    private suspend fun executeSearch() {
        val state = _uiState.value
        val hasFilters = state.selectedGenre != null ||
            state.selectedFormat != null ||
            state.selectedStatus != null ||
            state.isAdult ||
            state.selectedSort != "TRENDING_DESC"

        if (state.query.isBlank() && !hasFilters) {
            _uiState.update { it.copy(searchResults = emptyList(), isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        try {
            val results = anilistService.searchAnime(
                search = state.query,
                genre = state.selectedGenre,
                format = state.selectedFormat,
                status = state.selectedStatus,
                sort = state.selectedSort,
                isAdult = state.isAdult,
                perPage = 35
            )
            _uiState.update { it.copy(searchResults = results, isLoading = false) }
        } catch (_: Exception) {
            _uiState.update { it.copy(searchResults = emptyList(), isLoading = false) }
        }
    }
}
