package com.playtorrio.tv.data.skip

import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A segment that can be skipped (intro, recap, credits, preview/outro).
 */
data class SkipSegment(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,        // Long.MAX_VALUE means "to end of media"
    val source: String       // "introdb" or "theintrodb"
)

enum class SkipType(val label: String) {
    INTRO("Skip Intro"),
    RECAP("Skip Recap"),
    CREDITS("Skip Credits"),
    PREVIEW("Skip Preview"),
    OUTRO("Skip Outro");
}

object SkipSegmentService {

    private const val TAG = "SkipSegmentService"

    /**
     * Fetches skip segments from BOTH APIs in parallel and merges them.
     * Segments from one source fill gaps left by the other — no duplicates.
     */
    suspend fun fetchSegments(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int?,
        episode: Int?
    ): List<SkipSegment> = coroutineScope {
        // introdb.app needs IMDB ID
        val imdbDeferred = async(Dispatchers.IO) {
            try {
                val ids = if (isMovie) {
                    TmdbClient.api.getMovieExternalIds(tmdbId, TmdbClient.API_KEY)
                } else {
                    TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY)
                }
                ids.imdbId?.takeIf { it.startsWith("tt") }
            } catch (e: Exception) {
                Log.i(TAG, "IMDB ID lookup failed: ${e.message}")
                null
            }
        }

        // theintrodb.org uses TMDB ID — can start immediately
        val theIntroDb = async(Dispatchers.IO) { fetchTheIntroDB(tmdbId, season, episode) }

        val imdbId = imdbDeferred.await()
        val introDb = if (imdbId != null) {
            async(Dispatchers.IO) { fetchIntroDB(imdbId, season, episode) }
        } else null

        val setA = theIntroDb.await()
        val setB = introDb?.await() ?: emptyList()

