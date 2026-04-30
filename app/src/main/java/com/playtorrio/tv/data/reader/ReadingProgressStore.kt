package com.playtorrio.tv.data.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight in-app "Continue Reading" store. Records the last-opened chapter
 * for each manga/comic so the home of those screens can surface it. Backed by
 * SharedPreferences so it survives restarts but is intentionally not synced to
 * the watch-progress system used for video playback.
 */
object ReadingProgressStore {
    private const val PREFS_BASE = "playtorrio_reading_progress"
    private const val KEY = "entries_v1"
    private const val MAX = 30

    /** SharedPreferences file name scoped to the currently active profile. */
    private fun prefsName(): String {
        val id = com.playtorrio.tv.data.profile.ProfileManager.activeId()
        return "${'$'}{PREFS_BASE}_${'$'}id"
    }

    enum class Source { MANGA, COMIC }

    data class Entry(
        val source: Source,
        /** seriesId (manga) or comic.url (comic). */
        val workKey: String,
        val workTitle: String,
        val coverUrl: String,
        val chapterTitle: String,
        /** chapter id (manga) or chapter url (comic). */
        val chapterKey: String,
        val chapterIndex: Int,
        val pageIndex: Int,
        val totalPages: Int,
        val updatedAt: Long,
        // Comic-only extras so we can re-open detail screens without re-fetching.
        val comicSourceTag: String = "",
        val comicSummary: String = "",
    )

    fun list(ctx: Context): List<Entry> {
        val prefs = ctx.getSharedPreferences(prefsName(), Context.MODE_PRIVATE)
        // One-time migration: if this profile has no entries yet but the
        // legacy global file does, inherit them so existing users don't lose
        // their Continue Reading list when per-profile scoping is enabled.
        var raw = prefs.getString(KEY, null)
        if (raw == null) {
            val legacy = ctx.getSharedPreferences(PREFS_BASE, Context.MODE_PRIVATE)
            val legacyRaw = legacy.getString(KEY, null)
            if (legacyRaw != null) {
                prefs.edit().putString(KEY, legacyRaw).apply()
                legacy.edit().remove(KEY).apply()
                raw = legacyRaw
            }
        }
        if (raw == null) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    source = if (o.optString("source") == "MANGA") Source.MANGA else Source.COMIC,
                    workKey = o.optString("workKey"),
                    workTitle = o.optString("workTitle"),
                    coverUrl = o.optString("coverUrl"),
                    chapterTitle = o.optString("chapterTitle"),
                    chapterKey = o.optString("chapterKey"),
                    chapterIndex = o.optInt("chapterIndex"),
                    pageIndex = o.optInt("pageIndex"),
                    totalPages = o.optInt("totalPages"),
                    updatedAt = o.optLong("updatedAt"),
                    comicSourceTag = o.optString("comicSourceTag", ""),
                    comicSummary = o.optString("comicSummary", ""),
                )
            }.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun upsert(ctx: Context, entry: Entry) {
        val current = list(ctx).filterNot { it.source == entry.source && it.workKey == entry.workKey }
        val updated = (listOf(entry) + current).take(MAX)
        save(ctx, updated)
    }

    fun remove(ctx: Context, source: Source, workKey: String) {
        val updated = list(ctx).filterNot { it.source == source && it.workKey == workKey }
        save(ctx, updated)
    }

    private fun save(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("source", e.source.name)
                put("workKey", e.workKey)
                put("workTitle", e.workTitle)
                put("coverUrl", e.coverUrl)
                put("chapterTitle", e.chapterTitle)
                put("chapterKey", e.chapterKey)
                put("chapterIndex", e.chapterIndex)
                put("pageIndex", e.pageIndex)
                put("totalPages", e.totalPages)
                put("updatedAt", e.updatedAt)
                put("comicSourceTag", e.comicSourceTag)
                put("comicSummary", e.comicSummary)
            })
        }
        ctx.getSharedPreferences(prefsName(), Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
