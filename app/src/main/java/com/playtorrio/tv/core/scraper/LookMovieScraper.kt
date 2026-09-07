package com.playtorrio.tv.core.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin LookMovie Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla LookMovie provider.
 * Resolves multi-quality HLS streams from lookmovie2.to / lookmovie.foundation.
 */
class LookMovieScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "LookMovieScraper"
        private val DOMAINS = listOf(
            "https://www.lookmovie2.to",
            "https://lookmovie2.to",
            "https://lookmovie.foundation"
        )
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val HEADERS_BASE = mapOf(
            "User-Agent" to UA,
            "Accept-Language" to "en-US,en;q=0.9"
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun getEpisodeId(html: String, s: Int, e: Int): String? {
            val storageMatch = Regex("""window\[['"](?:movie|show)_storage['"]\]\s*=\s*\{([^}]+)\}""").find(html)
            if (storageMatch != null) {
                val block = storageMatch.groupValues[1]
                val sm = Regex("""seasons\s*:\s*(\[[\s\S]+?\])\s*[,}]""").find(block)
                if (sm != null) {
                    try {
                        val seasons = JSONArray(sm.groupValues[1])
                        for (i in 0 until seasons.length()) {
                            val seasonObj = seasons.optJSONObject(i) ?: continue
                            val sNum = seasonObj.optInt("season", -1).let {
                                if (it == -1) seasonObj.optJSONObject("meta")?.optInt("season", -1) ?: -1 else it
                            }
                            if (sNum == s) {
                                val eps = seasonObj.opt("episodes")
                                if (eps is JSONArray) {
                                    for (j in 0 until eps.length()) {
                                        val epObj = eps.optJSONObject(j) ?: continue
                                        if (epObj.optInt("episode") == e) {
                                            val idEp = epObj.optString("id_episode")
                                            return if (idEp.isNotBlank()) idEp else epObj.optString("id")
                                        }
                                    }
                                } else if (eps is JSONObject) {
                                    val directEp = eps.optJSONObject(e.toString())
                                    if (directEp != null) {
                                        val idEp = directEp.optString("id_episode")
                                        return if (idEp.isNotBlank()) idEp else directEp.optString("id")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            val am = Regex("""data-season=["']$s["'][^>]*?data-episode=["']$e["'][^>]*?data-id=["'](\d+)["']""").find(html)
                ?: Regex("""data-episode=["']$e["'][^>]*?data-season=["']$s["'][^>]*?data-id=["'](\d+)["']""").find(html)
            return am?.groupValues?.get(1)
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"
        val typeStr = if (isTv) "shows" else "movies"

        try {
            var matchedSlug: String? = null
            var matchedBase: String? = null
            var matchedIdMovie: String? = null

            val encodedTitle = URLEncoder.encode(request.title, "UTF-8")

            for (base in DOMAINS) {
                try {
                    val searchReq = Request.Builder()
                        .url("$base/api/v1/$typeStr/do-search/?q=$encodedTitle")
                        .apply { HEADERS_BASE.forEach { (k, v) -> header(k, v) } }
                        .header("Accept", "application/json")
                        .header("Referer", "$base/")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    httpClient.newCall(searchReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            if (body.startsWith("{")) {
                                val data = JSONObject(body)
                                if (data.has("result")) {
                                    val results = data.getJSONArray("result")
                                    if (results.length() > 0) {
                                        var m: JSONObject? = null
                                        if (request.year != null) {
                                            for (i in 0 until results.length()) {
                                                val r = results.optJSONObject(i) ?: continue
                                                if (r.optInt("year") == request.year) {
                                                    m = r
                                                    break
                                                }
                                            }
                                        }
                                        if (m == null) {
                                            for (i in 0 until results.length()) {
                                                val r = results.optJSONObject(i) ?: continue
                                                if (r.optString("title").equals(request.title, ignoreCase = true)) {
                                                    m = r
                                                    break
                                                }
                                            }
                                        }
                                        if (m == null) {
                                            m = results.optJSONObject(0)
                                        }

                                        if (m != null) {
                                            val slug = m.optString("slug")
                                            if (slug.isNotBlank()) {
                                                matchedSlug = slug
                                                matchedBase = base
                                                matchedIdMovie = m.optString("id_movie").ifEmpty { m.optString("id") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (matchedSlug != null) break
                } catch (_: Exception) {}
            }

            if (matchedSlug == null || matchedBase == null) return@withContext emptyList()

            val pageReq = Request.Builder()
                .url("$matchedBase/$typeStr/play/$matchedSlug")
                .apply { HEADERS_BASE.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "text/html")
                .header("Referer", "$matchedBase/")
                .build()

            val pageHtml = httpClient.newCall(pageReq).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (pageHtml.isEmpty()) return@withContext emptyList()

            val storageMatch = Regex("""window\[['"](?:movie|show)_storage['"]\]\s*=\s*\{([^}]+)\}""").find(pageHtml)
                ?: return@withContext emptyList()
            val block = storageMatch.groupValues[1]

            val hashMatch = Regex("""hash\s*:\s*['"]([^'"]+)['"]""").find(block)
            val expiresMatch = Regex("""expires\s*:\s*(\d+)""").find(block)
            if (hashMatch == null || expiresMatch == null) return@withContext emptyList()

            val hash = hashMatch.groupValues[1]
            val expires = expiresMatch.groupValues[1]

            val streamId = if (isTv) {
                getEpisodeId(pageHtml, request.season ?: 1, request.episode ?: 1)
            } else {
                if (!matchedIdMovie.isNullOrEmpty()) {
                    matchedIdMovie
                } else {
                    Regex("""['"]?(?:id_movie|movieId)['"]?\s*[:=]\s*['"]?(\d+)['"]?""").find(pageHtml)?.groupValues?.get(1)
                }
            }

            if (streamId.isNullOrEmpty()) return@withContext emptyList()

            val accessParam = if (isTv) "episode" else "movie"
            val accessUrl = "$matchedBase/api/v1/security/$accessParam-access?id_$accessParam=$streamId&hash=$hash&expires=$expires"

            val accessReq = Request.Builder()
                .url(accessUrl)
                .apply { HEADERS_BASE.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json")
                .header("Referer", "$matchedBase/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val results = mutableListOf<ScraperStreamResult>()

            httpClient.newCall(accessReq).execute().use { res ->
                if (!res.isSuccessful) return@use
                val body = res.body?.string().orEmpty()
                if (!body.startsWith("{")) return@use
                val data = JSONObject(body)

                val streams = if (data.has("streams")) {
                    data.optJSONObject("streams")
                } else if (data.has("result")) {
                    data.optJSONObject("result")?.optJSONObject("streams")
                } else if (data.has("data")) {
                    data.optJSONObject("data")?.optJSONObject("streams")
                } else null

                if (streams != null) {
                    val keys = streams.keys()
                    while (keys.hasNext()) {
                        val q = keys.next()
                        val url = streams.optString(q)
                        if (url.isNotBlank() && url.contains(".m3u8")) {
                            val qualityLabel = when {
                                q.contains("1080") -> "1080p"
                                q.contains("720") -> "720p"
                                else -> "${q}p"
                            }
                            val streamHeaders = mapOf(
                                "User-Agent" to UA,
                                "Referer" to "$matchedBase/"
                            )

                            results.add(
                                ScraperStreamResult(
                                    name = "PlayTorrioHTTP",
                                    title = "LookMovie · $qualityLabel",
                                    description = "LookMovie HLS Stream · $qualityLabel",
                                    url = url,
                                    quality = qualityLabel,
                                    headers = streamHeaders
                                )
                            )
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping LookMovie: ${e.message}")
            emptyList()
        }
    }
}
