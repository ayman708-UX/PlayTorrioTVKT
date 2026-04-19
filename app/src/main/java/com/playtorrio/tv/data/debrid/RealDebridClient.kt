package com.playtorrio.tv.data.debrid

import android.util.Log
import com.playtorrio.tv.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RealDebridClient {

    private const val TAG = "RealDebridClient"
    private const val BASE = "https://api.real-debrid.com/rest/1.0"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves a magnet link to a direct HTTPS download URL via Real-Debrid.
     * Cache-check first: returns null immediately if the torrent is not cached.
     */
    suspend fun resolve(magnetUri: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = AppPreferences.realDebridApiKey.trim()
            if (apiKey.isEmpty()) {
                Log.w(TAG, "No API key configured")
                return@withContext null
            }

            val hash = extractMagnetHash(magnetUri) ?: run {
                Log.w(TAG, "Could not extract hash from magnet")
                return@withContext null
            }

            // 1. Check instant availability (only proceed if cached)
            if (!isCached(apiKey, hash)) {
                Log.d(TAG, "Torrent not cached on Real-Debrid: $hash")
                return@withContext null
            }

            // 2. Add magnet
            val torrentId = addMagnet(apiKey, magnetUri) ?: run {
                Log.w(TAG, "Failed to add magnet")
                return@withContext null
            }

            // 3. Select all files
            selectFiles(apiKey, torrentId)

            // 4. Poll for download link (should be instant since cached)
            val link = getTorrentLink(apiKey, torrentId, maxWaitMs = 15_000) ?: run {
                Log.w(TAG, "No download link found for torrent $torrentId")
                return@withContext null
            }

            // 5. Unrestrict link to get the final CDN URL
            val downloadUrl = unrestrictLink(apiKey, link)
            Log.d(TAG, "Resolved: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving via Real-Debrid", e)
            null
        }
    }

    private fun isCached(apiKey: String, hash: String): Boolean {
        val lowerHash = hash.lowercase()
        val req = Request.Builder()
            .url("$BASE/torrents/instantAvailability/$lowerHash")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val body = http.newCall(req).execute().use { it.body?.string() } ?: return false
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return false
        // Response: { "<hash>": { "rd": [ {...} ] } }  OR  {}
        val hashData = runCatching { obj.getJSONObject(lowerHash) }.getOrNull()
            ?: runCatching { obj.getJSONObject(hash.uppercase()) }.getOrNull()
            ?: return false
        val rdArr = runCatching { hashData.getJSONArray("rd") }.getOrNull() ?: return false
        return rdArr.length() > 0
    }

    private fun addMagnet(apiKey: String, magnetUri: String): String? {
        val body = FormBody.Builder().add("magnet", magnetUri).build()
        val req = Request.Builder()
            .url("$BASE/torrents/addMagnet")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val respBody = http.newCall(req).execute().use { it.body?.string() } ?: return null
        return runCatching { JSONObject(respBody).getString("id") }.getOrNull()
    }

    private fun selectFiles(apiKey: String, torrentId: String) {
        val body = FormBody.Builder().add("files", "all").build()
        val req = Request.Builder()
            .url("$BASE/torrents/selectFiles/$torrentId")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        http.newCall(req).execute().close()
    }

    private fun getTorrentLink(apiKey: String, torrentId: String, maxWaitMs: Long): String? {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            val req = Request.Builder()
                .url("$BASE/torrents/info/$torrentId")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val body = http.newCall(req).execute().use { it.body?.string() } ?: return null
            val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val status = obj.optString("status")
            Log.d(TAG, "Torrent $torrentId status: $status")
            if (status == "downloaded") {
                val links = obj.optJSONArray("links")
                if (links != null && links.length() > 0) {
                    return links.getString(0)
                }
                return null
            }
            if (status == "error" || status == "dead" || status == "virus") return null
            Thread.sleep(1_000)
        }
        Log.w(TAG, "Timed out waiting for torrent $torrentId")
        return null
    }

    private fun unrestrictLink(apiKey: String, link: String): String? {
        val body = FormBody.Builder().add("link", link).build()
        val req = Request.Builder()
            .url("$BASE/unrestrict/link")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val respBody = http.newCall(req).execute().use { it.body?.string() } ?: return null
        return runCatching { JSONObject(respBody).getString("download") }.getOrNull()
    }

    private fun extractMagnetHash(magnetUri: String): String? {
        val xt = magnetUri.substringAfter("xt=urn:btih:", missingDelimiterValue = "")
            .substringBefore("&")
            .substringBefore(" ")
        return xt.ifEmpty { null }
    }
}
