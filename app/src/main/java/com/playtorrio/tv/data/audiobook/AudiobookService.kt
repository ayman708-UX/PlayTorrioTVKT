package com.playtorrio.tv.data.audiobook

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Source-agnostic representations.
 * Currently a single source (fulllengthaudiobooks.net) is supported, but the
 * shape leaves room for more — hence [sourceId].
 */
data class AudiobookSearchResult(
    val sourceId: String,
    val id: String,          // unique id (URL slug) per source
    val title: String,
    val pageUrl: String,
    val posterUrl: String? = null,
)

data class AudiobookChapter(
    val index: Int,
    val title: String,
    val mp3Url: String,
)

data class AudiobookDetail(
    val sourceId: String,
    val id: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String?,
    val chapters: List<AudiobookChapter>,
    /** Optional Referer header required by some hosts (e.g. audiozaic) for hot-link protection. */
    val referer: String? = null,
    /**
     * Static HTTP headers that the player MUST send for every chunk request to this book.
     * Used by hosts that require Origin / x-stream-token / etc. (e.g. tokybook).
     */
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    fun toJson(): String {
        val arr = JSONArray()
        chapters.forEach { c ->
            arr.put(JSONObject().apply {
                put("index", c.index)
                put("title", c.title)
                put("url", c.mp3Url)
            })
        }
        val hdrs = JSONObject()
        extraHeaders.forEach { (k, v) -> hdrs.put(k, v) }
        return JSONObject().apply {
            put("sourceId", sourceId)
            put("id", id)
            put("title", title)
            put("pageUrl", pageUrl)
            put("posterUrl", posterUrl ?: JSONObject.NULL)
            put("referer", referer ?: JSONObject.NULL)
            put("extraHeaders", hdrs)
            put("chapters", arr)
        }.toString()
    }

    companion object {
        fun fromJson(s: String): AudiobookDetail? = try {
            val j = JSONObject(s)
            val arr = j.getJSONArray("chapters")
            val chs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AudiobookChapter(
                    index = o.getInt("index"),
                    title = o.getString("title"),
                    mp3Url = o.getString("url"),
                )
            }
            AudiobookDetail(
                sourceId = j.getString("sourceId"),
                id = j.getString("id"),
                title = j.getString("title"),
                pageUrl = j.getString("pageUrl"),
                posterUrl = if (j.isNull("posterUrl")) null else j.getString("posterUrl"),
                chapters = chs,
                referer = if (!j.has("referer") || j.isNull("referer")) null else j.getString("referer"),
                extraHeaders = j.optJSONObject("extraHeaders")?.let { obj ->
                    val map = mutableMapOf<String, String>()
                    val keys = obj.keys()
                    while (keys.hasNext()) { val k = keys.next(); map[k] = obj.optString(k) }
                    map
                } ?: emptyMap(),
            )
        } catch (e: Exception) {
            Log.w("AudiobookService", "fromJson failed: ${e.message}")
            null
        }
    }
}

object AudiobookService {

    private const val TAG = "AudiobookService"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Cache of result-id -> poster URL so search results can backfill posters. */
    private val posterCache = mutableMapOf<String, String?>()

    /** Resolve a poster lazily for a search result that didn't include one. */
    suspend fun resolvePoster(result: AudiobookSearchResult): String? = withContext(Dispatchers.IO) {
        val key = "${result.sourceId}:${result.id}"
        synchronized(posterCache) {
            if (posterCache.containsKey(key)) return@withContext posterCache[key]
        }
        val src = sourceById(result.sourceId) ?: return@withContext null
        val poster = try { src.fetchPoster(result, http, UA) } catch (e: Exception) {
            Log.w(TAG, "resolvePoster failed: ${e.message}"); null
        }
        synchronized(posterCache) { posterCache[key] = poster }
        poster
    }

    // ─── Source registry ──────────────────────────────────────────
    val sources: List<AudiobookSource> = listOf(
        TokybookSource,
        FullLengthAudiobooksSource,
        HDAudiobooksSource,
        GoldenAudiobookSource,
        AppAudiobooksSource,
        AudiozaicSource,
    )

    fun sourceById(id: String): AudiobookSource? = sources.firstOrNull { it.id == id }

