package com.playtorrio.tv.data.streaming

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SixtySevenMoviesExtractor {
    private const val TAG = "SixtySevenMoviesExtractor"
    private const val BASE_URL = "https://momlover.notyourtype.dad"
    private const val PASSWORD = "Sn00pD0g#RESP_B4SE_K3y_2026!"

    private fun decryptPayload(payloadBase64: String): String {
        val raw = Base64.decode(payloadBase64, Base64.DEFAULT)
        
        // Layout: [Salt 16 bytes][IV 12 bytes][Ciphertext][Tag 16 bytes]
        val salt = raw.copyOfRange(0, 16)
        val iv = raw.copyOfRange(16, 28)
        val cipherAndTag = raw.copyOfRange(28, raw.size)
        
        // Key derivation: SHA-256(PASSWORD + SALT)
        val passBuf = PASSWORD.toByteArray(Charsets.UTF_8)
        val concatBuf = ByteArray(passBuf.size + salt.size)
        System.arraycopy(passBuf, 0, concatBuf, 0, passBuf.size)
        System.arraycopy(salt, 0, concatBuf, passBuf.size, salt.size)
        
        val md = MessageDigest.getInstance("SHA-256")
        val keyBytes = md.digest(concatBuf)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        val decrypted = cipher.doFinal(cipherAndTag)
        return String(decrypted, Charsets.UTF_8)
    }

    suspend fun extract(
        client: OkHttpClient,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val isMovie = season == null
            
            // Generate Token
            val tokenReq = Request.Builder()
                .url("$BASE_URL/auth/generate-token")
                .header("Content-Type", "application/json")
                .header("Origin", "https://player.vidlove.cc")
                .header("Referer", "https://player.vidlove.cc/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                .post("{\"clientData\":{}}".toRequestBody("application/json".toMediaType()))
                .build()
                
            val tokenResStr = client.newCall(tokenReq).execute().use { it.body?.string() } ?: return@withContext null
            val token = JSONObject(tokenResStr).optString("token")
            if (token.isBlank()) return@withContext null
            
            val endpoint = if (isMovie) "$BASE_URL/fabric/movie/$tmdbId" else "$BASE_URL/fabric/tv/$tmdbId/$season/$episode"
            
            val streamReq = Request.Builder()
                .url(endpoint)
                .header("x-request-token", token)
                .header("x-response-encryption", "aes-gcm")
                .header("Origin", "https://player.vidlove.cc")
                .header("Referer", "https://player.vidlove.cc/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                .build()
                
            try {
                val resStr = client.newCall(streamReq).execute().use { it.body?.string() } ?: return@withContext null
                val data = JSONObject(resStr)
                
                if (data.optString("v") != "gcm" || !data.has("payload")) {
                    Log.w(TAG, "Unsupported response format")
                    return@withContext null
                }
                
                val decryptedStr = decryptPayload(data.getString("payload"))
                val decObj = JSONObject(decryptedStr)
                
                if (decObj.has("sources")) {
                    val sourcesArr = decObj.getJSONArray("sources")
                    if (sourcesArr.length() > 0) {
                        val streamUrl = sourcesArr.getJSONObject(0).getString("url")
                        return@withContext StreamResult(
                            url = streamUrl,
                            referer = "https://player.vidlove.cc/",
                            headers = mapOf(
                                "Origin" to "https://player.vidlove.cc",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decrypt or parse 67movies payload: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract 67movies: ${e.message}")
        }
        
        return@withContext null
    }
}
