package com.playtorrio.tv.data.audiobook

import android.util.Log
import com.playtorrio.tv.data.AppPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Continue listening" entry for a single audiobook.
 *
 * We persist enough info to (a) display a poster card and (b) re-fetch the same
 * book by piping the stored fields back into [AudiobookSearchResult] so the
 * scraper can rebuild fresh chapter URLs (signed URLs / referer-protected hosts
 * tend to expire, so we never trust the saved mp3 URL).
 */
data class AudiobookProgress(
    val sourceId: String,
    val id: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String?,
    val chapterIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    fun toSearchResult(): AudiobookSearchResult = AudiobookSearchResult(
        sourceId = sourceId,
        id = id,
        title = title,
        pageUrl = pageUrl,
        posterUrl = posterUrl,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceId", sourceId)
        put("id", id)
        put("title", title)
        put("pageUrl", pageUrl)
        put("posterUrl", posterUrl ?: JSONObject.NULL)
        put("chapterIndex", chapterIndex)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): AudiobookProgress? = try {
            AudiobookProgress(
                sourceId = o.getString("sourceId"),
                id = o.getString("id"),
                title = o.getString("title"),
                pageUrl = o.getString("pageUrl"),
                posterUrl = if (o.isNull("posterUrl")) null else o.optString("posterUrl"),
                chapterIndex = o.optInt("chapterIndex", 0),
                positionMs = o.optLong("positionMs", 0L),
                durationMs = o.optLong("durationMs", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        } catch (e: Exception) {
            Log.w("AudiobookProgress", "fromJson failed: ${e.message}")
            null
        }
    }
}

object AudiobookProgressStore {
    private const val MAX_ENTRIES = 24

    /** Most-recently-updated first. */
    fun load(): List<AudiobookProgress> = try {
        val arr = JSONArray(AppPreferences.audiobookProgress)
        (0 until arr.length())
            .mapNotNull { AudiobookProgress.fromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.updatedAt }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(list: List<AudiobookProgress>) {
        val arr = JSONArray()
        list.take(MAX_ENTRIES).forEach { arr.put(it.toJson()) }
        AppPreferences.audiobookProgress = arr.toString()
    }

    /** Insert or update; bumps [AudiobookProgress.updatedAt] to "now". */
    fun upsert(progress: AudiobookProgress): List<AudiobookProgress> {
        val now = System.currentTimeMillis()
        val updated = progress.copy(updatedAt = now)
        val merged = (listOf(updated) + load().filterNot {
            it.sourceId == progress.sourceId && it.id == progress.id
        }).take(MAX_ENTRIES)
        persist(merged)
        return merged
    }

    fun remove(sourceId: String, id: String): List<AudiobookProgress> {
        val list = load().filterNot { it.sourceId == sourceId && it.id == id }
        persist(list)
        return list
    }
}
