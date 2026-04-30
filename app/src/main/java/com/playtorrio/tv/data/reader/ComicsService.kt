package com.playtorrio.tv.data.reader

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Port of lib/api/comics_service.dart — readcomiconline.li scraper +
 * faithful Kotlin port of the rguard.min.js obfuscated page-URL decoder.
 *
 * Image URLs are returned as raw upstream URLs. The reader UI loads them via
 * [ComicImageLoader] which adds the right Referer header per host.
 */
object ComicsService {
    private const val BASE = "https://readcomiconline.li"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun fetchHtml(url: String, post: String? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
        val req = if (post != null) {
            builder
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(post.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
        } else builder.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: ""
        }
    }

    suspend fun getComics(page: Int = 1): List<Comic> = runCatching {
        parseComics(fetchHtml("$BASE/ComicList?page=$page"))
    }.getOrElse { emptyList() }

    suspend fun searchComics(query: String): List<Comic> {
        val rco = runCatching { searchRco(query) }.getOrElse { emptyList() }
        val rcoru = runCatching { ReadComicsOnlineScraper.searchComics(query) }.getOrElse { emptyList() }
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<Comic>()
        for (list in listOf(rco, rcoru)) {
            for (c in list) {
                val key = c.title.lowercase().replace(Regex("\\s+"), " ").trim()
                if (key.isEmpty() || key in seen) continue
                seen.add(key)
                merged.add(c)
            }
        }
        return merged
    }

    private fun searchRco(query: String): List<Comic> = runCatching {
        parseComics(fetchHtml("$BASE/Search/Comic", post = "keyword=$query"))
    }.getOrElse { emptyList() }

    suspend fun getComicDetails(comic: Comic): ComicDetails? {
        if (comic.source == ReadComicsOnlineScraper.SOURCE_TAG ||
            ReadComicsOnlineScraper.ownsUrl(comic.url)) {
            return ReadComicsOnlineScraper.getComicDetails(comic)
        }
        return runCatching {
            var url = if (comic.url.startsWith("http")) comic.url else "$BASE${comic.url}"
            if (!url.contains("s=s2")) {
                url += if (url.contains("?")) "&s=s2" else "?s=s2"
            }
            val doc = Jsoup.parse(fetchHtml(url))

            var otherName = "None"
            var genres = listOf<String>()
            var publisher = "Unknown"
            var writer = "Unknown"
            var artist = "Unknown"
            var publicationDate = "Unknown"

            for (p in doc.select(".barContent p")) {
                val infoSpan = p.selectFirst(".info") ?: continue
                val label = infoSpan.text().lowercase()
                val content = p.text().replaceFirst(infoSpan.text(), "").trim()

                if (label.contains("other name")) otherName = content
                if (label.contains("genres")) {
                    genres = p.select("a").map { it.text().trim() }
                }
                if (label.contains("publisher")) publisher = content
                if (label.contains("writer")) writer = content
                if (label.contains("artist")) artist = content
                if (label.contains("publication date")) publicationDate = content
            }

            val chapters = mutableListOf<ComicChapter>()
            val table = doc.selectFirst("table.listing")
            if (table != null) {
                for (row in table.select("tr")) {
                    val link = row.selectFirst("a") ?: continue
                    val tds = row.select("td")
                    val date = if (tds.size > 1) tds[1].text().trim() else ""
                    var chapterUrl = link.attr("href")
                    if (chapterUrl.isNotEmpty() && !chapterUrl.contains("s=s2")) {
                        chapterUrl += if (chapterUrl.contains("?")) "&s=s2" else "?s=s2"
                    }
                    chapters.add(ComicChapter(title = link.text().trim(), url = chapterUrl, dateAdded = date))
                }
            }

            ComicDetails(
                comic = comic,
                otherName = otherName,
                genres = genres,
                publisher = publisher,
                writer = writer,
                artist = artist,
                publicationDate = publicationDate,
                chapters = chapters
            )
        }.getOrNull()
    }

