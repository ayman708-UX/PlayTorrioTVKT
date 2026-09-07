package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VideasyScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VideasyScraper"
        private const val API_BASE = "https://api.speedracelight.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Referer" to "https://player.videasy.to/",
            "Origin" to "https://player.videasy.to",
            "Accept" to "application/json, text/plain, */*"
        )

        private val providers = listOf(
            mapOf("path" to "/cdn/sources-with-title", "label" to "Yoru"),
            mapOf("path" to "/neon2/sources-with-title", "label" to "Neon"),
            mapOf("path" to "/m4uhd/sources-with-title", "label" to "Breach"),
            mapOf("path" to "/meine/sources-with-title", "label" to "Killjoy"),
            mapOf("path" to "/lamovie/sources-with-title", "label" to "Omen")
        )

        private val f = longArrayOf(
            1116352408L, 1899447441L, 3049323471L, 3921009573L, 961987163L, 1508970993L,
            2453635748L, 2870763221L, 3624381080L, 310598401L, 607225278L, 1426881987L,
            1925078388L, 2162078206L, 2614888103L, 3248222580L
        )

        private val magic = byteArrayOf(109, 118, 109, 49) // "mvm1"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun imul(a: Long, b: Long): Long {
            val aInt = a.toInt()
            val bInt = b.toInt()
            return ((aInt and 0xffff) * bInt + (((aInt ushr 16) * bInt and 0xffff) shl 16)).toLong() and 0xffffffffL
        }

        private fun isEvenTri(e: Long): Boolean = (((e * (e + 1L)) and 1L) == 0L)
        private fun isOddTri(e: Long): Boolean = (((e * (e + 1L)) and 1L) == 1L)

        private fun mix(eIn: Long): Long {
            var e = eIn and 0xffffffffL
            e = e xor (e ushr 16)
            e = imul(e, 2246822507L) and 0xffffffffL
            e = e xor (e ushr 13)
            e = imul(e, 3266489909L) and 0xffffffffL
            return (e xor (e ushr 16)) and 0xffffffffL
        }

        private fun rotl(e: Long, t: Long): Long {
            val eVal = e and 0xffffffffL
            val tVal = (t and 31L).toInt()
            if (tVal == 0) return eVal
            return (((eVal shl tVal) and 0xffffffffL) or (eVal ushr (32 - tVal))) and 0xffffffffL
        }

        private fun fnv1a(e: String): Long {
            var t = 2166136261L
            for (s in 0 until e.length) {
                t = imul(t xor e[s].code.toLong(), 16777619L) and 0xffffffffL
            }
            return mix(t)
        }

        private fun accSeed(e: String): Long {
            var t = 1732584193L
            for (s in 0 until e.length) {
                t = rotl((t xor imul(e[s].code.toLong(), f[15 and s])) and 0xffffffffL, 5L)
            }
            return mix(t)
        }

        private fun rc4Sbox(e: String): LongArray {
            val t = LongArray(256) { it.toLong() }
            var s = 0L
            for (a in 0 until 256) {
                s = (s + t[a] + e[a % e.length].code.toLong()) and 255L
                val r = t[a]
                t[a] = t[s.toInt()]
                t[s.toInt()] = r
            }
            return t
        }

        private class State(
            val s: LongArray,
            var acc: Long
        )

        private fun buildState(seed: String, mediaId: Int): State {
            if (isOddTri(seed.length.toLong())) {
                return State(rc4Sbox(seed), accSeed(seed))
            }
            val s = LongArray(61)
            var a = mix(fnv1a(seed) xor mix((mediaId.toLong() and 0xffffffffL) xor 2654435769L)) and 0xffffffffL
            for (e in 0 until 8) {
                if (isEvenTri(e.toLong())) {
                    val t = (a % 61L).toInt()
                    a = rotl((a + 2654435769L) and 0xffffffffL, 7L + (7L and e.toLong()))
                    s[t] = (a xor mix(a)) and 0xffffffffL
                    a = mix((a + t.toLong()) and 0xffffffffL)
                } else {
                    s[e] = f[15 and e]
                }
            }
            return State(s, mix(2779096485L xor a) and 0xffffffffL)
        }

        private fun nextWord(state: State, counter: Long): Long {
            val r = state.s
            var acc = state.acc
            val n = (acc % 61L).toInt()
            val exists = n in r.indices
            val i = if (exists) -1L else 0L
            val l = if (exists) r[n] else 0L
            val a = (l xor (imul(2654435769L, counter + 1L) and 0xffffffffL)) and 0xffffffffL
            var d = (((acc xor a) and 0xffffffffL) or ((acc and a and i) and 0xffffffffL)) and 0xffffffffL
            d = (rotl((d + acc) and 0xffffffffL, 31L and n.toLong()) xor rotl(acc, 31L and imul(n.toLong(), 7L))) and 0xffffffffL
            acc = mix((d + 2654435769L) and 0xffffffffL)
            if (n in r.indices) r[n] = acc and 0xffffffffL
            state.acc = acc
            return acc and 0xffffffffL
        }

        private fun keystream(seed: String, mediaId: Int, len: Int): ByteArray {
            val state = buildState(seed, mediaId)
            val out = ByteArray(len)
            var counter = 0L
            var e = 0
            while (e < len) {
                val t = nextWord(state, counter++)
                out[e++] = (255L and t).toByte()
                if (e < len) out[e++] = ((t ushr 8) and 255L).toByte()
                if (e < len) out[e++] = ((t ushr 16) and 255L).toByte()
                if (e < len) out[e++] = ((t ushr 24) and 255L).toByte()
            }
            return out
        }

        private fun decryptPayload(payload: String, seed: String, mediaId: Int): String {
            val r = Base64.decode(payload, Base64.DEFAULT)
            val o = keystream(seed, mediaId, r.size)
            val decrypted = ByteArray(r.size)
            for (i in r.indices) {
                decrypted[i] = (r[i].toInt() xor o[i].toInt()).toByte()
            }
            for (i in magic.indices) {
                if (decrypted[i] != magic[i]) {
                    throw IllegalStateException("Videasy decrypt magic check failed")
                }
            }
            return String(decrypted.copyOfRange(magic.size, decrypted.size), Charsets.UTF_8)
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        try {
            val seedReq = Request.Builder()
                .url("$API_BASE/seed?mediaId=$tmdbId")
                .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val seed = httpClient.newCall(seedReq).execute().use { res ->
                if (res.isSuccessful) {
                    JSONObject(res.body?.string().orEmpty()).optString("seed")
                } else null
            } ?: return@withContext emptyList()

            for (p in providers) {
                if (sources.size >= 6) break
                val path = p["path"]!!
                val label = p["label"]!!

                val urlBuilder = "$API_BASE$path".toHttpUrl().newBuilder()
                    .addQueryParameter("title", request.title)
                    .addQueryParameter("mediaType", mediaType)
                    .addQueryParameter("tmdbId", tmdbId.toString())
                    .addQueryParameter("enc", "2")
                    .addQueryParameter("seed", seed)

                if (request.year != null) urlBuilder.addQueryParameter("year", request.year.toString())
                if (!request.imdbId.isNullOrBlank()) urlBuilder.addQueryParameter("imdbId", request.imdbId)
                if (isTv && request.season != null) urlBuilder.addQueryParameter("seasonId", request.season.toString())
                if (isTv && request.episode != null) urlBuilder.addQueryParameter("episodeId", request.episode.toString())

                try {
                    val req = Request.Builder()
                        .url(urlBuilder.build())
                        .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            var body = res.body?.string().orEmpty().trim()
                            if (body.startsWith("\"") && body.endsWith("\"")) {
                                body = body.substring(1, body.length - 1)
                            }
                            val decryptedJson = decryptPayload(body, seed, tmdbId)
                            val data = JSONObject(decryptedJson)
                            val rawSources = data.optJSONArray("sources")
                            if (rawSources != null) {
                                for (i in 0 until rawSources.length()) {
                                    val s = rawSources.getJSONObject(i)
                                    val streamUrl = s.optString("url").ifEmpty { s.optString("file") }
                                    if (streamUrl.startsWith("http")) {
                                        val q = s.optString("quality").ifEmpty { "Auto" }
                                        sources.add(
                                            ScraperStreamResult(
                                                name = "PlayTorrioHTTP",
                                                title = "Videasy $label · $q",
                                                description = "Videasy Multi-CDN HLS Stream",
                                                url = streamUrl,
                                                quality = q,
                                                headers = mapOf(
                                                    "User-Agent" to UA,
                                                    "Referer" to "https://player.videasy.to/"
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Videasy provider $label failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "VideasyScraper error: ${e.message}")
        }

        sources
    }
}
