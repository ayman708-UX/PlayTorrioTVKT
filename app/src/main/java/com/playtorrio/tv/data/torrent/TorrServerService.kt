package com.playtorrio.tv.data.torrent

import android.content.Context
import android.util.Log
import com.playtorrio.tv.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * HTTP API client for TorrServer running on localhost.
 * Handles: startup, configuration, adding torrents, streaming, stats.
 */
object TorrServerService {

    private const val TAG = "TorrServerService"
    private val baseUrl get() = "http://127.0.0.1:${TorrServerManager.port}"

    private var configured = false
    private var trackers: List<String> = emptyList()
    private val initMutex = Mutex()

    private const val TRACKERS_URL =
        "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"

    private val FALLBACK_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.pirateparty.gr:6969/announce",
        "https://tracker.tamersunion.org:443/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://p4p.arenabg.com:1337/announce"
    )

    // ── Initialization ──

    suspend fun warmup(context: Context) = withContext(Dispatchers.IO) {
        try { fetchTrackers() } catch (_: Exception) {}
        try { ensureInitialized(context) } catch (e: Exception) {
            Log.e(TAG, "Warmup init failed: ${e.message}")
        }
    }

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (isEchoAlive()) {
                Log.d(TAG, "TorrServer already responding")
                configureServer()
                return@withContext
            }

            Log.d(TAG, "TorrServer not responding, starting...")
            val ok = TorrServerManager.start(context)
            if (!ok) throw Exception("Failed to start TorrServer binary")

            Log.d(TAG, "Binary started, waiting for echo...")
            if (!waitForEcho(30_000)) {
                // Check if process is still alive
                Log.e(TAG, "Echo timeout. Process alive: ${TorrServerManager.isRunning}")
                throw Exception("TorrServer did not respond in time")
            }

            Log.d(TAG, "TorrServer responding, configuring...")
            configureServer()
        }
    }

    private fun isEchoAlive(): Boolean {
        return try {
            val conn = URL("$baseUrl/echo").openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) { false }
    }

    private fun waitForEcho(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            if (isEchoAlive()) {
                Log.d(TAG, "Echo alive after $attempt attempts")
                return true
            }
            attempt++
            if (attempt % 20 == 0) {
                Log.d(TAG, "Still waiting for echo... attempt $attempt, process alive=${TorrServerManager.isRunning}")
            }
            Thread.sleep(150)
        }
        Log.e(TAG, "Echo never responded after $attempt attempts (${timeoutMs}ms)")
        return false
    }

    // ── Configuration ──

    private fun configureServer() {
        val settingsUrl = "$baseUrl/settings"
        try {
            // Get current settings
            var current = JSONObject()
            try {
                val body = postJson(settingsUrl, JSONObject().put("action", "get"))
                if (body.isNotEmpty()) current = JSONObject(body)
            } catch (_: Exception) {}

            // Apply user-editable streaming settings + fast defaults.
            current.put("CacheSize", AppPreferences.torrentCacheSizeMb.toLong() * 1024 * 1024)
            current.put("UseDisk", false)
            current.put("RemoveCacheOnDrop", false)
            current.put("PreloadCache", AppPreferences.torrentPreloadPercent)
            current.put("ReaderReadAHead", AppPreferences.torrentReadAheadPercent)
            current.put("ResponsiveMode", AppPreferences.torrentResponsiveMode)
            current.put("Strategy", 2)
            current.put("ConnectionsLimit", AppPreferences.torrentConnectionsLimit)
            current.put("DhtConnectionLimit", 0)
            current.put("PeersListenPort", 0)
            current.put("DisableTCP", false)
            current.put("DisableUTP", false)
            current.put("DisableDHT", false)
            current.put("DisablePEX", false)
            current.put("EnableIPv6", !AppPreferences.torrentDisableIpv6)
            current.put("DisableUPNP", false)
            current.put("DisableUpload", AppPreferences.torrentDisableUpload)
            current.put("DownloadRateLimit", 0)
            current.put("UploadRateLimit", 0)
            current.put("RetrackersMode", 1)
            current.put("ForceEncrypt", false)
            current.put("TorrentDisconnectTimeout", 86400)
            current.put("EnableDLNA", false)
            current.put("EnableDebug", false)

            Log.d(
                TAG,
                "Applying preset=${AppPreferences.torrentPreset} cache=${AppPreferences.torrentCacheSizeMb}MB preload=${AppPreferences.torrentPreloadPercent}% readAhead=${AppPreferences.torrentReadAheadPercent}% conns=${AppPreferences.torrentConnectionsLimit} responsive=${AppPreferences.torrentResponsiveMode}"
            )

            postJson(settingsUrl, JSONObject().put("action", "set").put("sets", current))
            configured = true
            Log.d(TAG, "Configuration applied")
        } catch (e: Exception) {
            Log.e(TAG, "Configuration error (non-fatal): ${e.message}")
        }
    }

    private fun fetchTrackers() {
        try {
            val conn = URL(TRACKERS_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode == 200) {
                val lines = conn.inputStream.bufferedReader().readText()
                    .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    trackers = lines
                    Log.d(TAG, "Fetched ${lines.size} trackers")
                    conn.disconnect()
                    return
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "Tracker fetch failed: ${e.message}")
        }
        if (trackers.isEmpty()) {
            trackers = FALLBACK_TRACKERS
            Log.d(TAG, "Using ${FALLBACK_TRACKERS.size} fallback trackers")
        }
    }

    // ── Streaming ──

    data class StreamResult(
        val url: String,
        val hash: String,
        val fileIdx: Int,
        val fileName: String,
        val fileSize: Long
    )

    data class TorrentStats(
        val speedMbps: Double,
        val activePeers: Int,
        val totalPeers: Int,
        val loadedBytes: Long,
        val totalBytes: Long
    )

    suspend fun startStreaming(
        context: Context,
        magnetUri: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        fileIdx: Int? = null
    ): StreamResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "startStreaming called, season=$seasonNumber, episode=$episodeNumber")
        ensureInitialized(context)

        val hash = extractHash(magnetUri) ?: throw Exception("Cannot extract info-hash")
        Log.d(TAG, "Hash: $hash")
        val boosted = boostMagnet(magnetUri)
        val torrentsUrl = "$baseUrl/torrents"

        // Step 1: Add torrent
        Log.d(TAG, "Adding torrent...")
        addTorrentWithRetry(torrentsUrl, boosted, hash)
        Log.d(TAG, "Torrent added")

        // Step 2: Resolve file index
        Log.d(TAG, "Resolving file index...")
        val fileInfo = resolveFileIndex(torrentsUrl, hash, seasonNumber, episodeNumber, fileIdx)
            ?: throw Exception("No video file found in torrent")
        Log.d(TAG, "Resolved file: ${fileInfo.filename} (idx=${fileInfo.index}, size=${fileInfo.size})")

        // Step 3: Build stream URL
        val encoded = URLEncoder.encode(fileInfo.filename, "UTF-8")
        val streamUrl = "$baseUrl/stream/$encoded?link=$hash&index=${fileInfo.index}&play"
        Log.d(TAG, "Stream URL: $streamUrl")

        StreamResult(
            url = streamUrl,
            hash = hash,
            fileIdx = fileInfo.index,
            fileName = fileInfo.filename,
            fileSize = fileInfo.size
        )
    }

    private fun addTorrentWithRetry(torrentsUrl: String, magnet: String, hash: String) {
        if (torrentExists(torrentsUrl, hash)) return

        for (attempt in 0 until 6) {
            try {
                val body = postJson(torrentsUrl, JSONObject()
                    .put("action", "add")
                    .put("link", magnet)
                    .put("save_to_db", false)
                )
                if (body.contains("BT client not connected")) {
                    Log.d(TAG, "BT client not connected, retrying...")
                    Thread.sleep(500L * (1 shl attempt.coerceAtMost(4)))
                    continue
                }
                return // success
            } catch (e: Exception) {
                Log.d(TAG, "Add attempt $attempt error: ${e.message}")
                if (torrentExists(torrentsUrl, hash)) return
            }
            Thread.sleep(500L * (1 shl attempt.coerceAtMost(4)))
        }
        throw Exception("Failed to add torrent after retries")
    }

    private fun torrentExists(torrentsUrl: String, hash: String): Boolean {
        return try {
            val body = postJson(torrentsUrl, JSONObject()
                .put("action", "get")
                .put("hash", hash)
            )
            body.isNotEmpty() && !body.contains("null")
        } catch (_: Exception) { false }
    }

    private data class FileInfo(val index: Int, val filename: String, val size: Long)

    private fun resolveFileIndex(
        torrentsUrl: String,
        hash: String,
        season: Int?,
        episode: Int?,
        preferredIdx: Int?
    ): FileInfo? {
        val deadline = System.currentTimeMillis() + 30_000

        while (System.currentTimeMillis() < deadline) {
            try {
                val body = postJson(torrentsUrl, JSONObject()
                    .put("action", "get")
                    .put("hash", hash)
                )
                if (body.isEmpty()) { Thread.sleep(250); continue }

                val data = JSONObject(body)
                val rawFiles = data.optJSONArray("file_stats") ?: data.optJSONArray("files")
                if (rawFiles == null || rawFiles.length() == 0) { Thread.sleep(250); continue }

                var bestIdx: Int? = null
                var bestName: String? = null
                var bestSize: Long = -1
                var largestIdx: Int? = null
                var largestName: String? = null
                var largestSize: Long = -1

                for (i in 0 until rawFiles.length()) {
                    val f = rawFiles.getJSONObject(i)
                    val name = f.optString("path", f.optString("name", ""))
                    val size = f.optLong("length", 0)
                    val id = f.optInt("id", i)

                    if (!isVideoFile(name)) continue

                    if (season != null && episode != null && isEpisodeMatch(name, season, episode)) {
                        if (size > bestSize) {
                            bestSize = size; bestIdx = id; bestName = name
                        }
                    }
                    if (size > largestSize) {
                        largestSize = size; largestIdx = id; largestName = name
                    }
                }

                var selectedIdx = bestIdx
                var selectedName = bestName
                var selectedSize = bestSize

                if (selectedIdx == null && preferredIdx != null) {
                    for (i in 0 until rawFiles.length()) {
                        val f = rawFiles.getJSONObject(i)
                        if (f.optInt("id", i) == preferredIdx &&
                            isVideoFile(f.optString("path", f.optString("name", "")))) {
                            selectedIdx = preferredIdx
                            selectedName = f.optString("path", f.optString("name", ""))
                            selectedSize = f.optLong("length", 0)
                            break
                        }
                    }
                }

                if (selectedIdx == null) { selectedIdx = largestIdx; selectedName = largestName; selectedSize = largestSize }

                if (selectedIdx != null && selectedName != null) {
                    // Set priority for selected file only
                    try {
                        val priorities = JSONArray()
                        for (i in 0 until rawFiles.length()) {
                            val fId = rawFiles.getJSONObject(i).optInt("id", i)
                            priorities.put(if (fId == selectedIdx) 1 else 0)
                        }
                        postJson(torrentsUrl, JSONObject()
                            .put("action", "set")
                            .put("hash", hash)
                            .put("priority", priorities)
                        )
                    } catch (_: Exception) {}

                    return FileInfo(selectedIdx, selectedName, selectedSize)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Metadata poll error: ${e.message}")
            }
            Thread.sleep(250)
        }
        return null
    }

    suspend fun getTorrentStats(hash: String): TorrentStats? = withContext(Dispatchers.IO) {
        try {
            val body = postJson("$baseUrl/torrents", JSONObject()
                .put("action", "get")
                .put("hash", hash)
            )
            if (body.isEmpty()) return@withContext null
            val json = JSONObject(body)
            TorrentStats(
                speedMbps = json.optDouble("download_speed", 0.0) / 1024 / 1024,
                activePeers = json.optInt("active_peers", 0),
                totalPeers = json.optInt("total_peers", 0),
                loadedBytes = json.optLong("preload_size", 0),
                totalBytes = json.optLong("total_size", 0)
            )
        } catch (_: Exception) { null }
    }

    suspend fun removeTorrent(hash: String) = withContext(Dispatchers.IO) {
        try {
            postJson("$baseUrl/torrents", JSONObject()
                .put("action", "rem").put("hash", hash))
        } catch (_: Exception) {}
    }

    // ── Helpers ──

    private fun extractHash(magnetOrHash: String): String? {
        if (Regex("^[0-9a-fA-F]{40}$").matches(magnetOrHash) ||
            Regex("^[0-9a-fA-F]{64}$").matches(magnetOrHash)) {
            return magnetOrHash.lowercase()
        }
        if (magnetOrHash.startsWith("magnet:?")) {
            val match = Regex("btih:([a-fA-F0-9]+)").find(magnetOrHash)
            return match?.groupValues?.get(1)?.lowercase()
        }
        return null
    }

    private fun boostMagnet(magnet: String): String {
        val existing = mutableSetOf<String>()
        Regex("[?&]tr=([^&]+)").findAll(magnet).forEach {
            existing.add(java.net.URLDecoder.decode(it.groupValues[1], "UTF-8"))
        }
        val sb = StringBuilder(magnet)
        for (tracker in trackers) {
            if (tracker !in existing) {
                sb.append("&tr=${URLEncoder.encode(tracker, "UTF-8")}")
            }
        }
        return sb.toString()
    }

    private fun isVideoFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi") ||
                lower.endsWith(".mov") || lower.endsWith(".wmv") || lower.endsWith(".flv") ||
                lower.endsWith(".webm") || lower.endsWith(".m4v") || lower.endsWith(".ts") ||
                lower.endsWith(".m2ts") || lower.endsWith(".vob")
    }

    private fun isEpisodeMatch(name: String, season: Int, episode: Int): Boolean {
        val t = name.lowercase()
        if (Regex("s0*${season}[ ._-]*e0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
        if (Regex("\\b0*${season}x0*${episode}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
        return false
    }

    private fun postJson(url: String, json: JSONObject): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        conn.outputStream.use { it.write(json.toString().toByteArray()) }
        return try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