    suspend fun search(query: String): List<AudiobookSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val raw = sources.flatMap { src ->
            try { src.search(query, http, UA) } catch (e: Exception) {
                Log.w(TAG, "search ${src.id} failed: ${e.message}"); emptyList()
            }
        }
        rankByRelevance(query, raw)
    }

    /**
     * Rank search results so the most relevant ones surface first.
     * Score is based on exact match, prefix match, query-token coverage in the title,
     * and an inverse-length penalty so "Pride and Prejudice" beats "Pride and Prejudice
     * and Zombies and Other Long Subtitle" for the query "pride and prejudice".
     */
    private fun rankByRelevance(
        query: String,
        results: List<AudiobookSearchResult>,
    ): List<AudiobookSearchResult> {
        if (results.isEmpty()) return results
        val q = query.trim().lowercase()
        val qTokens = q.split(Regex("\\s+")).filter { it.length >= 2 }
        if (qTokens.isEmpty()) return results

        return results
            .map { it to scoreRelevance(q, qTokens, it.title.lowercase()) }
            .sortedWith(compareByDescending<Pair<AudiobookSearchResult, Double>> { it.second }
                .thenBy { it.first.title.length })
            .map { it.first }
    }

    private fun scoreRelevance(q: String, qTokens: List<String>, title: String): Double {
        if (title.isBlank()) return 0.0
        var score = 0.0
        // Exact title (modulo "audiobook" suffix the sites add).
        val cleanedTitle = title.replace(Regex("\\baudiobook\\b"), "").trim()
        if (cleanedTitle == q) score += 1000.0
        if (title.startsWith(q)) score += 200.0
        if (title.contains(q)) score += 100.0
        // Token coverage — most queries are multi-word.
        var covered = 0
        for (tok in qTokens) {
            if (Regex("\\b" + Regex.escape(tok) + "\\b").containsMatchIn(title)) {
                covered++
                score += 25.0
            } else if (title.contains(tok)) {
                score += 8.0
            }
        }
        // All tokens present? big bonus.
        if (covered == qTokens.size) score += 80.0
        // Slight inverse-length nudge so shorter, tighter titles win ties.
        score += 50.0 / (1.0 + title.length / 40.0)
        return score
    }

    suspend fun fetchDetail(result: AudiobookSearchResult): AudiobookDetail? =
        withContext(Dispatchers.IO) {
            val src = sourceById(result.sourceId) ?: return@withContext null
            try { src.fetchDetail(result, http, UA) } catch (e: Exception) {
                Log.w(TAG, "fetchDetail failed: ${e.message}"); null
            }
        }

    /**
     * For browse-on-open: fetch the homepage's featured items.
     * Tokybook is the preferred source. If it fails or returns nothing, fall back to
     * the other sources so the home page never ends up empty.
     */
    suspend fun browse(page: Int = 0, pageSize: Int = 24): List<AudiobookSearchResult> = withContext(Dispatchers.IO) {
        val toky = try {
            TokybookSource.browsePage(http, UA, page = page, pageSize = pageSize)
        } catch (e: Exception) {
            Log.w(TAG, "tokybook browse failed: ${e.message}"); emptyList()
        }
        if (toky.isNotEmpty()) return@withContext toky
        // Fallback only used for the first page; the legacy sources are search-only.
        if (page > 0) return@withContext emptyList()
        sources.filter { it !== TokybookSource }.flatMap { src ->
            try { src.browse(http, UA) } catch (e: Exception) {
                Log.w(TAG, "browse ${src.id} failed: ${e.message}"); emptyList()
            }
        }
    }

    // Helpers used by sources
    internal fun get(http: OkHttpClient, url: String, ua: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: Exception) {
            Log.w("AudiobookService", "GET $url failed: ${e.message}"); null
        }
    }

    internal fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** A scraping source (e.g., fulllengthaudiobooks.net). */
interface AudiobookSource {
    val id: String
    val displayName: String
    fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult>
    fun fetchDetail(result: AudiobookSearchResult, http: OkHttpClient, ua: String): AudiobookDetail?
    fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult>
    /** Lightweight poster lookup (used to backfill search rows). Defaults to fetchDetail's poster. */
    fun fetchPoster(result: AudiobookSearchResult, http: OkHttpClient, ua: String): String? =
        fetchDetail(result, http, ua)?.posterUrl
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: tokybook.com  (JSON API; HLS streams gated by per-request headers)
//
// Tokybook chapters are .m3u8 playlists hosted at
//   https://tokybook.com/api/v1/public/audio/<src>
// Every chunk request — both the .m3u8 and each .ts segment — must carry:
//   Referer: https://tokybook.com/
//   Origin:  https://tokybook.com
//   x-audiobook-id:  <book.audioBookId>
//   x-stream-token:  <playlist.streamToken>
//   x-track-src:     "/api/v1/public/audio/" + URL-encoded(path of THIS request)
//
// The first three are constants per book (set as default request properties on
// the player's HttpDataSource). x-track-src varies per request and is injected
// at request time by `TokybookHeaderInjectingFactory` in the player.
// ═══════════════════════════════════════════════════════════════════════

object TokybookSource : AudiobookSource {
    override val id: String = "toky"
    override val displayName: String = "Tokybook"

