package com.playtorrio.tv.core.audiobook.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudiobookProgressRepo"
private const val FILE_NAME = "audiobook_progress.json"

@Singleton
class AudiobookProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val mutex = Mutex()
    private val progressFile by lazy { File(context.filesDir, FILE_NAME) }

    private val _continueListening = MutableStateFlow<List<AudiobookProgress>>(emptyList())
    val continueListening: StateFlow<List<AudiobookProgress>> = _continueListening.asStateFlow()

    init {
        scope.launch {
            loadFromDisk()
        }
    }

    private suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (progressFile.exists()) {
                    val json = progressFile.readText()
                    if (json.isNotBlank()) {
                        val type = object : TypeToken<List<AudiobookProgress>>() {}.type
                        val list: List<AudiobookProgress> = gson.fromJson(json, type) ?: emptyList()
                        _continueListening.value = list.sortedByDescending { it.updatedAt }
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load audiobook progress: ${e.message}", e)
            }
            _continueListening.value = emptyList()
        }
    }

    suspend fun saveProgress(
        book: Audiobook,
        chapterIndex: Int,
        chapterTitle: String,
        positionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        // Ignore trivial initial progress (< 5s on chapter 0)
        if (chapterIndex == 0 && positionMs < 5_000L) return@withContext

        mutex.withLock {
            try {
                val currentList = _continueListening.value.toMutableList()
                currentList.removeAll { it.audiobook.uuid == book.uuid }

                val newEntry = AudiobookProgress(
                    audiobook = book,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis()
                )
                currentList.add(0, newEntry)

                // Limit history to 100 items
                val trimmed = if (currentList.size > 100) currentList.subList(0, 100) else currentList
                _continueListening.value = trimmed

                val json = gson.toJson(trimmed)
                progressFile.writeText(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save audiobook progress: ${e.message}", e)
            }
        }
    }

    fun getProgress(uuid: String): AudiobookProgress? {
        return _continueListening.value.firstOrNull { it.audiobook.uuid == uuid }
    }

    suspend fun removeProgress(uuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val updated = _continueListening.value.filter { it.audiobook.uuid != uuid }
                _continueListening.value = updated
                val json = gson.toJson(updated)
                progressFile.writeText(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove audiobook progress: ${e.message}", e)
            }
        }
    }
}
