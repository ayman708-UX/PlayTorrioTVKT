package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin MeowTV Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla MeowTV provider.
 * Resolves streams via api.meowtv.ru and enc-dec.app decryption.
 */
class MeowTvScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MeowTvScraper"
        private const val API_BASE = "https://api.meowtv.ru"
        private const val REFERER = "https://meowtv.ru/"
        private const val ENC_DEC_API = "https://enc-dec.app/api/dec-meowtv"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "application/json",
            "Referer" to REFERER,
            "Origin" to "https://meowtv.ru",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

            val path = if (isTv) {
                "/streams/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}?s=hindiv3"
            } else {
                "/streams/movie/$tmdbId?s=hindiv3"
            }

            val req = Request.Builder()
                .url("$API_BASE$path")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val payloadStr = httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (payloadStr.isEmpty()) return@withContext emptyList()

            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val decBody = JSONObject().apply {
                if (payloadStr.startsWith("{")) {
                    put("data", JSONObject(payloadStr))
                } else if (payloadStr.startsWith("[")) {
                    put("data", JSONArray(payloadStr))
                } else {
                    put("data", payloadStr)
                }
            }

            val decReq = Request.Builder()
                .url(ENC_DEC_API)
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .post(decBody.toString().toRequestBody(jsonMedia))
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(decReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val decJson = JSONObject(body)
                if (decJson.optInt("status") != 200) return@use

                val resultObj = decJson.opt("result")
                val finalData: JSONObject? = when (resultObj) {
                    is JSONObject -> resultObj
                    is String -> if (resultObj.startsWith("{")) JSONObject(resultObj) else null
                    else -> null
                }

                if (finalData == null) return@use

                val urls = mutableSetOf<String>()
                val directUrl = finalData.optString("url")
                if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                    urls.add(directUrl)
                }

                if (finalData.has("streams")) {
                    val streamsArr = finalData.optJSONArray("streams")
                    if (streamsArr != null) {
                        for (i in 0 until streamsArr.length()) {
                            val st = streamsArr.optJSONObject(i) ?: continue
                            val sUrl = st.optString("url")
                            if (sUrl.isNotBlank() && sUrl.startsWith("http")) {
                                urls.add(sUrl)
                            }
                        }
                    }
                }

                val reqHeaders = mapOf(
                    "User-Agent" to UA,
                    "Referer" to REFERER,
                    "Origin" to "https://meowtv.ru"
                )

                for (u in urls) {
                    val isHls = u.contains(".m3u8")
                    results.add(
                        ScraperStreamResult(
                            name = "PlayTorrioHTTP",
                            title = "MeowTV · Hindiv3 · 1080p",
                            description = "MeowTV Stream · ${if (isHls) "HLS" else "MP4"}",
                            url = u,
                            quality = "1080p",
                            headers = reqHeaders
                        )
                    )
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping MeowTV: ${e.message}")
            emptyList()
        }
    }
}
