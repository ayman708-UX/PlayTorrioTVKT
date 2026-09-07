package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class AniDbExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "AniDbExtractor"
        private const val BASE_URL = "https://anidb.app"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private data class AniDbEpisode(
        val number: Int,
        val id: String
    )

    private val slugCache = ConcurrentHashMap<String, String>()
    private val episodesCache = ConcurrentHashMap<String, List<AniDbEpisode>>()

    private fun fetch(url: String): String {
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "$BASE_URL/")
            .addHeader("Origin", BASE_URL)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun mapAnime(titleCandidates: List<String>): String? {
        val uniqueTitles = titleCandidates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueTitles.isEmpty()) return null

        val cacheKey = uniqueTitles.first().lowercase()
        slugCache[cacheKey]?.let { return it }

        for (title in uniqueTitles) {
            try {
                val searchUrl = "$BASE_URL/search/suggestions?q=${URLEncoder.encode(title, "UTF-8")}"
                val html = fetch(searchUrl)

                val results = mutableListOf<Pair<String, String>>()
                if (html.isNotBlank()) {
                    val doc = Jsoup.parse(html)
                    val links = doc.select("a[href*=/anime/]")
                    for (el in links) {
                        val href = el.attr("href")
                        val itemTitle = el.selectFirst("p")?.text()?.trim()
                            ?: el.attr("title").trim()
                            ?: el.text().trim()

                        if (href.isNotBlank() && itemTitle.isNotBlank()) {
                            val match = Regex("""/anime/([a-z0-9-]+-[0-9]+)""", RegexOption.IGNORE_CASE).find(href)
                            if (match != null) {
                                results.add(match.groupValues[1] to itemTitle)
                            }
                        }
                    }
                }

                if (results.isEmpty()) {
                    val browseUrl = "$BASE_URL/browse?q=${URLEncoder.encode(title, "UTF-8")}"
                    val browseHtml = fetch(browseUrl)
                    if (browseHtml.isNotBlank()) {
                        val doc = Jsoup.parse(browseHtml)
                        val links = doc.select("a[href*=/anime/]")
                        for (el in links) {
                            val href = el.attr("href")
                            val itemTitle = el.attr("title").trim().ifBlank {
                                el.selectFirst("h3, p, span")?.text()?.trim().orEmpty()
                            }.ifBlank { el.text().trim() }

                            if (href.isNotBlank() && itemTitle.isNotBlank()) {
                                val match = Regex("""/anime/([a-z0-9-]+-[0-9]+)""", RegexOption.IGNORE_CASE).find(href)
                                if (match != null) {
                                    results.add(match.groupValues[1] to itemTitle)
                                }
                            }
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    val normalized = title.lowercase().trim()
                    val exactMatch = results.firstOrNull { it.second.lowercase().trim() == normalized }
                        ?: results.first()
                    val slug = exactMatch.first
                    slugCache[cacheKey] = slug
                    return slug
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error searching AniDB for $title: ${e.message}")
            }
        }
        return null
    }

    private fun getEpisodes(providerId: String): List<AniDbEpisode>? {
        if (providerId.isBlank()) return null
        episodesCache[providerId]?.let { return it }

        try {
            val numericId = providerId.substringAfterLast('-')
            if (numericId.isBlank()) return null

            val episodesUrl = "$BASE_URL/api/frontend/anime/$numericId/episodes"
            val jsonStr = fetch(episodesUrl)
            if (jsonStr.isNotBlank()) {
                val data = JSONObject(jsonStr)
                val episodesArr = data.optJSONArray("episodes")
                if (episodesArr != null) {
                    val list = mutableListOf<AniDbEpisode>()
                    for (i in 0 until episodesArr.length()) {
                        val epObj = episodesArr.optJSONObject(i) ?: continue
                        val num = epObj.optInt("number", 1)
                        val id = epObj.optString("id")
                        if (id.isNotBlank()) {
                            list.add(AniDbEpisode(number = num, id = id))
                        }
                    }
                    list.sortBy { it.number }
                    episodesCache[providerId] = list
                    return list
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching AniDB episodes: ${e.message}")
        }
        return null
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): AnimeStreamResult? = withContext(Dispatchers.IO) {
        val cat = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"
        try {
            val slug = mapAnime(titleCandidates) ?: return@withContext null
            val episodes = getEpisodes(slug) ?: return@withContext null
            if (episodes.isEmpty()) return@withContext null

            val ep = episodes.firstOrNull { it.number == episodeNumber } ?: episodes.first()
            if (ep.number != episodeNumber && episodes.any { it.number == episodeNumber }) {
                return@withContext null
            }

            val languagesUrl = "$BASE_URL/api/frontend/episode/${ep.id}/languages"
            val langJsonStr = fetch(languagesUrl)
            if (langJsonStr.isBlank()) return@withContext null

            val langData = JSONObject(langJsonStr)
            val langArr = langData.optJSONArray("languages") ?: return@withContext null
            if (langArr.length() == 0) return@withContext null

            var targetLang: JSONObject? = null
            if (cat == "dub") {
                for (i in 0 until langArr.length()) {
                    val l = langArr.optJSONObject(i) ?: continue
                    val code = l.optString("code")
                    val name = l.optString("name").lowercase()
                    if (code == "eng" || name.contains("english") || name.contains("dub")) {
                        targetLang = l
                        break
                    }
                }
            } else {
                for (i in 0 until langArr.length()) {
                    val l = langArr.optJSONObject(i) ?: continue
                    val code = l.optString("code")
                    val name = l.optString("name").lowercase()
                    if (code == "jpn" || name.contains("japanese") || name.contains("sub")) {
                        targetLang = l
                        break
                    }
                }
            }

            if (targetLang == null) {
                targetLang = langArr.optJSONObject(0)
            }

            val embedUrl = targetLang?.optString("embed_url")
            if (embedUrl.isNullOrBlank()) return@withContext null

            val embedHtml = fetch(embedUrl)
            if (embedHtml.isBlank()) return@withContext null

            val fileMatch = Regex("""file:\s*['"]([^'"]+)['"]""").find(embedHtml)
            val masterUrl = fileMatch?.groupValues?.get(1)

            if (!masterUrl.isNullOrBlank() && masterUrl.startsWith("http")) {
                return@withContext AnimeStreamResult(
                    streamUrl = masterUrl,
                    serverName = "AniDB",
                    category = cat.uppercase(),
                    quality = "1080p",
                    headers = mapOf(
                        "Referer" to "$BASE_URL/",
                        "Origin" to BASE_URL,
                        "User-Agent" to USER_AGENT
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniDb scrape error: ${e.message}")
        }
        null
    }
}
