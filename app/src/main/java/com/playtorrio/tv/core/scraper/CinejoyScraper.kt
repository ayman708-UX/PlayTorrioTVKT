package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigInteger
import java.security.*
import java.security.spec.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CinejoyScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "CinejoyScraper"
        private const val API_BASE = "https://api.shegu.st"
        private const val ORIGIN = "https://cinejoy.to"
        private const val REFERER = "https://cinejoy.to/"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Origin" to ORIGIN,
            "Referer" to REFERER,
            "Accept" to "application/json, text/plain, */*"
        )

        private val fallbackServers = listOf(
            mapOf("name" to "Lisbon", "4k" to true, "status" to "ok"),
            mapOf("name" to "Solara", "4k" to false, "status" to "ok"),
            mapOf("name" to "Athens", "4k" to false, "status" to "ok"),
            mapOf("name" to "Castle", "4k" to false, "status" to "ok"),
            mapOf("name" to "Canaias", "4k" to false, "status" to "ok")
        )

        private const val SERVER_PUB_KEY_HEX =
            "0483c7a82132b8516e3eb4061b82e9c881cc585593a4709001131bff7443eabc1701c1f0d50e23ac02b0b9a5979903dbd7e9055aab5e4a5532132d1d200707f5f2"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        private val serverPublicKey: PublicKey by lazy {
            val pubBytes = hexToBytes(SERVER_PUB_KEY_HEX)
            val xBytes = pubBytes.copyOfRange(1, 33)
            val yBytes = pubBytes.copyOfRange(33, 65)
            val x = BigInteger(1, xBytes)
            val y = BigInteger(1, yBytes)
            val ecPoint = ECPoint(x, y)

            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val kf = KeyFactory.getInstance("EC")
            val params = (kpg.generateKeyPair().public as java.security.interfaces.ECPublicKey).params
            val spec = ECPublicKeySpec(ecPoint, params)
            kf.generatePublic(spec)
        }

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

        private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            return mac.doFinal(ikm)
        }

        private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val input = ByteArray(info.size + 1)
            System.arraycopy(info, 0, input, 0, info.size)
            input[info.size] = 1
            val out = mac.doFinal(input)
            return out.copyOf(length)
        }

        private data class SealedRequest(
            val body: ByteArray,
            val resKey: ByteArray,
            val resAad: ByteArray
        )

        private fun sealRequest(path: String, payload: JSONObject): SealedRequest {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = kpg.generateKeyPair()
            val clientPriv = keyPair.private
            val clientPub = keyPair.public as java.security.interfaces.ECPublicKey

            // Uncompressed 65-byte EC public key (0x04 || X || Y)
            val clientPubBytes = ByteArray(65)
            clientPubBytes[0] = 0x04
            val x = clientPub.w.affineX.toByteArray()
            val y = clientPub.w.affineY.toByteArray()
            val xStart = 33 - minOf(32, x.size)
            val yStart = 65 - minOf(32, y.size)
            System.arraycopy(x, maxOf(0, x.size - 32), clientPubBytes, xStart, minOf(32, x.size))
            System.arraycopy(y, maxOf(0, y.size - 32), clientPubBytes, yStart, minOf(32, y.size))

            // ECDH agreement
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(clientPriv)
            agreement.doPhase(serverPublicKey, true)
            val zBytes = agreement.generateSecret()

            // HKDF
            val prk = hkdfExtract(salt = clientPubBytes, ikm = zBytes)
            val reqKey = hkdfExpand(prk, "lumen-gate-v2|c2s".toByteArray(Charsets.UTF_8), 32)
            val resKey = hkdfExpand(prk, "lumen-gate-v2|s2c".toByteArray(Charsets.UTF_8), 32)

            // AES-256-GCM Encrypt
            val reqJson = JSONObject().apply {
                put("path", path)
                put("payload", payload)
            }.toString()
            val reqPlaintext = reqJson.toByteArray(Charsets.UTF_8)
            val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

            // Request AAD: "lumen-gate-v2" || 0x00 || 0x01 || 0x01 || clientPubBytes
            val prefix = "lumen-gate-v2".toByteArray(Charsets.UTF_8)
            val reqAad = ByteArray(prefix.size + 3 + clientPubBytes.size)
            System.arraycopy(prefix, 0, reqAad, 0, prefix.size)
            reqAad[prefix.size] = 0
            reqAad[prefix.size + 1] = 1
            reqAad[prefix.size + 2] = 1 // keyId = 1
            System.arraycopy(clientPubBytes, 0, reqAad, prefix.size + 3, clientPubBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(reqKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(reqAad)
            val ciphertextAndTag = cipher.doFinal(reqPlaintext)

            // Binary packet: version(0x02) || keyId(0x01) || clientPub(65b) || IV(12b) || ciphertextAndTag
            val body = ByteArray(2 + clientPubBytes.size + iv.size + ciphertextAndTag.size)
            body[0] = 2
            body[1] = 1
            System.arraycopy(clientPubBytes, 0, body, 2, clientPubBytes.size)
            System.arraycopy(iv, 0, body, 2 + clientPubBytes.size, iv.size)
            System.arraycopy(ciphertextAndTag, 0, body, 2 + clientPubBytes.size + iv.size, ciphertextAndTag.size)

            // Response AAD: "lumen-gate-v2" || 0x00 || 0x02 || 0x01 || clientPubBytes
            val resAad = ByteArray(prefix.size + 3 + clientPubBytes.size)
            System.arraycopy(prefix, 0, resAad, 0, prefix.size)
            resAad[prefix.size] = 0
            resAad[prefix.size + 1] = 2
            resAad[prefix.size + 2] = 1
            System.arraycopy(clientPubBytes, 0, resAad, prefix.size + 3, clientPubBytes.size)

            return SealedRequest(body = body, resKey = resKey, resAad = resAad)
        }

        private fun decryptResponse(resKey: ByteArray, aad: ByteArray, respBytes: ByteArray): ByteArray {
            val iv = respBytes.copyOfRange(0, 12)
            val ciphertext = respBytes.copyOfRange(12, respBytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(resKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertext)
        }

        private suspend fun executeEncryptedQuery(path: String, payload: JSONObject): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    val sealed = sealRequest(path, payload)
                    val mediaType = "application/octet-stream".toMediaType()
                    val req = Request.Builder()
                        .url("$API_BASE/g")
                        .addHeader("User-Agent", UA)
                        .addHeader("Origin", ORIGIN)
                        .addHeader("Referer", "$ORIGIN/watch")
                        .post(sealed.body.toRequestBody(mediaType))
                        .build()

                    httpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.size > 28) {
                                val decryptedBytes = decryptResponse(sealed.resKey, sealed.resAad, bytes)
                                val jsonStr = String(decryptedBytes, Charsets.UTF_8)
                                return@withContext JSONObject(jsonStr)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "executeEncryptedQuery error on $path: ${e.message}")
                }
                null
            }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = if (isTv) "tv" else "movie",
            year = request.year
        ) ?: return@withContext emptyList()

        val servers = fallbackServers
        val seenUrls = mutableSetOf<String>()

        val serverTasks = servers.map { srv ->
            async {
                val srvName = srv["name"]?.toString().orEmpty()
                if (srvName.isEmpty()) return@async emptyList<ScraperStreamResult>()

                val payload = JSONObject().apply {
                    put("tmdb", tmdbId.toString())
                    if (isTv) {
                        put("season", (request.season ?: 1).toString())
                        put("episode", (request.episode ?: 1).toString())
                    }
                }
                val targetPath = if (isTv) "/$srvName/series" else "/$srvName/movie"
                val responseJson = executeEncryptedQuery(targetPath, payload) ?: return@async emptyList<ScraperStreamResult>()

                val streamObj = if (responseJson.has("data")) {
                    responseJson.optJSONObject("data")?.optJSONArray("stream")
                } else {
                    responseJson.optJSONArray("stream")
                } ?: return@async emptyList<ScraperStreamResult>()

                val is4k = srv["4k"] == true
                val serverResults = mutableListOf<ScraperStreamResult>()

                for (i in 0 until streamObj.length()) {
                    val st = streamObj.getJSONObject(i)
                    val stType = st.optString("type")

                    if (stType == "hls") {
                        val playlistUrl = st.optString("playlist").trim()
                        if (playlistUrl.isNotBlank() && playlistUrl.startsWith("http")) {
                            val quality = if (is4k) "4K / 1080p" else "Auto"
                            serverResults.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = "[Cinejoy - $srvName] $quality",
                                    description = "$srvName • $quality • HLS",
                                    url = playlistUrl,
                                    quality = quality,
                                    headers = defaultHeaders
                                )
                            )
                        }
                    } else if (stType == "file" && st.has("qualities")) {
                        val quals = st.optJSONObject("qualities")
                        val subId = st.optString("id")
                        if (quals != null) {
                            val keys = quals.keys()
                            while (keys.hasNext()) {
                                val qKey = keys.next()
                                val qVal = quals.optJSONObject(qKey)
                                val fileUrl = qVal?.optString("url").orEmpty().trim()
                                if (fileUrl.isNotBlank() && fileUrl.startsWith("http")) {
                                    val quality = if (qKey.endsWith("p") || qKey.equals("4k", ignoreCase = true)) qKey else "${qKey}p"
                                    val label = if (subId.isNotBlank()) "$srvName ($subId)" else srvName
                                    serverResults.add(
                                        ScraperStreamResult(
                                            name = "PlayTorrioHTTP",
                                            title = "[Cinejoy - $label] $quality",
                                            description = "$label • $quality • MP4",
                                            url = fileUrl,
                                            quality = quality,
                                            headers = defaultHeaders
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                serverResults
            }
        }

        val allServerResults = serverTasks.awaitAll().flatten()
        for (res in allServerResults) {
            if (!seenUrls.contains(res.url)) {
                seenUrls.add(res.url)
                results.add(res)
            }
        }

        results
    }
}
