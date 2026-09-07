package com.playtorrio.tv.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.music.InnerTubeMusicService
import com.playtorrio.tv.core.music.MusicPlayerController
import com.playtorrio.tv.core.music.model.MusicRow
import com.playtorrio.tv.core.music.model.MusicTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicUiState(
    val isLoading: Boolean = true,
    val rows: List<MusicRow> = emptyList(),
    val spotlightTrack: MusicTrack? = null,
    val focusedTrack: MusicTrack? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MusicTrack> = emptyList(),
    val isSearchMode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicService: InnerTubeMusicService,
    val playerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    val currentPlayingTrack = playerController.currentTrack
    val isPlaying = playerController.isPlaying
    val isLoadingAudio = playerController.isLoading
    val currentPositionMs = playerController.currentPositionMs
    val durationMs = playerController.durationMs
    val playerError = playerController.error

    private var searchJob: Job? = null

    init {
        loadMusicHome()
    }

    fun loadMusicHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val shelves = musicService.getHomeShelves()
                val initialSpotlight = shelves.firstOrNull()?.tracks?.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = shelves,
                        spotlightTrack = initialSpotlight,
                        focusedTrack = initialSpotlight
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load music"
                    )
                }
            }
        }
    }

    fun setFocusedTrack(track: MusicTrack) {
        _uiState.update {
            it.copy(
                focusedTrack = track,
                spotlightTrack = track
            )
        }
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack> = emptyList()) {
        val effectiveQueue = if (queue.isNotEmpty()) {
            queue
        } else {
            // Flatten all current rows or search results to build a rich queue
            val state = _uiState.value
            if (state.isSearchMode && state.searchResults.isNotEmpty()) {
                state.searchResults
            } else {
                state.rows.flatMap { it.tracks }.distinctBy { it.id }
            }
        }
        playerController.playTrack(track, effectiveQueue)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun stopPlayback() {
        playerController.stop()
    }

    fun playNext() {
        playerController.playNext()
    }

    val queue = playerController.queue
    val isShuffleEnabled = playerController.isShuffleEnabled
    val isRepeatEnabled = playerController.isRepeatEnabled

    fun playPrevious() {
        playerController.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        playerController.seekRelative(deltaMs)
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeat() {
        playerController.toggleRepeat()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchMode = query.isNotBlank()) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400L) // debounce
            _uiState.update { it.copy(isSearching = true) }
            val results = musicService.searchSongs(query)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    spotlightTrack = results.firstOrNull() ?: it.spotlightTrack
                )
            }
        }
    }

    fun setSearchMode(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchMode = active,
                searchQuery = if (!active) "" else it.searchQuery,
                searchResults = if (!active) emptyList() else it.searchResults
            )
        }
    }
}
