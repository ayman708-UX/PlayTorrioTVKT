package com.playtorrio.tv.data.streaming

import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * DownloadEverything Stream Scraper & Direct Link Extractor.
 *
 * Connects to slave.downloadeverythingfromeverywhere.com and resolves
 * streamable direct links from high-speed CDNs (Cloudflare R2, Pixeldrain,
 * Moviebox/HakunaMatata, ClicknUpload, and HubCloud).
 */
object DownloadEverythingExtractor {
    private const val TAG = "DownloadEverythingExtractor"
    private const val SLAVE_URL = "https://slave.downloadeverythingfromeverywhere.com/"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    private val PIXELDRAIN_REGEX = Regex("pixeldrain\\.(?:dev|com)/(?:u|l)/([a-zA-Z0-9_-]+)")
    private val HUBCLOUD_PHP_REGEX = Regex("https?://[^\\s\"<>]*/hubcloud\\.php\\?[^\\s\"<>]*")
    private val R2_REGEX = Regex("https?://[a-zA-Z0-9.\\-_]+\\.r2\\.cloudflarestorage\\.com/[^\\s\"<>]+")
    private val PIXEL_MIRROR_REGEX = Regex("https?://pixel\\.hubcloud\\.[a-z]+/\\?id=[^\\s\"<>]+")
    private val DIRECT_EXT_REGEX = Regex("\\.(?:mp4|mkv)(?:\\?|$)", RegexOption.IGNORE_CASE)

    suspend fun extract(
        client: OkHttpClient,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        title: String? = null,
        year: Int? = null,
        imdbId: String? = null,
    ): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val isTv = season != null
            var resolvedTitle = title
            var resolvedYear = year
            var resolvedImdbId = imdbId

