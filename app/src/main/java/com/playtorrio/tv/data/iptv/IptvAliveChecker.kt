package com.playtorrio.tv.data.iptv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.util.concurrent.TimeUnit

/**
 * Port of the System/server.js stream validator.
 *
 * For each candidate live stream:
 *  - Open a connection, read up to ~32 KB or until idle.
 *  - Reject HTML/JSON/XML/text content (offline error pages).
 *  - Accept on M3U8 manifest text, MPEG-TS sync byte (0x47), MP4 ftyp,
 *    AAC/MPEG/Matroska/Ogg/H.264 NAL signatures, or any payload >= 32 KB.
 *  - Reject HTTP 200 responses with Content-Length 0..5 MB (typical
 *    "channel offline" canned video).
 *
 * Concurrency is intentionally lower than the Node version (24 vs 100) since
 * Android TV boxes have weaker Wi-Fi chips & CPUs.
 */
object IptvAliveChecker {
    private const val MIN_BYTES = 16 * 1024
    private const val MAX_BYTES = 64 * 1024
    private const val CHECK_TIMEOUT_MS = 8_000L
    private const val CONCURRENCY = 24

    // Use one client; per-call timeouts via callTimeout.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    data class Progress(val checked: Int, val total: Int, val alive: Int)

    /**
     * Runs alive checks for [streams] inside the caller's coroutine scope.
     *
     * @param onResult per-stream callback (alive flag).
     * @param onProgress periodic progress callback.
     * @return a Job that the caller may cancel.
     */
    fun launchCheck(
        scope: CoroutineScope,
        streams: List<Pair<String /*streamId*/, String /*url*/>>,
        onResult: suspend (streamId: String, alive: Boolean) -> Unit,
        onProgress: suspend (Progress) -> Unit,
        onDone: suspend () -> Unit,
    ): Job = scope.launch(Dispatchers.IO) {
        val sem = Semaphore(CONCURRENCY)
        var checked = 0
        var alive = 0
        val total = streams.size
        coroutineScope {
            val jobs = streams.map { (id, url) ->
                async {
                    sem.withPermit {
                        if (!isActive) return@withPermit
                        val ok = isAlive(url)
                        if (!isActive) return@withPermit
                        synchronized(this@IptvAliveChecker) {
                            checked++
                            if (ok) alive++
                        }
                        onResult(id, ok)
                        onProgress(Progress(checked, total, alive))
                    }
                }
            }
            jobs.awaitAll()
        }
        onDone()
    }

    private fun isAlive(url: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .header("Range", "bytes=0-${MAX_BYTES - 1}")
                .build()
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code !in 200..299 && code != 206) return false
                val ct = resp.header("Content-Type").orEmpty().lowercase()
                val cl = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                if (isDeadContentType(ct)) return false

                val body = resp.body ?: return false
                val source = body.source()
                val (buf, ended) = readUpTo(source, MAX_BYTES, MIN_BYTES)

                val isM3U8 = ct.contains("mpegurl") || url.contains(".m3u8", true)
                if (isM3U8) {
                    val str = String(buf, 0, minOf(buf.size, 1024))
                    return str.contains("#EXTM3U")
                }
                if (ended && buf.size < MIN_BYTES) return false
                // Canned offline videos typically <5 MB w/ Content-Length set.
                if (cl in 1L..5_000_000L) return false

                if (buf.isNotEmpty() && buf[0] == 0x47.toByte()) {
                    var validTs = true
                    var checkedPackets = 0
                    var i = 0
                    while (i < buf.size - 188 && checkedPackets < 10) {
                        if (buf[i] != 0x47.toByte()) { validTs = false; break }
                        checkedPackets++
                        i += 188
                    }
                    if (validTs && checkedPackets >= 3) return true
                }
                if (buf.size >= 8) {
                    val str4 = String(buf, 4, 4)
                    if (str4 == "ftyp") return true
                }
                if (hasVideoSignature(buf)) return true
                if (buf.size >= 32_768) return true
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readUpTo(src: BufferedSource, max: Int, min: Int): Pair<ByteArray, Boolean> {
        val out = ByteArray(max)
        var written = 0
        var ended = false
        while (written < max) {
            val n = try {
                src.read(out, written, max - written)
            } catch (_: Exception) { -1 }
            if (n <= 0) { ended = true; break }
            written += n
            if (written >= min) {
                // Got enough to evaluate; bail early to free the connection.
                break
            }
        }
        return out.copyOf(written) to ended
    }

    private fun isDeadContentType(ct: String): Boolean =
        ct.contains("text/html") ||
            ct.contains("application/json") ||
            ct.contains("text/xml") ||
            ct.contains("text/plain")

    private fun hasVideoSignature(buf: ByteArray): Boolean {
        if (buf.size < 4) return false
        if (buf[0] == 0x47.toByte()) return true
        if (buf.size >= 7 && String(buf, 0, 7) == "#EXTM3U") return true
        if (String(buf, 0, 4) == "#EXT") return true
        // AAC/MPEG sync (1111 1111 111x xxxx)
        if (buf[0] == 0xFF.toByte() && (buf[1].toInt() and 0xE0) == 0xE0) return true
        // Matroska / WebM
        if (buf[0] == 0x1A.toByte() && buf[1] == 0x45.toByte() &&
            buf[2] == 0xDF.toByte() && buf[3] == 0xA3.toByte()
        ) return true
        // Ogg
        if (buf[0] == 0x4F.toByte() && buf[1] == 0x67.toByte() &&
            buf[2] == 0x67.toByte() && buf[3] == 0x53.toByte()
        ) return true
        // H.264 NAL start code
        if (buf[0] == 0x00.toByte() && buf[1] == 0x00.toByte() &&
            buf[2] == 0x00.toByte() && buf[3] == 0x01.toByte()
        ) return true
        if (buf[0] == 0x00.toByte() && buf[1] == 0x00.toByte() &&
            buf[2] == 0x01.toByte() && (buf[3].toInt() and 0xFF) >= 0xB0
        ) return true
        return false
    }
}
