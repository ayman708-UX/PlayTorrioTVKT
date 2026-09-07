package com.playtorrio.tv.core.audiobook.scrapers

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookChapter
import com.playtorrio.tv.core.audiobook.model.AudiobookRow
import com.playtorrio.tv.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudiobookScraperService"

@Singleton
class AudiobookScraperService @Inject constructor(
    private val audiobookBayScraper: AudiobookBayScraper
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    // Audionest auth token cache
    private var audionestToken: String? = null
    private var audionestTokenExpiryMs: Long = 0L

    companion object {
        fun cleanAudiobookTitle(rawTitle: String, sourceId: String): String {
            var title = rawTitle

            // 1. Decode numeric entities &#xxxx;
            val numEntityPattern = Pattern.compile("&#(\\d+);")
            val numMatcher = numEntityPattern.matcher(title)
            val sb = StringBuffer()
            while (numMatcher.find()) {
                val code = numMatcher.group(1)?.toIntOrNull()
                val replacement = when (code) {
                    8220, 8221 -> "\""
                    8211, 8212 -> "-"
                    8216, 8217 -> "'"
                    in 1..65535 -> code?.toChar()?.toString() ?: numMatcher.group(0)
                    else -> numMatcher.group(0)
                }
                numMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
            }
            numMatcher.appendTail(sb)
            title = sb.toString()

            // 2. Decode named & remaining string entities
            title = title
                .replace("&#8220;", "\"")
                .replace("&#8221;", "\"")
                .replace("&#8211;", "-")
                .replace("&#8212;", "-")
                .replace("&#8217;", "'")
                .replace("&#8216;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#039;", "'")
                .replace("&nbsp;", " ")

            if (sourceId == "audiozaic") {
                title = title.replace(Regex("""\[(Listen|Download|Audiobook)[^\]]*]""", RegexOption.IGNORE_CASE), "")
                title = title.replace(Regex("""^(Listen|Download)\s*""", RegexOption.IGNORE_CASE), "")

                val quoteMatch = Regex("""["“]([^"”]+)["”]""").find(title)
                if (quoteMatch != null) {
                    title = quoteMatch.groupValues[1]
                } else {
                    title = title.replace(Regex("""^["“\s]+"""), "")
                    val andMoreIdx = title.lowercase().indexOf(" and more")
                    if (andMoreIdx != -1) {
                        title = title.substring(0, andMoreIdx)
                    }
                }
                title = title.replace(Regex("""\s+Audiobook.*$""", RegexOption.IGNORE_CASE), "")
                title = title.replace(Regex("""^["“]+|["”]+$"""), "")
            } else if (sourceId == "goldenaudiobooks") {
                title = title.replace(Regex("[-–—]"), " ")
                title = title.replace(Regex("""\s+"""), " ")
            }

            title = title.replace(Regex("""\s+Audiobook$""", RegexOption.IGNORE_CASE), "")
            title = title.replace(Regex("""^[\s\-"“”:]+|[\s\-"“”:]+$"""), "").trim()

            return title
        }
    }

    suspend fun search(query: String): List<Audiobook> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = coroutineScope {
            val tasks = listOf(
                async { runCatching { searchGolden(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchFullLength(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchHot(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchBookAudio(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchAudiozaic(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchAudioAZ(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchAudiobooks4Soul(query) }.getOrDefault(emptyList()) },
                async { runCatching { searchAudionest(query) }.getOrDefault(emptyList()) },
                async { runCatching { audiobookBayScraper.search(query) }.getOrDefault(emptyList()) }
            )
            tasks.awaitAll().flatten()
        }

        // Deduplicate by title
        val seen = mutableSetOf<String>()
        val uniqueBooks = results.filter { b ->
            val key = b.title.lowercase().trim()
            key.isNotBlank() && seen.add(key)
        }.toMutableList()

        // Sort by relevance to query
        val qLower = query.lowercase().trim()
        uniqueBooks.sortWith { a, b ->
            val aTitle = a.title.lowercase().trim()
            val bTitle = b.title.lowercase().trim()

            if (aTitle == qLower && bTitle != qLower) return@sortWith -1
            if (bTitle == qLower && aTitle != qLower) return@sortWith 1

            val aStarts = aTitle.startsWith(qLower)
            val bStarts = bTitle.startsWith(qLower)
            if (aStarts && !bStarts) return@sortWith -1
            if (bStarts && !aStarts) return@sortWith 1

            val aContains = aTitle.contains(qLower)
            val bContains = bTitle.contains(qLower)
            if (aContains && !bContains) return@sortWith -1
            if (bContains && !aContains) return@sortWith 1

            if (aContains && bContains) {
                return@sortWith aTitle.length.compareTo(bTitle.length)
            }
            aTitle.compareTo(bTitle)
        }

        uniqueBooks
    }

    suspend fun getHomeShelves(): List<AudiobookRow> = withContext(Dispatchers.IO) {
        val shelves = mutableListOf<AudiobookRow>()
        coroutineScope {
            val d1 = async {
                val books = runCatching { search("bestseller") }.getOrDefault(emptyList())
                if (books.isNotEmpty()) AudiobookRow("Trending Audiobooks", books.take(15)) else null
            }
            val d2 = async {
                val books = runCatching { search("fiction fantasy") }.getOrDefault(emptyList())
                if (books.isNotEmpty()) AudiobookRow("Fantasy & Sci-Fi", books.take(15)) else null
            }
            val d3 = async {
                val books = runCatching { search("mystery thriller") }.getOrDefault(emptyList())
                if (books.isNotEmpty()) AudiobookRow("Mystery & Thriller", books.take(15)) else null
            }
            val d4 = async {
                val books = runCatching { audiobookBayScraper.search("audiobook") }.getOrDefault(emptyList())
                if (books.isNotEmpty()) AudiobookRow("AudiobookBay Highlights", books.take(15)) else null
            }

            listOf(d1, d2, d3, d4).awaitAll().filterNotNull().forEach {
                shelves.add(it)
            }
        }
        shelves
    }

    suspend fun getChapters(book: Audiobook): List<AudiobookChapter> = withContext(Dispatchers.IO) {
        try {
            when (book.source) {
                "goldenaudiobooks",
                "fulllengthaudiobooks",
                "hotaudiobooks",
                "bookaudiobooks" -> getStandardWpChapters(book.pageUrl)
                "audiozaic" -> getAudiozaicChapters(book.pageUrl)
                "audioaz" -> getAudioAZChapters(book.pageUrl)
                "audiobooks4soul" -> getAudiobooks4SoulChapters(book.pageUrl)
                "audionest" -> getAudionestChapters(book.audioBookId)
                "audiobookbay" -> audiobookBayScraper.getChapters(book.pageUrl)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chapters for ${book.title}: ${e.message}", e)
            emptyList()
        }
    }

    // --- Helper Methods ---

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) "" else response.body?.string().orEmpty()
        }
    }

    private fun extractWpAudio(html: String): List<String> {
        val pattern = Pattern.compile("""<source[^>]*src="([^"]+)"""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        val list = mutableListOf<String>()
        while (matcher.find()) {
            val src = matcher.group(1).orEmpty()
            if (src.isNotBlank()) list.add(src)
        }
        return list
    }

    private fun parseWordPressResults(html: String, sourceId: String, prefix: String): List<Audiobook> {
        val books = mutableListOf<Audiobook>()
        val blockPattern = Pattern.compile(
            """(?:<article[^>]*>|<div[^>]*class="[^"]*post[^"]*"[^>]*>|<li[^>]*class="[^"]*post[^"]*"[^>]*>)([\s\S]*?)(?:</article>|</div><!-- end \.post -->|</div>\s*</article>|</li>)""",
            Pattern.CASE_INSENSITIVE
        )
        val blockMatcher = blockPattern.matcher(html)
        val blocks = mutableListOf<String>()
        while (blockMatcher.find()) {
            val content = blockMatcher.group(1).orEmpty()
            if (content.isNotBlank()) blocks.add(content)
        }

        val iterableBlocks = if (blocks.isNotEmpty()) blocks else listOf(html)

        val titlePattern = Pattern.compile("""<h[23][^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""", Pattern.CASE_INSENSITIVE)
        val imgTagPattern = Pattern.compile("""<img[^>]+>""", Pattern.CASE_INSENSITIVE)
        val attrPatterns = listOf(
            Pattern.compile("""data-lazy-src="([^"]+)"""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""data-src="([^"]+)"""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""src="([^"]+)"""", Pattern.CASE_INSENSITIVE)
        )

        for (block in iterableBlocks) {
            val titleMatcher = titlePattern.matcher(block)
            if (!titleMatcher.find()) continue

            val url = titleMatcher.group(1).orEmpty()
            val rawTitle = titleMatcher.group(2).orEmpty()
            val title = cleanAudiobookTitle(rawTitle, sourceId)

            var coverImage = ""
            val imgTagMatcher = imgTagPattern.matcher(block)
            if (imgTagMatcher.find()) {
                val imgTag = imgTagMatcher.group(0).orEmpty()
                for (p in attrPatterns) {
                    val m = p.matcher(imgTag)
                    if (m.find()) {
                        val v = m.group(1).orEmpty()
                        if (!v.startsWith("data:image") && !v.contains("histats.com") && !v.contains("yandex.ru")) {
                            coverImage = v
                            break
                        }
                    }
                }
            }

            if (url.isNotBlank() && title.isNotBlank()) {
                books.add(
                    Audiobook(
                        uuid = "${prefix}_$url",
                        audioBookId = url,
                        dynamicSlugId = url,
                        title = title,
                        author = sourceId.replaceFirstChar { it.uppercase() },
                        coverImage = coverImage,
                        source = sourceId,
                        pageUrl = url
                    )
                )
            }
        }
        return books
    }

    // --- 1. GoldenAudiobooks ---
    private fun searchGolden(query: String): List<Audiobook> {
        val html = fetch("https://goldenaudiobooks.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "goldenaudiobooks", "golden")
    }

    // --- 2. FullLengthAudiobooks ---
    private fun searchFullLength(query: String): List<Audiobook> {
        val html = fetch("https://fulllengthaudiobooks.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "fulllengthaudiobooks", "full")
    }

    // --- 3. HotAudiobooks ---
    private fun searchHot(query: String): List<Audiobook> {
        val html = fetch("https://hotaudiobooks.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "hotaudiobooks", "hot")
    }

    // --- 4. BookAudiobooks ---
    private fun searchBookAudio(query: String): List<Audiobook> {
        val html = fetch("https://bookaudiobooks.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "bookaudiobooks", "book")
    }

    // --- 5. Audiozaic ---
    private fun searchAudiozaic(query: String): List<Audiobook> {
        val html = fetch("https://audiozaic.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "audiozaic", "zaic")
    }

    // --- 6. AudioAZ ---
    private fun searchAudioAZ(query: String): List<Audiobook> {
        val html = fetch("https://audioaz.com/en/search?q=${URLEncoder.encode(query, "UTF-8")}")
        val bookPattern = Pattern.compile("""href="(/en/(?:audiobook|archive)/[^"]+)"[^>]*title="([^"]+)"""", Pattern.CASE_INSENSITIVE)
        val matcher = bookPattern.matcher(html)
        val books = mutableListOf<Audiobook>()
        while (matcher.find()) {
            val path = matcher.group(1).orEmpty()
            val rawTitle = matcher.group(2).orEmpty()
            val fullUrl = "https://audioaz.com$path"
            books.add(
                Audiobook(
                    uuid = "az_$path",
                    audioBookId = fullUrl,
                    dynamicSlugId = fullUrl,
                    title = cleanAudiobookTitle(rawTitle, "audioaz"),
                    author = "AudioAZ",
                    coverImage = "",
                    source = "audioaz",
                    pageUrl = fullUrl
                )
            )
        }
        return books
    }

    // --- 7. Audiobooks4Soul ---
    private fun searchAudiobooks4Soul(query: String): List<Audiobook> {
        val html = fetch("https://audiobooks4soul.com/?s=${URLEncoder.encode(query, "UTF-8")}")
        return parseWordPressResults(html, "audiobooks4soul", "soul")
    }

    // --- 8. AudionestApp ---
    private fun ensureAudionestToken(force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!force && audionestToken != null && now < audionestTokenExpiryMs) {
            return audionestToken
        }
        try {
            val apiKey = "AIzaSyAG-z_yl0_55NEYTEKGoVJyixtHG-FhnfA"
            val body = "{\"returnSecureToken\":true}".toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                .post(body)
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JsonParser.parseString(res.body?.string().orEmpty()).asJsonObject
                    val token = json.get("idToken")?.asString
                    val expiresIn = json.get("expiresIn")?.asLong ?: 3600L
                    audionestToken = token
                    audionestTokenExpiryMs = now + (expiresIn - 60) * 1000L
                    return token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audionest auth error: ${e.message}", e)
        }
        return null
    }

    private fun searchAudionest(query: String): List<Audiobook> {
        try {
            val bodyObj = JsonObject().apply {
                addProperty("q", query)
                addProperty("limit", 30)
            }
            val req = Request.Builder()
                .url("https://search.audionestapp.com/indexes/trackfiles/search")
                .header("Authorization", "Bearer MWJiNWM0MjA2N2ZkM2RiMDNhNWFmNGNk")
                .post(bodyObj.toString().toRequestBody(jsonMediaType))
                .build()

            val resp = httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return emptyList()
                res.body?.string().orEmpty()
            }

            val json = JsonParser.parseString(resp).asJsonObject
            val hits = json.getAsJsonArray("hits") ?: return emptyList()
            val books = mutableListOf<Audiobook>()

            for (hitElement in hits) {
                val hit = hitElement.asJsonObject
                val id = hit.get("id")?.asString ?: continue
                val title = hit.get("title")?.asString.orEmpty()
                val thumb = hit.get("thumbnailUrl")?.asString
                    ?: hit.get("img_prefix")?.asString.orEmpty()

                books.add(
                    Audiobook(
                        uuid = "nest_$id",
                        audioBookId = id,
                        dynamicSlugId = id,
                        title = cleanAudiobookTitle(title, "audionest"),
                        author = "Audionest",
                        coverImage = thumb,
                        source = "audionest",
                        pageUrl = id
                    )
                )
            }
            return books
        } catch (e: Exception) {
            Log.e(TAG, "Audionest search error: ${e.message}", e)
            return emptyList()
        }
    }

    // --- Chapter Fetchers ---

    private fun getStandardWpChapters(url: String): List<AudiobookChapter> {
        val html = fetch(url)
        val streams = extractWpAudio(html)
        val host = Uri.parse(url).host.orEmpty()
        return streams.mapIndexed { i, streamUrl ->
            AudiobookChapter(
                title = "Chapter ${i + 1}",
                url = streamUrl,
                httpHeaders = mapOf(
                    "Referer" to "https://$host/",
                    "User-Agent" to userAgent
                )
            )
        }
    }

    private fun getAudiozaicChapters(url: String): List<AudiobookChapter> {
        val html = fetch(url)
        val listenPattern = Pattern.compile("""window\.open\('([^']+)'""")
        val m = listenPattern.matcher(html)
        if (!m.find()) return emptyList()

        var listenUrl = m.group(1).orEmpty()
        if (listenUrl.startsWith("/")) listenUrl = "https://audiozaic.com$listenUrl"

        val audioPage = fetch(listenUrl)
        val streams = extractWpAudio(audioPage)
        return streams.mapIndexed { i, streamUrl ->
            AudiobookChapter(
                title = "Chapter ${i + 1}",
                url = streamUrl,
                httpHeaders = mapOf(
                    "Referer" to "https://audiozaic.com/",
                    "User-Agent" to userAgent
                )
            )
        }
    }

    private fun getAudioAZChapters(url: String): List<AudiobookChapter> {
        val html = fetch(url)
        val streams = extractWpAudio(html)
        return streams.mapIndexed { i, streamUrl ->
            AudiobookChapter(
                title = if (streams.size == 1) "Full Audiobook" else "Part ${i + 1}",
                url = streamUrl,
                httpHeaders = mapOf(
                    "Referer" to "https://audioaz.com/",
                    "User-Agent" to userAgent
                )
            )
        }
    }

    private fun getAudiobooks4SoulChapters(url: String): List<AudiobookChapter> {
        val html = fetch(url)
        val playlistPattern = Pattern.compile(
            """<div[^>]*class="[^"]*simp-playlist[^"]*"[\s\S]*?</div>""",
            Pattern.CASE_INSENSITIVE
        )
        val plMatcher = playlistPattern.matcher(html)
        if (!plMatcher.find()) return emptyList()

        val playlistHtml = plMatcher.group(0).orEmpty()
        val dataSrcPattern = Pattern.compile("""data-src=(?:'|")([^'"]+)(?:'|")""")
        val dsMatcher = dataSrcPattern.matcher(playlistHtml)
        val chapters = mutableListOf<AudiobookChapter>()
        var chapterNum = 1

        while (dsMatcher.find()) {
            val encrypted = dsMatcher.group(1).orEmpty()
            val decryptUrl = "https://audiobooks4soul.com/wp-content/plugins/custom-story-audio/inc/security/decrypt.php?encrypted=${URLEncoder.encode(encrypted, "UTF-8")}"
            val decryptedStream = fetch(decryptUrl).trim()

            if (decryptedStream.startsWith("http") && !decryptedStream.contains("Soulful_Exploration")) {
                chapters.add(
                    AudiobookChapter(
                        title = "Chapter $chapterNum",
                        url = decryptedStream,
                        httpHeaders = mapOf(
                            "Referer" to "https://audiobooks4soul.com/",
                            "User-Agent" to userAgent
                        )
                    )
                )
                chapterNum++
            }
        }
        return chapters
    }

    private fun getAudionestChapters(bookId: String): List<AudiobookChapter> {
        var token = ensureAudionestToken() ?: return emptyList()

        val queryJson = JsonObject().apply {
            add("structuredQuery", JsonObject().apply {
                add("from", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("collectionId", "TrackFiles") })
                })
                add("where", JsonObject().apply {
                    add("fieldFilter", JsonObject().apply {
                        add("field", JsonObject().apply { addProperty("fieldPath", "book_id") })
                        addProperty("op", "EQUAL")
                        add("value", JsonObject().apply { addProperty("integerValue", bookId) })
                    })
                })
                addProperty("limit", 1)
            })
        }

        fun runQuery(tk: String): String {
            val req = Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/learningfirebase-ae02f/databases/(default)/documents:runQuery")
                .header("Authorization", "Bearer $tk")
                .post(queryJson.toString().toRequestBody(jsonMediaType))
                .build()
            return httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) "" else res.body?.string().orEmpty()
            }
        }

        var resBody = runQuery(token)
        if (resBody.isBlank()) {
            token = ensureAudionestToken(force = true) ?: return emptyList()
            resBody = runQuery(token)
        }

        if (resBody.isBlank()) return emptyList()
        try {
            val array = JsonParser.parseString(resBody).asJsonArray
            for (elem in array) {
                val obj = elem.asJsonObject
                val doc = obj.getAsJsonObject("document") ?: continue
                val fields = doc.getAsJsonObject("fields") ?: continue
                val urlLink = fields.getAsJsonObject("urlLink") ?: continue
                val arrayValue = urlLink.getAsJsonObject("arrayValue") ?: continue
                val values = arrayValue.getAsJsonArray("values") ?: continue

                val urls = mutableListOf<String>()
                for (v in values) {
                    val s = v.asJsonObject.get("stringValue")?.asString
                    if (!s.isNullOrBlank()) urls.add(s)
                }

                return urls.mapIndexed { i, u ->
                    AudiobookChapter(
                        title = if (urls.size == 1) "Full Audiobook" else "Chapter ${i + 1}",
                        url = u
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audionest firestore parse error: ${e.message}", e)
        }
        return emptyList()
    }
}
