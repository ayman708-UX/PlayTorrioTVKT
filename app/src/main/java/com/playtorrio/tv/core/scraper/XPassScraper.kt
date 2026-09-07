package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin XPass Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla XPass provider.
 * Resolves multi-server streams from play.xpass.top.
 */
class XPassScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "XPassScraper"
        private const val BASE_URL = "https://play.xpass.top"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "Accept" to "*/*",
            "User-Agent" to UA,
            "Origin" to BASE_URL,
            "Referer" to "$BASE_URL/",
            "Cookie" to "auth_token=de21073d24bca9b50f189b402ac870734cf945f2085cb7e1a4fc453fcfe4f57e"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
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

            val refererUrl = "$BASE_URL/e/${if (isTv) "tv" else "movie"}/$tmdbId?autostart=true"
            val reqHeaders = HashMap(HEADERS).apply {
                put("Referer", refererUrl)
            }

            var sources: JSONArray? = null

            if (!isTv) {
                val req = Request.Builder()
                    .url(refererUrl)
                    .apply { reqHeaders.forEach { (k, v) -> header(k, v) } }
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val m = Regex("""var backups=(\[[\s\S]*?\])""").find(body)
                        if (m != null) {
                            try {
                                sources = JSONArray(m.groupValues[1])
                            } catch (_: Exception) {}
                        }
                    }
                }
            } else {
                val tvDataUrl = "$BASE_URL/data/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}?autostart=true&force=true"
                val req = Request.Builder()
                    .url(tvDataUrl)
                    .apply { reqHeaders.forEach { (k, v) -> header(k, v) } }
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        if (body.startsWith("[")) {
                            try {
                                sources = JSONArray(body)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            if (sources == null || sources!!.length() == 0) return@withContext emptyList()

            val tasks = (0 until sources!!.length()).map { i ->
                async {
                    try {
                        val src = sources!!.optJSONObject(i) ?: return@async null
                        val sUrl = src.optString("url")
                        val sName = src.optString("name", "Server").ifEmpty { "Server" }
                        if (sUrl.isEmpty()) return@async null

                        val mReq = Request.Builder()
                            .url("$BASE_URL$sUrl")
                            .apply { reqHeaders.forEach { (k, v) -> header(k, v) } }
                            .build()

                        httpClient.newCall(mReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val mdata = JSONObject(body)
                                    if (mdata.has("playlist")) {
                                        val pl = mdata.getJSONArray("playlist")
                                        if (pl.length() > 0) {
                                            val pl0 = pl.optJSONObject(0)
                                            if (pl0 != null && pl0.has("sources")) {
                                                val sList = pl0.getJSONArray("sources")
                                                var target: JSONObject? = null
                                                for (j in 0 until sList.length()) {
                                                    val item = sList.optJSONObject(j) ?: continue
                                                    if (item.optString("type") == "hls") {
                                                        target = item
                                                        break
                                                    }
                                                }
                                                if (target == null && sList.length() > 0) {
                                                    target = sList.optJSONObject(0)
                                                }

                                                if (target != null) {
                                                    val streamUrl = target.optString("file")
                                                    if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                                        val isHls = target.optString("type") == "hls" || streamUrl.contains(".m3u8")
                                                        val finalHeaders = mapOf(
                                                            "Origin" to BASE_URL,
                                                            "Referer" to BASE_URL,
                                                            "User-Agent" to UA
                                                        )

                                                        return@async ScraperStreamResult(
                                                            name = "PlayTorrioHTTP",
                                                            title = "XPass · $sName · 1080p",
                                                            description = "XPass Stream · ${if (isHls) "HLS" else "MP4"}",
                                                            url = streamUrl,
                                                            quality = "1080p",
                                                            headers = finalHeaders
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            tasks.awaitAll().filterNotNull()
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping XPass: ${e.message}")
            emptyList()
        }
    }
}
