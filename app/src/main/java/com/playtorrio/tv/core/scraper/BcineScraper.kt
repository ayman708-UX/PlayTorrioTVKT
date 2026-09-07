package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class BcineScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "BcineScraper"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val V_PARAM = "_v=34403446"
        private const val N_D = "4860ac8bfddb"
        private const val A_D = "224eff10e662e9635c9f671cf46351dcd69af42b1edd56f5e5fa21751f44b9c8"
        private val LS = intArrayOf(17, 91, 203, 44, 8, 177, 62, 239, 119, 3, 154, 81, 28, 210, 101, 7)
        private const val WA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        private val SERVERS = listOf(
            Triple("NIGHT", "/server/night", "Night"),
            Triple("EMP", "/server/emp", "Empire"),
            Triple("MAIN", "/server/vidsrc", "VidSrc")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        private fun ab(e: Long): Long {
            var t = e and 0xFFFFFFFFL
            t = t xor (t ushr 16)
            t = (t * 2146121005L) and 0xFFFFFFFFL
            t = t xor (t ushr 15)
            t = (t * 2221713035L) and 0xFFFFFFFFL
            return (t xor (t ushr 16)) and 0xFFFFFFFFL
        }

        private fun sD(e: Int): IntArray {
            val t = A_D.toByteArray(StandardCharsets.UTF_8)
            val r = min(max(e + 17, 32), 128)
            val n = IntArray(r)
            var a = 2166136261L
            for (s in 0 until r) {
                a = a xor (t[s % t.size].toLong() and 0xFFL)
                a = ab((a + LS[s % LS.size] + ((2654435761L * s) and 0xFFFFFFFFL)) and 0xFFFFFFFFL)
                n[s] = (a and 255L).toInt()
            }
            return n
        }

        private fun iD(e: IntArray): String {
            val t = StringBuilder()
            var r = 0
            while (r < e.size) {
                val n = e[r]
                val a = if (r + 1 < e.size) e[r + 1] else null
                val s = if (r + 2 < e.size) e[r + 2] else null
                t.append(WA[n ushr 2])
                t.append(WA[((3 and n) shl 4) or ((a ?: 0) ushr 4)])
                if (a == null) break
                t.append(WA[((15 and a) shl 2) or ((s ?: 0) ushr 6)])
                if (s == null) break
                t.append(WA[63 and s])
                r += 3
            }
            return t.toString()
        }

        private fun generateDirectHlsUrl(tmdbId: Int, s: Int?, e: Int?): String {
            val isTv = s != null && e != null
            val season = if (isTv) s else 0
            val episode = if (isTv) e else 0

            val str = "$N_D:${if (isTv) 's' else 'm'}:$tmdbId:$season:$episode"
            val a = str.toByteArray(StandardCharsets.UTF_8)
            val sArr = sD(a.size)
            val i = IntArray(a.size + 2)
            i[0] = a.size and 255
            i[1] = (a.size ushr 8) and 255
            var o = (2654435769L xor a.size.toLong()) and 0xFFFFFFFFL
            for (l in a.indices) {
                val byteVal = a[l].toInt() and 0xFF
                o = ab((o + sArr[l % sArr.size] + LS[l % LS.size] + l) and 0xFFFFFFFFL)
                i[l + 2] = ((byteVal xor (255 and o.toInt())) xor sArr[(7 * l + 3) % sArr.size]) and 255
            }

            return "https://glendale-plumbing.com/c/v1/${iD(i)}/master.m3u8"
        }

        private fun fetchInternalToken(): String? {
            return try {
                val req = Request.Builder()
                    .url("https://1embed.cc/api/token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://1embed.cc/")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val json = JSONObject(body)
                        json.optString("token").takeIf { it.isNotBlank() }
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        try {
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = if (isTv) "tv" else "movie",
                year = request.year
            ) ?: return@withContext emptyList()

            // 1. Direct Cryptographic HLS Master Stream
            val directHls = "${generateDirectHlsUrl(tmdbId, request.season, request.episode)}?$V_PARAM"
            val directHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://bcine.ru/",
                "Origin" to "https://bcine.ru"
            )

            results.add(
                ScraperStreamResult(
                    name = "PlayTorrioHTTP",
                    title = "Bcine · Direct Master · 1080p",
                    description = "Bcine Direct Master HLS Stream",
                    url = directHls,
                    quality = "1080p",
                    headers = directHeaders
                )
            )

            // 2. Multi-server streams
            val token = fetchInternalToken()
            if (!token.isNullOrBlank()) {
                val query = if (isTv) {
                    "id=$tmdbId?type=tv&s=${request.season ?: 1}&e=${request.episode ?: 1}"
                } else {
                    "id=$tmdbId?type=movie"
                }

                for ((_, endpoint, srvName) in SERVERS) {
                    try {
                        val req = Request.Builder()
                            .url("https://1embed.cc$endpoint?$query")
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Referer", "https://1embed.cc/")
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("Accept", "application/json, text/plain, */*")
                            .build()

                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                val json = JSONObject(body)
                                val sUrl = json.optString("url")
                                if (sUrl.contains(".m3u8")) {
                                    results.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = "Bcine · $srvName · 1080p",
                                            description = "Bcine $srvName HLS Stream",
                                            url = sUrl,
                                            quality = "1080p",
                                            headers = mapOf(
                                                "User-Agent" to USER_AGENT,
                                                "Referer" to "https://1embed.cc/"
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Bcine server $srvName error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bcine error: ${e.message}")
        }

        results
    }
}
