package com.playtorrio.tv.core.audiobook.scrapers

import android.util.Log
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookChapter
import com.playtorrio.tv.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudiobookBayScraper"
private const val BASE_URL = "https://audiobookbay.lu"

@Singleton
class AudiobookBayScraper @Inject constructor() {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    suspend fun search(query: String): List<Audiobook> = withContext(Dispatchers.IO) {
        val books = mutableListOf<Audiobook>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/?s=$encodedQuery&cat=undefined%2Cundefined"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string().orEmpty()
            }

            val blocks = html.split("<div class=\"post\">")
            val titlePattern = Pattern.compile(
                """<div class="postTitle">\s*<h2>\s*<a href="([^"]+)"[^>]*>([^<]+)</a>""",
                Pattern.CASE_INSENSITIVE
            )
            val imgPattern = Pattern.compile("""<img[^>]*src="([^"]+)"""", Pattern.CASE_INSENSITIVE)

            for (i in 1 until blocks.size) {
                val block = blocks[i]
                val titleMatcher = titlePattern.matcher(block)
                if (!titleMatcher.find()) continue

                var bookUrl = titleMatcher.group(1).orEmpty()
                if (bookUrl.startsWith("/")) bookUrl = "$BASE_URL$bookUrl"
                val rawTitle = titleMatcher.group(2).orEmpty().trim()

                val imgMatcher = imgPattern.matcher(block)
                var coverImage = ""
                if (imgMatcher.find()) {
                    coverImage = imgMatcher.group(1).orEmpty()
                }

                val cleanedTitle = AudiobookScraperService.cleanAudiobookTitle(rawTitle, "audiobookbay")

                books.add(
                    Audiobook(
                        uuid = "abb_${bookUrl.hashCode()}",
                        audioBookId = bookUrl,
                        dynamicSlugId = bookUrl,
                        title = cleanedTitle,
                        author = "AudiobookBay",
                        coverImage = coverImage,
                        source = "audiobookbay",
                        pageUrl = bookUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudiobookBay search error: ${e.message}", e)
        }
        books
    }

    suspend fun getChapters(url: String): List<AudiobookChapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<AudiobookChapter>()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string().orEmpty()
            }

            val hashPattern = Pattern.compile(
                """Info Hash:</td>\s*<td[^>]*>\s*([a-fA-F0-9]{40})\s*</td>""",
                Pattern.CASE_INSENSITIVE
            )
            val hashMatcher = hashPattern.matcher(html)
            if (!hashMatcher.find()) return@withContext emptyList()

            val infoHash = hashMatcher.group(1).orEmpty()

            val trackerPattern = Pattern.compile(
                """(?:Announce URL|Tracker):</td>\s*<td[^>]*>\s*([^<]+?)\s*</td>""",
                Pattern.CASE_INSENSITIVE
            )
            val trackerMatcher = trackerPattern.matcher(html)
            val trackers = mutableListOf<String>()
            while (trackerMatcher.find()) {
                val tr = trackerMatcher.group(1).orEmpty().trim()
                if (tr.isNotBlank()) trackers.add(tr)
            }

            val magnetSb = java.lang.StringBuilder("magnet:?xt=urn:btih:$infoHash")
            for (tr in trackers) {
                magnetSb.append("&tr=").append(URLEncoder.encode(tr, "UTF-8"))
            }
            val magnetString = magnetSb.toString()

            val filePattern = Pattern.compile(
                """(?:<td>|<li>|<code>|<pre>|class="torrent_files"|class="file_list"|>|\n|^)\s*([a-zA-Z0-9_\-\.\s\(\)\[\]\,']+\.(?:mp3|m4b|m4a|aac|flac|ogg|opus|wav|wma))""",
                Pattern.CASE_INSENSITIVE
            )
            val fileMatcher = filePattern.matcher(html)
            val seenFiles = mutableSetOf<String>()
            var fileIdx = 0

            while (fileMatcher.find()) {
                val filename = fileMatcher.group(1).orEmpty().trim()
                if (filename.isNotBlank() && seenFiles.add(filename.lowercase())) {
                    chapters.add(
                        AudiobookChapter(
                            title = filename,
                            url = magnetString,
                            isTorrent = true,
                            torrentFileIndex = fileIdx,
                            infoHash = infoHash,
                            trackers = trackers
                        )
                    )
                    fileIdx++
                }
            }

            if (chapters.isEmpty()) {
                chapters.add(
                    AudiobookChapter(
                        title = "Full Audiobook Torrent",
                        url = magnetString,
                        isTorrent = true,
                        torrentFileIndex = 0,
                        infoHash = infoHash,
                        trackers = trackers
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudiobookBay getChapters error: ${e.message}", e)
        }
        chapters
    }
}
