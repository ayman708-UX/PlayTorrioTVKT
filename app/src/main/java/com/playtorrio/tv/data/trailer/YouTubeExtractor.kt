package com.playtorrio.tv.data.trailer

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "YouTubeExtractor"
private const val EXTRACTOR_TIMEOUT_MS = 30_000L
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
private const val PREFERRED_SEPARATE_CLIENT = "android_vr"

private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")

private data class YouTubeClient(
    val key: String,
    val id: String,
    val version: String,
    val userAgent: String,
    val context: Map<String, Any>,
    val priority: Int
)

private data class WatchConfig(val apiKey: String?, val visitorData: String?)

private data class StreamCandidate(
    val client: String,
    val priority: Int,
    val url: String,
    val score: Double,
    val hasN: Boolean,
    val itag: String,
    val height: Int,
    val fps: Int,
    val ext: String
)

private data class ManifestBestVariant(
    val url: String, val width: Int, val height: Int, val bandwidth: Long
)

private data class ManifestCandidate(
    val client: String, val priority: Int, val manifestUrl: String,
    val selectedVariantUrl: String, val height: Int, val bandwidth: Long
)

private val DEFAULT_HEADERS = mapOf(
    "accept-language" to "en-US,en;q=0.9",
    "user-agent" to DEFAULT_USER_AGENT
)

private val CLIENTS = listOf(
    YouTubeClient(
        key = "android_vr", id = "28", version = "1.56.21",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.56.21 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1) gzip",
        context = mapOf(
            "clientName" to "ANDROID_VR", "clientVersion" to "1.56.21",
            "deviceMake" to "Oculus", "deviceModel" to "Quest 3",
            "osName" to "Android", "osVersion" to "12",
            "platform" to "MOBILE", "androidSdkVersion" to 32,
            "hl" to "en", "gl" to "US"
        ),
        priority = 0
    ),
    YouTubeClient(
        key = "android", id = "3", version = "20.10.35",
        userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
        context = mapOf(
            "clientName" to "ANDROID", "clientVersion" to "20.10.35",
            "osName" to "Android", "osVersion" to "14",
            "platform" to "MOBILE", "androidSdkVersion" to 34,
            "hl" to "en", "gl" to "US"
        ),
        priority = 1
    ),
    YouTubeClient(
        key = "ios", id = "5", version = "20.10.1",
        userAgent = "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)",
        context = mapOf(
            "clientName" to "IOS", "clientVersion" to "20.10.1",
            "deviceModel" to "iPhone16,2", "osName" to "iPhone",
            "osVersion" to "17.4.0.21E219", "platform" to "MOBILE",
            "hl" to "en", "gl" to "US"
        ),
        priority = 2
    )
)

