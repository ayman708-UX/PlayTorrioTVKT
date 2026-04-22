package com.playtorrio.tv.data.iptv

import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC decryption for paste.sh blobs.
 *
 * paste.sh stores ciphertext as `Salted__` + 8-byte salt + ciphertext, base64.
 * The key + IV are derived from a password = `id + serverkey + clientkey + "https://paste.sh"`,
 * using PBKDF2-HMAC-SHA512 (1 iteration, 48 bytes → 32 key + 16 iv). Older
 * pastes use OpenSSL's legacy MD5-based EVP_BytesToKey instead.
 */
internal object PasteShDecryptor {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * @param urlWithHash full URL including the `#clientkey` fragment.
     * @return decrypted plaintext, or empty string on any failure.
     */
    fun decrypt(urlWithHash: String): String {
        val hashIdx = urlWithHash.indexOf('#')
        if (hashIdx <= 0) return ""
        val baseUrl = urlWithHash.substring(0, hashIdx)
        val clientKey = urlWithHash.substring(hashIdx + 1)
        val id = baseUrl.substringAfterLast('/')

        val raw = httpGetText("$baseUrl.txt") ?: return ""
        val lines = raw.split('\n')
        if (lines.isEmpty()) return ""
        val serverKey = lines[0].trim()
        val b64 = lines.drop(1).joinToString("").trim()
        if (b64.isEmpty()) return ""

        val cipherBytes = try {
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } catch (_: Exception) { return "" }
        if (cipherBytes.size < 17) return ""

        // Layout: "Salted__" (8) + salt (8) + ciphertext
        val salt = cipherBytes.copyOfRange(8, 16)
        val ct = cipherBytes.copyOfRange(16, cipherBytes.size)
        val password = "$id$serverKey$clientKey" + "https://paste.sh"

        // Try PBKDF2-HMAC-SHA512 first (modern pastes)
        runCatching {
            val spec = PBEKeySpec(password.toCharArray(), salt, 1, 48 * 8)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val keyIv = factory.generateSecret(spec).encoded
            val key = keyIv.copyOfRange(0, 32)
            val iv = keyIv.copyOfRange(32, 48)
            return aesDecrypt(ct, key, iv)
        }

        // Fallback: OpenSSL EVP_BytesToKey (MD5)
        runCatching {
            val (key, iv) = evpBytesToKey(password.toByteArray(Charsets.UTF_8), salt, 32, 16)
            return aesDecrypt(ct, key, iv)
        }

        return ""
    }

    private fun aesDecrypt(ct: ByteArray, key: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun evpBytesToKey(
        password: ByteArray,
        salt: ByteArray,
        keyLen: Int,
        ivLen: Int,
    ): Pair<ByteArray, ByteArray> {
        val md = MessageDigest.getInstance("MD5")
        var prev = ByteArray(0)
        val out = ByteArray(keyLen + ivLen)
        var copied = 0
        while (copied < out.size) {
            md.reset()
            md.update(prev)
            md.update(password)
            md.update(salt)
            prev = md.digest()
            val n = minOf(prev.size, out.size - copied)
            System.arraycopy(prev, 0, out, copied, n)
            copied += n
        }
        return out.copyOfRange(0, keyLen) to out.copyOfRange(keyLen, keyLen + ivLen)
    }

    private fun httpGetText(url: String): String? = try {
        val req = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36",
            )
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (_: Exception) {
        null
    }
}
