package com.playtorrio.tv.ui.screens.anime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.anime.metadata.AnilistService
import com.playtorrio.tv.core.anime.model.AnimeEpisode
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

data class AnimeDetailsUiState(
    val isLoading: Boolean = true,
    val anime: AnimeMedia? = null,
    val episodes: List<AnimeEpisode> = emptyList(),
    val selectedBatchIndex: Int = 0,
    val error: String? = null,
    val focusedPosterBackdropExpandEnabled: Boolean = true,
    val posterCardWidthDp: Int = 126,
    val posterCardCornerRadiusDp: Int = 12
) {
    companion object {
        const val EPISODES_PER_PAGE = 25
    }

    val totalBatches: Int
        get() = if (episodes.isEmpty()) 1 else ((episodes.size + EPISODES_PER_PAGE - 1) / EPISODES_PER_PAGE)

    val currentBatchEpisodes: List<AnimeEpisode>
        get() {
            if (episodes.isEmpty()) return emptyList()
            val safeBatch = selectedBatchIndex.coerceIn(0, (totalBatches - 1).coerceAtLeast(0))
            val start = safeBatch * EPISODES_PER_PAGE
            val end = minOf(start + EPISODES_PER_PAGE, episodes.size)
            return if (start in episodes.indices) episodes.subList(start, end) else emptyList()
        }
}

@HiltViewModel
class AnimeDetailsViewModel @Inject constructor(
    private val anilistService: AnilistService,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val animeId: Int = savedStateHandle.get<String>("animeId")?.toIntOrNull()
        ?: savedStateHandle.get<Int>("animeId") ?: 0

    private val _uiState = MutableStateFlow(AnimeDetailsUiState())
    val uiState: StateFlow<AnimeDetailsUiState> = _uiState.asStateFlow()

    init {
        observeLayoutPreferences()
        loadDetails()
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

    fun loadDetails() {
        if (animeId <= 0) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid Anime ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val details = anilistService.fetchAnimeDetails(animeId)
                if (details != null) {
                    val eps = anilistService.getEpisodes(details)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            anime = details,
                            episodes = eps
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Anime not found") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load details")
                }
            }
        }
    }

    fun selectBatch(batchIndex: Int) {
        val total = _uiState.value.totalBatches
        val clamped = batchIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        _uiState.update { it.copy(selectedBatchIndex = clamped) }
    }

    fun nextPage() {
        val current = _uiState.value.selectedBatchIndex
        selectBatch(current + 1)
    }

    fun prevPage() {
        val current = _uiState.value.selectedBatchIndex
        selectBatch(current - 1)
    }

    fun jumpToEpisode(episodeNumber: Int) {
        val totalEps = _uiState.value.episodes.size
        if (totalEps <= 0) return
        val clampedEp = episodeNumber.coerceIn(1, totalEps)
        val targetBatch = (clampedEp - 1) / AnimeDetailsUiState.EPISODES_PER_PAGE
        selectBatch(targetBatch)
    }

    fun jumpToPage(pageNumber: Int) {
        // 1-based page number
        selectBatch(pageNumber - 1)
    }
}
