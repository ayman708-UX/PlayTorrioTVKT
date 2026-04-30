package com.playtorrio.tv.data.reader

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

object MangaService {
    private const val BASE = "https://weebcentral.com"
    private const val COVER_CDN = "https://temp.compsci88.com/cover"
    private const val PAGE_SIZE = 32
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: ""
        }
    }

    private val SERIES_ID_RE = Regex("/series/([A-Z0-9]{26})")
    private val CHAPTER_ID_RE = Regex("/chapters/([A-Z0-9]{26})")

    private fun extractSeriesId(url: String): String? = SERIES_ID_RE.find(url)?.groupValues?.get(1)
    private fun extractChapterId(url: String): String? = CHAPTER_ID_RE.find(url)?.groupValues?.get(1)

    suspend fun getManga(page: Int = 1, allowAdult: Boolean = false): List<Manga> = runCatching {
        val offset = (page - 1) * PAGE_SIZE
        val adult = if (allowAdult) "Any" else "False"
        val url =
            "$BASE/search/data?text=&display_mode=Full+Display&sort=Popularity&order=Descending&official=Any&adult=$adult&offset=$offset"
        parseSearchResults(fetchHtml(url))
    }.getOrElse { emptyList() }

    suspend fun searchManga(query: String, page: Int = 1, allowAdult: Boolean = false): List<Manga> = runCatching {
        val offset = (page - 1) * PAGE_SIZE
        val adult = if (allowAdult) "Any" else "False"
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val url =
            "$BASE/search/data?text=$q&display_mode=Full+Display&sort=Best+Match&order=Descending&official=Any&adult=$adult&offset=$offset"
        parseSearchResults(fetchHtml(url))
    }.getOrElse { emptyList() }

    private fun parseSearchResults(html: String): List<Manga> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Manga>()
        for (article in doc.select("article")) {
            val seriesLink = article.selectFirst("a[href*=/series/]") ?: continue
            val href = seriesLink.attr("href")
            val sid = extractSeriesId(href) ?: continue

            val img = article.selectFirst("img")
            val alt = img?.attr("alt").orEmpty()
            var title = if (alt.endsWith(" cover")) alt.substring(0, alt.length - 6) else ""
            if (title.isEmpty()) {
                title = article.selectFirst(".truncate")?.text()?.trim()
                    ?: article.selectFirst(".line-clamp-1")?.text()?.trim()
                    ?: seriesLink.text().trim().lineSequence().firstOrNull()?.trim().orEmpty()
            }

            var type = ""
            for (el in article.select("[data-tip]")) {
                val tip = el.attr("data-tip")
                if (tip in listOf("Manga", "Manhwa", "Manhua", "OEL")) {
                    type = tip
                    break
                }
            }

            out.add(
                Manga(
                    id = sid,
                    title = title,
                    coverSmall = "$COVER_CDN/small/$sid.webp",
                    coverNormal = "$COVER_CDN/normal/$sid.webp",
                    type = type,
                    url = href
                )
            )
        }
        return out
    }

    suspend fun getSeriesDetail(seriesId: String): Manga = runCatching {
        val doc = Jsoup.parse(fetchHtml("$BASE/series/$seriesId"))
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()

        val details = mutableMapOf<String, List<String>>()
        for (li in doc.select("li")) {
            val strong = li.selectFirst("strong") ?: continue
            val label = strong.text().trim().replace(":", "").replace("(s)", "")
            val links = li.select("a")
            val spans = li.select("span")
            if (links.isNotEmpty()) {
                details[label] = links.map { it.text().trim() }.filter { it.isNotEmpty() }
            } else if (spans.isNotEmpty()) {
                details[label] = spans.map { it.text().trim() }.filter { it.isNotEmpty() }
            }
        }

        var synopsis = ""
        for (p in doc.select("p")) {
            val text = p.text().trim()
            if (text.length > 50 && !text.contains("Copyright") && !text.contains("verified")) {
                synopsis = text
                break
            }
        }

        Manga(
            id = seriesId,
            title = title,
            coverSmall = "$COVER_CDN/small/$seriesId.webp",
            coverNormal = "$COVER_CDN/normal/$seriesId.webp",
            type = (details["Type"] ?: emptyList()).firstOrNull().orEmpty(),
            status = (details["Status"] ?: emptyList()).firstOrNull().orEmpty(),
            year = (details["Released"] ?: emptyList()).firstOrNull().orEmpty(),
            author = (details["Author"] ?: emptyList()).joinToString(", "),
            tags = details["Tag"] ?: emptyList(),
            synopsis = synopsis,
            url = "/series/$seriesId"
        )
    }.getOrElse {
        Manga(
            id = seriesId,
            title = "",
            coverSmall = "$COVER_CDN/small/$seriesId.webp",
            coverNormal = "$COVER_CDN/normal/$seriesId.webp"
        )
    }

    suspend fun getChapters(seriesId: String): List<MangaChapter> = runCatching {
        val doc = Jsoup.parse(fetchHtml("$BASE/series/$seriesId/full-chapter-list"))
        val chapters = mutableListOf<MangaChapter>()
        for (a in doc.select("a[href*=/chapters/]")) {
            val href = a.attr("href")
            val cid = extractChapterId(href) ?: continue

            // WeebCentral renders multiple spans per chapter; the chapter name span
            // is the one that looks like "Chapter <num>" / "Volume <num>". Other
            // spans hold status badges ("Last Read"), dates, or inline-svg styles.
            var name = ""
            val candidates = a.select("span").map { it.text().trim() }
                .filter { t ->
                    t.isNotEmpty() &&
                        !t.contains("{") && !t.contains(".st0") && !t.contains("fill:")
                }
            // Prefer a span matching "Chapter N" / "Vol N" / "Episode N".
            val nameRe = Regex("""^(chapter|vol(ume)?|episode|ep)\b""", RegexOption.IGNORE_CASE)
            name = candidates.firstOrNull { nameRe.containsMatchIn(it) }
                ?: candidates.firstOrNull { it.lowercase() != "last read" && Regex("""\d""").containsMatchIn(it) }
                ?: candidates.firstOrNull { it.lowercase() != "last read" }
                ?: ""
            if (name.isNotEmpty()) chapters.add(MangaChapter.fromRaw(cid, name, href))
        }
        chapters
    }.getOrElse { emptyList() }

    suspend fun getChapterImages(chapterId: String): List<String> = runCatching {
        val url = "$BASE/chapters/$chapterId/images?is_prev=False&current_page=1&reading_style=long_strip"
        val doc = Jsoup.parse(fetchHtml(url))
        val images = mutableListOf<String>()
        for (img in doc.select("img")) {
            val src = img.attr("src")
            if (src.isNotEmpty() && !src.contains("/static/") && !src.contains("brand")) {
                images.add(src)
            }
        }
        images
    }.getOrElse { emptyList() }
}
