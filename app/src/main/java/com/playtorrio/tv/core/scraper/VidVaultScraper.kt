package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin VidVault Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidVault provider.
 * Resolves direct high-speed MP4 & MKV files from vidvault.ru.
 */
class VidVaultScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidVaultScraper"
        private const val BASE_URL = "https://vidvault.ru"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL,
            "User-Agent" to UA
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

            val tokenReq = Request.Builder()
                .url("$BASE_URL/api/get-token")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val token = httpClient.newCall(tokenReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use null
                val data = JSONObject(body)
                data.optString("t").ifEmpty { null }
            } ?: return@withContext emptyList()

            val payload = JSONObject().apply {
                put("type", mediaType)
                put("tmdbId", tmdbId.toString())
                if (isTv) {
                    put("season", (request.season ?: 1).toString())
                    put("episode", (request.episode ?: 1).toString())
                }
            }

            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val dlReq = Request.Builder()
                .url("$BASE_URL/api/download-proxy")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", "application/json")
                .header("x-request-token", token)
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(dlReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val data = JSONObject(body)

                if (data.has("mp4Data")) {
                    val mp4 = data.optJSONObject("mp4Data")
                    if (mp4 != null) {
                        val lanName = mp4.optString("lanName").trim()
                        val country = mp4.optString("country").trim()
                        val detailPath = mp4.optString("detailPath").trim()

                        val dlInfo = mp4.optJSONObject("downloadInfo")
                        val downloads = dlInfo?.optJSONObject("data")?.optJSONArray("downloads")
                        if (downloads != null) {
                            for (i in 0 until downloads.length()) {
                                val d = downloads.optJSONObject(i) ?: continue
                                val dUrl = d.optString("url")
                                if (dUrl.isNotBlank() && dUrl.startsWith("http")) {
                                    val resLabel = d.optString("resolution", "1080").ifEmpty { "1080" }

                                    val titleParts = mutableListOf("VidVault", "MP4")
                                    if (lanName.isNotEmpty()) titleParts.add(lanName)
                                    if (country.isNotEmpty() && country != lanName) titleParts.add(country)
                                    titleParts.add("${resLabel}p")

                                    val descParts = mutableListOf("VidVault Direct MP4")
                                    if (lanName.isNotEmpty()) descParts.add(lanName)
                                    if (country.isNotEmpty()) descParts.add(country)
                                    if (detailPath.isNotEmpty()) descParts.add(detailPath)

                                    results.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = titleParts.joinToString(" · "),
                                            description = descParts.joinToString(" · "),
                                            url = dUrl,
                                            quality = "${resLabel}p",
                                            headers = HEADERS
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (data.has("mkvData")) {
                    val mkvFiles = data.optJSONObject("mkvData")?.optJSONArray("files")
                    if (mkvFiles != null) {
                        for (i in 0 until mkvFiles.length()) {
                            val f = mkvFiles.optJSONObject(i) ?: continue
                            val fUrl = f.optString("url")
                            if (fUrl.isNotBlank() && fUrl.startsWith("http")) {
                                val size = f.optString("size")
                                val label = if (size.isNotEmpty()) "MKV · $size" else "MKV"

                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = "VidVault · $label",
                                        description = "VidVault Direct File",
                                        url = fUrl,
                                        quality = "1080p",
                                        headers = HEADERS
                                    )
                                )
                            }
                        }
                    }
                }

                for (key in listOf("mkvV2Data", "mkvV3Data")) {
                    if (data.has(key)) {
                        val mkvExtra = data.optJSONObject(key)
                        if (mkvExtra != null) {
                            val eUrl = mkvExtra.optString("url")
                            if (eUrl.isNotBlank() && eUrl.startsWith("http")) {
                                val eQuality = mkvExtra.optString("quality").trim()
                                val eLang = mkvExtra.optString("language").trim()
                                val eCountry = mkvExtra.optString("country").trim()
                                val eSize = mkvExtra.optString("size").trim()

                                val parts = mutableListOf("VidVault", "MKV")
                                if (eLang.isNotEmpty()) parts.add(eLang)
                                if (eCountry.isNotEmpty() && eCountry != eLang) parts.add(eCountry)
                                if (eQuality.isNotEmpty()) parts.add(eQuality)
                                if (eSize.isNotEmpty()) parts.add(eSize)

                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = parts.joinToString(" · "),
                                        description = "VidVault Direct MKV · $eLang $eCountry $eSize".trim(),
                                        url = eUrl,
                                        quality = if (eQuality.isNotEmpty()) eQuality else "1080p",
                                        headers = HEADERS
                                    )
                                )
                            }
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidVault: ${e.message}")
            emptyList()
        }
    }
}
