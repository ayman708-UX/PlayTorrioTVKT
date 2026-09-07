package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin FshareTV Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla FshareTV provider.
 * Resolves high-speed MP4 & HLS streams from fsharetv.cc.
 */
class FshareTvScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "FshareTvScraper"
        private const val BASE_URL = "https://fsharetv.cc"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "$BASE_URL/"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        if (request.type == "tv" || request.type == "series" || request.season != null) {
            return@withContext emptyList()
        }

        try {
            var resolvedImdbId = request.imdbId
            if (resolvedImdbId == null || !resolvedImdbId.startsWith("tt")) {
                val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
                    imdbId = request.imdbId,
                    title = request.title,
                    type = "movie",
                    year = request.year
                )

                if (tmdbId != null) {
                    try {
                        val uri = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=b3556f3b206e16f82df4d1f6fd4545e6"
                        val req = Request.Builder().url(uri).build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val body = res.body?.string().orEmpty()
                                val json = JSONObject(body)
                                val imdb = json.optString("imdb_id")
                                if (imdb.isNotBlank()) resolvedImdbId = imdb
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (resolvedImdbId == null || !resolvedImdbId!!.startsWith("tt")) return@withContext emptyList()

            val pageReq = Request.Builder()
                .url("$BASE_URL/movie/$resolvedImdbId")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val watchPath = httpClient.newCall(pageReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val m = Regex("""href="(/w/[^"]+)"""").find(html)
                m?.groupValues?.get(1)
            } ?: return@withContext emptyList()

            val watchReq = Request.Builder()
                .url("$BASE_URL$watchPath")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .build()

            val sourceId = httpClient.newCall(watchReq).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val html = res.body?.string().orEmpty()
                val m = Regex("""(?:source_id|file_id|setSource)[\s=:\('"]+([^'"\)]+)""", RegexOption.IGNORE_CASE)
                    .find(html)
                m?.groupValues?.get(1)
            } ?: return@withContext emptyList()

            val apiReq = Request.Builder()
                .url("$BASE_URL/api/file/$sourceId/source?trailer=Png81APqcxU&type=watch")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL$watchPath")
                .build()

            val results = mutableListOf<ScraperStreamResult>()
            val seen = mutableSetOf<String>()

            httpClient.newCall(apiReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val json = JSONObject(body)
                if (json.optString("status") != "ok") return@use

                val data = json.optJSONObject("data") ?: return@use
                val file = data.optJSONObject("file") ?: return@use

                val groups = mutableListOf<org.json.JSONArray>()
                if (file.has("sources")) {
                    groups.add(file.getJSONArray("sources"))
                }
                if (file.has("alternatives")) {
                    val alt = file.getJSONArray("alternatives")
                    for (i in 0 until alt.length()) {
                        val item = alt.optJSONArray(i)
                        if (item != null) groups.add(item)
                    }
                }

                for (group in groups) {
                    for (i in 0 until group.length()) {
                        val item = group.optJSONObject(i) ?: continue
                        val rawSrc = item.optString("src")
                        if (rawSrc.isBlank()) continue
                        val srcUrl = if (rawSrc.startsWith("http")) rawSrc else "$BASE_URL$rawSrc"

                        if (seen.add(srcUrl)) {
                            val quality = item.optString("quality", "1080").ifEmpty { "1080" }
                            val label = item.optString("label").trim()
                            val isHls = srcUrl.contains(".m3u8")

                            val titleParts = mutableListOf("FshareTV")
                            if (label.isNotEmpty()) titleParts.add(label)
                            titleParts.add("${quality}p")

                            val descParts = mutableListOf("FshareTV Stream")
                            if (label.isNotEmpty()) descParts.add(label)
                            descParts.add(if (isHls) "HLS" else "MP4")

                            results.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = titleParts.joinToString(" · "),
                                    description = descParts.joinToString(" · "),
                                    url = srcUrl,
                                    quality = "${quality}p",
                                    headers = HEADERS
                                )
                            )
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping FshareTV: ${e.message}")
            emptyList()
        }
    }
}