    private const val BASE = "https://tokybook.com/api/v1"
    private const val UA_BROWSER =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    private fun userIdentity(): JSONObject = JSONObject().apply {
        put("ipAddress", "")
        put("userAgent", UA_BROWSER)
        put("timestamp", java.time.Instant.now().toString())
    }

    private fun jsonPost(http: OkHttpClient, url: String, body: JSONObject): JSONObject? {
        return try {
            val req = Request.Builder().url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Origin", "https://tokybook.com")
                .header("Referer", "https://tokybook.com/")
                .header("User-Agent", UA_BROWSER)
                .post(
                    okhttp3.RequestBody.create(
                        "application/json".toMediaTypeOrNull(),
                        body.toString(),
                    )
                )
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("TOKY", "POST $url -> HTTP ${resp.code}")
                    return null
                }
                val txt = resp.body?.string() ?: return null
                JSONObject(txt)
            }
        } catch (e: Exception) {
            Log.w("TOKY", "POST $url failed: ${e.message}"); null
        }
    }

    private fun mapItem(o: JSONObject): AudiobookSearchResult? {
        val slug = o.optString("dynamicSlugId").takeIf { it.isNotBlank() } ?: return null
        val abId = o.optString("audioBookId").takeIf { it.isNotBlank() } ?: return null
        return AudiobookSearchResult(
            sourceId = id,
            // Encode both pieces so fetchDetail can recover them without another API call.
            id = "$abId|$slug",
            title = o.optString("title", "Untitled"),
            pageUrl = "https://tokybook.com/$slug",
            posterUrl = o.optString("coverImage").takeIf { it.isNotBlank() },
        )
    }

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val body = JSONObject().apply {
            put("query", query)
            put("offset", 0)
            put("limit", 20)
            put("userIdentity", userIdentity())
        }
        val resp = jsonPost(http, "$BASE/search/instant", body) ?: return emptyList()
        val arr = resp.optJSONArray("content") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { mapItem(arr.getJSONObject(it)) }
    }

    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> =
        browsePage(http, ua, page = 0, pageSize = 24)

    fun browsePage(http: OkHttpClient, ua: String, page: Int, pageSize: Int): List<AudiobookSearchResult> {
        val body = JSONObject().apply {
            put("offset", page * pageSize)
            put("limit", pageSize)
            put("typeFilter", "audiobook")
            put("slugIdFilter", JSONObject.NULL)
            put("userIdentity", userIdentity())
        }
        val resp = jsonPost(http, "$BASE/search/audiobooks", body) ?: return emptyList()
        val arr = resp.optJSONArray("content") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { mapItem(arr.getJSONObject(it)) }
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String,
    ): AudiobookDetail? {
        val parts = result.id.split('|', limit = 2)
        if (parts.size != 2) return null
        val abId = parts[0]
        val slug = parts[1]

        val det = jsonPost(http, "$BASE/search/post-details", JSONObject().apply {
            put("dynamicSlugId", slug)
            put("userIdentity", userIdentity())
        }) ?: return null
        val token = det.optString("postDetailToken").takeIf { it.isNotBlank() } ?: return null
        val title = det.optString("title").takeIf { it.isNotBlank() } ?: result.title
        val cover = det.optString("coverImage").takeIf { it.isNotBlank() } ?: result.posterUrl

        val pl = jsonPost(http, "$BASE/playlist", JSONObject().apply {
            put("audioBookId", abId)
            put("postDetailToken", token)
            put("userIdentity", userIdentity())
        }) ?: return null
        val streamToken = pl.optString("streamToken")
        val tracks = pl.optJSONArray("tracks") ?: return null

        val chapters = (0 until tracks.length()).mapNotNull { i ->
            val t = tracks.getJSONObject(i)
            val src = t.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val titleT = t.optString("trackTitle").takeIf { it.isNotBlank() } ?: "Track ${i + 1}"
            val encoded = src.split('/').joinToString("/") { android.net.Uri.encode(it) }
            AudiobookChapter(
                index = i + 1,
                title = titleT,
                mp3Url = "https://tokybook.com/api/v1/public/audio/$encoded",
            )
        }
        if (chapters.isEmpty()) return null

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = cover,
            chapters = chapters,
            referer = "https://tokybook.com/",
            extraHeaders = mapOf(
                "User-Agent" to UA_BROWSER,
                "Referer" to "https://tokybook.com/",
                "Origin" to "https://tokybook.com",
                "Accept" to "*/*",
                "x-audiobook-id" to abId,
                "x-stream-token" to streamToken,
            ),
        )
    }

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String,
    ): String? = result.posterUrl
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: fulllengthaudiobooks.net
// ═══════════════════════════════════════════════════════════════════════

