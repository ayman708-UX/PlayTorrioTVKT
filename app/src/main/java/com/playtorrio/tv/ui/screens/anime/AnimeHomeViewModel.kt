package com.playtorrio.tv.ui.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.anime.arabic.AnimeArabicService
import com.playtorrio.tv.core.anime.metadata.AnilistService
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.data.local.AnimeSettingsDataStore
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
    val isArabicAnime: Boolean = false,
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
    private val animeArabicService: AnimeArabicService,
    private val animeSettingsDataStore: AnimeSettingsDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeHomeUiState())
    val uiState: StateFlow<AnimeHomeUiState> = _uiState.asStateFlow()

    init {
        observeLayoutPreferences()
        observeAnimeSettings()
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

    private fun observeAnimeSettings() {
        viewModelScope.launch {
            animeSettingsDataStore.isArabicAnime.collectLatest { isArabic ->
                _uiState.update { it.copy(isArabicAnime = isArabic) }
                loadAnimeCatalogs(isArabic)
            }
        }
    }

    fun toggleArabicAnime() {
        viewModelScope.launch {
            val next = !_uiState.value.isArabicAnime
            animeSettingsDataStore.setArabicAnime(next)
        }
    }

    fun loadAnimeCatalogs(isArabic: Boolean = _uiState.value.isArabicAnime) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (isArabic) {
                    val feed = animeArabicService.getHome()
                    val catalogRows = mutableListOf<AnimeCatalogRow>()
                    if (feed.recentEpisodes.isNotEmpty()) catalogRows.add(AnimeCatalogRow("آخر الحلقات • Latest Episodes", feed.recentEpisodes))
                    if (feed.popularMovies.isNotEmpty()) catalogRows.add(AnimeCatalogRow("الأفلام الأكثر شعبية • Movies", feed.popularMovies))
                    if (feed.upcoming.isNotEmpty()) catalogRows.add(AnimeCatalogRow("الأنميات المنتظرة • Upcoming", feed.upcoming))
                    if (feed.trending.isNotEmpty()) catalogRows.add(AnimeCatalogRow("الأكثر شهرة • Trending", feed.trending))
                    if (feed.topSeasonal.isNotEmpty()) catalogRows.add(AnimeCatalogRow("أفضل الأنميات • Top Seasonal", feed.topSeasonal))
                    if (feed.seasonal.isNotEmpty()) catalogRows.add(AnimeCatalogRow("أنميات موسمية • Seasonal", feed.seasonal))
                    if (feed.legendary.isNotEmpty()) catalogRows.add(AnimeCatalogRow("أنميات أسطورية • Legendary", feed.legendary))
                    for (entry in feed.misc) {
                        if (entry.second.isNotEmpty()) {
                            catalogRows.add(AnimeCatalogRow(entry.first, entry.second))
                        }
                    }

                    if (feed.trending.isEmpty() && catalogRows.isNotEmpty()) {
                        val trendingSample = (feed.popularMovies.take(6) + feed.recentEpisodes.take(6)).distinctBy { it.slug }
                        if (trendingSample.isNotEmpty()) {
                            catalogRows.add(0, AnimeCatalogRow("الأكثر شهرة • Trending", trendingSample))
                        }
                    }

                    val hero = feed.spotlight.firstOrNull() 
                        ?: feed.popularMovies.firstOrNull() 
                        ?: feed.recentEpisodes.firstOrNull() 
                        ?: feed.upcoming.firstOrNull()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            heroAnime = hero,
                            focusedAnime = hero,
                            rows = catalogRows
                        )
                    }
                } else {
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