        mergeSegments(setA, setB)
    }

    // ── introdb.app (uses IMDB ID) ──────────────────────────────────────

    private fun fetchIntroDB(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        return try {
            val sb = StringBuilder("https://api.introdb.app/segments?imdb_id=$imdbId")
            if (season != null && episode != null) {
                sb.append("&season=$season&episode=$episode")
            }
            val json = httpGet(sb.toString()) ?: return emptyList()
            val root = JSONObject(json)
            val segments = mutableListOf<SkipSegment>()

            // intro
            root.optJSONObject("intro")?.let { obj ->
                val startMs = obj.optLong("start_ms", -1)
                val endMs = obj.optLong("end_ms", -1)
                if (startMs >= 0 && endMs > startMs) {
                    segments.add(SkipSegment(SkipType.INTRO, startMs, endMs, "introdb"))
                }
            }

            // recap
            root.optJSONObject("recap")?.let { obj ->
                val startMs = obj.optLong("start_ms", -1)
                val endMs = obj.optLong("end_ms", -1)
                if (startMs >= 0 && endMs > startMs) {
                    segments.add(SkipSegment(SkipType.RECAP, startMs, endMs, "introdb"))
                }
            }

            // outro (introdb calls it "outro")
            root.optJSONObject("outro")?.let { obj ->
                val startMs = obj.optLong("start_ms", -1)
                val endMs = obj.optLong("end_ms", -1)
                if (startMs >= 0 && endMs > startMs) {
                    segments.add(SkipSegment(SkipType.CREDITS, startMs, endMs, "introdb"))
                }
            }

            Log.i(TAG, "introdb.app: ${segments.size} segments for $imdbId")
            segments
        } catch (e: Exception) {
            Log.i(TAG, "introdb.app fetch failed: ${e.message}")
            emptyList()
        }
    }

    // ── theintrodb.org (uses TMDB ID) ───────────────────────────────────

    private fun fetchTheIntroDB(tmdbId: Int, season: Int?, episode: Int?): List<SkipSegment> {
        return try {
            val sb = StringBuilder("https://api.theintrodb.org/v2/media?tmdb_id=$tmdbId")
            if (season != null && episode != null) {
                sb.append("&season=$season&episode=$episode")
            }
            val json = httpGet(sb.toString()) ?: return emptyList()
            val root = JSONObject(json)
            val segments = mutableListOf<SkipSegment>()

            parseTheIntroArray(root, "intro", SkipType.INTRO, segments)
            parseTheIntroArray(root, "recap", SkipType.RECAP, segments)
            parseTheIntroArray(root, "credits", SkipType.CREDITS, segments)
            parseTheIntroArray(root, "preview", SkipType.PREVIEW, segments)

            Log.i(TAG, "theintrodb.org: ${segments.size} segments for tmdb=$tmdbId")
            segments
        } catch (e: Exception) {
            Log.i(TAG, "theintrodb.org fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseTheIntroArray(
        root: JSONObject, key: String, type: SkipType, out: MutableList<SkipSegment>
    ) {
        val arr = root.optJSONArray(key) ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val startMs = if (obj.isNull("start_ms")) 0L else obj.optLong("start_ms", -1)
            val endMs = if (obj.isNull("end_ms")) Long.MAX_VALUE else obj.optLong("end_ms", -1)
            if (endMs > startMs && startMs >= 0) {
                out.add(SkipSegment(type, startMs, endMs, "theintrodb"))
            }
        }
    }

    // ── Merge logic: combine both sources without collisions ────────────

    /**
     * Merges segments from two sources. For each SkipType, if both sources
     * provide a segment, prefer the one with the tighter (more specific) range.
     * Different types never collide — both are kept.
     */
    private fun mergeSegments(
        setA: List<SkipSegment>,
        setB: List<SkipSegment>
    ): List<SkipSegment> {
        // Group by type
        val byType = mutableMapOf<SkipType, MutableList<SkipSegment>>()
        for (s in setA + setB) {
            byType.getOrPut(s.type) { mutableListOf() }.add(s)
        }

        val result = mutableListOf<SkipSegment>()
        for ((_, segs) in byType) {
            if (segs.size == 1) {
                result.add(segs[0])
            } else {
                // Multiple segments of same type — deduplicate
                // If they overlap significantly (>50%), keep the one with more precise bounds
                val unique = deduplicateSameType(segs)
                result.addAll(unique)
            }
        }

        return result.sortedBy { it.startMs }
    }

    private fun deduplicateSameType(segs: List<SkipSegment>): List<SkipSegment> {
        if (segs.size <= 1) return segs
        val sorted = segs.sortedBy { it.startMs }
        val result = mutableListOf<SkipSegment>()
        for (seg in sorted) {
            val overlapping = result.find { existing ->
                overlaps(existing, seg)
            }
            if (overlapping != null) {
                // Keep the one with more precise bounds (finite end_ms preferred)
                if (seg.endMs != Long.MAX_VALUE && overlapping.endMs == Long.MAX_VALUE) {
                    result.remove(overlapping)
                    result.add(seg)
                }
                // Otherwise keep existing
            } else {
                result.add(seg)
            }
        }
        return result
    }

    private fun overlaps(a: SkipSegment, b: SkipSegment): Boolean {
        val aEnd = if (a.endMs == Long.MAX_VALUE) Long.MAX_VALUE else a.endMs
        val bEnd = if (b.endMs == Long.MAX_VALUE) Long.MAX_VALUE else b.endMs
        val overlapStart = maxOf(a.startMs, b.startMs)
        val overlapEnd = minOf(aEnd, bEnd)
        if (overlapEnd <= overlapStart) return false
        val overlapLen = overlapEnd - overlapStart
        val minLen = minOf(aEnd - a.startMs, bEnd - b.startMs).coerceAtLeast(1)
        return overlapLen.toFloat() / minLen.toFloat() > 0.5f
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "PlayTorrio-TV/1.0")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
            else { Log.i(TAG, "HTTP ${conn.responseCode} from $url"); null }
        } finally {
            conn.disconnect()
        }
    }
}
