package com.playtorrio.tv.ui.screens.audiobook

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.audiobook.AudiobookDetail
import com.playtorrio.tv.data.audiobook.AudiobookProgress
import com.playtorrio.tv.data.audiobook.AudiobookProgressStore
import com.playtorrio.tv.data.audiobook.AudiobookSearchResult
import com.playtorrio.tv.data.audiobook.AudiobookService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AudiobookVM"
const val BROWSE_PAGE_SIZE = 24

enum class AudiobookView { BROWSE, LIKED }

data class AudiobookUiState(
    val view: AudiobookView = AudiobookView.BROWSE,
    val query: String = "",
    val isLoadingResults: Boolean = false,
    val results: List<AudiobookSearchResult> = emptyList(),
    val featured: List<AudiobookSearchResult> = emptyList(),
    /** Current Tokybook page (0-based, page size [BROWSE_PAGE_SIZE]). */
    val browsePage: Int = 0,
    /** True when the last browse fetch returned a full page (so a next page may exist). */
    val browseHasMore: Boolean = true,
    val saved: List<AudiobookDetail> = emptyList(),
    /** Continue-listening entries, most-recent first. */
    val continueListening: List<AudiobookProgress> = emptyList(),
    /** Edit mode for the continue-listening row (toggled by the pen button). */
    val continueEditMode: Boolean = false,
    /** Pending {chapterIndex, positionMs} to resume on the next opened book. */
    val pendingResume: Pair<Int, Long>? = null,
    val isOpeningBook: Boolean = false,
    val openedBook: AudiobookDetail? = null,
    val openError: String? = null,
)

class AudiobookViewModel : ViewModel() {

    private val _ui = MutableStateFlow(AudiobookUiState())
    val ui: StateFlow<AudiobookUiState> = _ui

    private var searchJob: Job? = null

    init {
        loadSaved()
        loadProgress()
        loadFeatured()
    }

    // ─── Library ──────────────────────────────────────────────────

    private fun loadSaved() {
        try {
            val arr = JSONArray(AppPreferences.savedAudiobooks)
            val list = (0 until arr.length()).mapNotNull { i ->
                AudiobookDetail.fromJson(arr.getJSONObject(i).toString())
            }
            _ui.update { it.copy(saved = list) }
        } catch (e: Exception) {
            Log.w(TAG, "loadSaved failed: ${e.message}")
        }
    }