object FullLengthAudiobooksSource : AudiobookSource {
    override val id: String = "flab"
    override val displayName: String = "FullLengthAudiobooks"
    private const val BASE = "https://fulllengthaudiobooks.net"
    private const val SEARCH_AJAX =
        "$BASE/wp-admin/admin-ajax.php?action=searchwp_live_search&swpengine=default&origin_id=0"

    private val SEARCH_RESULT_REGEX = Regex(
        """<a\s+href=["'](https://fulllengthaudiobooks\.net/[^"']+)["']>\s*([\s\S]*?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )
    private val MP3_REGEX = Regex(
        """<source\s+type=["']audio/mpeg["']\s+src=["']([^"']+\.mp3[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
    private val IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+\.(?:jpe?g|png|webp))[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_REGEX = Regex(
        """<h1[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )
    private val POST_LINK_REGEX = Regex(
        """<h2[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>\s*<a\s+href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE
    )
    /** Match an entire <article>…</article> block so we can pull title + poster together. */
    private val ARTICLE_REGEX = Regex(
        """<article[^>]*>([\s\S]*?)</article>""",
        RegexOption.IGNORE_CASE
    )

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val q = AudiobookService.urlEncode(query)
        val url = "$SEARCH_AJAX&swpquery=$q"
        val body = AudiobookService.get(http, url, ua) ?: return emptyList()
        return SEARCH_RESULT_REGEX.findAll(body).map {
            val href = it.groupValues[1].trim()
            val rawTitle = it.groupValues[2].trim()
            AudiobookSearchResult(
                sourceId = id,
                id = slugFromUrl(href),
                title = decodeHtml(rawTitle),
                pageUrl = href,
            )
        }.toList()
    }

    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val body = AudiobookService.get(http, BASE, ua) ?: return emptyList()
        // Walk each <article>…</article> block — pull link, title, and the first uploads-folder image.
        val results = mutableListOf<AudiobookSearchResult>()
        ARTICLE_REGEX.findAll(body).forEach { art ->
            val block = art.groupValues[1]
            val link = POST_LINK_REGEX.find(block) ?: return@forEach
            val href = link.groupValues[1].trim()
            val rawTitle = link.groupValues[2].trim()
            val poster = IMG_REGEX.findAll(block).map { it.groupValues[1] }
                .firstOrNull { url ->
                    url.contains("wp-content/uploads", ignoreCase = true) &&
                        !url.contains("logo", ignoreCase = true) &&
                        !url.contains("icon", ignoreCase = true)
                }
            results.add(
                AudiobookSearchResult(
                    sourceId = id,
                    id = slugFromUrl(href),
                    title = decodeHtml(rawTitle),
                    pageUrl = href,
                    posterUrl = poster,
                )
            )
        }
        return results.distinctBy { it.id }
    }

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): String? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null
        return IMG_REGEX.findAll(body).map { it.groupValues[1] }
            .firstOrNull { url ->
                url.contains("wp-content/uploads", ignoreCase = true) &&
                    !url.contains("logo", ignoreCase = true) &&
                    !url.contains("icon", ignoreCase = true)
            }
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): AudiobookDetail? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null

        val title = TITLE_REGEX.find(body)?.groupValues?.get(1)?.let { decodeHtml(it.trim()) }
            ?: result.title

        val poster = IMG_REGEX.findAll(body)
            .map { it.groupValues[1] }
            .firstOrNull { url ->
                url.contains("wp-content/uploads", ignoreCase = true) &&
                    !url.contains("logo", ignoreCase = true) &&
                    !url.contains("icon", ignoreCase = true)
            }

        val chapters = MP3_REGEX.findAll(body)
            .mapIndexed { i, m ->
                val raw = m.groupValues[1]
                // Strip the cache-busting "?_=N" suffix.
                val clean = raw.substringBefore("?")
                AudiobookChapter(
                    index = i + 1,
                    title = "Chapter ${i + 1}",
                    mp3Url = clean,
                )
            }
            .distinctBy { it.mp3Url }
            .toList()

