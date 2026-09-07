package com.playtorrio.tv.ui.screens.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookProgress
import com.playtorrio.tv.core.audiobook.model.AudiobookRow
import com.playtorrio.tv.core.audiobook.player.AudiobookPlayerController
import com.playtorrio.tv.core.audiobook.repository.AudiobookProgressRepository
import com.playtorrio.tv.core.audiobook.scrapers.AudiobookScraperService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudiobookUiState(
    val isLoading: Boolean = true,
    val rows: List<AudiobookRow> = emptyList(),
    val spotlightBook: Audiobook? = null,
    val focusedBook: Audiobook? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<Audiobook> = emptyList(),
    val isSearchMode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val scraperService: AudiobookScraperService,
    private val progressRepository: AudiobookProgressRepository,
    val playerController: AudiobookPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookUiState())
    val uiState: StateFlow<AudiobookUiState> = _uiState.asStateFlow()

    // Continue Listening strictly isolated to Audiobooks
    val continueListening: StateFlow<List<AudiobookProgress>> = progressRepository.continueListening

    // Player state forwards
    val currentPlayingBook = playerController.currentBook
    val chapters = playerController.chapters
    val currentChapterIndex = playerController.currentChapterIndex
    val isPlaying = playerController.isPlaying
    val isLoadingAudio = playerController.isLoading
    val currentPositionMs = playerController.currentPositionMs
    val durationMs = playerController.durationMs
    val playbackSpeed = playerController.playbackSpeed
    val playerError = playerController.error
    val needsP2pConsent = playerController.needsP2pConsent

    private var searchJob: Job? = null

    init {
        loadAudiobookHome()
    }

    fun loadAudiobookHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val shelves = scraperService.getHomeShelves()
                val initialSpotlight = shelves.firstOrNull()?.books?.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows = shelves,
                        spotlightBook = initialSpotlight,
                        focusedBook = initialSpotlight
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load audiobooks"
                    )
                }
            }
        }
    }

    fun setFocusedBook(book: Audiobook) {
        _uiState.update {
            it.copy(
                focusedBook = book,
                spotlightBook = book
            )
        }
    }

    fun playBook(book: Audiobook, continueProgress: AudiobookProgress? = null) {
        viewModelScope.launch {
            val progress = continueProgress ?: progressRepository.getProgress(book.uuid)
            val chapterList = scraperService.getChapters(book)
            if (chapterList.isNotEmpty()) {
                val startIdx = progress?.chapterIndex ?: 0
                val startPos = progress?.positionMs ?: 0L
                playerController.playBook(book, chapterList, startIdx, startPos)
            }
        }
    }

    fun playChapter(index: Int) {
        playerController.playChapter(index, 0L)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        playerController.seekRelative(deltaMs)
    }

    fun playNextChapter() {
        playerController.playNextChapter()
    }

    fun playPreviousChapter() {
        playerController.playPreviousChapter()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun onP2pConsentGranted() {
        playerController.onP2pConsentGranted()
    }

    fun onP2pConsentDismissed() {
        playerController.onP2pConsentDismissed()
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
            val results = scraperService.search(query)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    spotlightBook = results.firstOrNull() ?: it.spotlightBook
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

    fun closePlayer() {
        playerController.stop()
    }

    override fun onCleared() {
        super.onCleared()
        playerController.stop()
    }
}