    private fun persistSaved(list: List<AudiobookDetail>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject(it.toJson())) }
        AppPreferences.savedAudiobooks = arr.toString()
    }

    fun isSaved(detail: AudiobookDetail): Boolean =
        _ui.value.saved.any { it.sourceId == detail.sourceId && it.id == detail.id }

    fun toggleSave(detail: AudiobookDetail) {
        val current = _ui.value.saved.toMutableList()
        val idx = current.indexOfFirst { it.sourceId == detail.sourceId && it.id == detail.id }
        if (idx >= 0) current.removeAt(idx) else current.add(0, detail)
        _ui.update { it.copy(saved = current) }
        persistSaved(current)
    }

    // ─── Continue listening ─────────────────────────────────────────

    private fun loadProgress() {
        _ui.update { it.copy(continueListening = AudiobookProgressStore.load()) }
    }

    fun toggleContinueEditMode() {
        _ui.update { it.copy(continueEditMode = !it.continueEditMode) }
    }

    fun removeProgress(sourceId: String, id: String) {
        val updated = AudiobookProgressStore.remove(sourceId, id)
        _ui.update { s ->
            s.copy(
                continueListening = updated,
                continueEditMode = if (updated.isEmpty()) false else s.continueEditMode,
            )
        }
    }

    /**
     * Persist the current playback position. Called every few seconds by the player
     * and once more on dispose. Skips no-op writes (chapter/pos didn't move) so we
     * don't churn shared preferences.
     */
    fun saveProgress(
        book: AudiobookDetail,
        chapterIndex: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (book.chapters.isEmpty()) return
        val prev = _ui.value.continueListening.firstOrNull {
            it.sourceId == book.sourceId && it.id == book.id
        }
        // Avoid spamming prefs when nothing meaningful changed (< 1.5s drift, same chapter).
        if (prev != null &&
            prev.chapterIndex == chapterIndex &&
            kotlin.math.abs(prev.positionMs - positionMs) < 1_500L &&
            prev.durationMs == durationMs
        ) return

        val updated = AudiobookProgressStore.upsert(
            AudiobookProgress(
                sourceId = book.sourceId,
                id = book.id,
                title = book.title,
                pageUrl = book.pageUrl,
                posterUrl = book.posterUrl,
                chapterIndex = chapterIndex,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
        _ui.update { it.copy(continueListening = updated) }
    }

    /**
     * Re-scrape the book (signed/streaming URLs may have expired) then open the
     * player with the saved chapter + offset queued for auto-seek.
     */
    fun openProgress(progress: AudiobookProgress) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isOpeningBook = true,
                    openError = null,
                    pendingResume = progress.chapterIndex to progress.positionMs,
                )
            }
            val detail = AudiobookService.fetchDetail(progress.toSearchResult())
            if (detail == null) {
                _ui.update {
                    it.copy(
                        isOpeningBook = false,
                        pendingResume = null,
                        openError = "Couldn't reload this audiobook.",
                    )
                }
            } else {
                _ui.update { it.copy(isOpeningBook = false, openedBook = detail) }
            }
        }
    }

    /** Returns the queued resume {chapter, posMs} exactly once, then clears it. */
    fun consumePendingResume(): Pair<Int, Long>? {
        val v = _ui.value.pendingResume ?: return null
        _ui.update { it.copy(pendingResume = null) }
        return v
    }

    // ─── Browse / Search ──────────────────────────────────────────

    private fun loadFeatured(page: Int = 0) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingResults = true) }
            val featured = AudiobookService.browse(page = page, pageSize = BROWSE_PAGE_SIZE)
            _ui.update {
                it.copy(
                    featured = featured,
                    browsePage = page,
                    browseHasMore = featured.size >= BROWSE_PAGE_SIZE,
                    isLoadingResults = false,
                )
            }
        }
    }

    fun nextBrowsePage() {
        if (!_ui.value.browseHasMore || _ui.value.isLoadingResults) return
        loadFeatured(_ui.value.browsePage + 1)
    }

    fun prevBrowsePage() {
        if (_ui.value.browsePage <= 0 || _ui.value.isLoadingResults) return
        loadFeatured(_ui.value.browsePage - 1)
    }

    fun setView(view: AudiobookView) {
        _ui.update { it.copy(view = view) }
    }

    fun updateQuery(q: String) {
        // Just track the typed text; do not search until the user submits.
        _ui.update { it.copy(query = q) }
        if (q.isBlank()) {
            searchJob?.cancel()
            _ui.update { it.copy(results = emptyList(), isLoadingResults = false) }
        }
    }

    /** Run the actual search (called when the user presses the keyboard's OK / Search action). */
    fun submitQuery(q: String? = null) {
        val text = (q ?: _ui.value.query).trim()
        searchJob?.cancel()
        if (text.isBlank()) {
            _ui.update { it.copy(results = emptyList(), isLoadingResults = false) }
            return
        }
        _ui.update { it.copy(query = text) }
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(isLoadingResults = true) }
            val results = AudiobookService.search(text)
            _ui.update { it.copy(results = results, isLoadingResults = false) }
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _ui.update { it.copy(query = "", results = emptyList()) }
    }

    // ─── Open / Close book ────────────────────────────────────────

    fun openBook(result: AudiobookSearchResult) {
        viewModelScope.launch {
            _ui.update { it.copy(isOpeningBook = true, openError = null) }
            val detail = AudiobookService.fetchDetail(result)
            if (detail == null) {
                _ui.update { it.copy(isOpeningBook = false, openError = "Couldn't load this audiobook.") }
            } else {
                _ui.update { it.copy(isOpeningBook = false, openedBook = detail) }
            }
        }
    }

    fun openSaved(detail: AudiobookDetail) {
        _ui.update { it.copy(openedBook = detail) }
    }

    fun closeBook() {
        _ui.update { it.copy(openedBook = null) }
    }

    fun dismissOpenError() {
        _ui.update { it.copy(openError = null) }
    }

    /**
     * Lazily fetch a poster for a search result that didn't include one,
     * then patch it into the visible list.
     */
    fun ensurePoster(result: AudiobookSearchResult) {
        if (!result.posterUrl.isNullOrBlank()) return
        viewModelScope.launch {
            val poster = AudiobookService.resolvePoster(result) ?: return@launch
            _ui.update { s ->
                s.copy(
                    results = s.results.map {
                        if (it.sourceId == result.sourceId && it.id == result.id) it.copy(posterUrl = poster) else it
                    },
                    featured = s.featured.map {
                        if (it.sourceId == result.sourceId && it.id == result.id) it.copy(posterUrl = poster) else it
                    }
                )
            }
        }
    }
}
