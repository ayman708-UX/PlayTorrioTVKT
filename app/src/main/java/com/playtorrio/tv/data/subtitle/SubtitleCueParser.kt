package com.playtorrio.tv.data.subtitle

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/** A single subtitle cue: start/end in milliseconds + plain-text payload. */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Lightweight SRT / VTT parser used by the in-Compose subtitle overlay so the
 * subtitle delay can be applied instantly without reloading the player.
 *
 * The parser is intentionally permissive — it tolerates BOM, CRLF/LF mixed
 * endings, missing index lines, blank cues, and either '.' or ',' as the
 * milliseconds separator.
 */
object SubtitleCueParser {
    private const val TAG = "SubtitleCueParser"

    /** Fetch a subtitle URL and parse it. Returns an empty list on failure. */
    fun fetchAndParse(url: String, formatHint: String): List<SubtitleCue> {
        val bytes = try {
            fetchBytes(url)
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed ($url): ${e.message}")
            return emptyList()
        }
        val text = decode(bytes)
        return when (formatHint.lowercase()) {
            "vtt" -> parseVtt(text)
            else -> parseSrt(text)
        }
    }

    fun parseSrt(text: String): List<SubtitleCue> = parseInternal(text, isVtt = false)
    fun parseVtt(text: String): List<SubtitleCue> = parseInternal(stripVttHeader(text), isVtt = true)

    // ── Internals ────────────────────────────────────────────────────────────

    private fun stripVttHeader(text: String): String {
        // Drop "WEBVTT" header and any STYLE/REGION/NOTE/comment blocks above
        // the first cue. We only need timing+text.
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val ln = lines[i].trim()
            if (ln.startsWith("WEBVTT", ignoreCase = true) || ln.startsWith("Kind:")
                || ln.startsWith("Language:") || ln.isEmpty() || ln.startsWith("NOTE")
                || ln.startsWith("STYLE") || ln.startsWith("REGION")
            ) { i++; continue }
            break
        }
        return lines.drop(i).joinToString("\n")
    }

    // Matches "HH:MM:SS,mmm --> HH:MM:SS,mmm" (SRT) and the dot variant (VTT),
    // and also accepts the VTT "MM:SS.mmm --> MM:SS.mmm" short form.
    private val timingRegex = Regex(
        """(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""
    )

    private fun parseInternal(text: String, @Suppress("UNUSED_PARAMETER") isVtt: Boolean): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        // Normalize line endings; split on blank-line boundaries.
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\n\\s*\n"))
        for (raw in blocks) {
            val lines = raw.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            // Find the timing line — first or second line depending on whether
            // an SRT index is present (or a VTT cue identifier).
            val timingIdx = lines.indexOfFirst { timingRegex.containsMatchIn(it) }
            if (timingIdx < 0) continue
            val match = timingRegex.find(lines[timingIdx]) ?: continue
            val startMs = toMs(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val endMs = toMs(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            if (endMs <= startMs) continue

            val payload = lines.drop(timingIdx + 1).joinToString("\n")
            val cleanText = stripTags(payload).trim()
            if (cleanText.isEmpty()) continue
            out.add(SubtitleCue(startMs, endMs, cleanText))
        }
        out.sortBy { it.startMs }
        return out
    }

    private fun toMs(h: String, m: String, s: String, msStr: String): Long {
        val hours = h.trimEnd(':').toIntOrNull() ?: 0
        val mins = m.toInt()
        val secs = s.toInt()
        val millis = msStr.padEnd(3, '0').take(3).toInt()
        return hours * 3_600_000L + mins * 60_000L + secs * 1_000L + millis
    }

    /** Strip common SRT/VTT/HTML formatting so the overlay can render plain text. */
    private fun stripTags(s: String): String {
        // Drop ASS/SSA override blocks like {\an8}.
        var t = s.replace(Regex("""\{[^}]*\}"""), "")
        // Drop HTML-ish tags <i>, <b>, <font ...>, <c.foo>, etc.
        t = t.replace(Regex("""<[^>]+>"""), "")
        // Drop VTT cue voice prefix "<v Speaker>" already removed above; also
        // drop "[ \\h]" non-breaking space artifacts.
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
        return t
    }

    /** Binary-search for the cue that should be visible at [positionMs]. */
    fun cueAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        // Binary search by start time, then walk back over overlapping cues.
        var lo = 0
        var hi = cues.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (ans < 0) return null
        // Walk a few cues backward in case earlier cues are still active and overlap forward.
        var i = ans
        var found: SubtitleCue? = null
        while (i >= 0 && cues[i].startMs >= positionMs - 30_000L) {
            val c = cues[i]
            if (positionMs in c.startMs..c.endMs) { found = c; break }
            i--
        }
        return found
    }

    // ── Network / decoding ───────────────────────────────────────────────────

    private fun fetchBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        )
        conn.connect()
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream.use { it.readBytes() }
    }

    /** Best-effort decode (UTF-8 → Latin-1 fallback). */
    private fun decode(bytes: ByteArray): String {
        val stripped = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            val s = stripped.toString(Charsets.UTF_8)
            if (s.contains('\uFFFD')) stripped.toString(Charsets.ISO_8859_1) else s
        } catch (_: Exception) {
            stripped.toString(Charsets.ISO_8859_1)
        }
    }
}
