package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * DownloadEverything Stream Scraper & Inside Extractor for PlayTorrioHTTP.
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

    private val DEFAULT_HEADERS = mapOf(
        "User-Agent" to UA,
        "Origin" to "https://downloadeverythingfromeverywhere.com",
        "Referer" to "https://downloadeverythingfromeverywhere.com/",
        "Content-Type" to "application/json"
    )

    private val PIXELDRAIN_REGEX = Regex("pixeldrain\\.(?:dev|com)/(?:u|l)/([a-zA-Z0-9_-]+)")
    private val HUBCLOUD_PHP_REGEX = Regex("https?://[^\\s\"<>]*/hubcloud\\.php\\?[^\\s\"<>]*")
    private val R2_REGEX = Regex("https?://[a-zA-Z0-9.\\-_]+\\.r2\\.cloudflarestorage\\.com/[^\\s\"<>]+")
    private val PIXEL_MIRROR_REGEX = Regex("https?://pixel\\.hubcloud\\.[a-z]+/\\?id=[^\\s\"<>]+")
    private val DIRECT_EXT_REGEX = Regex("\\.(?:mp4|mkv)(?:\\?|$)", RegexOption.IGNORE_CASE)

    // Dedicated OkHttpClient with long read timeout for NDJSON streaming
    private val slaveHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Resolver client with faster per-link timeout
    private val resolverHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extractLive(
        client: OkHttpClient? = null,
        title: String,
        isMovie: Boolean,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        tmdbId: Int = 0,
        onStreamFound: (HttpStreamResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val isTv = !isMovie
            var resolvedTmdbId = tmdbId

            if (resolvedTmdbId <= 0) {
                resolvedTmdbId = TmdbIdResolver.resolveTmdbId(
                    imdbId = imdbId,
                    title = title,
                    isMovie = isMovie,
                    year = year
                ) ?: 0
            }

            Log.i(TAG, "🚀 [DownloadEverything] Starting scrape for \"$title\" (tmdb: $resolvedTmdbId, imdb: $imdbId, year: $year, S:${season}E:$episode)")

            val payloadObj = JSONObject().apply {
                put("mode", if (isTv) "series" else "movie")
                put("title", title)
                if (year != null && year > 0) put("year", year.toString())
                if (resolvedTmdbId > 0) put("tmdb_id", resolvedTmdbId)
                if (!imdbId.isNullOrBlank()) put("imdb_id", imdbId)
                if (isTv && season != null) put("season", season)
                if (isTv && episode != null) put("episode", episode)
            }

            val req = Request.Builder()
                .url(SLAVE_URL)
                .header("User-Agent", UA)
                .header("Origin", "https://downloadeverythingfromeverywhere.com")
                .header("Referer", "https://downloadeverythingfromeverywhere.com/")
                .header("Content-Type", "application/json")
                .post(payloadObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val call = slaveHttpClient.newCall(req)
            val response = call.execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "❌ [DownloadEverything] Slave returned HTTP ${response.code}")
                return@withContext
            }

            val responseBody = response.body ?: return@withContext
            val inputStream = responseBody.byteStream()
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            val activeJobs = mutableListOf<Job>()
            var totalHits = 0

            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val parsed = JSONObject(trimmed)
                            val eventType = parsed.optString("t")
                            if (eventType == "hit" && parsed.has("links")) {
                                val site = parsed.optString("site").ifEmpty { "DownloadEverything" }
                                val links = parsed.optJSONArray("links")
                                if (links != null && links.length() > 0) {
                                    totalHits += links.length()
                                    Log.i(TAG, "📦 [DownloadEverything] Hit from \"$site\": ${links.length()} candidate(s)")

                                    for (i in 0 until links.length()) {
                                        val linkObj = links.optJSONObject(i) ?: continue
                                        val itemJob = launch(Dispatchers.IO) {
                                            try {
                                                val resolved = resolveItem(linkObj, site, title)
                                                if (resolved != null) {
                                                    Log.i(TAG, "✨ [DownloadEverything] [+] Stream ready: ${resolved.title} -> ${resolved.url}")
                                                    onStreamFound(resolved)
                                                }
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Error resolving item: ${e.message}")
                                            }
                                        }
                                        activeJobs.add(itemJob)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed parsing NDJSON line: ${e.message}")
                        }
                    }
                    line = try {
                        reader.readLine()
                    } catch (e: Exception) {
                        Log.i(TAG, "NDJSON stream completed reading: ${e.message}")
                        null
                    }
                }
            } finally {
                response.close()
            }

            // Wait for all resolution jobs to complete
            if (activeJobs.isNotEmpty()) {
                activeJobs.joinAll()
            }

            Log.i(TAG, "🏁 [DownloadEverything] Completed scrape for \"$title\" (total candidates: $totalHits)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DownloadEverything] Live extraction failed: ${e.message}", e)
        }
    }

    suspend fun extract(
        client: OkHttpClient,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        title: String? = null,
        year: Int? = null,
        imdbId: String? = null,
    ): StreamResult? = withContext(Dispatchers.IO) {
        var result: StreamResult? = null
        extractLive(
            client = client,
            title = title ?: "",
            isMovie = season == null,
            year = year,
            season = season,
            episode = episode,
            imdbId = imdbId,
            tmdbId = tmdbId,
            onStreamFound = {
                if (result == null) {
                    result = StreamResult(it.url, it.headers?.get("Referer") ?: "https://downloadeverythingfromeverywhere.com/", it.headers)
                }
            }
        )
        result
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

    private suspend fun resolveItem(
        item: JSONObject,
        site: String,
        fallbackTitle: String
    ): HttpStreamResult? {
        val rawUrl = item.optString("url")
        if (rawUrl.isBlank() || isBlockedDomain(rawUrl)) return null

        var directStreamUrl: String? = null
        var provider = site
        var streamHeaders: Map<String, String>? = null

        try {
            // 1. Moviebox / HakunaMatata direct streams
            if (rawUrl.contains("hakunaymatata.com")) {
                directStreamUrl = rawUrl
                provider = "Moviebox"
                streamHeaders = mapOf("User-Agent" to "Lavf/60.16.100")
            }
            // 2. Pixeldrain Direct API
            else if (rawUrl.contains("pixeldrain.dev") || rawUrl.contains("pixeldrain.com")) {
                val match = PIXELDRAIN_REGEX.find(rawUrl)
                if (match != null) {
                    val fileId = match.groupValues[1]
                    directStreamUrl = "https://pixeldrain.com/api/file/$fileId"
                    provider = "Pixeldrain"
                    streamHeaders = mapOf("User-Agent" to UA)
                }
            }
            // 3. HubCloud Inside Resolver (Cloudflare R2 Direct S3 Signed Stream)
            else if (rawUrl.contains("hubcloud.") || rawUrl.contains("vcloud.zip")) {
                val resolvedHub = resolveHubCloud(rawUrl)
                if (resolvedHub != null) {
                    directStreamUrl = resolvedHub
                    provider = "HubCloud"
                    streamHeaders = mapOf("User-Agent" to UA)
                }
            }
            // 4. ClicknUpload Inside Form Resolver
            else if (rawUrl.contains("clicknupload.")) {
                val resolvedClickn = resolveClicknUpload(rawUrl)
                if (resolvedClickn != null) {
                    directStreamUrl = resolvedClickn
                    provider = "ClicknUpload"
                    streamHeaders = mapOf("User-Agent" to UA)
                }
            }
            // 5. Direct MP4 / MKV Streams (Tattooin, Vikingfile, etc.)
            else if (DIRECT_EXT_REGEX.containsMatchIn(rawUrl) &&
                !rawUrl.contains(".cyou/res/")
            ) {
                try {
                    val headReq = Request.Builder()
                        .url(rawUrl)
                        .header("User-Agent", UA)
                        .head()
                        .build()
                    val checkCode = resolverHttpClient.newCall(headReq).execute().use { it.code }
                    if (checkCode in 200..299 || checkCode == 302) {
                        directStreamUrl = rawUrl
                        provider = if (site.isNotEmpty()) site else "DirectStream"
                        streamHeaders = mapOf("User-Agent" to UA)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving candidate $rawUrl: ${e.message}")
        }

        if (directStreamUrl.isNullOrBlank()) return null

        val tags = mutableListOf<String>()
        val tagsArr = item.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.optString(i))
            }
        }

        val quality = tags.firstOrNull { Regex("2160p|4k|1080p|720p|480p", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: "1080p"
        val rawName = item.optString("name").ifEmpty { item.optString("release").ifEmpty { fallbackTitle } }
        val tagsStr = if (tags.isNotEmpty()) tags.joinToString(" · ") else quality

        return HttpStreamResult(
            sourceName = "DownloadEverything ($provider)",
            title = "[$provider] $rawName ($quality)",
            description = "$quality · $tagsStr · $provider",
            url = directStreamUrl,
            headers = streamHeaders ?: mapOf("User-Agent" to UA),
            quality = quality
        )
    }

    private fun resolveHubCloud(hubUrl: String): String? {
        try {
            val req1 = Request.Builder()
                .url(hubUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://downloadeverythingfromeverywhere.com/")
                .build()

            val html = resolverHttpClient.newCall(req1).execute().use { resp ->
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
                val phpBody = resolverHttpClient.newCall(req2).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }

                // 1. Cloudflare R2 signed link
                val r2Match = R2_REGEX.find(phpBody)
                if (r2Match != null) {
                    return r2Match.value.replace("&amp;", "&")
                }

                // 2. Pixel mirror link
                val pixelMatch = PIXEL_MIRROR_REGEX.find(phpBody)
                if (pixelMatch != null) {
                    return pixelMatch.value
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HubCloud resolve error: ${e.message}")
        }
        return null
    }

    private fun resolveClicknUpload(clicknUrl: String): String? {
        try {
            val req1 = Request.Builder()
                .url(clicknUrl)
                .header("User-Agent", UA)
                .header("Referer", "https://downloadeverythingfromeverywhere.com/")
                .build()

            var cookies = ""
            val html1 = resolverHttpClient.newCall(req1).execute().use { resp ->
                cookies = resp.header("Set-Cookie") ?: ""
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            val formPattern = Pattern.compile("<form[^>]+method=[\"']POST[\"'][^>]*>([\\s\\S]*?)</form>", Pattern.CASE_INSENSITIVE)
            val matcher1 = formPattern.matcher(html1)
            if (!matcher1.find()) return null

            val formBody1 = matcher1.group(1) ?: return null
            val inputPattern = Pattern.compile("<input[^>]+name=[\"']([^\"']+)[\"'][^>]+value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            val inputMatcher1 = inputPattern.matcher(formBody1)

            val formBodyBuilder = okhttp3.FormBody.Builder()
            while (inputMatcher1.find()) {
                val name = inputMatcher1.group(1) ?: continue
                val value = inputMatcher1.group(2) ?: ""
                formBodyBuilder.add(name, value)
            }
            formBodyBuilder.add("method_free", "Slow Download")

            val req2 = Request.Builder()
                .url(clicknUrl)
                .header("User-Agent", UA)
                .header("Referer", clicknUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                .post(formBodyBuilder.build())
                .build()

            val html2 = resolverHttpClient.newCall(req2).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            val matcher2 = formPattern.matcher(html2)
            if (!matcher2.find()) return null

            val formBody2 = matcher2.group(1) ?: return null
            val inputMatcher2 = inputPattern.matcher(formBody2)

            val formBodyBuilder2 = okhttp3.FormBody.Builder()
            while (inputMatcher2.find()) {
                val name = inputMatcher2.group(1) ?: continue
                val value = inputMatcher2.group(2) ?: ""
                formBodyBuilder2.add(name, value)
            }
            formBodyBuilder2.add("down_script", "1")

            Thread.sleep(4500)

            val req3 = Request.Builder()
                .url(clicknUrl)
                .header("User-Agent", UA)
                .header("Referer", clicknUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                .post(formBodyBuilder2.build())
                .build()

            val html3 = resolverHttpClient.newCall(req3).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            val directMatcher = Pattern.compile("https?://[a-zA-Z0-9.\\-_:]+/d/[a-zA-Z0-9_\\-/]+").matcher(html3)
            if (directMatcher.find()) {
                return directMatcher.group(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ClicknUpload resolve error: ${e.message}")
        }
        return null
    }
}
