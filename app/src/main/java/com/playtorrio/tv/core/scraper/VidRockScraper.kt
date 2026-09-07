package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin VidRock Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla VidRock provider.
 * Resolves AES-GCM encrypted streams from vidrock.ru.
 */
class VidRockScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VidRockScraper"
        private const val GCM_HEX_KEY = "7f3e9c2a8b5d1f4e6a9c3b7d2e5f8a1c4b6d9e2f5a8c1b4d7e9f2a5c8b1d4e7f"
        private const val BASE_URL = "https://vidrock.ru/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        private fun decryptStreamUrl(value: String): String? {
            try {
                val data = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                if (data.size < 28) return null

                val iv = data.copyOfRange(0, 12)
                val ciphertext = data.copyOfRange(12, data.size)

                val keyBytes = hexToBytes(GCM_HEX_KEY)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)

                val decrypted = cipher.doFinal(ciphertext)
                return String(decrypted, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
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

            val path = if (isTv) {
                "tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "movie/$tmdbId"
            }

            val req = Request.Builder()
                .url("${BASE_URL}api/$path")
                .header("User-Agent", UA)
                .header("Referer", BASE_URL)
                .header("Origin", BASE_URL)
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val data = JSONObject(body)

                val keys = data.keys()
                while (keys.hasNext()) {
                    val provider = keys.next()
                    val info = data.opt(provider) ?: continue

                    var streamUrl: String? = null
                    if (info is String) {
                        streamUrl = if (info.startsWith("http")) info else decryptStreamUrl(info)
                    } else if (info is JSONObject) {
                        val raw = info.optString("url").ifEmpty {
                            info.optString("stream").ifEmpty {
                                info.optString("file")
                            }
                        }
                        if (raw.isNotBlank()) {
                            streamUrl = if (raw.startsWith("http")) raw else decryptStreamUrl(raw)
                        }
                    }

                    if (!streamUrl.isNullOrEmpty()) {
                        if (streamUrl.contains("/playlist/")) {
                            try {
                                val pReq = Request.Builder()
                                    .url(streamUrl)
                                    .header("User-Agent", UA)
                                    .header("Referer", BASE_URL)
                                    .build()

                                httpClient.newCall(pReq).execute().use { pRes ->
                                    if (pRes.isSuccessful) {
                                        val pBody = pRes.body?.string()?.trim().orEmpty()
                                        if (pBody.startsWith("[")) {
                                            val pList = JSONArray(pBody)
                                            for (i in 0 until pList.length()) {
                                                val item = pList.optJSONObject(i) ?: continue
                                                val rawItemUrl = item.optString("url")
                                                if (rawItemUrl.isNotBlank() && rawItemUrl.startsWith("http")) {
                                                    val resVal = item.optString("resolution", "1080").ifEmpty { "1080" }
                                                    val isHls = rawItemUrl.contains(".m3u8")
                                                    val reqHeaders = mapOf(
                                                        "User-Agent" to UA,
                                                        "Referer" to BASE_URL
                                                    )

                                                    results.add(
                                                        ScraperStreamResult(
                                                            name = "PlayTorrioHTTP",
                                                            title = "VidRock · $provider · ${resVal}p",
                                                            description = "VidRock Stream · ${if (isHls) "HLS" else "MP4"}",
                                                            url = rawItemUrl,
                                                            quality = "${resVal}p",
                                                            headers = reqHeaders
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                continue
                            } catch (_: Exception) {}
                        }

                        val isHls = streamUrl.contains(".m3u8")
                        val reqHeaders = mapOf(
                            "User-Agent" to UA,
                            "Referer" to BASE_URL
                        )

                        results.add(
                            ScraperStreamResult(
                                name = "PlayTorrioHTTP",
                                title = "VidRock · $provider · 1080p",
                                description = "VidRock Stream · ${if (isHls) "HLS" else "MP4"}",
                                url = streamUrl,
                                quality = "1080p",
                                headers = reqHeaders
                            )
                        )
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping VidRock: ${e.message}")
            emptyList()
        }
    }
}
