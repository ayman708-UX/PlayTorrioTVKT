package com.playtorrio.tv.core.iptv.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasteShDecryptor {
    private const val TAG = "PasteShDecryptor"
    private const val UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko)"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun decrypt(urlWithHash: String): String = withContext(Dispatchers.IO) {
        val hashIdx = urlWithHash.indexOf('#')
        if (hashIdx <= 0) return@withContext ""
        val baseUrl = urlWithHash.substring(0, hashIdx)
        val clientKey = urlWithHash.substring(hashIdx + 1)
        val id = baseUrl.substring(baseUrl.lastIndexOf('/') + 1)

        val raw = try {
            val req = Request.Builder()
                .url("$baseUrl.txt")
                .addHeader("User-Agent", UA)
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed fetching paste.sh text: ${e.message}")
            return@withContext ""
        }

        if (raw.isEmpty()) return@withContext ""
        val lines = raw.split("\n")
        if (lines.isEmpty()) return@withContext ""
        val serverKey = lines.first().trim()
        val b64 = lines.drop(1).joinToString("").trim()
        if (b64.isEmpty()) return@withContext ""

        val cipherBytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            return@withContext ""
        }

        if (cipherBytes.size < 17) return@withContext ""

        // Layout: "Salted__" (8 bytes) + salt (8 bytes) + ciphertext
        val salt = cipherBytes.copyOfRange(8, 16)
        val ct = cipherBytes.copyOfRange(16, cipherBytes.size)
        val password = "${id}${serverKey}${clientKey}https://paste.sh"
        val passBytes = password.toByteArray(Charsets.UTF_8)

        // 1. PBKDF2-HMAC-SHA512 (Modern pastes: iteration 1, 48 bytes key+iv)
        try {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(passBytes, "HmacSHA512"))
            val saltAndIndex = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, saltAndIndex, 0, salt.size)
            saltAndIndex[salt.size + 3] = 1 // 0x00 0x00 0x00 0x01 (big endian 1)
            val u1 = mac.doFinal(saltAndIndex)

            val key = u1.copyOfRange(0, 32)
            val iv = u1.copyOfRange(32, 48)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ct)
            val out = String(decrypted, Charsets.UTF_8)
            if (out.isNotEmpty()) {
                Log.d(TAG, "Successfully decrypted paste.sh blob (PBKDF2): ${out.take(50)}...")
                return@withContext out
            }
        } catch (e: Exception) {
            Log.d(TAG, "PBKDF2 decrypt failed: ${e.message}")
        }

        // 2. OpenSSL EVP_BytesToKey (MD5) fallback (legacy pastes)
        try {
            val (key, iv) = evpBytesToKey(passBytes, salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ct)
            val out = String(decrypted, Charsets.UTF_8)
            if (out.isNotEmpty()) {
                Log.d(TAG, "Successfully decrypted paste.sh blob (EVP): ${out.take(50)}...")
                return@withContext out
            }
        } catch (e: Exception) {
            Log.d(TAG, "EVP decrypt failed: ${e.message}")
        }

        ""
    }

    private fun evpBytesToKey(
        password: ByteArray,
        salt: ByteArray,
        keyLen: Int,
        ivLen: Int
    ): Pair<ByteArray, ByteArray> {
        val md5 = MessageDigest.getInstance("MD5")
        val out = mutableListOf<Byte>()
        var prev = ByteArray(0)
        while (out.size < keyLen + ivLen) {
            md5.reset()
            if (prev.isNotEmpty()) md5.update(prev)
            md5.update(password)
            md5.update(salt)
            val d = md5.digest()
            prev = d
            for (b in d) out.add(b)
        }
        val outBytes = out.toByteArray()
        val key = outBytes.copyOfRange(0, keyLen)
        val iv = outBytes.copyOfRange(keyLen, keyLen + ivLen)
        return Pair(key, iv)
    }
}