    /**
     * Returns raw image URLs (no proxy wrapper). Caller must use
     * [ComicImageLoader] (Coil) so the Referer header is sent.
     */
    suspend fun getChapterPages(chapterUrl: String): List<String> {
        if (ReadComicsOnlineScraper.ownsUrl(chapterUrl)) {
            return ReadComicsOnlineScraper.getChapterPages(chapterUrl)
        }
        var url = if (chapterUrl.startsWith("http")) chapterUrl else "$BASE$chapterUrl"
        if (!url.contains("s=s2")) {
            url += if (url.contains("?")) "&s=s2" else "?s=s2"
        }

        val html = fetchHtml(url)

        val callMatch = Regex("""src\s*=\s*"'\s*\+\s*(f\w+)\s*\(\s*3\s*,\s*(\w+)\[""").find(html)
            ?: error("Could not locate decoder call site in chapter HTML.")
        val funcName = callMatch.groupValues[1]
        val arrName = callMatch.groupValues[2]

        val funcBodyMatch = Regex(
            "function\\s+${Regex.escape(funcName)}\\s*\\(\\s*z\\s*,\\s*l\\s*\\)\\s*\\{([\\s\\S]*?)\\}"
        ).find(html)
        val outerRules = mutableListOf<Pair<String, String>>()
        var baseUrl = "https://ano1.rconet.biz/pic"
        if (funcBodyMatch != null) {
            val body = funcBodyMatch.groupValues[1]
            val ruleRe = Regex("""l\s*=\s*l\.replace\(/([^/]+)/g,\s*'([^']*)'\)""")
            for (m in ruleRe.findAll(body)) {
                outerRules.add(m.groupValues[1] to m.groupValues[2])
            }
            val baseMatch = Regex("""baeu\s*\(\s*l\s*,\s*'([^']+)'\s*\)""").find(body)
            if (baseMatch != null) baseUrl = baseMatch.groupValues[1]
        }

        val encVarName = "${arrName}xnz"
        val valueRe = Regex(Regex.escape(encVarName) + """\s*=\s*'([^']+)'""")
        val encodedValues = valueRe.findAll(html).map { it.groupValues[1] }.toList()
        if (encodedValues.isEmpty()) error("No comic pages found on this chapter page.")

        val pageUrls = encodedValues
            .mapNotNull { decodeEncodedValue(it, outerRules, baseUrl).takeIf { d -> d.isNotEmpty() } }
        if (pageUrls.isEmpty()) error("Failed to decode any comic page URLs.")
        return pageUrls
    }

    /**
     * Faithful Kotlin port of the Dart `_decodeEncodedValue` from
     * lib/api/comics_service.dart. See the Dart source for protocol notes.
     */
    private fun decodeEncodedValue(
        enc: String,
        outerRules: List<Pair<String, String>>,
        baseUrl: String
    ): String {
        return try {
            var l = enc
            for ((pat, rep) in outerRules) {
                l = Regex(pat).replace(l, Regex.escapeReplacement(rep))
            }
            // baeu reverse replacements (cancels b/h obfuscation)
            l = l.replace(Regex("pw_\\.g28x"), "b").replace(Regex("d2pr\\.x_27"), "h")

            val qi = l.indexOf('?')
            val trailer = if (qi >= 0) l.substring(qi) else ""
            var e: String
            val suffix: String
            val s0Idx = l.indexOf("=s0?")
            if (s0Idx > 0) {
            e = l.substring(0, s0Idx)
            suffix = "=s0"
        } else {
            val s16Idx = l.indexOf("=s1600?")
            if (s16Idx > 0) {
                e = l.substring(0, s16Idx)
                suffix = "=s1600"
            } else {
                when {
                    l.endsWith("=s0") -> {
                        e = l.substring(0, l.length - 3); suffix = "=s0"
                    }
                    l.endsWith("=s1600") -> {
                        e = l.substring(0, l.length - 6); suffix = "=s1600"
                    }
                    else -> {
                        e = l; suffix = "=s1600"
                    }
                }
            }
        }

        if (e.length < 50) return ""
        e = e.substring(15, 33) + e.substring(50)
        if (e.length < 11) return ""
        e = e.substring(0, e.length - 11) + e.substring(e.length - 2)

        val padded = e + "=".repeat((4 - e.length % 4) % 4)
        val bytes = Base64.decode(padded, Base64.DEFAULT)
        var d = String(bytes, Charsets.UTF_8)

        if (d.length <= 17) return ""
        d = d.substring(0, 13) + d.substring(17)
        if (d.length < 2) return ""
        d = d.substring(0, d.length - 2) + suffix

        val base = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
        "$base/$d$trailer"
        } catch (e: Throwable) {
            ""
        }
    }

    private fun parseComics(html: String): List<Comic> {
        val out = mutableListOf<Comic>()
        val doc = Jsoup.parse(html)
        val items = doc.select(".list-comic .item, .item")
        for (item in items) {
            val titleAttr = item.attr("title")
            val titleDoc = Jsoup.parse(titleAttr)

            val title = titleDoc.selectFirst(".title")?.text()
                ?: item.selectFirst(".title")?.text()
                ?: "Unknown"
            val status = extractFromTitle(titleAttr, "Status:")
            val publication = extractFromTitle(titleAttr, "Publication:")
            val summary = titleDoc.selectFirst(".description")?.text() ?: "No summary available"

            val link = item.selectFirst("a")
            val url = link?.attr("href").orEmpty()
            val img = item.selectFirst("img")
            var poster = img?.attr("src").orEmpty()
            if (poster.isNotEmpty() && !poster.startsWith("http")) poster = "$BASE$poster"

            if (title != "Unknown" && url.isNotEmpty()) {
                out.add(
                    Comic(
                        title = title.trim(),
                        url = url,
                        poster = poster,
                        status = status,
                        publication = publication,
                        summary = summary.trim()
                    )
                )
            }
        }
        return out
    }

    private fun extractFromTitle(titleAttr: String, label: String): String {
        val doc = Jsoup.parse(titleAttr)
        for (strong in doc.select("strong")) {
            if (strong.text().contains(label)) {
                val parentText = strong.parent()?.text().orEmpty()
                return parentText.replaceFirst(strong.text(), "").trim()
            }
        }
        return "Unknown"
    }
}
