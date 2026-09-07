package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin CineSu Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla CineSu provider.
 * Resolves direct master HLS from cine.su.
 */
class CineSuScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "CineSuScraper"
        private const val BASE_DOMAIN = "https://glendale-plumbing.com"
        private const val REFERER = "https://cine.su/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val N_D = "4860ac8bfddb"
        private const val A_D = "224eff10e662e9635c9f671cf46351dcd69af42b1edd56f5e5fa21751f44b9c8"
        private val LS = intArrayOf(17, 91, 203, 44, 8, 177, 62, 239, 119, 3, 154, 81, 28, 210, 101, 7)
        private const val WA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

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
            val sb = java.lang.StringBuilder()
            var r = 0
            while (r < e.size) {
                val n = e[r]
                val a = if (r + 1 < e.size) e[r + 1] else null
                val s = if (r + 2 < e.size) e[r + 2] else null

                sb.append(WA[n ushr 2])
                sb.append(WA[((3 and n) shl 4) or ((a ?: 0) ushr 4)])
                if (a == null) break
                sb.append(WA[((15 and a) shl 2) or ((s ?: 0) ushr 6)])
                if (s == null) break
                sb.append(WA[63 and s])
                r += 3
            }
            return sb.toString()
        }

        fun generateMasterUrl(tmdbId: Int, s: Int?, e: Int?): String {
            val isTv = s != null && e != null
            val season = if (isTv) s else 0
            val episode = if (isTv) e else 0

            val str = "$N_D:${if (isTv) "s" else "m"}:$tmdbId:$season:$episode"
            val a = str.toByteArray(StandardCharsets.UTF_8)
            val sArr = sD(a.size)
            val i = IntArray(a.size + 2)
            i[0] = a.size and 255
            i[1] = (a.size ushr 8) and 255
            var o = (2654435769L xor a.size.toLong()) and 0xFFFFFFFFL

            for (l in a.indices) {
                o = ab((o + sArr[l % sArr.size] + LS[l % LS.size] + l) and 0xFFFFFFFFL)
                i[l + 2] = ((a[l].toInt() and 0xFF) xor (o and 255L).toInt()) xor sArr[(7 * l + 3) % sArr.size]
            }

            return "$BASE_DOMAIN/c/v1/${iD(i)}/master.m3u8"
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val mediaType = if (isTv) "tv" else "movie"

        try {
            val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                imdbId = request.imdbId,
                title = request.title,
                type = mediaType,
                year = request.year
            ) ?: return@withContext emptyList()

            val streamUrl = generateMasterUrl(tmdbId, request.season, request.episode)

            val headers = mapOf(
                "User-Agent" to UA,
                "Referer" to REFERER,
                "Origin" to "https://cine.su"
            )

            listOf(
                ScraperStreamResult(
                    name = "PlayTorrioHTTP",
                    title = "CineSu · Direct Master · 1080p",
                    description = "CineSu Master HLS Stream",
                    url = streamUrl,
                    quality = "1080p",
                    headers = headers
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping CineSu: ${e.message}")
            emptyList()
        }
    }
}
