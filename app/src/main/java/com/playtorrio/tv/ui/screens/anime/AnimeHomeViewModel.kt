package com.playtorrio.tv.ui.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.anime.metadata.AnilistService
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.data.local.LayoutPreferenceDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimeCatalogRow(
    val title: String,
    val items: List<AnimeMedia>
)

data class AnimeHomeUiState(
    val isLoading: Boolean = true,
    val heroAnime: AnimeMedia? = null,
    val focusedAnime: AnimeMedia? = null,
    val rows: List<AnimeCatalogRow> = emptyList(),
    val focusedPosterBackdropExpandEnabled: Boolean = true,
    val posterCardWidthDp: Int = 126,
    val posterCardCornerRadiusDp: Int = 12,
    val error: String? = null
)

@HiltViewModel
class AnimeHomeViewModel @Inject constructor(
    private val anilistService: AnilistService,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeHomeUiState())
    val uiState: StateFlow<AnimeHomeUiState> = _uiState.asStateFlow()

    init {
        observeLayoutPreferences()
        loadAnimeCatalogs()
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

    fun loadAnimeCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val trending = anilistService.fetchTrendingAnime(perPage = 20)
                val popular = anilistService.fetchPopularThisSeason(perPage = 20)
                val upcoming = anilistService.fetchUpcomingNextSeason(perPage = 20)
                val topRated = anilistService.fetchTopRated(perPage = 20)
                val action = anilistService.fetchByGenre("Action", perPage = 20)
                val romance = anilistService.fetchByGenre("Romance", perPage = 20)
                val fantasy = anilistService.fetchByGenre("Fantasy", perPage = 20)
                val scifi = anilistService.fetchByGenre("Sci-Fi", perPage = 20)
                val comedy = anilistService.fetchByGenre("Comedy", perPage = 20)
                val adventure = anilistService.fetchByGenre("Adventure", perPage = 20)

                val catalogRows = mutableListOf<AnimeCatalogRow>()
                if (trending.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Trending Now", trending))
                if (popular.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Popular This Season", popular))
                if (upcoming.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Upcoming Next Season", upcoming))
                if (topRated.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Top Rated All-Time", topRated))
                if (action.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Action Anime", action))
                if (fantasy.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Fantasy Anime", fantasy))
                if (romance.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Romance Anime", romance))
                if (scifi.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Sci-Fi Anime", scifi))
                if (comedy.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Comedy Anime", comedy))
                if (adventure.isNotEmpty()) catalogRows.add(AnimeCatalogRow("Adventure Anime", adventure))

                val hero = trending.firstOrNull() ?: popular.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        heroAnime = hero,
                        focusedAnime = hero,
                        rows = catalogRows
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load Anime catalogs"
                    )
                }
            }
        }
    }

    fun setFocusedAnime(anime: AnimeMedia) {
        _uiState.update { it.copy(focusedAnime = anime) }
    }
}
