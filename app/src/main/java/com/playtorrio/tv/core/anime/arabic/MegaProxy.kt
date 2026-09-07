package com.playtorrio.tv.core.anime.arabic

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class MegaResolved(
    val url: String,
    val size: Long
)

private data class MegaFile(
    val dlUrl: String,
    val size: Long,
    val aesKey: ByteArray,
    val nonce: ByteArray
)

@Singleton
class MegaProxy @Inject constructor() {

    companion object {
        private const val TAG = "MegaProxy"
        val instance: MegaProxy by lazy { MegaProxy() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val files = ConcurrentHashMap<String, MegaFile>()
    private val seq = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0

    @Synchronized
    private fun ensureServer(): Int {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            return serverPort
        }
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        serverPort = ss.localPort
        Thread({
            while (!ss.isClosed) {
                try {
                    val socket = ss.accept()
                    Thread({ handleSocket(socket) }, "MegaProxy-Worker").start()
                } catch (e: Exception) {
                    if (ss.isClosed) break
                    Log.w(TAG, "Server socket accept error: ${e.message}")
                }
            }
        }, "MegaProxy-Server").start()
        return serverPort
    }

    suspend fun resolve(embedUrl: String): MegaResolved? = withContext(Dispatchers.IO) {
        try {
            val parsed = parseEmbed(embedUrl) ?: return@withContext null
            val (fileId, keyBytes) = parsed

            val aesKey = ByteArray(16)
            for (i in 0 until 16) {
                aesKey[i] = (keyBytes[i].toInt() xor keyBytes[i + 16].toInt()).toByte()
            }
            val nonce = ByteArray(8)
            System.arraycopy(keyBytes, 16, nonce, 0, 8)

            val api = callMegaApi(fileId) ?: return@withContext null
            val size = api.optLong("s", 0L)
            val dlUrl = api.optString("g", "")
            if (size <= 0L || dlUrl.isBlank()) {
                return@withContext null
            }

            val port = ensureServer()
            val token = "${System.currentTimeMillis()}_${seq.incrementAndGet()}"
            files[token] = MegaFile(
                dlUrl = dlUrl,
                size = size,
                aesKey = aesKey,
                nonce = nonce
            )

            val proxyUrl = "http://127.0.0.1:$port/v/$token.mp4"
            MegaResolved(url = proxyUrl, size = size)
        } catch (e: Exception) {
            Log.w(TAG, "Resolve failed for $embedUrl: ${e.message}")
            null
        }
    }

    private fun callMegaApi(fileId: String): JSONObject? {
        try {
            val body = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "g")
                    put("g", 1)
                    put("ssl", 1)
                    put("n", fileId)
                })
            }.toString()

            val req = Request.Builder()
                .url("https://g.api.mega.co.nz/cs?id=${seq.incrementAndGet()}")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val resBody = res.body?.string() ?: return null
                val arr = JSONArray(resBody)
                if (arr.length() > 0) {
                    return arr.getJSONObject(0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mega API call failed: ${e.message}")
        }
        return null
    }

    private fun parseEmbed(embedUrl: String): Pair<String, ByteArray>? {
        val regex = Regex("""mega\.(?:nz|co\.nz)/embed/(?:#|!)?([A-Za-z0-9_-]+)!([A-Za-z0-9_-]+)""")
        val m = regex.find(embedUrl) ?: return null
        val fileId = m.groupValues[1]
        val keyB64 = m.groupValues[2]
        return try {
            val keyBytes = Base64.decode(keyB64, Base64.URL_SAFE)
            if (keyBytes.size != 32) null else Pair(fileId, keyBytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun handleSocket(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            if (!path.startsWith("/v/")) {
                sendNotFound(socket)
                return
            }

            val token = path.substring(3).removeSuffix(".mp4")
            val file = files[token]
            if (file == null) {
                sendNotFound(socket)
                return
            }

            var rangeHeader: String? = null
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substring(6).trim()
                }
                line = reader.readLine()
            }

            var start = 0L
            var end = file.size - 1
            var isPartial = false

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val spec = rangeHeader.substring(6).split("-")[0].trim()
                val reqStart = spec.toLongOrNull()
                if (reqStart != null && reqStart in 0 until file.size) {
                    start = reqStart
                    isPartial = true
                }
            }

            val contentLength = end - start + 1
            val out = BufferedOutputStream(socket.getOutputStream())

            val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
            val headers = StringBuilder().apply {
                append(statusLine)
                append("Access-Control-Allow-Origin: *\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Type: video/mp4\r\n")
                if (isPartial) {
                    append("Content-Range: bytes $start-$end/${file.size}\r\n")
                }
                append("Content-Length: $contentLength\r\n")
                append("Connection: close\r\n\r\n")
            }.toString()

            out.write(headers.toByteArray(Charsets.UTF_8))
            out.flush()

            if (method.equals("HEAD", ignoreCase = true)) {
                return
            }

            val upReq = Request.Builder()
                .url(file.dlUrl)
                .addHeader("Range", "bytes=$start-$end")
                .build()

            client.newCall(upReq).execute().use { upRes ->
                val bodyStream = upRes.body?.byteStream() ?: return
                val blockOffset = start / 16
                val iv = ByteArray(16)
                System.arraycopy(file.nonce, 0, iv, 0, 8)
                ByteBuffer.wrap(iv).putLong(8, blockOffset)

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(file.aesKey, "AES"), IvParameterSpec(iv))

                val intraBlockSkip = (start % 16).toInt()
                if (intraBlockSkip > 0) {
                    cipher.update(ByteArray(intraBlockSkip))
                }

                val buffer = ByteArray(32 * 1024)
                val inStream = BufferedInputStream(bodyStream)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    val decrypted = cipher.update(buffer, 0, bytesRead)
                    if (decrypted != null && decrypted.isNotEmpty()) {
                        out.write(decrypted)
                    }
                }
                val finalBytes = cipher.doFinal()
                if (finalBytes != null && finalBytes.isNotEmpty()) {
                    out.write(finalBytes)
                }
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendNotFound(socket: Socket) {
        try {
            val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            socket.close()
        } catch (_: Exception) {}
    }
}
