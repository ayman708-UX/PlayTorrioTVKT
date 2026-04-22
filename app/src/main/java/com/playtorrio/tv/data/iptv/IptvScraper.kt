package com.playtorrio.tv.data.iptv

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reddit-only Xtream-Codes scraper.
 *
 * Mirrors the small subset of `System/server.js` needed for the Android TV use
 * case — fetches the latest posts from r/IPTV_ZONENEW, follows base64-encoded
 * deep links + raw paste links, then runs a few regex strategies to pull
 * `(portal_url, username, password)` triples out of the body text.
 *
 * Skips heavyweight sources (TVAppApk, TGStat) and AES-encrypted paste.sh
 * blobs to keep the runtime reasonable on a TV box.
 */
/** A page of scraped portals plus Reddit's pagination cursor for the next call. */
data class ScrapePage(
    val portals: List<IptvPortal>,
    val nextAfter: String?,
) {
    val hasMore: Boolean get() = !nextAfter.isNullOrEmpty()
}

object IptvScraper {
    private const val TAG = "IptvScraper"
    private const val REDDIT_BASE =
        "https://www.reddit.com/r/IPTV_ZONENEW/new/.json?limit=100&sort=new"

    private val PASTE_DOMAINS = listOf(
        "paste.sh", "pastebin.com", "justpaste.it", "controlc.com",
        "pastes.dev", "text.is", "rentry.co",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val UA =
        "Mozilla/5.0 (Linux; Android 11; PlayTorrio) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0 Safari/537.36"

    /**
     * Returns up to [maxResults] de-duplicated portals scraped from Reddit.
     */
    suspend fun scrapeReddit(maxResults: Int = 50): List<IptvPortal> =
        scrapeRedditPage(maxResults = maxResults, after = null).portals

    /**
     * Paginated variant — pass [after] = `null` for the first page, then feed
     * the returned [ScrapePage.nextAfter] back in to walk the subreddit.
     */
    suspend fun scrapeRedditPage(maxResults: Int = 50, after: String? = null): ScrapePage {
        val out = LinkedHashMap<String, IptvPortal>()
        val url = if (after.isNullOrEmpty()) REDDIT_BASE else "$REDDIT_BASE&after=$after"

        val redditJson = try {
            httpGetText(url)
        } catch (e: Exception) {
            Log.e(TAG, "Reddit fetch failed: ${e.message}")
            return ScrapePage(emptyList(), null)
        }

        val data = try {
            JSONObject(redditJson).getJSONObject("data")
        } catch (e: Exception) {
            Log.e(TAG, "Reddit JSON parse failed: ${e.message}")
            return ScrapePage(emptyList(), null)
        }
        val posts = data.optJSONArray("children") ?: return ScrapePage(emptyList(), null)
        val nextAfter = data.optString("after").takeIf { it.isNotEmpty() && it != "null" }

        Log.d(TAG, "Reddit returned ${posts.length()} posts (after=$after, next=$nextAfter)")

        for (i in 0 until posts.length()) {
            if (out.size >= maxResults) break
            val data = posts.getJSONObject(i).optJSONObject("data") ?: continue
            val title = data.optString("title")
            val body = (title + " " + data.optString("selftext")).trim()
            Log.d(TAG, "Post[$i] '${title.take(60)}' bodyLen=${body.length}")

            // 1. Process Reddit body itself
            val direct = extractPortals(body, "Reddit")
            if (direct.isNotEmpty()) Log.d(TAG, "  direct extract: ${direct.size}")
            direct.forEach { addPortal(out, it, maxResults) }
            if (out.size >= maxResults) break

            // 2. Decode base64 deep links → fetch + extract
            val deepLinks = mutableListOf<String>()
            B64_REGEX.findAll(body).forEach { m ->
                runCatching {
                    val decoded = String(Base64.decode(m.value, Base64.DEFAULT))
                    Log.d(TAG, "  b64→ '${decoded.take(80)}'")
                    if (decoded.startsWith("http") && isPasteSite(decoded)) {
                        deepLinks += decoded
                    } else if (!decoded.startsWith("http") && decoded.contains(":")) {
                        extractPortals(decoded, "Reddit (decoded)")
                            .forEach { addPortal(out, it, maxResults) }
                    } else {
                        Log.d(TAG, "  b64 not paste/cred: skipping")
                    }
                }.onFailure { Log.w(TAG, "  b64 decode failed: ${it.message}") }
            }

            // 3. Raw paste links in the body
            RAW_PASTE_REGEX.findAll(body).forEach { deepLinks += it.value }

            // 4. Fetch up to a few deep links per post
            for (url in deepLinks.distinct().take(4)) {
                if (out.size >= maxResults) break
                Log.d(TAG, "  fetching deep: $url")
                val text = runCatching { fetchPaste(url) }
                    .onFailure { Log.w(TAG, "  fetchPaste failed: ${it.message}") }
                    .getOrNull()
                if (text.isNullOrBlank()) {
                    Log.d(TAG, "  → empty body")
                    continue
                }
                Log.d(TAG, "  → got ${text.length} chars; sample='${text.take(160).replace('\n',' ')}'")
                val found = extractPortals(text, "Reddit (deep)")
                Log.d(TAG, "  → extracted ${found.size} portals")
                found.forEach { addPortal(out, it, maxResults) }
            }
        }

        Log.d(TAG, "Scraped ${out.size} portals from Reddit")
        return ScrapePage(out.values.toList(), nextAfter)
    }

    private fun addPortal(
        sink: LinkedHashMap<String, IptvPortal>,
        p: IptvPortal,
        max: Int,
    ) {
        if (sink.size >= max) return
        val key = "${p.url}|${p.username}|${p.password}".lowercase()
        if (key !in sink) sink[key] = p
    }

    // ── extraction ──────────────────────────────────────────────────────

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

    private val LABEL_REGEX = Regex(
        "(?:Portal|Host(?:\\s*URL)?|Panel|Real|URL|🔗|🌍|🌐):?\\s*" +
            "(https?://[^<\\s\"']+)" +
            "[\\s\\S]{1,500}?(?:Username|User|👤):?\\s*([^|\\s\\n<\"']+)" +
            "[\\s\\S]{1,200}?(?:Password|Pass|🔑):?\\s*([^\\s\\n<\"']+)",
        RegexOption.IGNORE_CASE,
    )

    private val JUNK_TOKENS = listOf(
        "type=m3u", "output=ts", "password=", "username=", "password", "username",
    )

    private fun extractPortals(rawText: String, source: String): List<IptvPortal> {
        if (rawText.length < 15 || isJunkCode(rawText)) return emptyList()
        // Strip HTML tags but keep newlines; do NOT insert spaces inside URLs.
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
        rawUrl: String,
        rawUser: String,
        rawPass: String,
        source: String,
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
            Regex("/(?:get|live|portal|c|index|playlist|player_api|xmltv|index\\.php|portal\\.php)\\.php$",
                RegexOption.IGNORE_CASE),
            "",
        ).trimEnd('/')
        if (!clean.startsWith("http")) clean = "http://$clean"
        return clean
    }

    private fun cleanCred(raw: String): String =
        raw.trimStart('=').split(' ', '\n', '&', '?').firstOrNull().orEmpty().trim()

    private fun isPasteSite(url: String): Boolean = PASTE_DOMAINS.any { url.contains(it) }

    // ── Paste-site fetch helpers ────────────────────────────────────────

    private suspend fun fetchPaste(url: String): String {
        // paste.sh → AES-256-CBC encrypted, fragment is the client key
        if (url.contains("paste.sh/") && url.contains('#')) {
            return PasteShDecryptor.decrypt(url)
        }
        // pastebin.com → /raw/<id> for plain text
        if (url.contains("pastebin.com/") && !url.contains("/raw/")) {
            val id = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
            return httpGetText("https://pastebin.com/raw/$id")
        }
        // pastes.dev → api endpoint
        if (url.contains("pastes.dev/")) {
            val id = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
            return httpGetText("https://api.pastes.dev/$id")
        }
        // rentry.co → raw view
        if (url.contains("rentry.co/") && !url.contains("/raw")) {
            val id = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
            return httpGetText("https://rentry.co/$id/raw")
        }
        return httpGetText(url)
    }

    private fun httpGetText(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json,*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }
}
