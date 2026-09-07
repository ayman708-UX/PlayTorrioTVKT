package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MovyScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MovyScraper"
        private const val API_BASE = "https://api.wecollege.net"
        private const val REFERER = "https://www.movy.bz/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Referer" to REFERER,
            "Origin" to "https://www.movy.bz"
        )

        private val magic = byteArrayOf(109, 118, 109, 49) // "mvm1"

        private val servers = listOf(
            mapOf("endpoint" to "miami", "name" to "Miami", "note" to "Original audio (Up to 4K)"),
            mapOf("endpoint" to "seattle", "name" to "Seattle", "note" to "Original audio"),
            mapOf("endpoint" to "denver", "name" to "Denver", "note" to "Original audio"),
            mapOf("endpoint" to "chicago", "name" to "Chicago", "note" to "Original audio"),
            mapOf("endpoint" to "dallas", "name" to "Dallas", "note" to "Original audio"),
            mapOf("endpoint" to "atlanta", "name" to "Atlanta", "note" to "Original audio"),
            mapOf("endpoint" to "houston", "name" to "Houston", "note" to "Original audio"),
            mapOf("endpoint" to "austin", "name" to "Austin", "note" to "Original audio"),
            mapOf("endpoint" to "boston", "name" to "Boston", "note" to "Original audio"),
            mapOf("endpoint" to "munich", "name" to "Munich", "note" to "German audio", "extra" to "language=german"),
            mapOf("endpoint" to "berlin", "name" to "Berlin", "note" to "German audio"),
            mapOf("endpoint" to "paris", "name" to "Paris", "note" to "French audio"),
            mapOf("endpoint" to "delhi", "name" to "Delhi", "note" to "Hindi audio"),
            mapOf("endpoint" to "cancun", "name" to "Cancun", "note" to "Spanish audio")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private data class SeedEntry(val seed: String, val expiresAt: Long)
        private val seedCache = ConcurrentHashMap<Int, SeedEntry>()

        private suspend fun getSeed(tmdbId: Int): String? = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = seedCache[tmdbId]
            if (cached != null && cached.expiresAt > now + 5000) {
                return@withContext cached.seed
            }
            try {
                val req = Request.Builder()
                    .url("$API_BASE/seed?mediaId=$tmdbId")
                    .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string().orEmpty())
                        val seed = json.optString("seed")
                        val ttlMs = json.optLong("ttlMs", 30000L)
                        if (seed.isNotBlank()) {
                            seedCache[tmdbId] = SeedEntry(seed, now + ttlMs)
                            return@withContext seed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch seed for TMDB $tmdbId: ${e.message}")
            }
            null
        }

        private fun l(e: Long): Long {
            var v = e and 0xFFFFFFFFL
            v = (v xor (v ushr 16)) and 0xFFFFFFFFL
            v = (v * 0x85ebca6bL) and 0xFFFFFFFFL
            v = (v xor (v ushr 13)) and 0xFFFFFFFFL
            v = (v * 0xc2b2ae35L) and 0xFFFFFFFFL
            return (v xor (v ushr 16)) and 0xFFFFFFFFL
        }

        private fun u(e: Long, t: Long): Long {
            val shift = (t and 31).toInt()
            if (shift == 0) return e and 0xFFFFFFFFL
            return (((e shl shift) and 0xFFFFFFFFL) or ((e and 0xFFFFFFFFL) ushr (32 - shift))) and 0xFFFFFFFFL
        }

        private fun fnv1a(str: String): Long {
            var t = 0x811c9dc5L
            for (i in 0 until str.length) {
                val code = str[i].code.toLong()
                t = (((t xor code) and 0xFFFFFFFFL) * 0x1000193L) and 0xFFFFFFFFL
            }
            return l(t)
        }

        private class KeyState(
            val s: LongArray,
            val isSet: BooleanArray,
            var acc: Long
        )

        private fun initKeyState(seed: String, tmdbId: Int): KeyState {
            val s = LongArray(61)
            val isSet = BooleanArray(61)
            var r = l(fnv1a(seed) xor l((tmdbId.toLong() and 0xFFFFFFFFL) xor 0x9e3779b9L))

            for (e in 0 until 8) {
                val t = (r % 61).toInt()
                r = u((r + 0x9e3779b9L) and 0xFFFFFFFFL, 7L + (7L and e.toLong()))
                s[t] = (r xor l(r)) and 0xFFFFFFFFL
                isSet[t] = true
                r = l((r + t) and 0xFFFFFFFFL)
            }

            val acc = l(0xa5a5a5a5L xor r)
            return KeyState(s, isSet, acc)
        }

        private fun nextKeystreamWord(state: KeyState, t: Long): Long {
            val r = state.s
            var nState = state.acc
            val i = (nState % 61).toInt()
            val oVal = if (state.isSet[i]) -1L else 0L
            val d = if (state.isSet[i]) r[i] else 0L
            val c = ((t + 1L) * 0x9e3779b9L) and 0xFFFFFFFFL
            val a = nState
            val sVal = d xor c
            val h = ((a xor sVal) or (a and sVal and oVal)) and 0xFFFFFFFFL
            val term1 = u((h + nState) and 0xFFFFFFFFL, 31L and i.toLong())
            val term2 = u(nState, 31L and (i.toLong() * 7L))
            nState = l(((term1 xor term2) + 0x9e3779b9L) and 0xFFFFFFFFL)
            r[i] = nState
            state.isSet[i] = true
            state.acc = nState
            return nState and 0xFFFFFFFFL
        }

        private fun generateKeyStream(seed: String, tmdbId: Int, len: Int): ByteArray {
            val state = initKeyState(seed, tmdbId)
            val out = ByteArray(len)
            var wordIdx = 0L
            var byteIdx = 0

            while (byteIdx < len) {
                val word = nextKeystreamWord(state, wordIdx++)
                out[byteIdx++] = (word and 0xFFL).toByte()
                if (byteIdx < len) out[byteIdx++] = ((word ushr 8) and 0xFFL).toByte()
                if (byteIdx < len) out[byteIdx++] = ((word ushr 16) and 0xFFL).toByte()
                if (byteIdx < len) out[byteIdx++] = ((word ushr 24) and 0xFFL).toByte()
            }
            return out
        }

        private fun decrypt(cipherB64: String, seed: String, tmdbId: Int): String? {
            try {
                val cipherBytes = Base64.decode(cipherB64, Base64.DEFAULT)
                if (cipherBytes.size <= magic.size) return null

                val ks = generateKeyStream(seed, tmdbId, cipherBytes.size)
                for (i in cipherBytes.indices) {
                    cipherBytes[i] = (cipherBytes[i].toInt() xor ks[i].toInt()).toByte()
                }

                for (k in magic.indices) {
                    if (cipherBytes[k] != magic[k]) {
                        return null
                    }
                }

                val payload = cipherBytes.copyOfRange(magic.size, cipherBytes.size)
                return String(payload, Charsets.UTF_8)
            } catch (e: Exception) {
                return null
            }
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        val seed = getSeed(tmdbId) ?: return@withContext emptyList()

        val encTitle = URLEncoder.encode(request.title, "UTF-8")
        val qBuilder = StringBuilder()
        qBuilder.append("title=$encTitle")
        qBuilder.append("&mediaType=$mediaType")
        if (request.year != null && request.year > 0) qBuilder.append("&year=${request.year}")
        if (isTv) {
            if (request.season != null) qBuilder.append("&seasonId=${request.season}")
            if (request.episode != null) qBuilder.append("&episodeId=${request.episode}")
        }
        qBuilder.append("&tmdbId=$tmdbId")
        if (!request.imdbId.isNullOrBlank()) qBuilder.append("&imdbId=${request.imdbId}")
        qBuilder.append("&enc=2&seed=$seed")
        val baseQuery = qBuilder.toString()

        val tasks = servers.map { server ->
            async {
                val endpoint = server["endpoint"]!!
                val serverName = server["name"]!!
                val note = server["note"]!!
                val extra = server["extra"]

                var fullUrl = "$API_BASE/$endpoint/sources?$baseQuery"
                if (!extra.isNullOrBlank()) fullUrl += "&$extra"

                val list = mutableListOf<ScraperStreamResult>()
                try {
                    val req = Request.Builder()
                        .url(fullUrl)
                        .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val encText = res.body?.string().orEmpty().trim()
                            if (encText.isNotEmpty() && !encText.startsWith("<")) {
                                val decJsonStr = decrypt(encText, seed, tmdbId)
                                if (!decJsonStr.isNullOrEmpty()) {
                                    val parsed = JSONObject(decJsonStr)
                                    val sourcesList = parsed.optJSONArray("sources")
                                    if (sourcesList != null) {
                                        for (i in 0 until sourcesList.length()) {
                                            val src = sourcesList.getJSONObject(i)
                                            val streamUrl = src.optString("url")
                                            if (streamUrl.isBlank() || !streamUrl.startsWith("http")) continue

                                            val quality = src.optString("quality").ifEmpty { "Auto" }
                                            list.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "[Movy - $serverName] $quality",
                                                    description = "$note • HLS",
                                                    url = streamUrl,
                                                    quality = quality,
                                                    headers = defaultHeaders
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Movy server $endpoint failed: ${e.message}")
                }
                list
            }
        }

        val allResults = tasks.awaitAll().flatten()
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<ScraperStreamResult>()
        for (r in allResults) {
            if (seen.add(r.url)) {
                deduped.add(r)
            }
        }
        deduped
    }
}