        if (chapters.isEmpty()) {
            Log.w("FLAB", "No chapters found for ${result.pageUrl}")
            return null
        }

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = poster,
            chapters = chapters,
        )
    }

    private fun slugFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifBlank { url }

    private fun decodeHtml(s: String): String {
        // Minimal entity decode for the common cases we see in listings.
        return s
            .replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: hdaudiobooks.com (search-only)
// ═══════════════════════════════════════════════════════════════════════

object HDAudiobooksSource : AudiobookSource {
    override val id: String = "hdab"
    override val displayName: String = "HDAudiobooks"
    private const val BASE = "https://hdaudiobooks.com"
    private const val SEARCH_AJAX =
        "$BASE/wp-admin/admin-ajax.php?action=searchwp_live_search&swpengine=default&origin_id=0"

    /** Each search result block: img src + page link + title. */
    private val RESULT_BLOCK_REGEX = Regex(
        """<div\s+class=["']searchwp-live-search-result["'][^>]*>([\s\S]*?)</div>\s*</div>""",
        RegexOption.IGNORE_CASE
    )
    private val RESULT_IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val RESULT_LINK_REGEX = Regex(
        """<a\s+href=["'](https://hdaudiobooks\.com/[^"']+)["'][^>]*>\s*([\s\S]*?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )
    /** ExoPlayer-friendly: just pull the inline JSON in data-playlist='…' */
    private val PLAYLIST_REGEX = Regex(
        """data-playlist=['"](\[[\s\S]*?\])['"]""",
        RegexOption.IGNORE_CASE
    )
    private val COVER_REGEX = Regex(
        """data-cover=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_H1_REGEX = Regex(
        """<h1[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val q = AudiobookService.urlEncode(query)
        val url = "$SEARCH_AJAX&s=$q&swpquery=$q"
        val body = AudiobookService.get(http, url, ua) ?: return emptyList()
        return RESULT_BLOCK_REGEX.findAll(body).mapNotNull { m ->
            val block = m.groupValues[1]
            val link = RESULT_LINK_REGEX.find(block) ?: return@mapNotNull null
            val href = link.groupValues[1].trim()
            val rawTitle = link.groupValues[2].trim()
            val poster = RESULT_IMG_REGEX.find(block)?.groupValues?.get(1)
            AudiobookSearchResult(
                sourceId = id,
                id = slugFromUrl(href),
                title = decodeHtml(rawTitle),
                pageUrl = href,
                posterUrl = poster,
            )
        }.toList()
    }

    /** Search-only — never appears in the browse grid. */
    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> = emptyList()

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): String? {
        if (!result.posterUrl.isNullOrBlank()) return result.posterUrl
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null
        return COVER_REGEX.find(body)?.groupValues?.get(1)
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): AudiobookDetail? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null

        val title = TITLE_H1_REGEX.find(body)?.groupValues?.get(1)?.let { decodeHtml(it.trim()) }
            ?: result.title

        val poster = COVER_REGEX.find(body)?.groupValues?.get(1)
            ?: result.posterUrl

        val playlistJson = PLAYLIST_REGEX.find(body)?.groupValues?.get(1) ?: run {
            Log.w("HDAB", "No data-playlist found for ${result.pageUrl}")
            return null
        }

        val chapters = parsePlaylist(playlistJson)
        if (chapters.isEmpty()) {
            Log.w("HDAB", "Empty playlist for ${result.pageUrl}")
            return null
        }

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = poster,
            chapters = chapters,
        )
    }

    private fun parsePlaylist(json: String): List<AudiobookChapter> {
        // The HTML attribute contains escaped slashes (\/) — JSONArray handles those.
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val src = o.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val rawTitle = o.optString("title").ifBlank { "Chapter ${i + 1}" }
                AudiobookChapter(
                    index = i + 1,
                    title = prettyChapterTitle(rawTitle, i + 1),
                    mp3Url = src,
                )
            }
        } catch (e: Exception) {
            Log.w("HDAB", "playlist parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun prettyChapterTitle(raw: String, idx: Int): String {
        // Titles look like "Christine/01" — keep the trailing chapter number.
        val tail = raw.substringAfterLast('/').trim()
        val n = tail.toIntOrNull()
        return if (n != null) "Chapter $n" else "Chapter $idx — $tail"
    }

    private fun slugFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifBlank { url }

    private fun decodeHtml(s: String): String =
        s.replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: goldenaudiobook.net (search-only)
// ═══════════════════════════════════════════════════════════════════════

object GoldenAudiobookSource : AudiobookSource {
    override val id: String = "goln"
    override val displayName: String = "GoldenAudiobook"
    private const val BASE = "https://goldenaudiobook.net"
    private const val SEARCH_AJAX =
        "$BASE/wp-admin/admin-ajax.php?action=searchwp_live_search&swpengine=default&origin_id=0"

    private val RESULT_BLOCK_REGEX = Regex(
        """<div\s+class=["']searchwp-live-search-result["'][^>]*>([\s\S]*?)</div>\s*</div>""",
        RegexOption.IGNORE_CASE
    )
    private val RESULT_IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val RESULT_LINK_REGEX = Regex(
        """<a\s+href=["'](https://goldenaudiobook\.net/[^"']+)["'][^>]*>\s*([\s\S]*?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )
    private val MP3_REGEX = Regex(
        """<source\s+type=["']audio/mpeg["']\s+src=["']([^"']+\.mp3[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
    private val FIGURE_IMG_REGEX = Regex(
        """<figure[^>]*class=["'][^"']*wp-caption[^"']*["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+\.(?:jpe?g|png|webp))[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_H1_REGEX = Regex(
        """<h1[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val q = AudiobookService.urlEncode(query)
        val url = "$SEARCH_AJAX&s=$q&swpquery=$q"
        val body = AudiobookService.get(http, url, ua) ?: return emptyList()
        return RESULT_BLOCK_REGEX.findAll(body).mapNotNull { m ->
            val block = m.groupValues[1]
            val link = RESULT_LINK_REGEX.find(block) ?: return@mapNotNull null
            val href = link.groupValues[1].trim()
            val rawTitle = link.groupValues[2].trim()
            val poster = RESULT_IMG_REGEX.find(block)?.groupValues?.get(1)
            AudiobookSearchResult(
                sourceId = id,
                id = slugFromUrl(href),
                title = decodeHtml(rawTitle),
                pageUrl = href,
                posterUrl = poster,
            )
        }.toList()
    }

    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> = emptyList()

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): String? {
        if (!result.posterUrl.isNullOrBlank()) return result.posterUrl
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null
        return FIGURE_IMG_REGEX.find(body)?.groupValues?.get(1)
            ?: IMG_REGEX.findAll(body).map { it.groupValues[1] }
                .firstOrNull { url ->
                    url.contains("wp-content/uploads", ignoreCase = true) &&
                        !url.contains("logo", ignoreCase = true) &&
                        !url.contains("icon", ignoreCase = true)
                }
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): AudiobookDetail? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null

        val title = TITLE_H1_REGEX.find(body)?.groupValues?.get(1)?.let { decodeHtml(it.trim()) }
            ?: result.title

        val poster = FIGURE_IMG_REGEX.find(body)?.groupValues?.get(1)
            ?: result.posterUrl

        val chapters = MP3_REGEX.findAll(body)
            .mapIndexed { i, m ->
                val raw = m.groupValues[1]
                val clean = raw.substringBefore("?")
                AudiobookChapter(
                    index = i + 1,
                    title = "Chapter ${i + 1}",
                    mp3Url = clean,
                )
            }
            .distinctBy { it.mp3Url }
            .toList()

        if (chapters.isEmpty()) {
            Log.w("GOLN", "No chapters for ${result.pageUrl}")
            return null
        }

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = poster,
            chapters = chapters,
        )
    }

    private fun slugFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifBlank { url }

    private fun decodeHtml(s: String): String =
        s.replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: appaudiobooks.com (search-only)
// ═══════════════════════════════════════════════════════════════════════

object AppAudiobooksSource : AudiobookSource {
    override val id: String = "apab"
    override val displayName: String = "AppAudiobooks"
    private const val BASE = "https://appaudiobooks.com"
    private const val SEARCH_AJAX =
        "$BASE/wp-admin/admin-ajax.php?action=searchwp_live_search&swpengine=default&origin_id=0"

    private val RESULT_BLOCK_REGEX = Regex(
        """<div\s+class=["']searchwp-live-search-result["'][^>]*>([\s\S]*?)</div>\s*</div>""",
        RegexOption.IGNORE_CASE
    )
    private val RESULT_LINK_REGEX = Regex(
        """<a\s+href=["'](https://appaudiobooks\.com/[^"']+)["'][^>]*>\s*([\s\S]*?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )
    private val MP3_REGEX = Regex(
        """<source\s+type=["']audio/mpeg["']\s+src=["']([^"']+\.mp3[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
    private val COVER_IMG_REGEX = Regex(
        """<img[^>]+id=["']imgBlkFront["'][^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val CAPTION_DIV_IMG_REGEX = Regex(
        """<div[^>]*class=["'][^"']*wp-caption[^"']*["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val FALLBACK_IMG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+\.(?:jpe?g|png|webp))[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_H1_REGEX = Regex(
        """<h1[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val q = AudiobookService.urlEncode(query)
        val url = "$SEARCH_AJAX&s=$q&swpquery=$q"
        val body = AudiobookService.get(http, url, ua) ?: return emptyList()
        return RESULT_BLOCK_REGEX.findAll(body).mapNotNull { m ->
            val block = m.groupValues[1]
            val link = RESULT_LINK_REGEX.find(block) ?: return@mapNotNull null
            val href = link.groupValues[1].trim()
            val rawTitle = link.groupValues[2].trim()
            // Search results return an SVG placeholder — no usable poster here.
            AudiobookSearchResult(
                sourceId = id,
                id = slugFromUrl(href),
                title = decodeHtml(rawTitle),
                pageUrl = href,
                posterUrl = null,
            )
        }.toList()
    }

    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> = emptyList()

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): String? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null
        return extractPoster(body)
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): AudiobookDetail? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null

        val title = TITLE_H1_REGEX.find(body)?.groupValues?.get(1)?.let { decodeHtml(it.trim()) }
            ?: result.title

        val poster = extractPoster(body) ?: result.posterUrl

        val chapters = MP3_REGEX.findAll(body)
            .mapIndexed { i, m ->
                val raw = m.groupValues[1]
                val clean = raw.substringBefore("?")
                AudiobookChapter(
                    index = i + 1,
                    title = "Chapter ${i + 1}",
                    mp3Url = clean,
                )
            }
            .distinctBy { it.mp3Url }
            .toList()

        if (chapters.isEmpty()) {
            Log.w("APAB", "No chapters for ${result.pageUrl}")
            return null
        }

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = poster,
            chapters = chapters,
        )
    }

    private fun extractPoster(body: String): String? {
        return COVER_IMG_REGEX.find(body)?.groupValues?.get(1)
            ?: CAPTION_DIV_IMG_REGEX.find(body)?.groupValues?.get(1)
            ?: FALLBACK_IMG_REGEX.findAll(body).map { it.groupValues[1] }
                .firstOrNull { url ->
                    url.contains("wp-content/uploads", ignoreCase = true) &&
                        !url.contains("logo", ignoreCase = true) &&
                        !url.contains("icon", ignoreCase = true)
                }
    }

    private fun slugFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifBlank { url }

    private fun decodeHtml(s: String): String =
        s.replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
}

// ═══════════════════════════════════════════════════════════════════════
// SOURCE: audiozaic.com (search-only)
// Each book is a single mp3 (no chapters). The mp3 lives behind a
// /file-audio?slug32=NNNN page linked to from a "Listen To Audiobook" button.
// ═══════════════════════════════════════════════════════════════════════

object AudiozaicSource : AudiobookSource {
    override val id: String = "azic"
    override val displayName: String = "Audiozaic"
    private const val BASE = "https://audiozaic.com"

    /** Each search result is an <article class="vce-post …"> with a meta-image link + entry-title. */
    private val ARTICLE_REGEX = Regex(
        """<article[^>]*class=["'][^"']*vce-post[^"']*["'][^>]*>([\s\S]*?)</article>""",
        RegexOption.IGNORE_CASE
    )
    private val META_LINK_REGEX = Regex(
        """<div[^>]*class=["']meta-image["'][^>]*>\s*<a\s+href=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val META_IMG_REGEX = Regex(
        """<div[^>]*class=["']meta-image["'][^>]*>[\s\S]*?<img[^>]*?(?:data-src|src)=["']([^"']+\.(?:jpe?g|png|webp))[^"']*["']""",
        RegexOption.IGNORE_CASE
    )
    private val ENTRY_TITLE_REGEX = Regex(
        """<h2[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>\s*<a[^>]*>\s*([\s\S]*?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )

    /** Detail page bits */
    private val LISTEN_BUTTON_REGEX = Regex(
        """window\.open\(\s*['"](https?://audiozaic\.com/file-audio[^'"]+)['"]""",
        RegexOption.IGNORE_CASE
    )
    /** WordPress marks the LCP image with fetchpriority="high" — that's the real book cover. */
    private val DETAIL_POSTER_PRIORITY_REGEX = Regex(
        """<img[^>]*fetchpriority=["']high["'][^>]*src=["']([^"']+wp-content/uploads/[^"']+\.(?:jpe?g|png|webp))[^"']*["']""",
        RegexOption.IGNORE_CASE
    )
    private val DETAIL_POSTER_REGEX = Regex(
        """<img[^>]+src=["']([^"']+wp-content/uploads/[^"']+\.(?:jpe?g|png|webp))[^"']*["']""",
        RegexOption.IGNORE_CASE
    )

    private fun pickDetailPoster(body: String): String? {
        // 1) Prefer the LCP-marked image (the real cover).
        DETAIL_POSTER_PRIORITY_REGEX.find(body)?.groupValues?.get(1)?.let { return it }
        // 2) Otherwise, walk all uploads images and skip banners/logos/related-book thumbnails.
        return DETAIL_POSTER_REGEX.findAll(body).map { it.groupValues[1] }.firstOrNull { url ->
            !url.contains("logo", ignoreCase = true) &&
            !url.contains("icon", ignoreCase = true) &&
            !url.contains("banner", ignoreCase = true) &&
            !url.contains("Untitled-design", ignoreCase = true) &&
            // Related-book widget thumbs are 145x100; the real cover is portrait (e.g. 195x300, 197x300).
            !url.contains("145x100", ignoreCase = true)
        }
    }
    private val MP3_REGEX = Regex(
        """<source\s+type=["']audio/mpeg["']\s+src=["']([^"']+\.mp3[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
    private val DETAIL_TITLE_REGEX = Regex(
        """<h1[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )

    override fun search(query: String, http: OkHttpClient, ua: String): List<AudiobookSearchResult> {
        val q = AudiobookService.urlEncode(query)
        val body = AudiobookService.get(http, "$BASE/?s=$q", ua) ?: return emptyList()
        val out = mutableListOf<AudiobookSearchResult>()
        ARTICLE_REGEX.findAll(body).forEach { art ->
            val block = art.groupValues[1]
            val href = META_LINK_REGEX.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
            // Skip non-audiobook links (categories, tags, etc.) — they all live on /<slug>/
            if (!href.startsWith("https://audiozaic.com/") || href.contains("/category/") || href.contains("/tag/")) return@forEach
            val rawTitle = ENTRY_TITLE_REGEX.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
            val poster = META_IMG_REGEX.find(block)?.groupValues?.get(1)
            out.add(
                AudiobookSearchResult(
                    sourceId = id,
                    id = slugFromUrl(href),
                    title = cleanTitle(decodeHtml(rawTitle)),
                    pageUrl = href,
                    posterUrl = poster,
                )
            )
        }
        return out.distinctBy { it.id }
    }

    override fun browse(http: OkHttpClient, ua: String): List<AudiobookSearchResult> = emptyList()

    override fun fetchPoster(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): String? {
        if (!result.posterUrl.isNullOrBlank()) return result.posterUrl
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null
        return pickDetailPoster(body)
    }

    override fun fetchDetail(
        result: AudiobookSearchResult, http: OkHttpClient, ua: String
    ): AudiobookDetail? {
        val body = AudiobookService.get(http, result.pageUrl, ua) ?: return null

        val title = DETAIL_TITLE_REGEX.find(body)?.groupValues?.get(1)?.let { cleanTitle(decodeHtml(it.trim())) }
            ?: result.title

        val poster = pickDetailPoster(body) ?: result.posterUrl

        // The "Listen To Audiobook" button opens /file-audio?slug32=NNNN; that page hosts the <audio> element.
        val fileAudioUrl = LISTEN_BUTTON_REGEX.find(body)?.groupValues?.get(1)
        val mp3Body = if (fileAudioUrl != null) {
            AudiobookService.get(http, fileAudioUrl, ua)
        } else null

        // Fallback: maybe the mp3 is on the main page already.
        val searchIn = mp3Body ?: body
        val chapters = MP3_REGEX.findAll(searchIn)
            .mapIndexed { i, m ->
                val raw = m.groupValues[1]
                val clean = raw.substringBefore("?")
                AudiobookChapter(
                    index = i + 1,
                    title = if (i == 0) "Audiobook" else "Part ${i + 1}",
                    mp3Url = clean,
                )
            }
            .distinctBy { it.mp3Url }
            .toList()

        if (chapters.isEmpty()) {
            Log.w("AZIC", "No mp3 found for ${result.pageUrl}")
            return null
        }

        return AudiobookDetail(
            sourceId = id,
            id = result.id,
            title = title,
            pageUrl = result.pageUrl,
            posterUrl = poster,
            chapters = chapters,
            referer = "$BASE/",
        )
    }

    private fun slugFromUrl(url: String): String =
        url.trimEnd('/').substringAfterLast('/').ifBlank { url }

    /** Strip the "[Listen][Download]" prefix the site adds to every title. */
    private fun cleanTitle(t: String): String =
        t.replace(Regex("""^\s*\[[^\]]+\]\s*\[[^\]]+\]\s*"""), "").trim()

    private fun decodeHtml(s: String): String =
        s.replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\u201C")
            .replace("&#8221;", "\u201D")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
}
