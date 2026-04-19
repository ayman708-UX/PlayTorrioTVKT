package com.playtorrio.tv.data.debrid

import android.util.Log
import com.playtorrio.tv.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TorBoxClient {

    private const val TAG = "TorBoxClient"
    private const val BASE = "https://api.torbox.app/v1/api"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "m4v", "ts", "webm")

    /**
     * Resolves a magnet link to a direct HTTPS download URL via TorBox.
     * Cache-check first: returns null immediately if the torrent is not cached.
     */
    suspend fun resolve(magnetUri: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = AppPreferences.torboxApiKey.trim()
            if (apiKey.isEmpty()) {
                Log.w(TAG, "No API key configured")
                return@withContext null
            }

            val hash = extractMagnetHash(magnetUri) ?: run {
                Log.w(TAG, "Could not extract hash from magnet")
                return@withContext null
            }

            // 1. Check if torrent is cached (abort early if not)
            if (!isCached(apiKey, hash)) {
                Log.d(TAG, "Torrent not cached on TorBox: $hash")
                return@withContext null
            }

            // 2. Create the torrent entry (TorBox serves it from cache)
            val torrentId = createTorrent(apiKey, magnetUri) ?: run {
                Log.w(TAG, "Failed to create torrent")
                return@withContext null
            }

            // 3. Poll mylist until torrent entry has file info
            val fileId = getVideoFileId(apiKey, torrentId, maxWaitMs = 20_000) ?: run {
                Log.w(TAG, "No video file found for torrent $torrentId")
                return@withContext null
            }

            // 4. Request download link
            val url = requestDownloadLink(apiKey, torrentId, fileId)
            Log.d(TAG, "Resolved: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving via TorBox", e)
            null
        }
    }

    private fun isCached(apiKey: String, hash: String): Boolean {
        val lowerHash = hash.lowercase()
        val req = Request.Builder()
            .url("$BASE/torrents/checkcached?hash=$lowerHash&format=object&list_files=true")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val body = http.newCall(req).execute().use { it.body?.string() } ?: return false
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return false
        if (!obj.optBoolean("success", false)) return false
        val data = obj.optJSONObject("data") ?: return false
        // data is keyed by lowercase hash; presence means it's cached
        return data.has(lowerHash) || data.has(hash.uppercase())
    }

    private fun createTorrent(apiKey: String, magnetUri: String): Int? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("magnet", magnetUri)
            .build()
        val req = Request.Builder()
            .url("$BASE/torrents/createtorrent")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val respBody = http.newCall(req).execute().use { it.body?.string() } ?: return null
        val obj = runCatching { JSONObject(respBody) }.getOrNull() ?: return null
        if (!obj.optBoolean("success", false)) return null
        val data = obj.optJSONObject("data") ?: return null
        return data.optInt("torrent_id", -1).takeIf { it > 0 }
    }

    private fun getVideoFileId(apiKey: String, torrentId: Int, maxWaitMs: Long): Int? {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            val req = Request.Builder()
                .url("$BASE/torrents/mylist?id=$torrentId&bypass_cache=true")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val body = http.newCall(req).execute().use { it.body?.string() } ?: return null
            val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null

            val data = obj.optJSONObject("data") ?: run {
                Thread.sleep(1_500)
                return@run null
            } ?: continue

            val downloadState = data.optString("download_state", "")
            Log.d(TAG, "Torrent $torrentId state: $downloadState")
            val files = data.optJSONArray("files")

            val readyStates = setOf("cached", "completed", "uploading", "seeding")
            if (downloadState in readyStates && files != null && files.length() > 0) {
                var bestId = -1
                var bestSize = -1L
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val name = file.optString("name", "")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in VIDEO_EXTENSIONS) continue
                    val size = file.optLong("size", 0L)
                    if (size > bestSize) {
                        bestSize = size
                        bestId = file.optInt("id", -1)
                    }
                }
                if (bestId != -1) return bestId
            }
            Thread.sleep(1_500)
        }
        Log.w(TAG, "Timed out waiting for torrent $torrentId")
        return null
    }

    private fun requestDownloadLink(apiKey: String, torrentId: Int, fileId: Int): String? {
        val req = Request.Builder()
            .url("$BASE/torrents/requestdl?token=$apiKey&torrent_id=$torrentId&file_id=$fileId")
            .get()
            .build()
        val respBody = http.newCall(req).execute().use { it.body?.string() } ?: return null
        val obj = runCatching { JSONObject(respBody) }.getOrNull() ?: return null
        if (!obj.optBoolean("success", false)) return null
        val data = obj.optString("data", "")
        return data.takeIf { it.startsWith("http") }
    }

    private fun extractMagnetHash(magnetUri: String): String? {
        val xt = magnetUri.substringAfter("xt=urn:btih:", missingDelimiterValue = "")
            .substringBefore("&")
            .substringBefore(" ")
        return xt.ifEmpty { null }
    }
}
