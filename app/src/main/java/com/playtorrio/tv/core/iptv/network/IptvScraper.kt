package com.playtorrio.tv.core.iptv.network

import android.util.Base64
import android.util.Log
import com.playtorrio.tv.core.iptv.model.CatalogSource
import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.ScrapePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object IptvScraper {
    private const val TAG = "IptvScraper"
    private val catalogSubs = listOf("IPTV_ZONENEW", "FreeIPTV", "iptvguru", "IPTVfree")
    private const val OAUTH_UA = "PlayTorrio/1.3.6 (by /u/PlayTorrioApp)"
    private val oauthClientIds = listOf(
        "ohXpoqrZYub1kg", // Slide for Reddit
        "NOe2iKrPPzwscA", // RedReader
        "JrPdG8Z6dkWNxA"  // Stealth
    )
    private var oauthToken: String? = null
    private var oauthTokenExpiryMs: Long = 0
    private var oauthClientIdx = 0

    private const val CLOUD_VAULT_PRIMARY =
        "https://pub-38f23eb5f3304328b9774fadfa233a38.r2.dev/xtreamity-plus-db.csv.gz"
    private const val CLOUD_VAULT_BACKUP =
        "https://s3.us-west-004.backblazeb2.com/Xtream-STBemu/xtreamity-plus-db.csv.gz"

    private var cachedCloudPortals: List<IptvPortal>? = null

    private const val UA = "Mozilla/5.0 (Linux; Android 11; PlayTorrio) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36"

    private val pasteDomains = listOf(
        "paste.sh", "pastebin.com", "justpaste.it", "controlc.com",
        "pastes.dev", "text.is", "rentry.co"
    )

    private val b64Regex = Regex("""aHR0c[a-zA-Z0-9+/_=-]{10,}""")
    private val rawPasteRegex = Regex(
        """https?://(?:paste\.sh|pastebin\.com|justpaste\.it|controlc\.com|pastes\.dev|text\.is|rentry\.co)/[a-zA-Z0-9#_=-]+""",
        RegexOption.IGNORE_CASE
    )

    private val junkTokens = listOf(
        "type=m3u", "output=ts", "password=", "username=", "password", "username"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun scrapeCatalogPage(
        maxResults: Int = 50,
        after: String? = null,
        source: CatalogSource = CatalogSource.CLOUD_VAULT
    ): ScrapePage = withContext(Dispatchers.IO) {
        if (source == CatalogSource.CLOUD_VAULT) {
            return@withContext scrapeCloudVault(maxResults, after)
        }
        val page = scrapeRedditCatalog(maxResults, after)
        if (page.portals.isEmpty() && after == null) {
            Log.d(TAG, "Reddit returned 0 portals. Falling back to Cloud Vault...")
            return@withContext scrapeCloudVault(maxResults, after)
        }
        page
    }

    private suspend fun scrapeCloudVault(maxResults: Int = 50, after: String? = null): ScrapePage {
        val allPortals = loadCloudVaultDatabase()
        if (allPortals.isEmpty()) return ScrapePage(emptyList(), null)

        var offset = 0
        if (after != null && after.startsWith("cloud:")) {
            offset = after.removePrefix("cloud:").toIntOrNull() ?: 0
        }

        if (offset >= allPortals.size) return ScrapePage(emptyList(), null)

        val slice = allPortals.drop(offset).take(maxResults)
        val nextOffset = offset + slice.size
        val nextAfter = if (nextOffset < allPortals.size) "cloud:$nextOffset" else null
        return ScrapePage(portals = slice, nextAfter = nextAfter)
    }

    private suspend fun loadCloudVaultDatabase(): List<IptvPortal> = withContext(Dispatchers.IO) {
        cachedCloudPortals?.let { return@withContext it }

        var gzBytes: ByteArray? = null
        for (url in listOf(CLOUD_VAULT_PRIMARY, CLOUD_VAULT_BACKUP)) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        gzBytes = res.body?.bytes()
                    }
                }
                if (gzBytes != null && gzBytes!!.isNotEmpty()) break
            } catch (e: Exception) {
                Log.d(TAG, "Failed downloading cloud vault from $url: ${e.message}")
            }
        }

        if (gzBytes == null || gzBytes!!.isEmpty()) return@withContext emptyList()

        try {
            val gis = GZIPInputStream(ByteArrayInputStream(gzBytes))
            val reader = BufferedReader(InputStreamReader(gis, Charsets.UTF_8))
            val list = mutableListOf<IptvPortal>()
            val seen = mutableSetOf<String>()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim().orEmpty()
                if (l.isEmpty()) continue
                val parts = parseCsvLine(l)
                if (parts.size >= 3) {
                    val host = parts[0].trim()
                    val user = parts[1].trim()
                    val pass = parts[2].trim()
                    if (host.startsWith("http://") || host.startsWith("https://")) {
                        if (user.isNotEmpty() && pass.isNotEmpty()) {
                            val key = "$user|$pass".lowercase()
                            if (seen.add(key)) {
                                val cleanUrl = if (host.endsWith("/")) host.dropLast(1) else host
                                list.add(
                                    IptvPortal(
                                        url = cleanUrl,
                                        username = user,
                                        password = pass,
                                        source = "Cloud Vault"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            list.shuffle()
            Log.d(TAG, "Loaded ${list.size} live IPTV portals from Cloud Vault database")
            cachedCloudPortals = list
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Cloud Vault gzip: ${e.message}")
            emptyList()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val res = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (i in 0 until line.length) {
            val ch = line[i]
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                res.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        res.add(sb.toString().trim())
        return res
    }

    private suspend fun scrapeRedditCatalog(maxResults: Int = 50, after: String? = null): ScrapePage = withContext(Dispatchers.IO) {
        val out = mutableMapOf<String, IptvPortal>()

        // Parse pagination state: "reddit:subIdx:redditAfter"
        var subIdx = 0
        var redditAfter: String? = null
        if (after != null && after.startsWith("reddit:")) {
            val parts = after.removePrefix("reddit:").split(":", limit = 2)
            subIdx = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val raw = parts.getOrNull(1)
            if (!raw.isNullOrEmpty() && raw != "null") redditAfter = raw
        } else if (after != null && after.isNotEmpty()) {
            redditAfter = after
        }
        if (subIdx >= catalogSubs.size) subIdx = 0
        val currentSub = catalogSubs[subIdx]

        // Try OAuth API first
        val catalogJson = fetchCatalogOAuth(currentSub, redditAfter)
        if (catalogJson != null) {
            try {
                val root = JSONObject(catalogJson)
                val data = root.optJSONObject("data")
                if (data != null) {
                    val posts = data.optJSONArray("children")
                    val nextAfterRaw = data.optString("after", "")
                    val hasMore = nextAfterRaw.isNotEmpty() && nextAfterRaw != "null"
                    var nextAfter: String? = null
                    if (hasMore) {
                        nextAfter = "reddit:$subIdx:$nextAfterRaw"
                    } else if (subIdx + 1 < catalogSubs.size) {
                        nextAfter = "reddit:${subIdx + 1}:"
                    }

                    if (posts != null) {
                        processPosts(posts, out, maxResults)
                        processDeepLinks(posts, out, maxResults)
                    }

                    Log.d(TAG, "Reddit OAuth r/$currentSub: ${out.size} portals from page (max=$maxResults)")
                    return@withContext ScrapePage(portals = out.values.toList(), nextAfter = nextAfter)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error parsing Reddit OAuth JSON for r/$currentSub: ${e.message}")
            }
        }

        // Fallback: RSS
        val rssBody = fetchCatalogRss(currentSub, redditAfter)
        if (rssBody == null) {
            if (subIdx + 1 < catalogSubs.size) {
                return@withContext ScrapePage(portals = emptyList(), nextAfter = "reddit:${subIdx + 1}:")
            }
            return@withContext ScrapePage(portals = emptyList(), nextAfter = null)
        }

        val entryRegex = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
        val contentRegex = Regex("""<content[^>]*>(.*?)</content>""", RegexOption.DOT_MATCHES_ALL)
        val idRegex = Regex("""<id>(t3_[^<]+)</id>""")

        val entries = entryRegex.findAll(rssBody).toList()
        val postIds = idRegex.findAll(rssBody).map { it.groupValues[1] }.toList()
        val lastPostId = postIds.lastOrNull()
        var nextAfter: String? = null
        if (lastPostId != null && entries.size >= 20) {
            nextAfter = "reddit:$subIdx:$lastPostId"
        } else if (subIdx + 1 < catalogSubs.size) {
            nextAfter = "reddit:${subIdx + 1}:"
        }

        for (entry in entries) {
            if (out.size >= maxResults) break
            val entryText = entry.groupValues[1]
            val titleMatch = titleRegex.find(entryText)
            val title = decodeXmlEntities(titleMatch?.groupValues?.get(1).orEmpty())
            val contentMatch = contentRegex.find(entryText)
            val rawContent = decodeXmlEntities(contentMatch?.groupValues?.get(1).orEmpty())
            val cleanContent = rawContent
                .replace(Regex("""<(?:p|br|div|li|h\d)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
            val body = "$title $cleanContent".trim()
            processPostBody(body, title, out, maxResults)
        }

        ScrapePage(portals = out.values.toList(), nextAfter = nextAfter)
    }

    private fun processPosts(posts: org.json.JSONArray, out: MutableMap<String, IptvPortal>, maxResults: Int = 50) {
        for (i in 0 until posts.length()) {
            if (out.size >= maxResults) break
            val post = posts.getJSONObject(i)
            val pdata = post.optJSONObject("data") ?: continue
            val title = pdata.optString("title")
            val body = "$title ${pdata.optString("selftext")}".trim()
            processPostBody(body, title, out, maxResults)
        }
    }

    private fun processPostBody(body: String, title: String, out: MutableMap<String, IptvPortal>, maxResults: Int = 50) {
        if (out.size >= maxResults) return
        val direct = extractPortals(body, "Community")
        for (p in direct) {
            addPortal(out, p, maxResults)
        }
    }

    private suspend fun processDeepLinks(posts: org.json.JSONArray, out: MutableMap<String, IptvPortal>, maxResults: Int = 50) {
        if (out.size >= maxResults) return

        val allDeepLinks = mutableListOf<String>()
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            val pdata = post.optJSONObject("data") ?: continue
            val title = pdata.optString("title")
            val body = "$title ${pdata.optString("selftext")}".trim()

            for (m in b64Regex.findAll(body)) {
                val decoded = decodeBase64Flexible(m.value)
                if (decoded.startsWith("http") && isPasteSite(decoded)) {
                    allDeepLinks.add(decoded)
                } else if (!decoded.startsWith("http") && decoded.contains(":")) {
                    extractPortals(decoded, "Community").forEach { p -> addPortal(out, p, maxResults) }
                }
            }
            for (m in rawPasteRegex.findAll(body)) {
                allDeepLinks.add(m.value)
            }
            if (out.size >= maxResults) return
        }

        val unique = allDeepLinks.distinct().take(4)
        for (dl in unique) {
            if (out.size >= maxResults) break
            try {
                val text = fetchPaste(dl)
                if (!text.isNullOrEmpty()) {
                    val found = extractPortals(text, "Community")
                    for (p in found) {
                        addPortal(out, p, maxResults)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error fetching paste: ${e.message}")
            }
        }
    }

    private fun decodeBase64Flexible(input: String): String {
        val s = input.trim()
        val standard = s.replace('-', '+').replace('_', '/')
        val padded = when (standard.length % 4) {
            2 -> "$standard=="
            3 -> "$standard="
            else -> standard
        }
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun addPortal(sink: MutableMap<String, IptvPortal>, p: IptvPortal, maxResults: Int = Int.MAX_VALUE) {
        if (sink.size >= maxResults) return
        sink.putIfAbsent(p.key, p)
    }

    private fun isPasteSite(url: String): Boolean = pasteDomains.any { url.contains(it) }

    private suspend fun fetchPaste(url: String): String? {
        if (url.contains("paste.sh/") && url.contains("#")) {
            val out = PasteShDecryptor.decrypt(url)
            return if (out.isEmpty()) null else out
        }
        if (url.contains("pastebin.com/") && !url.contains("/raw/")) {
            val id = lastPathSegment(url)
            return httpGetText("https://pastebin.com/raw/$id")
        }
        if (url.contains("pastes.dev/")) {
            val id = lastPathSegment(url)
            return httpGetText("https://api.pastes.dev/$id")
        }
        if (url.contains("rentry.co/") && !url.contains("/raw")) {
            val id = lastPathSegment(url)
            return httpGetText("https://rentry.co/$id/raw")
        }
        return httpGetText(url)
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

    private suspend fun httpGetText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("Accept", "text/html,application/json,*/*")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getOAuthToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (oauthToken != null && now < oauthTokenExpiryMs) {
            return@withContext oauthToken
        }
        for (i in oauthClientIds.indices) {
            val idx = (oauthClientIdx + i) % oauthClientIds.size
            val clientId = oauthClientIds[idx]
            try {
                val authHeader = "Basic " + Base64.encodeToString("$clientId:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val body = FormBody.Builder()
                    .add("grant_type", "https://oauth.reddit.com/grants/installed_client")
                    .add("device_id", "DO_NOT_TRACK_THIS_DEVICE")
                    .build()
                val req = Request.Builder()
                    .url("https://www.reddit.com/api/v1/access_token")
                    .addHeader("User-Agent", OAUTH_UA)
                    .addHeader("Authorization", authHeader)
                    .post(body)
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string().orEmpty())
                        val token = json.optString("access_token")
                        val expiresIn = json.optInt("expires_in", 3600)
                        if (token.isNotEmpty()) {
                            oauthToken = token
                            oauthTokenExpiryMs = now + ((expiresIn - 60) * 1000L)
                            oauthClientIdx = idx
                            Log.d(TAG, "Acquired Reddit OAuth token using clientId $clientId")
                            return@withContext token
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        oauthToken = null
        null
    }

    private suspend fun fetchCatalogOAuth(sub: String, after: String?): String? = withContext(Dispatchers.IO) {
        val token = getOAuthToken() ?: return@withContext null
        val base = "https://oauth.reddit.com/r/$sub/new?limit=100&sort=new&raw_json=1"
        val url = if (after.isNullOrEmpty()) base else "$base&after=$after"

        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", OAUTH_UA)
                .addHeader("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    if (body.startsWith("{") || body.startsWith("[")) return@withContext body
                }
                if (res.code == 401 || res.code == 403) {
                    oauthToken = null
                    oauthTokenExpiryMs = 0
                }
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun fetchCatalogRss(sub: String, after: String?): String? = withContext(Dispatchers.IO) {
        val base = "https://www.reddit.com/r/$sub/new/.rss?limit=25"
        val url = if (after.isNullOrEmpty()) base else "$base&after=$after"
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", OAUTH_UA)
                .addHeader("Accept", "application/atom+xml, application/xml, */*")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    if (body.contains("<entry>")) return@withContext body
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun decodeXmlEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#32;", " ")

    private fun normalizeUnicode(input: String): String {
        val buf = StringBuilder()
        var i = 0
        while (i < input.length) {
            val codePoint = input.codePointAt(i)
            i += Character.charCount(codePoint)

            // 1. Mathematical Bold / Italic / Sans-Serif uppercase (A-Z)
            if ((codePoint in 0x1D400..0x1D419) ||
                (codePoint in 0x1D434..0x1D44D) ||
                (codePoint in 0x1D468..0x1D481) ||
                (codePoint in 0x1D5A0..0x1D5B9) ||
                (codePoint in 0x1D5D4..0x1D5ED) ||
                (codePoint in 0x1D608..0x1D621) ||
                (codePoint in 0x1D670..0x1D689)
            ) {
                val offset = (codePoint - 0x1D400) % 26
                buf.append((0x41 + offset).toChar())
                continue
            }

            // 2. Mathematical Bold / Italic / Sans-Serif lowercase (a-z)
            if ((codePoint in 0x1D41A..0x1D433) ||
                (codePoint in 0x1D44E..0x1D467) ||
                (codePoint in 0x1D482..0x1D49B) ||
                (codePoint in 0x1D5BA..0x1D5D3) ||
                (codePoint in 0x1D5EE..0x1D607) ||
                (codePoint in 0x1D622..0x1D63B) ||
                (codePoint in 0x1D68A..0x1D6A3)
            ) {
                val offset = (codePoint - 0x1D41A) % 26
                buf.append((0x61 + offset).toChar())
                continue
            }

            // 3. Mathematical Digits 0-9
            if ((codePoint in 0x1D7CE..0x1D7D7) ||
                (codePoint in 0x1D7E2..0x1D7EB) ||
                (codePoint in 0x1D7EC..0x1D7F5) ||
                (codePoint in 0x1D7F6..0x1D7FF)
            ) {
                val offset = (codePoint - 0x1D7CE) % 10
                buf.append((0x30 + offset).toChar())
                continue
            }

            // 4. Fullwidth ASCII
            if (codePoint in 0xFF01..0xFF5E) {
                buf.append((codePoint - 0xFEE0).toChar())
                continue
            }

            buf.append(Character.toChars(codePoint))
        }
        return buf.toString()
    }

    private fun extractPortals(rawText: String, source: String): List<IptvPortal> {
        if (rawText.length < 10 || isJunkCode(rawText)) return emptyList()
        val normalized = normalizeUnicode(
            rawText
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace(Regex("""<(?:p|br|div|li|h\d)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<[^>]+>"""), " ")
        )

        val acc = mutableMapOf<String, IptvPortal>()

        // 1. URL with user first: username=...&password=...
        val urlParamUserFirst = Regex(
            """(https?://[^?\s"'<]+)\?(?:[^\s"'<]*?&)?(?:username|user|usr|login|account)=([^&\s"'<]+)\s*&(?:[^\s"'<]*?&)?(?:password|pass|pwd)=([^&\s"'<]+)""",
            RegexOption.IGNORE_CASE
        )
        for (m in urlParamUserFirst.findAll(normalized)) {
            finalize(acc, m.groupValues[1], m.groupValues[2], m.groupValues[3], source)
        }

        // 2. URL with pass first: password=...&username=...
        val urlParamPassFirst = Regex(
            """(https?://[^?\s"'<]+)\?(?:[^\s"'<]*?&)?(?:password|pass|pwd)=([^&\s"'<]+)\s*&(?:[^\s"'<]*?&)?(?:username|user|usr|login|account)=([^&\s"'<]+)""",
            RegexOption.IGNORE_CASE
        )
        for (m in urlParamPassFirst.findAll(normalized)) {
            finalize(acc, m.groupValues[1], m.groupValues[3], m.groupValues[2], source)
        }

        // 3. Multi-line labeled blocks (Portal/Host + Username + Password + optional Port)
        val labelRegex = Regex(
            """(?:Portal(?:-URL)?|Host(?:\s*URL)?|Panel|Server|Real-URL|URL|M3U(?:-Link)?|Link|Servidor|H[ôo]te|Сервер|🌐|🌍|🔗|📡)\s*[:=➤\-•*|]?\s*(https?://[^\s<"'\n]+)[\s\S]{1,600}?(?:Username|User|Usr|Usu[áa]rio|Usuario|Utilisateur|Login|Account|Compte|Логин|👤|🗣️)\s*[:=➤\-•*|]?\s*([^\s|<"'\n]+)[\s\S]{1,300}?(?:Password|Pass|Pwd|Senha|Contrase[ñn]a|Clave|Mot\s*de\s*passe|Пароль|🔑|🔒|🔐)\s*[:=➤\-•*|]?\s*([^\s|<"'\n]+)(?:[\s\S]{1,200}?(?:Port|Porta|Puerto|Порт)\s*[:=➤\-•*|]?\s*(\d+))?""",
            RegexOption.IGNORE_CASE
        )
        for (m in labelRegex.findAll(normalized)) {
            val port = if (m.groupValues.size > 4) m.groupValues[4].ifEmpty { null } else null
            finalize(acc, m.groupValues[1], m.groupValues[2], m.groupValues[3], source, port)
        }

        // 4. Stream URI path (http://host:port/live/user/pass/123.ts)
        val streamRegex = Regex(
            """(https?://[^\s/]+(?::\d+)?)/(?:live|movie|series)/([^/\s]+)/([^/\s]+)/\d+""",
            RegexOption.IGNORE_CASE
        )
        for (m in streamRegex.findAll(normalized)) {
            finalize(acc, m.groupValues[1], m.groupValues[2], m.groupValues[3], source)
        }

        // 5. Line combo format (http://host:port:user:pass or http://host:port|user|pass)
        val comboRegex = Regex(
            """(?:^|\n)\s*(https?://[a-zA-Z0-9.\-_]+(?::\d+)?(?:/[^\s:|,\t\n]*)?)\s*[:|,\t ]\s*([a-zA-Z0-9_\-.@]+)\s*[:|,\t ]\s*([a-zA-Z0-9_\-.@!#$%^&*+=]+)""",
            RegexOption.MULTILINE
        )
        for (m in comboRegex.findAll(normalized)) {
            finalize(acc, m.groupValues[1], m.groupValues[2], m.groupValues[3], source)
        }

        return acc.values.toList()
    }

    private fun isJunkCode(text: String): Boolean {
        val markers = listOf(
            "Array.isArray", "prototype.", "function(", "var ", "const ",
            "let ", "return!", "void ", ".message}", "window.", "document."
        )
        var hits = 0
        for (m in markers) {
            if (text.contains(m)) hits++
            if (hits >= 2) return true
        }
        return false
    }

    private fun finalize(
        acc: MutableMap<String, IptvPortal>,
        rawUrl: String,
        rawUser: String,
        rawPass: String,
        source: String,
        rawPort: String? = null
    ) {
        var url = cleanPortalUrl(rawUrl)
        val user = cleanCred(rawUser)
        val pass = cleanCred(rawPass)

        if (!rawPort.isNullOrEmpty() && !url.contains(Regex(""":\d+$"""))) {
            val portNum = rawPort.trim().toIntOrNull()
            if (portNum != null && portNum in 1..65535) {
                url = "$url:$portNum"
            }
        }

        if (url.length < 10 || user.length < 3 || pass.length < 3) return
        if (user.contains("http") || pass.contains("http")) return

        val lu = user.lowercase()
        val lp = pass.lowercase()
        if (lp.endsWith(".php") || lp.endsWith(".ts") || lp.endsWith(".m3u") || lp.endsWith(".m3u8") || lp.endsWith(".mp4")) return
        if (lu.endsWith(".php") || lu == "play") return

        for (j in junkTokens) {
            if (lu == j || lp == j || lu.contains(j) || lp.contains(j)) return
        }

        val p = IptvPortal(url = url, username = user, password = pass, source = source)
        acc.putIfAbsent(p.key, p)
    }

    private fun cleanPortalUrl(raw: String): String {
        var clean = raw.replace(Regex("""\s+"""), "")
        val qIdx = clean.indexOf('?')
        if (qIdx >= 0) clean = clean.substring(0, qIdx)
        clean = clean.trim()
        if (clean.contains('@')) {
            clean = "http://" + clean.substring(clean.lastIndexOf('@') + 1)
        }
        clean = clean.replace(
            Regex("""/(?:get|live|movie|series|portal|c|index|playlist|player_api|xmltv|index\.php|portal\.php)\.php$""", RegexOption.IGNORE_CASE),
            ""
        )
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }
        if (!clean.startsWith("http")) clean = "http://$clean"
        return clean
    }

    private fun cleanCred(raw: String): String {
        var s = raw.trim()
        while (s.startsWith("=") || s.startsWith(":") || s.startsWith("➤") || s.startsWith("•")) {
            s = s.substring(1).trim()
        }
        val parts = s.split(Regex("""[\s\n&?|<>]"""))
        var res = parts.firstOrNull()?.trim().orEmpty()
        res = res.replace(Regex("""[,;]+$"""), "")
        return res
    }
}
