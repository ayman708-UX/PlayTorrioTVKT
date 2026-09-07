package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin ZxcStream Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla ZxcStream provider.
 * Resolves multi-server streams (berkas, orion, aquarius, resshin) from player.zxcstream.xyz.
 */
class ZxcStreamScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "ZxcStreamScraper"
        private const val BASE_URL = "https://player.zxcstream.xyz"
        private const val AES_KEY = "7f4c9e2a81d63b05c4f7a9e8126d3b50e1a8c7f23d9465ab0c6e9f1d4a7b832c"
        private val SERVERS = listOf("berkas", "orion", "aquarius", "resshin")
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL,
            "Accept" to "application/json, text/plain, */*"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun decryptCryptoJS(ciphertextB64: String, passphrase: String): String? {
            try {
                val buf = Base64.decode(ciphertextB64, Base64.DEFAULT)
                if (buf.size < 8) return null

                val prefix = String(buf.copyOfRange(0, 8), StandardCharsets.UTF_8)
                if (prefix != "Salted__") {
                    val md5 = MessageDigest.getInstance("MD5")
                    val key = md5.digest(passphrase.toByteArray(StandardCharsets.UTF_8))
                    val iv = ByteArray(16)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                    val dec = cipher.doFinal(buf)
                    return String(dec, StandardCharsets.UTF_8)
                }

                if (buf.size < 16) return null
                val salt = buf.copyOfRange(8, 16)
                val cipherBytes = buf.copyOfRange(16, buf.size)

                val passBytes = passphrase.toByteArray(StandardCharsets.UTF_8)
                val md5 = MessageDigest.getInstance("MD5")
                var hash = ByteArray(0)
                val keyAndIv = mutableListOf<Byte>()

                while (keyAndIv.size < 48) {
                    md5.reset()
                    md5.update(hash)
                    md5.update(passBytes)
                    md5.update(salt)
                    hash = md5.digest()
                    for (b in hash) keyAndIv.add(b)
                }

                val key = ByteArray(32) { keyAndIv[it] }
                val iv = ByteArray(16) { keyAndIv[32 + it] }

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val decrypted = cipher.doFinal(cipherBytes)
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

            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            val tasks = SERVERS.map { server ->
                async {
                    val subResults = mutableListOf<ScraperStreamResult>()
                    try {
                        val tokenPayload = JSONObject().apply {
                            put("id", tmdbId)
                            put("media_type", mediaType)
                            put("path", server)
                            if (isTv) {
                                put("season", request.season ?: 1)
                                put("episode", request.episode ?: 1)
                            }
                        }

                        val tokenReq = Request.Builder()
                            .url("$BASE_URL/backend/you-are-gay")
                            .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                            .header("Content-Type", "application/json")
                            .post(tokenPayload.toString().toRequestBody(jsonMedia))
                            .build()

                        val (ts, token) = httpClient.newCall(tokenReq).execute().use { res ->
                            if (!res.isSuccessful) return@use Pair(null, null)
                            val body = res.body?.string().orEmpty()
                            if (!body.startsWith("{")) return@use Pair(null, null)
                            val data = JSONObject(body)
                            Pair(data.optString("ts"), data.optString("token").ifEmpty { null })
                        }

                        if (token == null) return@async subResults

                        val sourcesUrlBuilder = "$BASE_URL/backend_/sources/$server".toHttpUrlOrNull()?.newBuilder()
                            ?: return@async subResults
                        sourcesUrlBuilder.addQueryParameter("id", tmdbId.toString())
                        sourcesUrlBuilder.addQueryParameter("b", mediaType)
                        sourcesUrlBuilder.addQueryParameter("ts", ts.orEmpty())
                        sourcesUrlBuilder.addQueryParameter("token", token)
                        sourcesUrlBuilder.addQueryParameter("title", request.title)
                        sourcesUrlBuilder.addQueryParameter("year", (request.year ?: 2024).toString())
                        sourcesUrlBuilder.addQueryParameter("date", (request.year ?: 2024).toString())
                        if (isTv) {
                            sourcesUrlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                            sourcesUrlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
                        }

                        val sourcesReq = Request.Builder()
                            .url(sourcesUrlBuilder.build())
                            .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                            .build()

                        httpClient.newCall(sourcesReq).execute().use { res ->
                            if (res.isSuccessful) {
                                var body = res.body?.string().orEmpty()
                                if (body.isNotEmpty()) {
                                    if (!body.startsWith("{") && !body.startsWith("[")) {
                                        // Encrypted CryptoJS string
                                        val dec = decryptCryptoJS(body.trim('"', ' ', '\n', '\r'), AES_KEY)
                                        if (dec != null) body = dec
                                    }

                                    val streamUrls = mutableListOf<String>()
                                    if (body.startsWith("[")) {
                                        val arr = JSONArray(body)
                                        for (i in 0 until arr.length()) {
                                            val it = arr.optJSONObject(i) ?: continue
                                            val u = it.optString("url")
                                            if (u.isNotBlank()) streamUrls.add(u)
                                        }
                                    } else if (body.startsWith("{")) {
                                        val obj = JSONObject(body)
                                        val u = obj.optString("url")
                                        if (u.isNotBlank()) streamUrls.add(u)
                                        if (obj.has("sources")) {
                                            val arr = obj.getJSONArray("sources")
                                            for (i in 0 until arr.length()) {
                                                val it = arr.optJSONObject(i) ?: continue
                                                val su = it.optString("url")
                                                if (su.isNotBlank()) streamUrls.add(su)
                                            }
                                        }
                                    }

                                    for (u in streamUrls) {
                                        val isHls = u.contains(".m3u8")
                                        subResults.add(
                                            ScraperStreamResult(
                                                name = "PlayTorrioHTTP",
                                                title = "ZxcStream · $server · 1080p",
                                                description = "ZxcStream Stream · ${if (isHls) "HLS" else "MP4"}",
                                                url = u,
                                                quality = "1080p",
                                                headers = HEADERS
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    subResults
                }
            }

            tasks.awaitAll().flatten()
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping ZxcStream: ${e.message}")
            emptyList()
        }
    }
}
