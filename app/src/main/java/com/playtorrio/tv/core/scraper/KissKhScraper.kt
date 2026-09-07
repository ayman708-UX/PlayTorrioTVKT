package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin KissKH Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla KissKH provider.
 * Resolves Asian dramas and shows from kisskh.do via enc-dec.app decryption.
 */
class KissKhScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "KissKhScraper"
        private const val ENC_API = "https://enc-dec.app/api"
        private const val BASE_URL = "https://kisskh.do"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private fun cleanTitle(t: String): String =
            t.lowercase().replace(Regex("""\(\d{4}\)"""), "").replace(Regex("""[^a-z0-9]"""), "")

        private fun encKey(episodeId: Any, type: String): String? {
            try {
                val req = Request.Builder()
                    .url("$ENC_API/enc-kisskh?text=$episodeId&type=$type")
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val data = JSONObject(body)
                        if (data.optInt("status") == 200) {
                            return data.optString("result")
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        try {
            val targetClean = cleanTitle(request.title)
            val encodedTitle = URLEncoder.encode(request.title, "UTF-8")

            val searchReq = Request.Builder()
                .url("$BASE_URL/api/DramaList/Search?q=$encodedTitle")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            val dramaId = httpClient.newCall(searchReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("[")) return@use null
                val results = JSONArray(body)
                if (results.length() == 0) return@use null

                var bestScore = -1
                var bestId: Any? = null

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val itemTitle = item.optString("title")
                    val parts = itemTitle.split(Regex("""\s*-\s*""")).map { cleanTitle(it) }

                    var score = 0
                    if (parts.any { it == targetClean }) {
                        score += 5
                    } else if (parts.any { it.contains(targetClean) || targetClean.contains(it) }) {
                        score += 3
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestId = item.opt("id")
                    }
                }
                bestId
            } ?: return@withContext emptyList()

            val detailReq = Request.Builder()
                .url("$BASE_URL/api/DramaList/Drama/$dramaId")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            val episodeId = httpClient.newCall(detailReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val detail = JSONObject(body)
                if (!detail.has("episodes")) return@use null
                val episodes = detail.getJSONArray("episodes")
                if (episodes.length() == 0) return@use null

                val targetEpNum = request.episode ?: 1
                var matchedEp: JSONObject? = null

                for (i in 0 until episodes.length()) {
                    val ep = episodes.optJSONObject(i) ?: continue
                    if (ep.optInt("number") == targetEpNum) {
                        matchedEp = ep
                        break
                    }
                }
                if (matchedEp == null) {
                    matchedEp = episodes.optJSONObject(0)
                }

                matchedEp?.opt("id")
            } ?: return@withContext emptyList()

            val vidKey = encKey(episodeId, "vid") ?: return@withContext emptyList()

            val videoReq = Request.Builder()
                .url("$BASE_URL/api/DramaList/Episode/$episodeId.png?err=false&ts=&time=&kkey=$vidKey")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(videoReq).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use emptyList()
                val videoData = JSONObject(body)
                val streamUrl = videoData.optString("Video")
                if (streamUrl.isBlank()) return@use emptyList()

                val isHls = streamUrl.contains(".m3u8")
                val reqHeaders = mapOf(
                    "User-Agent" to UA,
                    "Referer" to "$BASE_URL/"
                )

                listOf(
                    ScraperStreamResult(
                        name = "PlayTorrioHTTP",
                        title = "KissKH · Drama · 1080p",
                        description = "KissKH Stream · ${if (isHls) "HLS" else "MP4"}",
                        url = streamUrl,
                        quality = "1080p",
                        headers = reqHeaders
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping KissKH: ${e.message}")
            emptyList()
        }
    }
}
