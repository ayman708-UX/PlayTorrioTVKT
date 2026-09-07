package com.playtorrio.tv.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.manga.MangaHistoryRepository
import com.playtorrio.tv.core.manga.MangaReadingProgress
import com.playtorrio.tv.core.manga.MangaService
import com.playtorrio.tv.core.manga.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MangaShelf(
    val title: String,
    val items: List<Manga>
)

data class MangaHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val shelves: List<MangaShelf> = emptyList(),
    val continueReading: List<MangaReadingProgress> = emptyList(),
    val genres: List<String> = MangaService.popularGenres,
    val selectedGenre: String = "All",
    val genreItems: List<Manga> = emptyList(),
    val isLoadingGenre: Boolean = false,
    val spotlightManga: Manga? = null,
    val focusedManga: Manga? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<Manga> = emptyList()
)

@HiltViewModel
class MangaHomeViewModel @Inject constructor(
    private val mangaService: MangaService,
    private val historyRepository: MangaHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MangaHomeUiState())
    val uiState: StateFlow<MangaHomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadHomeData()
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            historyRepository.history.collect { history ->
                _uiState.update { it.copy(continueReading = history) }
            }
        }
    }

    fun removeFromContinueReading(mangaId: String) {
        historyRepository.removeFromHistory(mangaId)
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val popDeferred = async { mangaService.getManga(page = 1) }
                val actionDeferred = async { mangaService.getManga(page = 1, tag = "Action") }
                val fantasyDeferred = async { mangaService.getManga(page = 1, tag = "Fantasy") }
                val romanceDeferred = async { mangaService.getManga(page = 1, tag = "Romance") }

                val popular = popDeferred.await()
                val action = actionDeferred.await()
                val fantasy = fantasyDeferred.await()
                val romance = romanceDeferred.await()

                val shelves = mutableListOf<MangaShelf>()
                if (popular.isNotEmpty()) shelves.add(MangaShelf("Popular Manga", popular))
                if (action.isNotEmpty()) shelves.add(MangaShelf("Trending Action", action))
                if (fantasy.isNotEmpty()) shelves.add(MangaShelf("Fantasy & Adventure", fantasy))
                if (romance.isNotEmpty()) shelves.add(MangaShelf("Romance & Drama", romance))

                val spotlight = popular.firstOrNull() ?: action.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        shelves = shelves,
                        spotlightManga = spotlight
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load manga"
                    )
                }
            }
        }
    }

    fun selectGenre(genre: String) {
        if (_uiState.value.selectedGenre == genre) return
        _uiState.update { it.copy(selectedGenre = genre) }

        if (genre == "All") {
            _uiState.update { it.copy(genreItems = emptyList(), isLoadingGenre = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGenre = true) }
            try {
                val items = mangaService.getManga(page = 1, tag = genre)
                _uiState.update {
                    it.copy(
                        genreItems = items,
                        isLoadingGenre = false,
                        spotlightManga = items.firstOrNull() ?: it.spotlightManga
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingGenre = false) }
            }
        }
    }

    fun setFocusedManga(manga: Manga) {
        _uiState.update { it.copy(focusedManga = manga) }
    }

    fun setSearchMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isSearchMode = enabled,
                searchQuery = if (!enabled) "" else it.searchQuery,
                searchResults = if (!enabled) emptyList() else it.searchResults
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = mangaService.searchManga(query = query.trim())
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                        spotlightManga = results.firstOrNull() ?: it.spotlightManga
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }
}