object YouTubeExtractor {
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private data class CachedConfig(
        val apiKey: String, val visitorData: String?,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val cachedConfig = AtomicReference<CachedConfig?>(null)
    private val configMutex = Mutex()
    private const val CONFIG_TTL_MS = 3 * 60 * 60 * 1000L
    private const val FALLBACK_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    private suspend fun ensureWatchConfig(forceRefresh: Boolean = false): CachedConfig {
        if (!forceRefresh) {
            val current = cachedConfig.get()
            if (current != null && System.currentTimeMillis() - current.fetchedAt < CONFIG_TTL_MS) {
                return current
            }
        }
        return configMutex.withLock {
            if (!forceRefresh) {
                val current = cachedConfig.get()
                if (current != null && System.currentTimeMillis() - current.fetchedAt < CONFIG_TTL_MS) {
                    return@withLock current
                }
            }
            // Try fetching watch page for visitor_data, but don't fail if blocked
            Log.i(TAG, "Fetching watch page for visitor_data")
            try {
                val resp = performRequest(
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en", "GET", DEFAULT_HEADERS
                )
                if (resp.ok) {
                    val parsed = getWatchConfig(resp.body)
                    val newConfig = CachedConfig(
                        apiKey = parsed.apiKey ?: FALLBACK_API_KEY,
                        visitorData = parsed.visitorData
                    )
                    cachedConfig.set(newConfig)
                    Log.i(TAG, "Watch config fetched successfully")
                    return@withLock newConfig
                }
                Log.w(TAG, "Watch page returned ${resp.status}, using fallback key")
            } catch (e: Exception) {
                Log.w(TAG, "Watch page fetch failed: ${e.message}, using fallback key")
            }
            // Fallback: use hardcoded API key without visitor data
            val fallback = CachedConfig(apiKey = FALLBACK_API_KEY, visitorData = null)
            cachedConfig.set(fallback)
            fallback
        }
    }

    suspend fun extractPlaybackSource(youtubeUrl: String, audioOnly: Boolean = false): TrailerPlaybackSource? =
        withContext(Dispatchers.IO) {
            if (youtubeUrl.isBlank()) return@withContext null
            try {
                withTimeout(EXTRACTOR_TIMEOUT_MS) {
                    extractInternal(youtubeUrl, forceRefreshConfig = false, audioOnly = audioOnly)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Extraction failed: ${e.message}")
                null
            }
        }

    private suspend fun extractInternal(
        youtubeUrl: String, forceRefreshConfig: Boolean, audioOnly: Boolean = false
    ): TrailerPlaybackSource? {
        val videoId = extractVideoId(youtubeUrl) ?: return null
        val config = ensureWatchConfig(forceRefresh = forceRefreshConfig)

        val progressive = mutableListOf<StreamCandidate>()
        val adaptiveVideo = mutableListOf<StreamCandidate>()
        val adaptiveAudio = mutableListOf<StreamCandidate>()
        val manifestUrls = mutableListOf<Triple<String, Int, String>>()
        var loginRequiredCount = 0

        for (client in CLIENTS) {
            // Skip VR client without visitor data — always returns LOGIN_REQUIRED
            if (client.key == "android_vr" && config.visitorData.isNullOrBlank()) {
                Log.i(TAG, "Skipping ${client.key} (no visitor data)")
                continue
            }
            try {
                Log.i(TAG, "Trying client: ${client.key}")
                val playerResponse = fetchPlayerResponse(config.apiKey, videoId, client, config.visitorData)

                val playabilityStatus = playerResponse.mapValue("playabilityStatus")
                val status = playabilityStatus?.stringValue("status")
                Log.i(TAG, "Client ${client.key} status: $status")
                if (status == "LOGIN_REQUIRED") {
                    loginRequiredCount++; continue
                }

                val streamingData = playerResponse.mapValue("streamingData")
                if (streamingData == null) {
                    Log.w(TAG, "Client ${client.key}: no streamingData")
                    continue
                }
                streamingData.stringValue("hlsManifestUrl")?.takeIf { it.isNotBlank() }?.let {
                    manifestUrls += Triple(client.key, client.priority, it)
                }

                for (format in streamingData.listMapValue("formats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (!mimeType.contains("video/") && mimeType.isNotBlank()) continue
                    val height = (format.numberValue("height")
                        ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble() ?: 0.0).toInt()
                    val fps = (format.numberValue("fps") ?: 0.0).toInt()
                    val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                    progressive += StreamCandidate(
                        client.key, client.priority, url,
                        videoScore(height, fps, bitrate), hasNParam(url),
                        format.stringValue("itag").orEmpty(), height, fps,
                        if (mimeType.contains("webm")) "webm" else "mp4"
                    )
                }

                for (format in streamingData.listMapValue("adaptiveFormats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (mimeType.contains("video/")) {
                        val height = (format.numberValue("height")
                            ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble() ?: 0.0).toInt()
                        val fps = (format.numberValue("fps") ?: 0.0).toInt()
                        val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                        adaptiveVideo += StreamCandidate(
                            client.key, client.priority, url,
                            videoScore(height, fps, bitrate), hasNParam(url),
                            format.stringValue("itag").orEmpty(), height, fps,
                            if (mimeType.contains("webm")) "webm" else "mp4"
                        )
                    } else if (mimeType.contains("audio/")) {
                        val bitrate = format.numberValue("bitrate") ?: format.numberValue("averageBitrate") ?: 0.0
                        val asr = format.numberValue("audioSampleRate") ?: 0.0
                        adaptiveAudio += StreamCandidate(
                            client.key, client.priority, url,
                            audioScore(bitrate, asr), hasNParam(url),
                            format.stringValue("itag").orEmpty(), 0, 0,
                            if (mimeType.contains("webm")) "webm" else "m4a"
                        )
                    }
                }

                // audioOnly: stop as soon as we have a progressive stream
                if (audioOnly && progressive.isNotEmpty()) {
                    Log.i(TAG, "audioOnly: got progressive from ${client.key}, skipping remaining clients")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client ${client.key} failed: ${e.message}")
            }
        }

        if (loginRequiredCount == CLIENTS.size) {
            cachedConfig.set(null)
            return null
        }
        if (progressive.isEmpty() && adaptiveVideo.isEmpty() && manifestUrls.isEmpty()) return null

        Log.i(TAG, "Streams: progressive=${progressive.size} adaptive=${adaptiveVideo.size} hls=${manifestUrls.size}")

        val bestProgressive = sortCandidates(progressive).firstOrNull()
        val bestVideo = pickBestForClient(adaptiveVideo, PREFERRED_SEPARATE_CLIENT)
        val bestAudio = pickBestForClient(adaptiveAudio, PREFERRED_SEPARATE_CLIENT)

        val videoUrl: String
        val audioUrl: String?
        val clientKey: String?

        // Prefer progressive/adaptive direct streams (avoids HLS ADTS+ID3 crash)
        if (bestProgressive != null) {
            videoUrl = bestProgressive.url
            audioUrl = null
            clientKey = bestProgressive.client
            Log.i(TAG, "Using progressive: ${bestProgressive.height}p client=${clientKey}")
        } else if (bestVideo != null) {
            videoUrl = bestVideo.url
            audioUrl = bestAudio?.url
            clientKey = bestVideo.client
            Log.i(TAG, "Using adaptive: ${bestVideo.height}p + audio=${audioUrl != null} client=${clientKey}")
        } else if (manifestUrls.isNotEmpty()) {
            videoUrl = manifestUrls.first().third
            audioUrl = null
            clientKey = manifestUrls.first().first
            Log.i(TAG, "Using HLS manifest as last resort from client=${clientKey}")
        } else {
            return null
        }

        // Find the user-agent of the client that provided the selected stream
        val clientUserAgent = CLIENTS.find { it.key == clientKey }?.userAgent

        Log.i(TAG, "Extracted: progressive=${progressive.size} adaptive=${adaptiveVideo.size}")
        return TrailerPlaybackSource(videoUrl = videoUrl, audioUrl = audioUrl, clientUserAgent = clientUserAgent)
    }

    private fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed
        val normalized = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
        return runCatching {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("youtu.be")) {
                uri.pathSegments.firstOrNull()?.takeIf { VIDEO_ID_REGEX.matches(it) }?.let { return it }
            }
            uri.getQueryParameter("v")?.takeIf { VIDEO_ID_REGEX.matches(it) }?.let { return it }
            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val second = segments[1]
                if (segments[0] in listOf("embed", "shorts", "live") && VIDEO_ID_REGEX.matches(second)) return second
            }
            null
        }.getOrNull()
    }

    private fun getWatchConfig(html: String): WatchConfig {
        return WatchConfig(
            apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1),
            visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
        )
    }

