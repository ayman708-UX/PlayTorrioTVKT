package com.playtorrio.tv.ui.screens.manga

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.manga.MangaHistoryRepository
import com.playtorrio.tv.core.manga.MangaReadingProgress
import com.playtorrio.tv.core.manga.MangaService
import com.playtorrio.tv.core.manga.model.Manga
import com.playtorrio.tv.core.manga.model.MangaChapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject
import kotlin.math.ceil

data class MangaDetailsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val manga: Manga? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val selectedBatchIndex: Int = 0,
    val readingProgress: MangaReadingProgress? = null,
    val isLiked: Boolean = false
) {
    companion object {
        const val CHAPTERS_PER_PAGE = 25
    }

    val totalBatches: Int
        get() = if (chapters.isEmpty()) 1 else ceil(chapters.size.toDouble() / CHAPTERS_PER_PAGE).toInt()

    val currentBatchChapters: List<MangaChapter>
        get() {
            if (chapters.isEmpty()) return emptyList()
            val start = (selectedBatchIndex * CHAPTERS_PER_PAGE).coerceIn(0, chapters.size)
            val end = ((selectedBatchIndex + 1) * CHAPTERS_PER_PAGE).coerceIn(0, chapters.size)
            return if (start <= end) chapters.subList(start, end) else emptyList()
        }
}

@HiltViewModel
class MangaDetailsViewModel @Inject constructor(
    private val mangaService: MangaService,
    private val historyRepository: MangaHistoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawSeriesId: String = savedStateHandle.get<String>("seriesId").orEmpty()
    val seriesId: String = URLDecoder.decode(rawSeriesId, "UTF-8")

    private val _uiState = MutableStateFlow(MangaDetailsUiState())
    val uiState: StateFlow<MangaDetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        if (seriesId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid manga ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detailDeferred = async { mangaService.getSeriesDetail(seriesId) }
                val chaptersDeferred = async { mangaService.getChapters(seriesId) }

                val manga = detailDeferred.await()
                val chapters = chaptersDeferred.await()
                val progress = historyRepository.getProgress(seriesId)
                val liked = historyRepository.isLiked(seriesId)

                // If user was reading a chapter, find its batch
                var initialBatch = 0
                if (progress != null) {
                    val idx = chapters.indexOfFirst { it.id == progress.chapterId }
                    if (idx >= 0) {
                        initialBatch = idx / MangaDetailsUiState.CHAPTERS_PER_PAGE
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        manga = manga,
                        chapters = chapters,
                        selectedBatchIndex = initialBatch,
                        readingProgress = progress,
                        isLiked = liked
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load manga details"
                    )
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
        val total = _uiState.value.totalBatches
        if (current < total - 1) {
            selectBatch(current + 1)
        }
    }

    fun prevPage() {
        val current = _uiState.value.selectedBatchIndex
        if (current > 0) {
            selectBatch(current - 1)
        }
    }

    fun jumpToChapter(targetChapterNumber: Double) {
        val chapters = _uiState.value.chapters
        // Look for exact or closest chapter
        val idx = chapters.indexOfFirst { it.number == targetChapterNumber }
            .takeIf { it >= 0 }
            ?: chapters.indexOfFirst { it.number <= targetChapterNumber }
                .takeIf { it >= 0 }
            ?: 0

        val targetBatch = idx / MangaDetailsUiState.CHAPTERS_PER_PAGE
        selectBatch(targetBatch)
    }

    fun toggleLike() {
        val manga = _uiState.value.manga ?: return
        historyRepository.toggleLike(manga.id)
        _uiState.update { it.copy(isLiked = historyRepository.isLiked(manga.id)) }
    }
}
