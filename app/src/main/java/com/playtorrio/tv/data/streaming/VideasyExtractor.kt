package com.playtorrio.tv.data.streaming

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object VideasyExtractor {
    private const val TAG = "VideasyExtractor"
    private const val API_KEY = "b3556f3b206e16f82df4d1f6fd4545e6"
    private const val API_BASE = "https://api.speedracelight.com"
    private const val TMDB_DIRECT = "https://api.themoviedb.org/3"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val DEFAULT_HEADERS = mapOf(
        "User-Agent" to UA,
        "Referer" to "https://player.videasy.to/",
        "Origin" to "https://player.videasy.to",
        "Accept" to "application/json, text/plain, */*"
    )

    private val PROVIDERS = listOf(
        Pair("/cdn/sources-with-title", "Yoru"),
        Pair("/neon2/sources-with-title", "Neon"),
        Pair("/m4uhd/sources-with-title", "Breach"),
        Pair("/meine/sources-with-title", "Killjoy"),
        Pair("/lamovie/sources-with-title", "Omen")
    )

    private val F_TABLE = longArrayOf(
        1116352408L, 1899447441L, 3049323471L, 3921009573L, 961987163L, 1508970993L,
        2453635748L, 2870763221L, 3624381080L, 310598401L, 607225278L, 1426881987L,
        1925078388L, 2162078206L, 2614888103L, 3248222580L
    )

    private val MAGIC = intArrayOf(109, 118, 109, 49) // "mvm1"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun imul(a: Long, b: Long): Long {
        val ah = (a ushr 16) and 0xffffL
        val al = a and 0xffffL
        val bh = (b ushr 16) and 0xffffL
        val bl = b and 0xffffL
        return ((al * bl) + (((ah * bl + al * bh) and 0xffffL) shl 16)) and 0xffffffffL
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

    private fun rotl(eIn: Long, tIn: Long): Long {
        val e = eIn and 0xffffffffL
        val t = (tIn and 31L).toInt()
        if (t == 0) return e
        return (((e shl t) and 0xffffffffL) or (e ushr (32 - t))) and 0xffffffffL
    }

    private fun fnv1a(e: String): Long {
        var t = 2166136261L
        for (i in 0 until e.length) {
            t = imul(t xor e[i].code.toLong(), 16777619L) and 0xffffffffL
        }
        return mix(t)
    }

    private fun accSeed(e: String): Long {
        var t = 1732584193L
        for (s in 0 until e.length) {
            val code = e[s].code.toLong()
            val fVal = F_TABLE[(15 and s)]
            t = rotl((t xor imul(code, fVal)) and 0xffffffffL, 5)
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

    private class VideasyState(
        var s: LongArray,
        var acc: Long
    )

    private fun buildState(seed: String, mediaId: Int): VideasyState {
        if (isOddTri(seed.length.toLong())) {
            return VideasyState(rc4Sbox(seed), accSeed(seed))
        }
        val s = LongArray(61) { 0L }
        val mediaLong = mediaId.toLong() and 0xffffffffL
        var a = mix(fnv1a(seed) xor mix((mediaLong and 0xffffffffL) xor 2654435769L)) and 0xffffffffL
        for (e in 0 until 8) {
            if (isEvenTri(e.toLong())) {
                val t = (a % 61L).toInt()
                a = rotl((a + 2654435769L) and 0xffffffffL, (7 + (7 and e)).toLong())
                s[t] = (a xor mix(a)) and 0xffffffffL
                a = mix((a + t.toLong()) and 0xffffffffL)
            } else {
                s[e] = F_TABLE[(15 and e)]
            }
        }
        return VideasyState(s, mix(2779096485L xor a) and 0xffffffffL)
    }

    private fun nextWord(state: VideasyState, counter: Int): Long {
        val r = state.s
        var acc = state.acc
        val n = (acc % 61L).toInt()
        val exists = n < r.size && r[n] != 0L
        val i = if (exists) -1L else 0L
        val l = (if (exists) r[n] else 0L) and 0xffffffffL
        val a = (l xor (imul(2654435769L, (counter + 1).toLong()) and 0xffffffffL)) and 0xffffffffL
        var d = (((acc xor a) and 0xffffffffL) or (((acc and a and i) and 0xffffffffL))) and 0xffffffffL
        d = (rotl((d + acc) and 0xffffffffL, (31L and acc.toLong())) xor rotl(acc, (31L and imul(n.toLong(), 7L)))) and 0xffffffffL
        acc = mix((d + 2654435769L) and 0xffffffffL)
        if (n < r.size) r[n] = acc and 0xffffffffL
        state.acc = acc
        return acc and 0xffffffffL
    }

    private fun keystream(seed: String, mediaId: Int, len: Int): ByteArray {
        val state = buildState(seed, mediaId)
        val out = ByteArray(len)
        var counter = 0
        var e = 0
        while (e < len) {
            val t = nextWord(state, counter++)
            out[e++] = (t and 0xffL).toByte()
            if (e < len) out[e++] = ((t ushr 8) and 0xffL).toByte()
            if (e < len) out[e++] = ((t ushr 16) and 0xffL).toByte()
            if (e < len) out[e++] = ((t ushr 24) and 0xffL).toByte()
        }
        return out
    }

    private fun decryptPayload(payload: String, seed: String, mediaId: Int): String {
        var normalized = payload.replace('-', '+').replace('_', '/')
        while (normalized.length % 4 != 0) {
            normalized += "="
        }
        val r = Base64.decode(normalized, Base64.DEFAULT)
        val o = keystream(seed, mediaId, r.size)
        val decrypted = ByteArray(r.size)
        for (i in r.indices) {
            decrypted[i] = (r[i].toInt() xor o[i].toInt()).toByte()
        }
        for (i in MAGIC.indices) {
            if ((decrypted[i].toInt() and 0xff) != MAGIC[i]) {
                throw Exception("Videasy decrypt magic check failed")
            }
        }
        return String(decrypted, MAGIC.size, decrypted.size - MAGIC.size, StandardCharsets.UTF_8)
    }

    private fun getSeed(mediaId: Int): String? {
        return try {
            val req = Request.Builder()
                .url("$API_BASE/seed?mediaId=$mediaId")
                .header("User-Agent", UA)
                .header("Referer", "https://player.videasy.to/")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                Log.i(TAG, "Videasy getSeed response code: ${resp.code}")
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    json.optString("seed").ifEmpty { null }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Videasy getSeed error: ${e.message}")
            null
        }
    }

    suspend fun extract(
        title: String,
        isMovie: Boolean,
        year: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        onStreamFound: (HttpStreamResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val resolvedTmdbId = tmdbId ?: TmdbIdResolver.resolveTmdbId(
                imdbId = imdbId,
                title = title,
                isMovie = isMovie,
                year = year
            ) ?: return@withContext

            val seed = getSeed(resolvedTmdbId)
            Log.i(TAG, "Videasy resolved seed: $seed for tmdbId: $resolvedTmdbId")
            if (seed == null) return@withContext

            var mediaTitle = title
            var mediaYear = year
            var targetImdb = imdbId ?: ""

            // Quick TMDB direct metadata lookup
            try {
                val metaPath = if (isMovie) "/movie/$resolvedTmdbId?api_key=$API_KEY" else "/tv/$resolvedTmdbId?api_key=$API_KEY"
                val req = Request.Builder().url("$TMDB_DIRECT$metaPath").build()
                httpClient.newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        val body = r.body?.string() ?: ""
                        val meta = JSONObject(body)
                        mediaTitle = meta.optString("title").ifEmpty { meta.optString("name").ifEmpty { title } }
                        val yStr = meta.optString("release_date").ifEmpty { meta.optString("first_air_date") }
                        if (yStr.length >= 4) mediaYear = yStr.substring(0, 4).toIntOrNull() ?: year
                        if (meta.has("imdb_id")) targetImdb = meta.optString("imdb_id")
                    }
                }
            } catch (_: Exception) {}

            val baseParams = mutableMapOf(
                "title" to mediaTitle,
                "mediaType" to if (isMovie) "movie" else "tv",
                "tmdbId" to resolvedTmdbId.toString(),
                "enc" to "2",
                "seed" to seed
            )
            if (mediaYear != null) baseParams["year"] = mediaYear.toString()
            if (targetImdb.isNotBlank()) baseParams["imdbId"] = targetImdb
            if (!isMovie) {
                if (seasonNumber != null) baseParams["seasonId"] = seasonNumber.toString()
                if (episodeNumber != null) baseParams["episodeId"] = episodeNumber.toString()
            }

            var emittedCount = 0
            for ((path, label) in PROVIDERS) {
                if (emittedCount >= 8) break
                try {
                    val urlBuilder = "$API_BASE$path".toHttpUrlOrNull()?.newBuilder() ?: continue
                    for ((k, v) in baseParams) {
                        urlBuilder.addQueryParameter(k, v)
                    }

                    val reqBuilder = Request.Builder().url(urlBuilder.build())
                    for ((k, v) in DEFAULT_HEADERS) {
                        reqBuilder.header(k, v)
                    }

                    val rawBody = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string()?.trim() else null
                    } ?: continue

                    var body = rawBody
                    if (body.startsWith("\"") && body.endsWith("\"")) {
                        body = JSONObject("{\"v\":$body}").optString("v")
                    }

                    val decryptedJson = decryptPayload(body, seed, resolvedTmdbId)
                    val data = JSONObject(decryptedJson)
                    val rawSources = data.optJSONArray("sources") ?: continue
                    Log.i(TAG, "Videasy provider $label returned ${rawSources.length()} sources")

                    for (sIdx in 0 until rawSources.length()) {
                        val s = rawSources.optJSONObject(sIdx) ?: continue
                        val streamUrl = s.optString("url").ifEmpty { s.optString("file") }
                        if (streamUrl.isBlank() || !streamUrl.startsWith("http")) continue

                        val q = s.optString("quality").ifEmpty { "Auto" }
                        val stream = HttpStreamResult(
                            sourceName = "Videasy ($label)",
                            title = "Videasy $label · $q",
                            description = "Videasy Multi-CDN HLS Stream",
                            url = streamUrl,
                            headers = mapOf(
                                "User-Agent" to UA,
                                "Referer" to "https://player.videasy.to/"
                            ),
                            quality = q
                        )
                        onStreamFound(stream)
                        emittedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Videasy provider $label error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Videasy extraction failed for $title", e)
        }
    }
}
