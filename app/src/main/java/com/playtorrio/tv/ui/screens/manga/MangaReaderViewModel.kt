package com.playtorrio.tv.ui.screens.manga

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.manga.MangaHistoryRepository
import com.playtorrio.tv.core.manga.MangaService
import com.playtorrio.tv.core.manga.model.Manga
import com.playtorrio.tv.core.manga.model.MangaChapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

private const val TAG = "MangaReaderViewModel"

data class MangaReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val seriesId: String = "",
    val chapterId: String = "",
    val manga: Manga? = null,
    val allChapters: List<MangaChapter> = emptyList(),
    val currentChapter: MangaChapter? = null,
    val currentChapterIndex: Int = 0,
    val pages: List<String> = emptyList(),
    val currentPageIndex: Int = 0
)

@HiltViewModel
class MangaReaderViewModel @Inject constructor(
    private val mangaService: MangaService,
    private val historyRepository: MangaHistoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawSeriesId: String = savedStateHandle.get<String>("seriesId").orEmpty()
    val seriesId: String = URLDecoder.decode(rawSeriesId, "UTF-8")

    private val rawChapterId: String = savedStateHandle.get<String>("chapterId").orEmpty()
    val initialChapterId: String = URLDecoder.decode(rawChapterId, "UTF-8")

    private val initialChapterIndex: Int = savedStateHandle.get<String>("chapterIndex")?.toIntOrNull() ?: 0
    private val initialPageIndex: Int = savedStateHandle.get<String>("pageIndex")?.toIntOrNull() ?: 0

    private val _uiState = MutableStateFlow(
        MangaReaderUiState(
            seriesId = seriesId,
            chapterId = initialChapterId,
            currentChapterIndex = initialChapterIndex,
            currentPageIndex = initialPageIndex
        )
    )
    val uiState: StateFlow<MangaReaderUiState> = _uiState.asStateFlow()

    init {
        loadChapterAndMetadata(initialChapterId, initialPageIndex)
    }

    fun loadChapterAndMetadata(chapterId: String, pageIndex: Int = 0) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, chapterId = chapterId) }
            try {
                // Fetch chapter images
                val images = mangaService.getChapterImages(chapterId)
                if (images.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No pages found for this chapter") }
                    return@launch
                }

                // If chapters or manga metadata not loaded, fetch them
                val allChapters = if (_uiState.value.allChapters.isEmpty()) {
                    mangaService.getChapters(seriesId)
                } else {
                    _uiState.value.allChapters
                }

                val manga = if (_uiState.value.manga == null) {
                    try { mangaService.getSeriesDetail(seriesId) } catch (_: Exception) { null }
                } else {
                    _uiState.value.manga
                }

                val chapterIndex = allChapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: 0
                val chapterObj = allChapters.getOrNull(chapterIndex)

                val safePageIndex = pageIndex.coerceIn(0, images.size - 1)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pages = images,
                        currentPageIndex = safePageIndex,
                        allChapters = allChapters,
                        currentChapter = chapterObj,
                        currentChapterIndex = chapterIndex,
                        manga = manga
                    )
                }

                saveCurrentProgress()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter $chapterId: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load chapter") }
            }
        }
    }

    fun nextPage(): Boolean {
        val current = _uiState.value.currentPageIndex
        val total = _uiState.value.pages.size
        if (current < total - 1) {
            _uiState.update { it.copy(currentPageIndex = current + 1) }
            saveCurrentProgress()
            return true
        }
        return false // At end of chapter
    }

    fun prevPage(): Boolean {
        val current = _uiState.value.currentPageIndex
        if (current > 0) {
            _uiState.update { it.copy(currentPageIndex = current - 1) }
            saveCurrentProgress()
            return true
        }
        return false // At beginning of chapter
    }

    fun loadNextChapter(): Boolean {
        val allChapters = _uiState.value.allChapters
        val currentIdx = _uiState.value.currentChapterIndex
        // WeebCentral chapters are listed newest to oldest or vice-versa.
        // Let's check by chapter number: next chapter is the one with number > current
        val currentNumber = _uiState.value.currentChapter?.number ?: 0.0
        val nextCh = allChapters.filter { it.number > currentNumber }.minByOrNull { it.number }
            ?: (if (currentIdx > 0) allChapters.getOrNull(currentIdx - 1) else null)

        if (nextCh != null) {
            loadChapterAndMetadata(nextCh.id, 0)
            return true
        }
        return false
    }

    fun loadPrevChapter(): Boolean {
        val allChapters = _uiState.value.allChapters
        val currentNumber = _uiState.value.currentChapter?.number ?: 0.0
        val prevCh = allChapters.filter { it.number < currentNumber }.maxByOrNull { it.number }
            ?: (if (_uiState.value.currentChapterIndex < allChapters.size - 1) allChapters.getOrNull(_uiState.value.currentChapterIndex + 1) else null)

        if (prevCh != null) {
            loadChapterAndMetadata(prevCh.id, 0)
            return true
        }
        return false
    }

    private fun saveCurrentProgress() {
        val s = _uiState.value
        val chapter = s.currentChapter ?: return
        val manga = s.manga
        historyRepository.saveProgress(
            mangaId = s.seriesId,
            title = manga?.title ?: "Manga",
            coverUrl = manga?.coverNormal ?: manga?.coverSmall ?: "",
            chapterId = chapter.id,
            chapterNumber = chapter.number,
            chapterName = chapter.name,
            pageIndex = s.currentPageIndex,
            totalPages = s.pages.size
        )
    }
}
