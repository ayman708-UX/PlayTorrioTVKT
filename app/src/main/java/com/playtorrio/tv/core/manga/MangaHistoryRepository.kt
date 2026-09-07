package com.playtorrio.tv.core.manga

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playtorrio.tv.core.manga.model.Manga
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MangaReadingProgress(
    val mangaId: String,
    val title: String,
    val coverUrl: String,
    val chapterId: String,
    val chapterNumber: Double,
    val chapterName: String,
    val pageIndex: Int,
    val totalPages: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Singleton
class MangaHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("manga_preferences", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _history = MutableStateFlow<List<MangaReadingProgress>>(emptyList())
    val history: StateFlow<List<MangaReadingProgress>> = _history.asStateFlow()

    private val _likedMangaIds = MutableStateFlow<Set<String>>(emptySet())
    val likedMangaIds: StateFlow<Set<String>> = _likedMangaIds.asStateFlow()

    init {
        loadHistory()
        loadLikes()
    }

    private fun loadHistory() {
        val json = prefs.getString("reading_history", null) ?: return
        try {
            val type = object : TypeToken<List<MangaReadingProgress>>() {}.type
            val list: List<MangaReadingProgress> = gson.fromJson(json, type) ?: emptyList()
            _history.value = list
        } catch (_: Exception) {
            _history.value = emptyList()
        }
    }

    private fun loadLikes() {
        val set = prefs.getStringSet("liked_manga_ids", emptySet()) ?: emptySet()
        _likedMangaIds.value = set
    }

    fun saveProgress(
        mangaId: String,
        title: String,
        coverUrl: String,
        chapterId: String,
        chapterNumber: Double,
        chapterName: String,
        pageIndex: Int,
        totalPages: Int
    ) {
        val updated = _history.value.toMutableList()
        updated.removeAll { it.mangaId == mangaId }
        updated.add(
            0,
            MangaReadingProgress(
                mangaId = mangaId,
                title = title,
                coverUrl = coverUrl,
                chapterId = chapterId,
                chapterNumber = chapterNumber,
                chapterName = chapterName,
                pageIndex = pageIndex,
                totalPages = totalPages
            )
        )
        // Keep last 30 entries
        val trimmed = if (updated.size > 30) updated.subList(0, 30) else updated
        _history.value = trimmed
        prefs.edit().putString("reading_history", gson.toJson(trimmed)).apply()
    }

    fun getProgress(mangaId: String): MangaReadingProgress? {
        return _history.value.firstOrNull { it.mangaId == mangaId }
    }

    fun removeFromHistory(mangaId: String) {
        val updated = _history.value.toMutableList()
        updated.removeAll { it.mangaId == mangaId }
        _history.value = updated
        prefs.edit().putString("reading_history", gson.toJson(updated)).apply()
    }

    fun toggleLike(mangaId: String) {
        val current = _likedMangaIds.value.toMutableSet()
        if (current.contains(mangaId)) {
            current.remove(mangaId)
        } else {
            current.add(mangaId)
        }
        _likedMangaIds.value = current
        prefs.edit().putStringSet("liked_manga_ids", current).apply()
    }

    fun isLiked(mangaId: String): Boolean {
        return _likedMangaIds.value.contains(mangaId)
    }
}
