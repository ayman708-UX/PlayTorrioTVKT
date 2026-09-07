package com.playtorrio.tv.core.scraper

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
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin Mapple Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla Mapple provider.
 * Solves SHA-256 Proof-of-Work and resolves multi-server HLS streams from mapple.club.
 */
class MappleScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "MappleScraper"
        private const val BASE_URL = "https://mapple.club"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val SERVERS = listOf(
            Pair("mapple", "Mapple"),
            Pair("s1", "Nexus"),
            Pair("s2", "Cipher"),
            Pair("s3", "Pulse"),
            Pair("s4", "Vertex"),
            Pair("s10", "Chimp")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun solvePoW(challenge: String, difficulty: Int): String? {
            val maskBytes = difficulty / 8
            val maskBits = difficulty % 8
            val finalMask = if (maskBits > 0) ((0xFF shl (8 - maskBits)) and 0xFF) else 0

            val md = MessageDigest.getInstance("SHA-256")
            val challengeBytes = challenge.toByteArray(StandardCharsets.UTF_8)

            for (nonce in 0 until 1000000) {
                val nonceStr = nonce.toString()
                val nonceBytes = nonceStr.toByteArray(StandardCharsets.UTF_8)
                val combined = ByteArray(challengeBytes.size + nonceBytes.size)
                System.arraycopy(challengeBytes, 0, combined, 0, challengeBytes.size)
                System.arraycopy(nonceBytes, 0, combined, challengeBytes.size, nonceBytes.size)

                val digest = md.digest(combined)
                var ok = true
                for (i in 0 until maskBytes) {
                    if (digest[i] != 0.toByte()) {
                        ok = false
                        break
                    }
                }
                if (ok && (finalMask == 0 || ((digest[maskBytes].toInt() and 0xFF) and finalMask) == 0)) {
                    return nonceStr
                }
            }
            return null
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

            val pageUrl = if (isTv) {
                "$BASE_URL/watch/tv/$tmdbId/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$BASE_URL/watch/movie/$tmdbId"
            }

            val pageReq = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", UA)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .build()

            var requestToken: String? = null
            var cookieHeader: String? = null

            httpClient.newCall(pageReq).execute().use { res ->
                if (res.isSuccessful) {
                    val html = res.body?.string().orEmpty()
                    val m = Regex("""window\.__REQUEST_TOKEN__\s*=\s*"([^"]+)"""").find(html)
                    requestToken = m?.groupValues?.get(1)

                    val setCookieHeaders = res.headers("set-cookie")
                    val cookieParts = mutableListOf<String>()
                    val reg = Regex("""(_mapple_site(?:_partitioned)?=[^;]+)""")
                    for (sc in setCookieHeaders) {
                        for (cm in reg.findAll(sc)) {
                            cookieParts.add(cm.groupValues[1])
                        }
                    }
                    if (cookieParts.isNotEmpty()) {
                        cookieHeader = cookieParts.joinToString("; ")
                    }
                }
            }

            if (requestToken == null) return@withContext emptyList()

            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            val initPayload = JSONObject().apply {
                put("mediaId", tmdbId)
                put("mediaType", mediaType)
                put("requestToken", requestToken)
            }

            val initReqBuilder = Request.Builder()
                .url("$BASE_URL/api/playback-init")
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .header("Referer", pageUrl)
                .header("Origin", BASE_URL)
                .post(initPayload.toString().toRequestBody(jsonMedia))

            if (!cookieHeader.isNullOrEmpty()) {
                initReqBuilder.header("Cookie", cookieHeader!!)
            }

            var streamToken: String? = null

            httpClient.newCall(initReqBuilder.build()).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    if (body.startsWith("{")) {
                        val initData = JSONObject(body)
                        streamToken = initData.optString("token").ifEmpty { null }

                        if (initData.optBoolean("requiresPow") && initData.has("pow")) {
                            val powInfo = initData.getJSONObject("pow")
                            val challenge = powInfo.optString("challenge")
                            val difficulty = powInfo.optInt("difficulty", 10)
                            val challengeId = powInfo.opt("challengeId")

                            val nonce = solvePoW(challenge, difficulty)
                            if (nonce != null) {
                                val solvePayload = JSONObject().apply {
                                    put("mediaId", tmdbId)
                                    put("mediaType", mediaType)
                                    put("requestToken", requestToken)
                                    put("pow", JSONObject().apply {
                                        put("challengeId", challengeId)
                                        put("nonce", nonce)
                                    })
                                }

                                val solveReq = Request.Builder()
                                    .url("$BASE_URL/api/playback-init")
                                    .header("Content-Type", "application/json")
                                    .header("User-Agent", UA)
                                    .header("Referer", pageUrl)
                                    .header("Origin", BASE_URL)
                                    .apply {
                                        if (!cookieHeader.isNullOrEmpty()) header("Cookie", cookieHeader!!)
                                    }
                                    .post(solvePayload.toString().toRequestBody(jsonMedia))
                                    .build()

                                httpClient.newCall(solveReq).execute().use { sRes ->
                                    if (sRes.isSuccessful) {
                                        val sBody = sRes.body?.string().orEmpty()
                                        if (sBody.startsWith("{")) {
                                            val sData = JSONObject(sBody)
                                            val t = sData.optString("token")
                                            if (t.isNotBlank()) streamToken = t
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (streamToken == null) return@withContext emptyList()

            val tvSlug = if (isTv) "${request.season ?: 1}-${request.episode ?: 1}" else ""

            val tasks = SERVERS.map { (sId, sName) ->
                async {
                    try {
                        val encPayload = JSONObject().apply {
                            put("data", JSONObject().apply {
                                put("mediaId", tmdbId)
                                put("mediaType", mediaType)
                                put("tv_slug", tvSlug)
                                put("source", sId)
                                put("apikey", "mptv_sk_a8f29c4e7b3d1f")
                            })
                        }

                        val encReq = Request.Builder()
                            .url("$BASE_URL/api/encrypt")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", UA)
                            .header("Referer", pageUrl)
                            .header("Origin", BASE_URL)
                            .apply {
                                if (!cookieHeader.isNullOrEmpty()) header("Cookie", cookieHeader!!)
                            }
                            .post(encPayload.toString().toRequestBody(jsonMedia))
                            .build()

                        val encrypted = httpClient.newCall(encReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    JSONObject(body).optString("encrypted")
                                } else null
                            } else null
                        } ?: return@async null

                        val streamUrlBuilder = "$BASE_URL/api/stream-encrypted".toHttpUrlOrNull()?.newBuilder()
                            ?: return@async null
                        streamUrlBuilder.addQueryParameter("data", encrypted)
                        streamUrlBuilder.addQueryParameter("requestToken", requestToken!!)
                        streamUrlBuilder.addQueryParameter("token", streamToken!!)

                        val streamReq = Request.Builder()
                            .url(streamUrlBuilder.build())
                            .header("User-Agent", UA)
                            .header("Referer", pageUrl)
                            .header("Origin", BASE_URL)
                            .apply {
                                if (!cookieHeader.isNullOrEmpty()) header("Cookie", cookieHeader!!)
                            }
                            .build()

                        httpClient.newCall(streamReq).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                if (body.startsWith("{")) {
                                    val data = JSONObject(body)
                                    if (data.optBoolean("success") && data.has("data")) {
                                        var fileUrl = data.getJSONObject("data").optString("stream_url")
                                        if (fileUrl.isNotBlank()) {
                                            if (fileUrl.contains("omena-puu") || fileUrl.contains("nocach")) {
                                                fileUrl += if (fileUrl.contains("?")) "&format=.m3u8" else "?format=.m3u8"
                                            }

                                            val reqHeaders = mapOf(
                                                "User-Agent" to UA,
                                                "Referer" to "$BASE_URL/",
                                                "Origin" to BASE_URL
                                            )

                                            return@async ScraperStreamResult(
                                                name = "PlayTorrioHTTP",
                                                title = "Mapple · $sName · 1080p",
                                                description = "Mapple $sName HLS Stream",
                                                url = fileUrl,
                                                quality = "1080p",
                                                headers = reqHeaders
                                            )
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
            Log.d(TAG, "Error scraping Mapple: ${e.message}")
            emptyList()
        }
    }
}
