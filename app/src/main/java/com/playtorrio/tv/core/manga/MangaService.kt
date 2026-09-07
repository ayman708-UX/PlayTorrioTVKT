package com.playtorrio.tv.core.manga

import android.util.Log
import com.playtorrio.tv.core.manga.model.Manga
import com.playtorrio.tv.core.manga.model.MangaChapter
import com.playtorrio.tv.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MangaService"
private const val BASE_URL = "https://weebcentral.com"
private const val COVER_CDN = "https://temp.compsci88.com/cover"
private const val PAGE_SIZE = 32
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

@Singleton
class MangaService @Inject constructor() {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val seriesIdPattern = Pattern.compile("/series/([A-Z0-9]{26})")
    private val chapterIdPattern = Pattern.compile("/chapters/([A-Z0-9]{26})")
    private val yearPattern = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b")

    companion object {
        val popularGenres = listOf(
            "All",
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Harem",
            "Historical",
            "Horror",
            "Isekai",
            "Martial Arts",
            "Mature",
            "Mystery",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-fi",
            "Seinen",
            "Shounen",
            "Slice of Life",
            "Sports",
            "Supernatural",
            "Tragedy"
        )
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun extractSeriesId(url: String): String? {
        val matcher = seriesIdPattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractChapterId(url: String): String? {
        val matcher = chapterIdPattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun getManga(
        page: Int = 1,
        tag: String? = null,
        allowAdult: Boolean = false
    ): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * PAGE_SIZE
            val adult = if (allowAdult) "Any" else "False"
            var url = "$BASE_URL/search/data?text=&display_mode=Full+Display&sort=Popularity&order=Descending&official=Any&adult=$adult&offset=$offset"
            if (!tag.isNullOrBlank() && tag != "All") {
                url += "&included_tag=${URLEncoder.encode(tag, "UTF-8")}"
            }
            val html = fetchHtml(url)
            parseSearchResults(html)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchManga(
        query: String,
        page: Int = 1,
        allowAdult: Boolean = false
    ): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val offset = (page - 1) * PAGE_SIZE
            val adult = if (allowAdult) "Any" else "False"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/data?text=$encodedQuery&display_mode=Full+Display&sort=Best+Match&order=Descending&official=Any&adult=$adult&offset=$offset"
            val html = fetchHtml(url)
            parseSearchResults(html)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching manga for query '$query': ${e.message}", e)
            emptyList()
        }
    }

    private fun parseSearchResults(html: String): List<Manga> {
        val doc = Jsoup.parse(html)
        val articles = doc.select("article")
        val results = mutableListOf<Manga>()

        for (article in articles) {
            val seriesLink = article.selectFirst("a[href*=/series/]") ?: continue
            val href = seriesLink.attr("href")
            val seriesId = extractSeriesId(href) ?: continue

            var title = ""
            val titleLink = article.selectFirst("a.line-clamp-1, a.link-hover")
            if (titleLink != null && titleLink.text().isNotBlank()) {
                title = titleLink.text().trim()
            }

            if (title.isBlank()) {
                val img = article.selectFirst("img")
                val alt = img?.attr("alt").orEmpty().trim()
                if (alt.endsWith(" cover", ignoreCase = true)) {
                    title = alt.substring(0, alt.length - 6).trim()
                } else if (alt.isNotBlank()) {
                    title = alt
                }
            }

            if (title.isBlank()) {
                title = seriesLink.text().trim().lines().firstOrNull().orEmpty().trim()
            }

            title = title.replace(Regex("(?i)^Official\\s+"), "").trim()

            var type = ""
            for (el in article.select("[data-tip]")) {
                val tip = el.attr("data-tip").trim()
                if (tip in setOf("Manga", "Manhwa", "Manhua", "OEL")) {
                    type = tip
                    break
                }
            }

            var year = ""
            var status = ""
            var author = ""
            val tags = mutableListOf<String>()

            for (div in article.select("div, li")) {
                val strong = div.selectFirst("strong, b, span.font-bold, span.font-semibold") ?: continue
                val label = strong.text().trim().lowercase()

                if (label.contains("tag") || label.contains("genre")) {
                    for (sp in div.select("span")) {
                        val text = sp.text().replace(",", "").trim()
                        if (text.isNotBlank() && !text.lowercase().contains("tag") && !text.lowercase().contains("genre")) {
                            tags.add(text)
                        }
                    }
                } else if (label.contains("year") || label.contains("date")) {
                    val span = div.selectFirst("span")
                    if (span != null) year = span.text().trim()
                } else if (label.contains("status")) {
                    val span = div.selectFirst("span")
                    if (span != null) status = span.text().trim()
                } else if (label.contains("author")) {
                    val links = div.select("a")
                    if (links.isNotEmpty()) {
                        author = links.joinToString(", ") { it.text().trim() }
                    } else {
                        val span = div.selectFirst("span")
                        if (span != null) author = span.text().trim()
                    }
                }
            }

            results.add(
                Manga(
                    id = seriesId,
                    title = title,
                    coverSmall = "$COVER_CDN/small/$seriesId.webp",
                    coverNormal = "$COVER_CDN/normal/$seriesId.webp",
                    type = type,
                    status = status,
                    year = year,
                    author = author,
                    tags = tags,
                    url = href
                )
            )
        }

        return results
    }

    suspend fun getSeriesDetail(seriesId: String): Manga = withContext(Dispatchers.IO) {
        val html = fetchHtml("$BASE_URL/series/$seriesId")
        val doc = Jsoup.parse(html)

        var title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        title = title.replace(Regex("(?i)^Official\\s+"), "").trim()
        if (title.isBlank()) {
            val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
            title = ogTitle.replace(Regex("(?i)\\s*-\\s*Weeb\\s*Central.*$"), "").trim()
        }

        val sidebar = doc.selectFirst("section.md\\:w-4\\/12") ?: doc.selectFirst("section")
        val details = mutableMapOf<String, MutableList<String>>()
        val items = sidebar?.select("li, div.flex, div.grid") ?: doc.select("li")

        for (element in items) {
            val strong = element.selectFirst("strong, b, span.font-bold, span.font-semibold") ?: continue
            val label = strong.text().trim().replace(":", "").replace("(s)", "").trim()
            if (label.isBlank()) continue

            val links = element.select("a")
            val values = mutableListOf<String>()
            if (links.isNotEmpty()) {
                values.addAll(
                    links.map { it.text().trim() }
                        .filter { it.isNotBlank() && it != strong.text().trim() && !it.contains("RSS") }
                )
            } else {
                var rawText = element.text().trim()
                if (rawText.startsWith(strong.text().trim())) {
                    rawText = rawText.substring(strong.text().trim().length).trim()
                }
                rawText = rawText.replace(Regex("^\\s*:\\s*"), "").trim()
                if (rawText.isNotBlank()) {
                    values.add(rawText)
                }
            }

            if (values.isNotEmpty() && !details.containsKey(label)) {
                details[label] = values
            }
        }

        var year = ""
        val possibleYearKeys = listOf("Released", "Release Year", "Year", "Published", "Date", "Release")
        for (key in possibleYearKeys) {
            val vals = details[key]
            if (!vals.isNullOrEmpty()) {
                val rawVal = vals.first()
                val matcher = yearPattern.matcher(rawVal)
                if (matcher.find()) {
                    year = matcher.group(1).orEmpty()
                    break
                } else if (rawVal.isNotBlank()) {
                    year = rawVal
                    break
                }
            }
        }

        if (year.isBlank()) {
            val matcher = yearPattern.matcher(html)
            if (matcher.find()) {
                year = matcher.group(1).orEmpty()
            }
        }

        var synopsis = doc.selectFirst("p.whitespace-pre-wrap, .whitespace-pre-wrap")?.text()?.trim().orEmpty()
        if (synopsis.isBlank()) {
            for (p in doc.select("p")) {
                val text = p.text().trim()
                if (text.length > 30 &&
                    !text.contains("Copyright") &&
                    !text.contains("verified") &&
                    !text.contains("Last Read") &&
                    !text.contains("Chapter")
                ) {
                    synopsis = text
                    break
                }
            }
        }

        val tags = mutableListOf<String>()
        for ((k, v) in details) {
            val lower = k.lowercase()
            if (lower.contains("tag") || lower.contains("genre")) {
                tags.addAll(v)
            }
        }
        if (tags.isEmpty()) {
            tags.addAll(details["Tag"] ?: details["Tags"] ?: details["Genres"] ?: emptyList())
        }

        val type = details["Type"]?.firstOrNull().orEmpty()
        val status = details["Status"]?.firstOrNull().orEmpty()
        val author = (details["Author"] ?: details["Author(s)"] ?: emptyList()).joinToString(", ")

        Manga(
            id = seriesId,
            title = title,
            coverSmall = "$COVER_CDN/small/$seriesId.webp",
            coverNormal = "$COVER_CDN/normal/$seriesId.webp",
            type = type,
            status = status,
            year = year,
            author = author,
            tags = tags.distinct(),
            synopsis = synopsis,
            url = "/series/$seriesId"
        )
    }

    suspend fun getChapters(seriesId: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml("$BASE_URL/series/$seriesId/full-chapter-list")
            val doc = Jsoup.parse(html)
            val chapters = mutableListOf<MangaChapter>()
            val links = doc.select("a[href*=/chapters/]")

            for (a in links) {
                val href = a.attr("href")
                val chapterId = extractChapterId(href) ?: continue

                val titleSpan = a.selectFirst(".grow > span:first-child")
                    ?: a.selectFirst("span:not([class*=\"link-info\"]):not([class*=\"me-2\"])")

                var chapterName = titleSpan?.text()?.trim().orEmpty()
                if (chapterName.isBlank()) {
                    var fullText = a.text().trim()
                    fullText = fullText.replace(Regex("(?i)Last Read"), "").trim()
                    chapterName = fullText
                }

                val date = a.selectFirst("time")?.text()?.trim().orEmpty()

                if (chapterName.isNotBlank()) {
                    chapters.add(MangaChapter.fromRaw(chapterId, chapterName, href, date))
                }
            }

            chapters
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapters for $seriesId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getChapterImages(chapterId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/chapters/$chapterId/images?is_prev=False&current_page=1&reading_style=long_strip"
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val images = mutableListOf<String>()

            for (img in doc.select("img")) {
                val src = img.attr("src").trim()
                if (src.isNotBlank() && !src.contains("/static/") && !src.contains("brand")) {
                    images.add(src)
                }
            }

            images
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter images for $chapterId: ${e.message}", e)
            emptyList()
        }
    }
}