            // Resolve missing title/year/imdb if needed
            if (resolvedTitle.isNullOrBlank() || (resolvedYear == null && tmdbId > 0)) {
                try {
                    if (!isTv) {
                        val details = TmdbClient.api.getMovieDetails(tmdbId, TmdbClient.API_KEY)
                        if (resolvedTitle.isNullOrBlank()) resolvedTitle = details.title
                        if (resolvedYear == null) resolvedYear = details.releaseDate?.take(4)?.toIntOrNull()
                        if (resolvedImdbId.isNullOrBlank()) {
                            val ext = TmdbClient.api.getMovieExternalIds(tmdbId, TmdbClient.API_KEY)
                            resolvedImdbId = ext.imdbId
                        }
                    } else {
                        val details = TmdbClient.api.getTvDetails(tmdbId, TmdbClient.API_KEY)
                        if (resolvedTitle.isNullOrBlank()) resolvedTitle = details.name
                        if (resolvedYear == null) resolvedYear = details.firstAirDate?.take(4)?.toIntOrNull()
                        if (resolvedImdbId.isNullOrBlank()) {
                            val ext = TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY)
                            resolvedImdbId = ext.imdbId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch TMDB details for $tmdbId: ${e.message}")
                }
            }

            if (resolvedTitle.isNullOrBlank()) {
                Log.w(TAG, "Cannot scrape without a title")
                return@withContext null
            }

            val payloadObj = JSONObject().apply {
                put("mode", if (isTv) "series" else "movie")
                put("title", resolvedTitle)
                if (resolvedYear != null) put("year", resolvedYear.toString())
                if (tmdbId > 0) put("tmdb_id", tmdbId)
                if (!resolvedImdbId.isNullOrBlank()) put("imdb_id", resolvedImdbId)
                if (season != null) put("season", season)
                if (episode != null) put("episode", episode)
            }

            val req = Request.Builder()
                .url(SLAVE_URL)
                .header("User-Agent", UA)
                .header("Origin", "https://downloadeverythingfromeverywhere.com")
                .header("Referer", "https://downloadeverythingfromeverywhere.com/")
                .header("Content-Type", "application/json")
                .post(payloadObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.i(TAG, "Starting scrape for \"$resolvedTitle\" (tmdb: $tmdbId, S:$season E:$episode)")

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Slave returned HTTP ${resp.code}")
                    return@withContext null
                }
                val reader = resp.body?.charStream()?.buffered() ?: return@withContext null
                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val parsed = JSONObject(trimmed)
                            if (parsed.optString("t") == "hit" && parsed.has("links")) {
                                val links = parsed.getJSONArray("links")
                                for (i in 0 until links.length()) {
                                    val linkObj = links.getJSONObject(i)
                                    val rawUrl = linkObj.optString("url")
                                    val resolved = resolveItem(client, rawUrl)
                                    if (resolved != null) {
                                        Log.i(TAG, "[+] Playable stream extracted: ${resolved.url}")
                                        return@withContext resolved
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DownloadEverything extraction failed: ${e.message}")
        }
        return@withContext null
    }

    private fun isBlockedDomain(rawUrl: String): Boolean {
        return rawUrl.contains("111477.xyz") ||
            rawUrl.contains("vadapav.mov") ||
            rawUrl.contains("driveseed.org") ||
            rawUrl.contains("new3.gdflix.io") ||
            rawUrl.contains("rapidrar.cr") ||
            rawUrl.contains("megaup.net") ||
            rawUrl.contains("telegram.dog") ||
            rawUrl.contains("t.me")
    }

    private suspend fun resolveItem(client: OkHttpClient, rawUrl: String): StreamResult? {
        if (rawUrl.isBlank() || isBlockedDomain(rawUrl)) return null

        try {
            // 1. Moviebox / HakunaMatata direct streams
            if (rawUrl.contains("hakunaymatata.com")) {
                return StreamResult(
                    url = rawUrl,
                    referer = "https://downloadeverythingfromeverywhere.com/",
                    headers = mapOf("User-Agent" to "Lavf/60.16.100")
                )
            }

            // 2. Pixeldrain Direct API
            if (rawUrl.contains("pixeldrain.dev") || rawUrl.contains("pixeldrain.com")) {
                val match = PIXELDRAIN_REGEX.find(rawUrl)
                if (match != null) {
                    val fileId = match.groupValues[1]
                    val directStreamUrl = "https://pixeldrain.com/api/file/$fileId"
                    return StreamResult(
                        url = directStreamUrl,
                        referer = "https://downloadeverythingfromeverywhere.com/",
                        headers = mapOf("User-Agent" to UA)
                    )
                }
            }

            // 3. HubCloud Inside Resolver (Cloudflare R2 Direct S3 Signed Stream)
            if (rawUrl.contains("hubcloud.") || rawUrl.contains("vcloud.zip")) {
                val resolvedHub = resolveHubCloud(client, rawUrl)
                if (resolvedHub != null) {
                    return StreamResult(
                        url = resolvedHub,
                        referer = "https://downloadeverythingfromeverywhere.com/",
                        headers = mapOf("User-Agent" to UA)
                    )
                }
            }

            // 4. Direct MP4 / MKV Streams (Tattooin, Vikingfile, etc.)
            if (DIRECT_EXT_REGEX.containsMatchIn(rawUrl) &&
                !rawUrl.contains(".cyou/res/")
            ) {
                try {
                    val headReq = Request.Builder()
                        .url(rawUrl)
                        .header("User-Agent", UA)
                        .head()
                        .build()
                    val checkCode = client.newCall(headReq).execute().use { it.code }
                    if (checkCode in 200..299 || checkCode == 302) {
                        return StreamResult(
                            url = rawUrl,
                            referer = "https://downloadeverythingfromeverywhere.com/",
                            headers = mapOf("User-Agent" to UA)
                        )
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return null
    }

    private fun resolveHubCloud(client: OkHttpClient, hubUrl: String): String? {
        try {
            val req1 = Request.Builder()
                .url(hubUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://downloadeverythingfromeverywhere.com/")
                .build()

            var html = client.newCall(req1).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            val phpMatch = HUBCLOUD_PHP_REGEX.find(html)
            if (phpMatch != null) {
                val phpUrl = phpMatch.value
                val req2 = Request.Builder()
                    .url(phpUrl)
                    .header("User-Agent", UA)
                    .header("Referer", hubUrl)
                    .build()
                val phpBody = client.newCall(req2).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }

                // Check for Cloudflare R2 signed link
                val r2Match = R2_REGEX.find(phpBody)
                if (r2Match != null) {
                    return r2Match.value.replace("&amp;", "&")
                }

                // Check for Pixel mirror link
                val pixelMatch = PIXEL_MIRROR_REGEX.find(phpBody)
                if (pixelMatch != null) {
                    return pixelMatch.value
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