    private fun fetchPlayerResponse(
        apiKey: String, videoId: String, client: YouTubeClient, visitorData: String?
    ): Map<*, *> {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}"
        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (!visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
        }
        val payload = mapOf(
            "videoId" to videoId, "contentCheckOk" to true, "racyCheckOk" to true,
            "context" to mapOf("client" to client.context),
            "playbackContext" to mapOf("contentPlaybackContext" to mapOf("html5Preference" to "HTML5_PREF_WANTS"))
        )
        val resp = performRequest(endpoint, "POST", headers, gson.toJson(payload))
        if (!resp.ok) throw IllegalStateException("player API ${client.key} failed (${resp.status})")
        return gson.fromJson(resp.body, Map::class.java) ?: emptyMap<String, Any>()
    }

    private fun parseHlsManifest(manifestUrl: String): ManifestBestVariant? {
        val resp = performRequest(manifestUrl, "GET", DEFAULT_HEADERS)
        if (!resp.ok) return null
        val lines = resp.body.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        var best: ManifestBestVariant? = null
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF:")) continue
            val attrs = parseHlsAttributes(lines[i])
            val nextLine = lines.getOrNull(i + 1) ?: continue
            if (nextLine.startsWith("#")) continue
            val (w, h) = parseResolution(attrs["RESOLUTION"].orEmpty())
            val bw = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
            val candidate = ManifestBestVariant(absolutizeUrl(manifestUrl, nextLine), w, h, bw)
            if (best == null || candidate.height > best.height ||
                (candidate.height == best.height && candidate.bandwidth > best.bandwidth)
            ) best = candidate
        }
        return best
    }

    private fun parseHlsAttributes(line: String): Map<String, String> {
        val idx = line.indexOf(':')
        if (idx == -1) return emptyMap()
        val raw = line.substring(idx + 1)
        val out = LinkedHashMap<String, String>()
        val key = StringBuilder(); val value = StringBuilder()
        var inKey = true; var inQuote = false
        for (ch in raw) {
            if (inKey) { if (ch == '=') inKey = false else key.append(ch); continue }
            if (ch == '"') { inQuote = !inQuote; continue }
            if (ch == ',' && !inQuote) {
                val k = key.toString().trim()
                if (k.isNotEmpty()) out[k] = value.toString().trim()
                key.clear(); value.clear(); inKey = true; continue
            }
            value.append(ch)
        }
        val k = key.toString().trim()
        if (k.isNotEmpty()) out[k] = value.toString().trim()
        return out
    }

    private fun parseResolution(raw: String): Pair<Int, Int> {
        val parts = raw.split('x')
        if (parts.size != 2) return 0 to 0
        return (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0)
    }

    private fun parseQualityLabel(label: String?): Int? {
        return label?.let { QUALITY_LABEL_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
    }

    private fun hasNParam(url: String): Boolean =
        runCatching { !Uri.parse(url).getQueryParameter("n").isNullOrBlank() }.getOrDefault(false)

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double =
        height * 1_000_000_000.0 + fps * 1_000_000.0 + bitrate

    private fun audioScore(bitrate: Double, asr: Double): Double =
        bitrate * 1_000_000.0 + asr

    private fun sortCandidates(items: List<StreamCandidate>): List<StreamCandidate> =
        items.sortedWith(
            compareByDescending<StreamCandidate> { it.score }
                .thenBy { if (it.hasN) 1 else 0 }
                .thenBy { if (it.ext.lowercase() in listOf("mp4", "m4a")) 0 else 1 }
                .thenBy { it.priority }
        )

    private fun pickBestForClient(items: List<StreamCandidate>, clientKey: String): StreamCandidate? {
        val sameClient = items.filter { it.client == clientKey }
        return sortCandidates(sameClient.ifEmpty { items }).firstOrNull()
    }

    private suspend fun resolveReachableUrl(url: String): String {
        if (!url.contains("googlevideo.com")) return url
        val uri = Uri.parse(url)
        val mnParam = uri.getQueryParameter("mn") ?: return url
        val servers = mnParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (servers.size < 2) return url

        val candidates = mutableListOf(url)
        for (server in servers) {
            val mviIndex = servers.indexOf(server)
            val altHost = uri.host?.replaceFirst(Regex("^rr\\d+---"), "rr${mviIndex + 1}---")
                ?.replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server) ?: continue
            if (altHost == uri.host) continue
            candidates += url.replace(uri.host!!, altHost)
        }
        if (candidates.size == 1) return candidates[0]

        val result = CompletableDeferred<String>()
        val probeScope = CoroutineScope(Dispatchers.IO)
        candidates.forEach { candidate ->
            probeScope.launch {
                if (isUrlReachable(candidate)) result.complete(candidate)
            }
        }
        return try {
            withTimeoutOrNull(2_000L) { result.await() } ?: url
        } finally {
            probeScope.cancel()
        }
    }

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .build()

    private fun isUrlReachable(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).get()
            .header("Range", "bytes=0-0")
            .headers(buildHeaders(DEFAULT_HEADERS)).build()
        probeClient.newCall(req).execute().use { it.code == 200 || it.code == 206 }
    }.getOrDefault(false)

    private fun absolutizeUrl(base: String, relative: String): String =
        runCatching { URL(URL(base), relative).toString() }.getOrElse { relative }

    private fun performRequest(
        url: String, method: String, headers: Map<String, String>, body: String? = null
    ): RequestResponse {
        val rb = Request.Builder().url(url).headers(buildHeaders(headers))
        when (method.uppercase()) {
            "POST" -> rb.post((body ?: "").toRequestBody())
            else -> rb.get()
        }
        httpClient.newCall(rb.build()).execute().use { response ->
            return RequestResponse(
                ok = response.isSuccessful, status = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val b = Headers.Builder()
        source.forEach { (k, v) ->
            if (!k.equals("Accept-Encoding", ignoreCase = true)) b.add(k, v)
        }
        return b.build()
    }
}

private data class RequestResponse(val ok: Boolean, val status: Int, val body: String)

private fun Map<*, *>.mapValue(key: String): Map<*, *>? = this[key] as? Map<*, *>
private fun Map<*, *>.listMapValue(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()
private fun Map<*, *>.numberValue(key: String): Double? = when (val v = this[key]) {
    is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null
}
