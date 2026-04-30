package com.playtorrio.tv.data.reader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Port of lib/api/readcomicsonline_scraper.dart — secondary comics source.
 */
object ReadComicsOnlineScraper {
    const val SOURCE_TAG = "rcoru"
    const val BASE = "https://readcomicsonline.ru"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun ownsUrl(url: String): Boolean = runCatching {
        java.net.URI(url).host?.contains("readcomicsonline.ru") == true
    }.getOrElse { false }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: ""
        }
    }

    fun searchComics(query: String): List<Comic> = runCatching {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = get("$BASE/search?query=$q")
        val json = JSONObject(body)
        if (!json.has("suggestions")) return emptyList()
        val arr = json.getJSONArray("suggestions")
        val out = mutableListOf<Comic>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val title = o.optString("value").trim()
            val slug = o.optString("data").trim()
            if (title.isEmpty() || slug.isEmpty()) continue
            out.add(
                Comic(
                    title = title,
                    url = "$BASE/comic/$slug",
                    poster = "$BASE/uploads/manga/$slug/cover/cover_250x350.jpg",
                    source = SOURCE_TAG
                )
            )
        }
        out
    }.getOrElse { emptyList() }

    fun getComicDetails(comic: Comic): ComicDetails? = runCatching {
        val doc = Jsoup.parse(get(comic.url))

        var publisher = "Unknown"
        var writer = "Unknown"
        var artist = "Unknown"
        var publicationDate = "Unknown"
        var status = ""
        val genres = mutableListOf<String>()

        val dl = doc.selectFirst("dl.dl-horizontal")
        if (dl != null) {
            val children = dl.children()
            var i = 0
            while (i < children.size - 1) {
                val dt = children[i]
                if (dt.tagName() != "dt") { i++; continue }
                val dd = children[i + 1]
                if (dd.tagName() != "dd") { i++; continue }
                val label = dt.text().trim().lowercase()
                val value = dd.text().trim().replace(Regex("\\s+"), " ")
                when {
                    label.startsWith("status") -> status = value
                    label.startsWith("author") -> writer = value
                    label.startsWith("artist") -> artist = value
                    label.startsWith("date") -> publicationDate = value
                    label.startsWith("categor") -> genres.addAll(dd.select("a").map { it.text().trim() })
                    label.startsWith("type") -> publisher = value
                }
                i += 2
            }
        }

        var summary = ""
        for (h5 in doc.select("h5")) {
            if (h5.text().lowercase().contains("summary")) {
                val p = h5.nextElementSibling()
                if (p != null) summary = p.text().trim()
                break
            }
        }

        val chapters = mutableListOf<ComicChapter>()
        for (li in doc.select("ul.chapters li")) {
            val a = li.selectFirst("a") ?: continue
            val href = a.attr("href")
            if (href.isEmpty()) continue
            val full = if (href.startsWith("http")) href else "$BASE$href"
            val date = li.selectFirst(".date-chapter-title-rtl")?.text()?.trim().orEmpty()
            chapters.add(ComicChapter(title = a.text().trim(), url = full, dateAdded = date))
        }

        val enriched = comic.copy(
            status = if (status.isNotEmpty()) status else comic.status,
            publication = if (publicationDate != "Unknown") publicationDate else comic.publication,
            summary = if (summary.isNotEmpty()) summary else comic.summary,
            source = SOURCE_TAG
        )

        ComicDetails(
            comic = enriched,
            otherName = "None",
            genres = genres,
            publisher = publisher,
            writer = writer,
            artist = artist,
            publicationDate = publicationDate,
            chapters = chapters
        )
    }.getOrNull()

    fun getChapterPages(chapterUrl: String): List<String> {
        val body = get(chapterUrl)
        val re = Regex(
            """data-src=\s*['"]\s*(https?://readcomicsonline\.ru/uploads/manga/[^'"\s]+\.(?:jpg|jpeg|png|webp|gif))\s*['"]""",
            RegexOption.IGNORE_CASE
        )
        val urls = re.findAll(body).map { it.groupValues[1] }.toList()
        if (urls.isEmpty()) error("No comic pages found on this chapter page.")
        return urls
    }
}
