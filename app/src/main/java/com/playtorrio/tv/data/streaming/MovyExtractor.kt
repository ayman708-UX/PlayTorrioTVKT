package com.playtorrio.tv.data.streaming

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MovyExtractor {
    private const val TAG = "MovyExtractor"
    private const val API_BASE = "https://api.wecollege.net"
    private const val REFERER = "https://www.movy.bz/"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val MAGIC = byteArrayOf(109, 118, 109, 49) // "mvm1"
    private val N_TABLE = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt()
    )

    private data class ServerDef(
        val endpoint: String,
        val displayName: String,
        val note: String = "Original audio",
        val extraParam: Pair<String, String>? = null
    )

    private val SERVERS = listOf(
        ServerDef("miami", "Miami", "Original audio (Up to 4K)"),
        ServerDef("seattle", "Seattle", "Original audio"),
        ServerDef("denver", "Denver", "Original audio"),
        ServerDef("chicago", "Chicago", "Original audio"),
        ServerDef("dallas", "Dallas", "Original audio"),
        ServerDef("atlanta", "Atlanta", "Original audio"),
        ServerDef("houston", "Houston", "Original audio"),
        ServerDef("austin", "Austin", "Original audio"),
        ServerDef("boston", "Boston", "Original audio"),
        ServerDef("munich", "Munich", "German audio", "language" to "german"),
        ServerDef("berlin", "Berlin", "German audio"),
        ServerDef("paris", "Paris", "French audio"),
        ServerDef("delhi", "Delhi", "Hindi audio"),
        ServerDef("cancun", "Cancun", "Spanish audio")
    )

    // Cache seeds per TMDB ID
    private val seedCache = ConcurrentHashMap<Int, Pair<String, Long>>()

    private suspend fun getSeed(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = seedCache[tmdbId]
        if (cached != null && cached.second > now + 5000) {
            return@withContext cached.first
        }

        try {
            val req = Request.Builder()
                .url("$API_BASE/seed?mediaId=$tmdbId")
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val seed = json.optString("seed", "")
                val ttlMs = json.optLong("ttlMs", 30000L)
                if (seed.isNotBlank()) {
                    seedCache[tmdbId] = Pair(seed, now + ttlMs)
                    return@withContext seed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get seed for TMDB $tmdbId: ${e.message}")
        }
        null
    }

    /**
     * Extracts streams in real-time from all Movy city servers concurrently.
     */
    suspend fun extractLive(
        title: String,
        isMovie: Boolean,
        year: Int?,
        season: Int?,
        episode: Int?,
        imdbId: String?,
        tmdbId: Int,
        onStreamFound: (HttpStreamResult) -> Unit
    ) = coroutineScope {
        if (tmdbId <= 0) {
            Log.w(TAG, "MovyExtractor requires a valid TMDB ID")
            return@coroutineScope
        }

        val seed = getSeed(tmdbId) ?: run {
            Log.w(TAG, "MovyExtractor could not acquire seed for TMDB $tmdbId")
            return@coroutineScope
        }

        val mediaType = if (isMovie) "movie" else "tv"
        val encodedTitle = URLEncoder.encode(title, "UTF-8")

        val queryParams = StringBuilder()
        queryParams.append("title=").append(encodedTitle)
        queryParams.append("&mediaType=").append(mediaType)
        if (year != null && year > 0) queryParams.append("&year=").append(year)
        if (!isMovie) {
            if (season != null) queryParams.append("&seasonId=").append(season)
            if (episode != null) queryParams.append("&episodeId=").append(episode)
        }
        queryParams.append("&tmdbId=").append(tmdbId)
        if (!imdbId.isNullOrBlank()) queryParams.append("&imdbId=").append(imdbId)
        queryParams.append("&enc=2&seed=").append(seed)

        val queryString = queryParams.toString()

        SERVERS.map { server ->
            launch(Dispatchers.IO) {
                try {
                    var fullUrl = "$API_BASE/${server.endpoint}/sources?$queryString"
                    if (server.extraParam != null) {
                        fullUrl += "&${server.extraParam.first}=${server.extraParam.second}"
                    }

                    val req = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", REFERER)
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@launch
                        val encText = resp.body?.string() ?: return@launch
                        if (encText.isBlank() || encText.startsWith("<")) return@launch

                        val decryptedJson = decrypt(encText, seed, tmdbId) ?: return@launch
                        val parsed = JSONObject(decryptedJson)
                        val sources = parsed.optJSONArray("sources") ?: JSONArray()

                        for (i in 0 until sources.length()) {
                            val src = sources.optJSONObject(i) ?: continue
                            val streamUrl = src.optString("url", "")
                            if (streamUrl.isBlank()) continue

                            val qualityRaw = src.optString("quality", "Auto")
                            val cleanQuality = when {
                                qualityRaw.contains("2160", ignoreCase = true) || qualityRaw.contains("4k", ignoreCase = true) -> "4K"
                                qualityRaw.contains("1080", ignoreCase = true) -> "1080p"
                                qualityRaw.contains("720", ignoreCase = true) -> "720p"
                                qualityRaw.contains("480", ignoreCase = true) -> "480p"
                                qualityRaw.contains("360", ignoreCase = true) -> "360p"
                                qualityRaw.isNotBlank() -> qualityRaw
                                else -> "Auto"
                            }

                            val streamTitle = "[Movy - ${server.displayName}] $cleanQuality"
                            val desc = "${server.note} • HLS"

                            val headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to REFERER
                            )

                            onStreamFound(
                                HttpStreamResult(
                                    sourceName = "Movy (${server.displayName})",
                                    title = streamTitle,
                                    description = desc,
                                    url = streamUrl,
                                    headers = headers,
                                    quality = cleanQuality
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore individual server timeouts/errors silently
                }
            }
        }.joinAll()
    }

    /**
     * Single stream extractor for fallback player integration.
     */
    suspend fun extract(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        title: String?,
        year: Int?,
        imdbId: String?
    ): StreamResult? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || title.isNullOrBlank()) return@withContext null

        var bestStream: StreamResult? = null
        val isMovie = season == null

        extractLive(
            title = title,
            isMovie = isMovie,
            year = year,
            season = season,
            episode = episode,
            imdbId = imdbId,
            tmdbId = tmdbId,
            onStreamFound = { result ->
                if (bestStream == null || result.quality?.contains("1080") == true || result.quality?.contains("4K") == true) {
                    bestStream = StreamResult(
                        url = result.url,
                        referer = REFERER,
                        headers = result.headers
                    )
                }
            }
        )

        bestStream
    }

    // --- Decryption implementation ---

    private fun l(e: Int): Int {
        var v = e
        v = v xor (v ushr 16)
        v = (v.toLong() * 0x85ebca6bL).toInt()
        v = v xor (v ushr 13)
        v = (v.toLong() * 0xc2b2ae35L).toInt()
        return v xor (v ushr 16)
    }

    private fun u(e: Int, t: Int): Int {
        val shift = t and 31
        return if (shift == 0) e else (e shl shift) or (e ushr (32 - shift))
    }

    private fun fnv1a(str: String): Int {
        var t = 0x811c9dc5.toInt()
        for (i in 0 until str.length) {
            val code = str[i].code
            t = ((t xor code).toLong() * 0x1000193L).toInt()
        }
        return l(t)
    }

    private class KeyState(
        val s: IntArray,
        val isSet: BooleanArray,
        var acc: Int
    )

    private fun initKeyState(seed: String, tmdbId: Int): KeyState {
        val s = IntArray(61)
        val isSet = BooleanArray(61)
        var r = l(fnv1a(seed) xor l(tmdbId xor 0x9e3779b9.toInt()))

        for (e in 0 until 8) {
            val t = ((r.toLong() and 0xFFFFFFFFL) % 61).toInt()
            r = u((r + 0x9e3779b9.toInt()), 7 + (7 and e))
            s[t] = r xor l(r)
            isSet[t] = true
            r = l(r + t)
        }

        val acc = l(0xa5a5a5a5.toInt() xor r)
        return KeyState(s, isSet, acc)
    }

    private fun nextKeystreamWord(state: KeyState, t: Int): Int {
        val r = state.s
        var nState = state.acc
        val i = ((nState.toLong() and 0xFFFFFFFFL) % 61).toInt()
        val oVal = if (state.isSet[i]) -1 else 0
        val d = if (state.isSet[i]) r[i] else 0
        val c = ((t + 1).toLong() * 0x9e3779b9L).toInt()
        val a = nState
        val sVal = d xor c
        val h = (a xor sVal) or (a and sVal and oVal)
        val term1 = u(h + nState, 31 and i)
        val term2 = u(nState, 31 and (i * 7))
        nState = l((term1 xor term2) + 0x9e3779b9.toInt())
        r[i] = nState
        state.isSet[i] = true
        state.acc = nState
        return nState
    }

    private fun generateKeyStream(seed: String, tmdbId: Int, len: Int): ByteArray {
        val state = initKeyState(seed, tmdbId)
        val out = ByteArray(len)
        var wordIdx = 0
        var byteIdx = 0

        while (byteIdx < len) {
            val word = nextKeystreamWord(state, wordIdx++)
            out[byteIdx++] = (word and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 8) and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 16) and 0xFF).toByte()
            if (byteIdx < len) out[byteIdx++] = ((word ushr 24) and 0xFF).toByte()
        }
        return out
    }

    fun decrypt(cipherB64: String, seed: String, tmdbId: Int): String? {
        try {
            val normalized = cipherB64.replace('-', '+').replace('_', '/')
            val cipherBytes = Base64.decode(normalized, Base64.DEFAULT)
            if (cipherBytes.size <= MAGIC.size) return null

            val ks = generateKeyStream(seed, tmdbId, cipherBytes.size)
            for (i in cipherBytes.indices) {
                cipherBytes[i] = (cipherBytes[i].toInt() xor ks[i].toInt()).toByte()
            }

            for (k in MAGIC.indices) {
                if (cipherBytes[k] != MAGIC[k]) {
                    Log.w(TAG, "Decryption magic header mismatch")
                    return null
                }
            }

            val payloadLength = cipherBytes.size - MAGIC.size
            return String(cipherBytes, MAGIC.size, payloadLength, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error: ${e.message}", e)
            return null
        }
    }
}
