package com.playtorrio.tv.data.watch

import android.util.Log
import com.playtorrio.tv.data.AppPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Source-type discriminator for a continue-watching entry.
 *
 * Each kind requires a different resume strategy:
 *  - [STREAMING]    → re-run StreamExtractorService.extract on the remembered source.
 *  - [MAGNET]       → re-launch PlayerActivity with the magnet (PlayerVM picks
 *                     debrid vs built-in TorrServer based on AppPreferences).
 *  - [ADDON_STREAM] → re-call the Stremio addon /stream endpoint, find the
 *                     same/similar stream by [WatchProgress.streamPickKey], route it.
 */
enum class WatchKind { STREAMING, MAGNET, ADDON_STREAM }

/**
 * One entry in the continue-watching list shown on the home screen.
 *
 * Keying:
 *  - For TMDB-known content (most cases): "tmdb|{tmdbId}|s{season}|e{episode}"
 *  - For pure-Stremio content (no tmdb): "stremio|{addonId}|{type}|{stremioId}"
 */
data class WatchProgress(
    val key: String,
    val kind: WatchKind,
    // Identity / metadata
    val tmdbId: Int,
    val imdbId: String?,
    val isMovie: Boolean,
    val title: String,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val year: String?,
    val rating: String?,
    val overview: String?,
    // STREAMING
    val sourceIndex: Int? = null,
    // MAGNET
    val magnetUri: String? = null,
    val fileIdx: Int? = null,
    // ADDON_STREAM
    val addonId: String? = null,
    val stremioType: String? = null,
    val stremioId: String? = null,
    val streamPickKey: String? = null,
    val streamPickName: String? = null,
    // Progress
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("kind", kind.name)
        put("tmdbId", tmdbId)
        put("imdbId", imdbId ?: JSONObject.NULL)
        put("isMovie", isMovie)
        put("title", title)
        put("episodeTitle", episodeTitle ?: JSONObject.NULL)
        put("seasonNumber", seasonNumber ?: JSONObject.NULL)
        put("episodeNumber", episodeNumber ?: JSONObject.NULL)
        put("posterUrl", posterUrl ?: JSONObject.NULL)
        put("backdropUrl", backdropUrl ?: JSONObject.NULL)
        put("logoUrl", logoUrl ?: JSONObject.NULL)
        put("year", year ?: JSONObject.NULL)
        put("rating", rating ?: JSONObject.NULL)
        put("overview", overview ?: JSONObject.NULL)
        put("sourceIndex", sourceIndex ?: JSONObject.NULL)
        put("magnetUri", magnetUri ?: JSONObject.NULL)
        put("fileIdx", fileIdx ?: JSONObject.NULL)
        put("addonId", addonId ?: JSONObject.NULL)
        put("stremioType", stremioType ?: JSONObject.NULL)
        put("stremioId", stremioId ?: JSONObject.NULL)
        put("streamPickKey", streamPickKey ?: JSONObject.NULL)
        put("streamPickName", streamPickName ?: JSONObject.NULL)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun makeKey(
            kind: WatchKind,
            tmdbId: Int,
            isMovie: Boolean,
            seasonNumber: Int?,
            episodeNumber: Int?,
            addonId: String?,
            stremioType: String?,
            stremioId: String?,
        ): String {
            return if (tmdbId > 0) {
                // For TV shows we collapse all episodes under one key so the row
                // shows only the latest episode per series.
                if (isMovie) "tmdb|$tmdbId|movie"
                else "tmdb|$tmdbId|tv"
            } else if (kind == WatchKind.ADDON_STREAM && addonId != null && stremioId != null) {
                val type = stremioType ?: "movie"
                // Stremio series ids look like "tt1234567:1:3" — strip the
                // ":season:episode" suffix so all episodes share one key.
                val baseId = if (type == "series") stremioId.substringBefore(":") else stremioId
                "stremio|$addonId|$type|$baseId"
            } else {
                // Fallback — should not normally happen.
                "fallback|${System.nanoTime()}"
            }
        }

        fun fromJson(o: JSONObject): WatchProgress? = try {
            WatchProgress(
                key = o.getString("key"),
                kind = WatchKind.valueOf(o.getString("kind")),
                tmdbId = o.optInt("tmdbId", 0),
                imdbId = o.optStringOrNull("imdbId"),
                isMovie = o.optBoolean("isMovie", true),
                title = o.optString("title", ""),
                episodeTitle = o.optStringOrNull("episodeTitle"),
                seasonNumber = if (o.isNull("seasonNumber")) null else o.optInt("seasonNumber").takeIf { it > 0 },
                episodeNumber = if (o.isNull("episodeNumber")) null else o.optInt("episodeNumber").takeIf { it > 0 },
                posterUrl = o.optStringOrNull("posterUrl"),
                backdropUrl = o.optStringOrNull("backdropUrl"),
                logoUrl = o.optStringOrNull("logoUrl"),
                year = o.optStringOrNull("year"),
                rating = o.optStringOrNull("rating"),
                overview = o.optStringOrNull("overview"),
                sourceIndex = if (o.isNull("sourceIndex")) null else o.optInt("sourceIndex"),
                magnetUri = o.optStringOrNull("magnetUri"),
                fileIdx = if (o.isNull("fileIdx")) null else o.optInt("fileIdx"),
                addonId = o.optStringOrNull("addonId"),
                stremioType = o.optStringOrNull("stremioType"),
                stremioId = o.optStringOrNull("stremioId"),
                streamPickKey = o.optStringOrNull("streamPickKey"),
                streamPickName = o.optStringOrNull("streamPickName"),
                positionMs = o.optLong("positionMs", 0L),
                durationMs = o.optLong("durationMs", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        } catch (e: Exception) {
            Log.w("WatchProgress", "fromJson failed: ${e.message}")
            null
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }
    }
}

object WatchProgressStore {
    private const val MAX_ENTRIES = 24
    /** Anything below this position is treated as "not really started" — don't save. */
    private const val MIN_SAVE_POSITION_MS = 5_000L
    /** Within this many ms of the end → treat as finished, remove the entry. */
    private const val NEAR_END_REMOVE_MS = 60_000L

    /** Most-recently-updated first. */
    fun load(): List<WatchProgress> = try {
        val arr = JSONArray(AppPreferences.watchProgress)
        (0 until arr.length())
            .mapNotNull { WatchProgress.fromJson(arr.getJSONObject(it)) }
            .sortedByDescending { it.updatedAt }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(list: List<WatchProgress>) {
        val arr = JSONArray()
        list.take(MAX_ENTRIES).forEach { arr.put(it.toJson()) }
        AppPreferences.watchProgress = arr.toString()
    }

    fun upsert(progress: WatchProgress): List<WatchProgress> {
        // Skip ultra-short positions (e.g. user just opened then closed).
        if (progress.positionMs < MIN_SAVE_POSITION_MS) return load()
        // Auto-remove if near end (i.e. finished).
        if (progress.durationMs > 0 &&
            progress.durationMs - progress.positionMs < NEAR_END_REMOVE_MS
        ) {
            return remove(progress.key)
        }
        val updated = progress.copy(updatedAt = System.currentTimeMillis())
        val merged = (listOf(updated) + load().filterNot { it.key == progress.key })
            .take(MAX_ENTRIES)
        persist(merged)
        return merged
    }

    fun remove(key: String): List<WatchProgress> {
        val list = load().filterNot { it.key == key }
        persist(list)
        return list
    }
}
