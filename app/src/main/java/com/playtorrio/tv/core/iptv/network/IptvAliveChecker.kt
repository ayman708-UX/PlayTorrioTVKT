package com.playtorrio.tv.core.iptv.network

import com.playtorrio.tv.core.iptv.model.AliveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object IptvAliveChecker {
    private const val MIN_BYTES = 2 * 1024
    private const val MAX_BYTES = 8 * 1024
    private const val CONCURRENCY = 20

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    suspend fun launchCheck(
        streams: List<Pair<String, String>>, // (streamId, url)
        onResult: suspend (String, Boolean) -> Unit,
        onProgress: suspend (AliveProgress) -> Unit,
        onDone: suspend () -> Unit,
        isCancelled: (() -> Boolean)? = null
    ) = withContext(Dispatchers.IO) {
        val checkedCounter = AtomicInteger(0)
        val aliveCounter = AtomicInteger(0)
        val total = streams.size
        val semaphore = Semaphore(CONCURRENCY)

        val jobs = streams.map { (id, url) ->
            async {
                if (isCancelled?.invoke() == true) return@async
                val ok = semaphore.withPermit {
                    if (isCancelled?.invoke() == true) return@withPermit false
                    isAlive(url)
                }

                if (isCancelled?.invoke() == true) return@async
                val checked = checkedCounter.incrementAndGet()
                val alive = if (ok) aliveCounter.incrementAndGet() else aliveCounter.get()
                onResult(id, ok)
                onProgress(AliveProgress(checked, total, alive))
            }
        }

        jobs.awaitAll()
        if (isCancelled?.invoke() != true) {
            onDone()
        }
    }

    suspend fun isAlive(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "keep-alive")
                .addHeader("Range", "bytes=0-${MAX_BYTES - 1}")
                .build()

            httpClient.newCall(req).execute().use { res ->
                val code = res.code
                if (code != 206 && (code < 200 || code >= 300)) return@withContext false

                val ct = res.header("Content-Type").orEmpty().lowercase()
                if (isDeadContentType(ct)) return@withContext false

                val body = res.body ?: return@withContext false
                val stream = body.byteStream()
                val buf = ByteArray(MAX_BYTES)
                var bytesRead = 0
                var r = 0

                while (bytesRead < MAX_BYTES && stream.read(buf, bytesRead, MAX_BYTES - bytesRead).also { r = it } != -1) {
                    bytesRead += r
                }

                if (ct.contains("mpegurl") || url.contains(".m3u8", ignoreCase = true)) {
                    val head = String(buf, 0, minOf(bytesRead, 1024), Charsets.UTF_8)
                    return@withContext head.contains("#EXTM3U")
                }

                if (bytesRead < MIN_BYTES) return@withContext false

                // Check MPEG-TS sync byte (0x47)
                if (buf[0] == 0x47.toByte()) {
                    var validTs = true
                    var checkedPackets = 0
                    var i = 0
                    while (i < bytesRead - 188 && checkedPackets < 10) {
                        if (buf[i] != 0x47.toByte()) {
                            validTs = false
                            break
                        }
                        checkedPackets++
                        i += 188
                    }
                    if (validTs && checkedPackets >= 3) return@withContext true
                }

                // Check MP4 ftyp
                if (bytesRead >= 8) {
                    val s = String(buf, 4, 4, Charsets.US_ASCII)
                    if (s == "ftyp") return@withContext true
                }

                if (hasVideoSignature(buf, bytesRead)) return@withContext true
                return@withContext bytesRead >= 32 * 1024
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isDeadContentType(ct: String): Boolean =
        ct.contains("text/html") || ct.contains("application/json") || ct.contains("text/xml") || ct.contains("text/plain")

    private fun hasVideoSignature(buf: ByteArray, len: Int): Boolean {
        if (len < 4) return false
        if (buf[0] == 0x47.toByte()) return true
        if (len >= 7 && String(buf, 0, 7, Charsets.US_ASCII) == "#EXTM3U") return true
        if (len >= 4 && String(buf, 0, 4, Charsets.US_ASCII) == "#EXT") return true
        if ((buf[0].toInt() and 0xFF) == 0xFF && ((buf[1].toInt() and 0xE0) == 0xE0)) return true
        return false
    }
}
