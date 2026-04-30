package com.playtorrio.tv.data.subtitle

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded NanoHTTPD on 127.0.0.1 that proxies an external SRT/VTT subtitle
 * URL and shifts every timestamp by `ms` milliseconds.
 *
 *   GET /shift?u=<url-encoded sub url>&ms=<int>&mime=srt|vtt
 *     → shifted SRT/VTT body (text/plain; charset=utf-8)
 *
 * The player uses this proxy whenever an external subtitle is selected, so
 * changing the subtitle delay just rebuilds the MediaItem with a new `ms`
 * value and the cached source body is re-shifted in-place.
 */
internal object SubtitleShiftServer {
    private const val TAG = "SubtitleShift"

    private var server: Server? = null
    @Volatile private var url: String? = null

    /** Original bodies cached so re-shifting on every delay change is instant. */
    private val sourceCache = ConcurrentHashMap<String, ByteArray>()

    @Synchronized
    fun baseUrl(): String? {
        url?.let { return it }
        return try {
            val s = Server()
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            url = "http://127.0.0.1:${s.listeningPort}"
            Log.i(TAG, "started at $url")
            url
        } catch (e: Exception) {
            Log.w(TAG, "failed to start: ${e.message}")
            null
        }
    }

    /**
     * Build a proxy URL that, when requested, returns [originalUrl] with its
     * timestamps shifted by [delayMs]. Returns the original URL unchanged if
     * the proxy could not be started.
     */
    fun proxyUrl(originalUrl: String, delayMs: Long, mime: String): String {
        val base = baseUrl() ?: return originalUrl
        val u = URLEncoder.encode(originalUrl, "UTF-8")
        return "$base/shift?u=$u&ms=$delayMs&mime=$mime"
    }

    private class Server : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(session: IHTTPSession): Response {
            if (session.uri != "/shift") {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "not found"
                )
            }
            val params = session.parameters
            val src = params["u"]?.firstOrNull().orEmpty()
            val ms = params["ms"]?.firstOrNull()?.toLongOrNull() ?: 0L
            val mime = (params["mime"]?.firstOrNull() ?: "srt").lowercase()
            if (src.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "text/plain", "missing u"
                )
            }
            return try {
                val rawBytes = sourceCache.getOrPut(src) { fetch(src) }
                val text = decodeSubtitle(rawBytes)
                val shifted = if (ms == 0L) text else shiftTimestamps(text, ms, mime)
                val outBytes = shifted.toByteArray(Charsets.UTF_8)
                val contentType = if (mime == "vtt") "text/vtt; charset=utf-8"
                                  else "application/x-subrip; charset=utf-8"
                newFixedLengthResponse(
                    Response.Status.OK,
                    contentType,
                    ByteArrayInputStream(outBytes),
                    outBytes.size.toLong()
                ).apply {
                    addHeader("Cache-Control", "no-store")
                    addHeader("Access-Control-Allow-Origin", "*")
                }
            } catch (e: Exception) {
                Log.w(TAG, "shift failed for $src: ${e.message}")
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", "shift failed"
                )
            }
        }

        private fun fetch(srcUrl: String): ByteArray {
            val conn = URL(srcUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            )
            conn.connect()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream.use { it.readBytes() }
        }
    }

    /** Best-effort decode (UTF-8 → Latin-1 fallback) — same approach as SubtitleCatService. */
    private fun decodeSubtitle(bytes: ByteArray): String {
        return try {
            val s = bytes.toString(Charsets.UTF_8)
            // If the UTF-8 decoder sprinkled replacement chars, fall back to Latin-1.
            if (s.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else s
        } catch (_: Exception) {
            bytes.toString(Charsets.ISO_8859_1)
        }
    }

    // ── Timestamp shifting ───────────────────────────────────────────────────

    // Matches "HH:MM:SS,mmm" (SRT) or "HH:MM:SS.mmm" / "MM:SS.mmm" (VTT).
    private val tsRegex = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

    private fun shiftTimestamps(text: String, deltaMs: Long, mime: String): String {
        val useDot = mime == "vtt"
        return tsRegex.replace(text) { match ->
            val hStr = match.groupValues[1].trimEnd(':')
            val mStr = match.groupValues[2]
            val sStr = match.groupValues[3]
            val msStr = match.groupValues[4].padEnd(3, '0').take(3)
            val totalMs = (hStr.toIntOrNull() ?: 0) * 3_600_000L +
                          mStr.toInt() * 60_000L +
                          sStr.toInt() * 1_000L +
                          msStr.toInt()
            val shifted = (totalMs + deltaMs).coerceAtLeast(0L)
            formatTimestamp(shifted, useDot, includeHours = match.groupValues[1].isNotEmpty())
        }
    }

    private fun formatTimestamp(ms: Long, useDot: Boolean, includeHours: Boolean): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1_000) % 60
        val frac = ms % 1_000
        val sep = if (useDot) '.' else ','
        return if (includeHours || h > 0) {
            "%02d:%02d:%02d%c%03d".format(h, m, s, sep, frac)
        } else {
            "%02d:%02d%c%03d".format(m, s, sep, frac)
        }
    }
}
