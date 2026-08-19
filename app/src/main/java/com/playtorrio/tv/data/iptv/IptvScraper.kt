package com.playtorrio.tv.data.iptv

import android.util.Base64
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reddit Xtream-Codes scraper using open-source Reddit OAuth2 installed_client auth
 * with anonymous token generation and RSS/Atom fallback.
 *
 * Scrapes subreddits: r/IPTV_ZONENEW, r/FreeIPTV, r/iptvguru, r/IPTVfree
 */

/** A page of scraped portals plus a pagination cursor for the next call. */
data class ScrapePage(
    val portals: List<IptvPortal>,
    val nextAfter: String?,
) {
    val hasMore: Boolean get() = !nextAfter.isNullOrEmpty()
}

object IptvScraper {
    private const val TAG = "IptvScraper"

    // ── Reddit ────────────────────────────────────────────────────────────
    private val CATALOG_SUBS = listOf("IPTV_ZONENEW", "FreeIPTV", "iptvguru", "IPTVfree")
    private const val OAUTH_UA = "PlayTorrio/1.3.6 (by /u/PlayTorrioApp)"

    // Open-source Reddit client IDs (public, installed-app type).
    private val OAUTH_CLIENT_IDS = listOf(
        "ohXpoqrZYub1kg", // Slide for Reddit
        "NOe2iKrPPzwscA", // RedReader
        "JrPdG8Z6dkWNxA", // Stealth
    )

    @Volatile private var oauthToken: String? = null
    @Volatile private var oauthTokenExpiry: Long = 0L
    @Volatile private var oauthClientIdx: Int = 0

    private const val UA =
        "Mozilla/5.0 (Linux; Android 11; PlayTorrio) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0 Safari/537.36"

    // ── Paste sites ───────────────────────────────────────────────────────
    private val PASTE_DOMAINS = listOf(
        "paste.sh", "pastebin.com", "justpaste.it", "controlc.com",
        "pastes.dev", "text.is", "rentry.co",
    )

    // ── HTTP ──────────────────────────────────────────────────────────────
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ── Regex ─────────────────────────────────────────────────────────────
    private val B64_REGEX = Regex("aHR0c[a-zA-Z0-9+/=]{10,}")
    private val RAW_PASTE_REGEX = Regex(
        "https?://(?:paste\\.sh|pastebin\\.com|justpaste\\.it|controlc\\.com|" +
            "pastes\\.dev|text\\.is|rentry\\.co)/[a-zA-Z0-9#_=-]+",
        RegexOption.IGNORE_CASE,
    )
    private val URL_PARAM_REGEX = Regex(
        "(https?://[^?\\s\"'<]+)\\?(?:[^\\s\"'<]*?&)?" +
            "(?:username|user)=([^&\\s\"'<]+)\\s*&(?:password|pass)=([^&\\s\"'<]+)",
        RegexOption.IGNORE_CASE,
    )
    // Label fallback for posts that don't expose a full /get.php?username=…&password=… URL.
    // Accepts English ("Host/User/Pass"), Portuguese ("Usuário/Senha"), Spanish ("Usuario/Contraseña"),
    // unicode smallcaps variants (Hᴏsᴛ / Usᴇʀ / Pᴀss / Usᴜᴀʀɪᴏ / Sᴇɴʜᴀ), and decorative separators.
    private val LABEL_REGEX = Regex(
        "(?:Portal|Host(?:\\s*URL)?|H[ᴏo]s[ᴛt]|Panel|Real|URL|🔗|🌍|🌐)\\W*?" +
            "(https?://[^<\\s\"']+)" +
            "[\\s\\S]{1,500}?(?:Username|Usu[áa]rio|Usuario|User|Us[ᴇe]r|Us[ᴜu][ᴀa]r[ɪi][ᴏo]|👤)\\W*?([^\\s|<\"'\\n]+)" +
            "[\\s\\S]{1,200}?(?:Password|Senha|Contrase[ñn]a|Pass|P[ᴀa]ss|S[ᴇe]nh[ᴀa]|🔑)\\W*?([^\\s|<\"'\\n]+)",
        RegexOption.IGNORE_CASE,
    )
    private val JUNK_TOKENS = listOf(
        "type=m3u", "output=ts", "password=", "username=", "password", "username",
    )

    private val ENTRY_REGEX = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val CONTENT_REGEX = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
    private val ID_REGEX = Regex("<id>(t3_[^<]+)</id>")

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns up to [maxResults] portals from Reddit. */
    suspend fun scrapeReddit(maxResults: Int = 50): List<IptvPortal> =
        scrapeRedditPage(maxResults = maxResults, after = null).portals

    /**
     * Paginated scrape from Reddit.
     * Cursor encoding:
     *  - `null`                  → start of first subreddit (r/IPTV_ZONENEW)
     *  - `reddit:`               → start of first subreddit
     *  - `reddit:<subIdx>:`      → start of subreddit at index subIdx
     *  - `reddit:<subIdx>:<tok>` → subreddit at index subIdx with after=<tok>
     */
    suspend fun scrapeRedditPage(maxResults: Int = 50, after: String? = null): ScrapePage {
        return scrapeRedditCatalog(maxResults = maxResults, after = after)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Reddit catalog source
    // ═══════════════════════════════════════════════════════════════════════

    private suspend fun scrapeRedditCatalog(
        maxResults: Int = 50,
        after: String? = null,
    ): ScrapePage {
        val out = LinkedHashMap<String, IptvPortal>()

        // Determine which subreddit + cursor we're on.
        val (subIdx, redditAfter) = parseRedditCursor(after)
        val validSubIdx = subIdx.coerceIn(0, CATALOG_SUBS.size - 1)
        val currentSub = CATALOG_SUBS[validSubIdx]

        // 1. Try OAuth2 JSON API first (100 posts/page, unlimited pagination)
        val catalogJson = fetchCatalogOAuth(sub = currentSub, after = redditAfter)
        if (catalogJson != null) {
            val data = try {
                JSONObject(catalogJson).optJSONObject("data")
            } catch (e: Exception) {
                Log.e(TAG, "[Catalog] JSON parse failed: ${e.message}")
                null
            }
            if (data != null) {
                val posts = data.optJSONArray("children") ?: JSONArray()
                val nextAfterRaw = data.optString("after").takeIf { it.isNotEmpty() && it != "null" }
                val hasMore = !nextAfterRaw.isNullOrEmpty()
                val nextAfter = if (hasMore) {
                    "reddit:$validSubIdx:$nextAfterRaw"
                } else if (validSubIdx + 1 < CATALOG_SUBS.size) {
                    "reddit:${validSubIdx + 1}:"
                } else {
                    null
                }
                Log.d(
                    TAG,
                    "[Catalog] OAuth r/$currentSub: ${posts.length()} posts (after=$redditAfter, next=$nextAfter)",
                )

                for (i in 0 until posts.length()) {
                    if (out.size >= maxResults) break
                    val pdata = posts.optJSONObject(i)?.optJSONObject("data") ?: continue
                    val title = pdata.optString("title")
                    val body = ("$title ${pdata.optString("selftext")}").trim()
                    processPostBody(body, title, i + 1, out, maxResults)
                }

                processDeepLinks(posts, out, maxResults)

                Log.d(TAG, "[Catalog] DONE — ${out.size} unique portals")
                return ScrapePage(out.values.toList(), nextAfter)
            }
        }

        // 2. Fallback: RSS (25 posts, limited pagination)
        Log.d(TAG, "[Catalog] OAuth failed, falling back to RSS")
        val rssBody = fetchCatalogRss(sub = currentSub, after = redditAfter)
        if (rssBody == null) {
            Log.d(TAG, "[Catalog] RSS also failed")
            if (validSubIdx + 1 < CATALOG_SUBS.size) {
                return ScrapePage(emptyList(), "reddit:${validSubIdx + 1}:")
            }
            return ScrapePage(emptyList(), null)
        }

        val entries = ENTRY_REGEX.findAll(rssBody).toList()
        val postIds = ID_REGEX.findAll(rssBody).map { it.groupValues[1] }.toList()
        val lastPostId = postIds.lastOrNull()
        val nextAfter = if (lastPostId != null && entries.size >= 20) {
            "reddit:$validSubIdx:$lastPostId"
        } else if (validSubIdx + 1 < CATALOG_SUBS.size) {
            "reddit:${validSubIdx + 1}:"
        } else {
            null
        }
        Log.d(TAG, "[Catalog] RSS r/$currentSub: ${entries.size} entries")

        var postIdx = 0
        for (entry in entries) {
            postIdx++
            if (out.size >= maxResults) break
            val entryText = entry.groupValues[1]
            val titleMatch = TITLE_REGEX.find(entryText)
            val title = decodeXmlEntities(titleMatch?.groupValues?.getOrNull(1).orEmpty())
            val contentMatch = CONTENT_REGEX.find(entryText)
            val rawContent = decodeXmlEntities(contentMatch?.groupValues?.getOrNull(1).orEmpty())
            val body = ("$title " + rawContent
                .replace(Regex("<(?:p|br|div|li|h\\d)[^>]*>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")).trim()

            processPostBody(body, title, postIdx, out, maxResults)
        }

        Log.d(TAG, "[Catalog] DONE — ${out.size} unique portals")
        return ScrapePage(out.values.toList(), nextAfter)
    }

    private fun parseRedditCursor(cursor: String?): Pair<Int, String?> {
        if (cursor.isNullOrEmpty()) return Pair(0, null)
        val clean = cursor.removePrefix("reddit:")
        if (clean.isEmpty()) return Pair(0, null)
        val parts = clean.split(":")
        return when {
            parts.size >= 2 -> {
                val subIdx = parts[0].toIntOrNull() ?: 0
                val token = parts.drop(1).joinToString(":").takeIf { it.isNotEmpty() && it != "null" }
                Pair(subIdx, token)
            }
            else -> {
                val subIdx = clean.toIntOrNull()
                if (subIdx != null) {
                    Pair(subIdx, null)
                } else {
                    val token = clean.takeIf { it.isNotEmpty() && it != "null" }
                    Pair(0, token)
                }
            }
        }
    }

    private fun processPostBody(
        body: String,
        title: String,
        postIdx: Int,
        out: LinkedHashMap<String, IptvPortal>,
        maxResults: Int,
    ) {
        val shortTitle = if (title.length > 60) title.take(60) + "…" else title
        Log.d(TAG, "[Catalog] post[$postIdx] '$shortTitle' bodyLen=${body.length}")
        val direct = extractPortals(body, "Reddit")
        if (direct.isNotEmpty()) {
            Log.d(TAG, "[Catalog]   direct: ${direct.size}")
        }
        for (p in direct) {
            addPortal(out, p, maxResults)
        }
    }

    private suspend fun processDeepLinks(
        posts: JSONArray,
        out: LinkedHashMap<String, IptvPortal>,
        maxResults: Int,
    ) {
        for (i in 0 until posts.length()) {
            if (out.size >= maxResults) break
            val pdata = posts.optJSONObject(i)?.optJSONObject("data") ?: continue
            val title = pdata.optString("title")
            val body = ("$title ${pdata.optString("selftext")}").trim()

            val deepLinks = mutableListOf<String>()
            B64_REGEX.findAll(body).forEach { m ->
                runCatching {
                    val decoded = String(Base64.decode(m.value, Base64.DEFAULT), Charsets.UTF_8)
                    if (decoded.startsWith("http") && isPasteSite(decoded)) {
                        deepLinks += decoded
                    } else if (!decoded.startsWith("http") && decoded.contains(":")) {
                        extractPortals(decoded, "Reddit (decoded)")
                            .forEach { addPortal(out, it, maxResults) }
                    }
                }
            }
            RAW_PASTE_REGEX.findAll(body).forEach { deepLinks += it.value }

            val unique = deepLinks.distinct().take(4)
            for (dl in unique) {
                if (out.size >= maxResults) break
                Log.d(TAG, "[Catalog]   deep: $dl")
                val text = runCatching { fetchPaste(dl) }.getOrNull()
                if (!text.isNullOrBlank()) {
                    val found = extractPortals(text, "Reddit (deep)")
                    Log.d(TAG, "[Catalog]     → ${text.length} chars, ${found.size} portals")
                    for (p in found) {
                        addPortal(out, p, maxResults)
                    }
                }
            }
        }
    }

    // ── Reddit OAuth2 "installed_client" anonymous auth ──────────────────
    // Grants an anonymous bearer token without needing a Reddit account.
    // Token is cached and auto-refreshed. Client IDs rotate on failure.
    private fun getOAuthToken(): String? {
        val now = System.currentTimeMillis()
        val cached = oauthToken
        if (cached != null && now < oauthTokenExpiry) {
            return cached
        }
        val clientCount = OAUTH_CLIENT_IDS.size
        for (i in 0 until clientCount) {
            val idx = (oauthClientIdx + i) % clientCount
            val clientId = OAUTH_CLIENT_IDS[idx]
            try {
                val basicAuth = "Basic " + Base64.encodeToString(
                    "$clientId:".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                ).trim()
                val formBody = FormBody.Builder()
                    .add("grant_type", "https://oauth.reddit.com/grants/installed_client")
                    .add("device_id", "DO_NOT_TRACK_THIS_DEVICE")
                    .build()
                val req = Request.Builder()
                    .url("https://www.reddit.com/api/v1/access_token")
                    .header("User-Agent", OAUTH_UA)
                    .header("Authorization", basicAuth)
                    .post(formBody)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val json = JSONObject(bodyStr)
                        val token = json.optString("access_token")
                        val expiresIn = json.optInt("expires_in", 3600)
                        if (token.isNotEmpty()) {
                            oauthToken = token
                            oauthTokenExpiry = now + (expiresIn - 60) * 1000L
                            oauthClientIdx = idx
                            Log.d(TAG, "[Catalog] OAuth token obtained (client #$idx)")
                            return token
                        }
                    }
                    Log.d(TAG, "[Catalog] OAuth auth failed (client #$idx): ${resp.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "[Catalog] OAuth auth error (client #$idx): ${e.message}")
            }
        }
        oauthClientIdx = (oauthClientIdx + 1) % clientCount
        oauthToken = null
        oauthTokenExpiry = 0L
        return null
    }

    private fun fetchCatalogOAuth(sub: String, after: String?): String? {
        val token = getOAuthToken() ?: return null
        val base = "https://oauth.reddit.com/r/$sub/new?limit=100&sort=new&raw_json=1"
        val url = if (after.isNullOrEmpty()) base else "$base&after=$after"
        Log.d(TAG, "[Catalog] OAuth GET r/$sub (after=$after)")
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", OAUTH_UA)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val t = body.trimStart()
                    if (t.startsWith("{") || t.startsWith("[")) {
                        return body
                    }
                }
                if (resp.code == 401 || resp.code == 403) {
                    oauthToken = null
                    oauthTokenExpiry = 0L
                }
                Log.d(TAG, "[Catalog] OAuth HTTP ${resp.code} len=${body.length}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "[Catalog] OAuth failed: ${e.message}")
        }
        return null
    }

    private fun fetchCatalogRss(sub: String, after: String?): String? {
        val base = "https://www.reddit.com/r/$sub/new/.rss?limit=25"
        val url = if (after.isNullOrEmpty()) base else "$base&after=$after"
        Log.d(TAG, "[Catalog] GET RSS $url")
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", OAUTH_UA)
                .header("Accept", "application/atom+xml, application/xml, */*")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful && body.contains("<entry>")) {
                    return body
                }
                Log.d(TAG, "[Catalog] RSS HTTP ${resp.code} len=${body.length}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "[Catalog] RSS failed: ${e.message}")
        }
        return null
    }

    private fun decodeXmlEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#32;", " ")

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun addPortal(sink: LinkedHashMap<String, IptvPortal>, p: IptvPortal, max: Int) {
        if (sink.size >= max) return
        val key = "${p.url}|${p.username}|${p.password}".lowercase()
        if (key !in sink) sink[key] = p
    }

    private fun extractPortals(rawText: String, source: String): List<IptvPortal> {
        if (rawText.length < 15 || isJunkCode(rawText)) return emptyList()
        val cleaned = rawText
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace(Regex("<(?:p|br|div|li|h\\d)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")

        val acc = LinkedHashMap<String, IptvPortal>()
        URL_PARAM_REGEX.findAll(cleaned).forEach {
            finalize(acc, it.groupValues[1], it.groupValues[2], it.groupValues[3], source)
        }
        LABEL_REGEX.findAll(cleaned).forEach {
            finalize(acc, it.groupValues[1], it.groupValues[2], it.groupValues[3], source)
        }
        return acc.values.toList()
    }

    private fun isJunkCode(text: String): Boolean {
        val markers = listOf(
            "Array.isArray", "prototype.", "function(", "var ", "const ",
            "let ", "return!", "void ", ".message}", "window.", "document.",
        )
        return markers.count { text.contains(it) } >= 2
    }

    private fun finalize(
        acc: LinkedHashMap<String, IptvPortal>,
        rawUrl: String, rawUser: String, rawPass: String, source: String,
    ) {
        val url = cleanPortalUrl(rawUrl)
        val user = cleanCred(rawUser)
        val pass = cleanCred(rawPass)
        if (url.isEmpty() || user.length < 3 || pass.length < 3) return
        if (user.contains("http") || pass.contains("http")) return
        if (JUNK_TOKENS.any { user.contains(it, true) || pass.contains(it, true) }) return
        val key = "$url|$user|$pass".lowercase()
        if (key !in acc) acc[key] = IptvPortal(url, user, pass, source)
    }

    private fun cleanPortalUrl(raw: String): String {
        var clean = raw.replace(Regex("\\s+"), "").substringBefore('?').trim()
        if (clean.contains('@')) clean = "http://" + clean.substringAfterLast('@')
        clean = clean.replace(
            Regex(
                "/(?:get|live|portal|c|index|playlist|player_api|xmltv|index\\.php|portal\\.php)\\.php$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).trimEnd('/')
        if (!clean.startsWith("http")) clean = "http://$clean"
        return clean
    }

    private fun cleanCred(raw: String): String =
        raw.trimStart('=').split(' ', '\n', '&', '?').firstOrNull().orEmpty().trim()

    private fun isPasteSite(url: String): Boolean = PASTE_DOMAINS.any { url.contains(it) }

    // ── Paste fetch helpers ───────────────────────────────────────────────

    private fun fetchPaste(url: String): String {
        if (url.contains("paste.sh/") && url.contains('#')) {
            return PasteShDecryptor.decrypt(url)
        }
        if (url.contains("pastebin.com/") && !url.contains("/raw/")) {
            val id = lastPathSegment(url)
            return httpGet("https://pastebin.com/raw/$id", UA, "text/plain,*/*")
        }
        if (url.contains("pastes.dev/")) {
            val id = lastPathSegment(url)
            return httpGet("https://api.pastes.dev/$id", UA, "text/plain,*/*")
        }
        if (url.contains("rentry.co/") && !url.contains("/raw")) {
            val id = lastPathSegment(url)
            return httpGet("https://rentry.co/$id/raw", UA, "text/plain,*/*")
        }
        return httpGet(url, UA, "text/html,application/json,*/*")
    }

    private fun lastPathSegment(url: String): String {
        var s = url
        val h = s.indexOf('#')
        if (h >= 0) s = s.substring(0, h)
        val q = s.indexOf('?')
        if (q >= 0) s = s.substring(0, q)
        val slash = s.lastIndexOf('/')
        return if (slash >= 0) s.substring(slash + 1) else s
    }

    // ── Core HTTP ─────────────────────────────────────────────────────────

    private fun httpGet(url: String, userAgent: String, accept: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            Log.d(TAG, "HTTP ${resp.code} ← $url")
            return resp.body?.string().orEmpty()
        }
    }
}
