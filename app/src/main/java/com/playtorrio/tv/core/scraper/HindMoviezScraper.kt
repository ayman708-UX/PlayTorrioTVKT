package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-Kotlin HindMoviez Stream Scraper for PlayTorrioHTTP.
 * Ported 1-to-1 from Vyla HindMoviez provider.
 * Scrapes Bollywood, Hindi Dubbed/Dual-Audio, and Hollywood movies and TV series
 * from hindmovie.icu via mvlink.blog and hshare.ink solver pipelines.
 */
class HindMoviezScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "HindMoviezScraper"
        private const val BASE_URL = "https://hindmovie.icu"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val DEFAULT_HEADERS = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Accept" to "application/json, text/html, */*"
        )

        @Volatile
        private var cachedSplashCookie: String? = null
        @Volatile
        private var cachedSplashCookieExpiry: Long = 0L

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        private val noRedirectHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        private fun getCleanTitle(raw: String): String {
            var s = raw.lowercase()
            s = s.replace("download", "")
            s = s.replace(Regex("""\b(dual audio|multi audio|hindi|english|tamil|telugu|malayalam|korean|japanese|chinese|spanish|french|italian|german)\b"""), "")
            s = s.replace(Regex("""\b(480p|720p|1080p|2160p|4k|2k|hd|fhd|uhd)\b"""), "")
            s = s.replace(Regex("""\b(web-?dl|web-?dlrip|web-?rip|brrip|bdrip|bluray|blu-?ray|hdtv|tvrip|dvdrip|camrip|hdrip)\b"""), "")
            s = s.replace(Regex("""\b(x264|x265|hevc|10bit|12bit|aac|ac3|dd5\.1|ddp5\.1|atmos|dts)\b"""), "")
            s = s.replace(Regex("""\b(season|saison|staffel)\s*\d+(?:\s*(?:-|to)\s*\d+)?\b"""), "")
            s = s.replace(Regex("""\bs\d+(?:\s*(?:-|to)\s*\d+)?\b"""), "")
            s = s.replace(Regex("""\b(episode|episodes|ep)\s*\d+(?:\s*(?:-|to)\s*\d+)?\s*(added|update|updated)?\b"""), "")
            s = s.replace(Regex("""\b(complete|all episodes|pack|batch)\b"""), "")
            s = s.replace(Regex("""\b(movie|film|part\s*\d+|vol\s*\d+|volume\s*\d+)\b"""), "")
            s = s.replace(Regex("""\b(unrated|extended|directors cut|uncut|18)\b"""), "")
            s = s.replace(Regex("""\b(19\d{2}|20\d{2})\b"""), "")
            s = s.replace(Regex("""[^a-z0-9]"""), " ")
            s = s.replace(Regex("""\s+"""), " ").trim()
            s = s.replace(Regex("""^(the|a|an)\s+"""), "")
            return s
        }

        private fun extractYear(s: String): Int? {
            val m = Regex("""\b(19\d{2}|20\d{2})\b""").find(s)
            return m?.groupValues?.get(1)?.toIntOrNull()
        }

        private fun isStrictMatch(targetTitle: String, targetYear: Int?, postTitle: String, postYear: Int?): Boolean {
            val c1 = getCleanTitle(targetTitle)
            val c2 = getCleanTitle(postTitle)
            if (c1.isEmpty() || c2.isEmpty()) return false
            if (c1 == c2) return true
            if (targetYear != null && postYear != null) {
                if (abs(targetYear - postYear) > 1) return false
            }
            return c1.startsWith(c2) || c2.startsWith(c1)
        }

        private fun parseQuality(context: String): String {
            val c = context.lowercase()
            if (c.contains("2160p") || c.contains("4k") || c.contains("uhd")) return "2160p"
            if (c.contains("1440p") || c.contains("2k")) return "1440p"
            if (c.contains("1080p") || c.contains("fhd")) return "1080p"
            if (c.contains("720p") || c.contains("hd")) return "720p"
            if (c.contains("480p") || c.contains("sd")) return "480p"
            return "1080p"
        }

        private fun extractSeasonHtml(html: String, season: Int): String {
            val seasonRegex = Regex("""(?:Season|Saison|Staffel)\s+0*(\d+)\b""", RegexOption.IGNORE_CASE)
            val matches = seasonRegex.findAll(html).toList()
            if (matches.isEmpty()) return html

            data class SeasonIndex(val season: Int, val index: Int)
            val seasonsFound = mutableListOf<SeasonIndex>()

            for (m in matches) {
                var startIdx = html.lastIndexOf('<', m.range.first)
                if (startIdx < 0 || m.range.first - startIdx > 500) {
                    startIdx = m.range.first
                }
                val tagPrefix = html.substring(startIdx, (m.range.first + 50).coerceAtMost(html.length)).lowercase()
                if (tagPrefix.contains("download") || tagPrefix.contains("episode")) continue

                val sNum = m.groupValues[1].toIntOrNull()
                if (sNum != null) {
                    seasonsFound.add(SeasonIndex(sNum, startIdx))
                }
            }

            val matched = seasonsFound.filter { it.season == season }
            if (matched.isEmpty()) return html

            val startIndex = matched.first().index
            var endIndex = html.length
            for (s in seasonsFound) {
                if (s.index > startIndex && s.season != season) {
                    endIndex = s.index
                    break
                }
            }

            return html.substring(startIndex, endIndex)
        }

        private fun appendTimestamp(url: String): String {
            val sep = if (url.contains("?")) "&" else "?"
            return "$url${sep}s=${System.currentTimeMillis()}"
        }

        private fun bypassHShareAPI(hshareId: String, mvlinkUrl: String): String? {
            try {
                val b64 = Base64.encodeToString(hshareId.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).replace("=", "")
                val formBody = FormBody.Builder()
                    .add("action", "hindshare_sign")
                    .add("d", b64)
                    .build()

                val req = Request.Builder()
                    .url("https://mvlink.blog/wp-admin/admin-ajax.php")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", mvlinkUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", DEFAULT_UA)
                    .post(formBody)
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val json = JSONObject(body)
                            if (json.optBoolean("success") && json.has("data")) {
                                return json.getJSONObject("data").optString("url")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        private fun getWithCookieAndRedirects(url: String, referer: String, cookie: String? = null, maxHops: Int = 5): Pair<Int, String?> {
            var currentUrl = url
            for (hop in 0 until maxHops) {
                val reqBuilder = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", referer)

                if (!cookie.isNullOrEmpty()) {
                    reqBuilder.header("Cookie", cookie)
                }

                noRedirectHttpClient.newCall(reqBuilder.build()).execute().use { res ->
                    val code = res.code
                    if (code in 300..399) {
                        val loc = res.header("Location")
                        if (loc != null) {
                            currentUrl = loc
                            return@use
                        }
                    }
                    return Pair(code, res.body?.string())
                }
            }
            return Pair(0, null)
        }

        private fun solveHShareChallenge(rUrl: String, html: String, referer: String): String? {
            try {
                val uMatch = Regex("""var\s+U\s*=\s*\[(.*?)\];""", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
                val rawU = uMatch.groupValues[1]
                val u = Regex("""'([^']*)'""").findAll(rawU).map { it.groupValues[1] }.toMutableList()

                val rotMatch = Regex("""\}\(a0w\s*,\s*(0x[0-9a-fA-F]+|\d+)\)\);""").find(html) ?: return null
                val targetRStr = rotMatch.groupValues[1]
                val targetR = if (targetRStr.startsWith("0x")) targetRStr.substring(2).toInt(16) else targetRStr.toInt()

                val baseMatch = Regex("""e\s*=\s*e\s*-\s*(0x[0-9a-fA-F]+|\d+);""").find(html) ?: return null
                val baseOffsetStr = baseMatch.groupValues[1]
                val baseOffset = if (baseOffsetStr.startsWith("0x")) baseOffsetStr.substring(2).toInt(16) else baseOffsetStr.toInt()

                val mapMatch = Regex("""var\s+([a-zA-Z0-9_]+)\s*=\s*\{([^}]+)\}\s*,\s*a\s*=\s*a0e""").find(html)
                val a0qMap = mutableMapOf<String, Int>()
                if (mapMatch != null) {
                    for (pair in mapMatch.groupValues[2].split(",")) {
                        val parts = pair.split(":")
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val vStr = parts[1].trim()
                            val v = if (vStr.startsWith("0x")) vStr.substring(2).toInt(16) else vStr.toInt()
                            a0qMap[key] = v
                        }
                    }
                }

                val exprMatch = Regex("""var\s+e\s*=\s*([^;]+);\s*if\s*\(\s*e\s*===\s*r\s*\)""").find(html) ?: return null
                val exprStr = exprMatch.groupValues[1]

                fun parseIntLeading(s: String): Int {
                    val m = Regex("""^-?\d+""").find(s)
                    return m?.value?.toIntOrNull() ?: 0
                }

                fun a(code: Int): String {
                    val idx = code - baseOffset
                    if (idx < 0 || idx >= u.size) return ""
                    return u[idx]
                }

                fun evalTerm(term: String): Double {
                    val mults = term.split("*")
                    var termVal = 1.0
                    for (mult in mults) {
                        val mTrim = mult.trim()
                        val isNeg = mTrim.startsWith("(-") || mTrim.startsWith("-")
                        val callMatch = Regex("""a\((0x[0-9a-fA-F]+|[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+)\)""").find(mTrim)
                        var code = 0
                        if (callMatch != null) {
                            val arg = callMatch.groupValues[1]
                            if (arg.contains(".")) {
                                val prop = arg.split(".")[1]
                                code = a0qMap[prop] ?: 0
                            } else {
                                code = if (arg.startsWith("0x")) arg.substring(2).toInt(16) else arg.toInt()
                            }
                        }
                        val strVal = a(code)
                        val intVal = parseIntLeading(strVal)

                        val divMatch = Regex("""/\s*(0x[0-9a-fA-F]+|\d+)""").find(mTrim)
                        val divisor = if (divMatch != null) {
                            val dStr = divMatch.groupValues[1]
                            if (dStr.startsWith("0x")) dStr.substring(2).toInt(16) else dStr.toInt()
                        } else 1

                        var partVal = intVal.toDouble() / divisor
                        if (isNeg) partVal = -partVal
                        termVal *= partVal
                    }
                    return termVal
                }

                val terms = exprStr.replace("+-", "-").split("+")
                var iterations = 0
                while (iterations < 200) {
                    var sum = 0.0
                    for (t in terms) {
                        if (t.contains("-") && !t.contains("(-")) {
                            val sub = t.split("-")
                            sum += evalTerm(sub[0])
                            for (i in 1 until sub.size) {
                                if (sub[i].isNotEmpty()) sum -= evalTerm(sub[i])
                            }
                        } else {
                            sum += evalTerm(t)
                        }
                    }
                    if (sum.roundToInt() == targetR) break
                    u.add(u.removeAt(0))
                    iterations++
                }

                val a0gMatch = Regex("""var\s+([a-zA-Z0-9_]+)\s*=\s*\{([^}]+)\}\s*;\s*setTimeout\s*\(\s*function\s*\(\)\s*\{""").find(html)
                val a0gMap = mutableMapOf<String, Int>()
                if (a0gMatch != null) {
                    for (pair in a0gMatch.groupValues[2].split(",")) {
                        val parts = pair.split(":")
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val vStr = parts[1].trim()
                            val v = if (vStr.startsWith("0x")) vStr.substring(2).toInt(16) else vStr.toInt()
                            a0gMap[key] = v
                        }
                    }
                }

                fun parseDigits(expr: String): Int {
                    val parts = expr.split(Regex("""\)\s*\+\s*\("""))
                    val sb = StringBuilder()
                    for (p in parts) {
                        val count = Regex("""!\+\[\]|!!\[\]""").findAll(p).count()
                        sb.append(count)
                    }
                    return sb.toString().toIntOrNull() ?: 0
                }

                val rDigitsMatch = Regex("""r=\+\(([\s\S]*?)\),\s*w=""").find(html) ?: return null
                val xDigitsMatch = Regex("""x=\+\(([\s\S]*?)\),\s*s=""").find(html) ?: return null

                val rVal = parseDigits(rDigitsMatch.groupValues[1])
                val xVal = parseDigits(xDigitsMatch.groupValues[1])
                val wsidchk = (rVal + xVal).toString()

                val wActionMatch = Regex("""W\s*=\s*'(\/[a-zA-Z0-9]+)'""").find(html) ?: return null
                val actionPath = wActionMatch.groupValues[1]

                val pDataMatch = Regex("""P\s*=\s*'([^']+)'""").find(html) ?: return null
                val pdata = pDataMatch.groupValues[1]

                val tsMatch = Regex("""'ts'\s*,\s*'(\d+)'""").find(html)
                    ?: Regex("""'ts'\s*,\s*[^=]+(?:'value'|L\([^)]+\))\s*=\s*'(\d+)'""").find(html)
                    ?: Regex("""E\[L\([^)]+\)\]\s*=\s*'(\d+)'""").find(html)
                val ts = tsMatch?.groupValues?.get(1) ?: (System.currentTimeMillis() / 1000).toString()

                val idStmtMatch = Regex("""o\['value'\]\s*=\s*([^;,]+)[;,]""").find(html)
                val idSb = StringBuilder()
                if (idStmtMatch != null) {
                    val pieces = idStmtMatch.groupValues[1].split("+")
                    for (piece in pieces) {
                        val pTrim = piece.trim()
                        if (pTrim.startsWith("'") && pTrim.endsWith("'")) {
                            idSb.append(pTrim.substring(1, pTrim.length - 1))
                        } else {
                            val lMatch = Regex("""L\((0x[0-9a-fA-F]+|[a-zA-Z0-9_]+\.[a-zA-Z0-9_]+)\)""").find(pTrim)
                            if (lMatch != null) {
                                val arg = lMatch.groupValues[1]
                                val code = if (arg.contains(".")) {
                                    a0gMap[arg.split(".")[1]] ?: 0
                                } else {
                                    if (arg.startsWith("0x")) arg.substring(2).toInt(16) else arg.toInt()
                                }
                                idSb.append(a(code))
                            }
                        }
                    }
                }
                val idVal = idSb.toString()

                val challengeUrl = "https://hshare.ink$actionPath?wsidchk=$wsidchk&pdata=$pdata&id=$idVal&ts=$ts&cttl=0"

                val chalReq = Request.Builder()
                    .url(challengeUrl)
                    .header("User-Agent", DEFAULT_UA)
                    .header("Referer", rUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                noRedirectHttpClient.newCall(chalReq).execute().use { chalRes ->
                    val cookieHeader = chalRes.header("set-cookie")
                    val locationHeader = chalRes.header("location")

                    var cookie: String? = null
                    if (cookieHeader != null) {
                        cookie = cookieHeader.substringBefore(";")
                        cachedSplashCookie = cookie
                        cachedSplashCookieExpiry = System.currentTimeMillis() + (50 * 60 * 1000L)
                    }

                    val finalTarget = if (locationHeader != null) {
                        if (locationHeader.startsWith("http")) locationHeader else "https://hshare.ink$locationHeader"
                    } else rUrl

                    val (_, body) = getWithCookieAndRedirects(finalTarget, rUrl, cookie)
                    return body
                }
            } catch (_: Exception) {
                return null
            }
        }

        private fun resolveHCloudUrl(hcloudUrl: String, referer: String): String? {
            try {
                val uri = android.net.Uri.parse(hcloudUrl)
                val rawParam = uri.getQueryParameter("url") ?: return null

                val decoded = String(Base64.decode(rawParam, Base64.DEFAULT), StandardCharsets.UTF_8)
                if (decoded.startsWith("http")) {
                    if (decoded.contains(".workers.dev") || decoded.contains(".powerly.dev")) {
                        return appendTimestamp(decoded)
                    }

                    val nestedMatch = Regex("""url=([^&]+)""", RegexOption.IGNORE_CASE).find(decoded)
                    if (nestedMatch != null) {
                        try {
                            val nestedDecoded = String(Base64.decode(nestedMatch.groupValues[1], Base64.DEFAULT), StandardCharsets.UTF_8)
                            if (nestedDecoded.contains(".workers.dev") || nestedDecoded.contains(".powerly.dev")) {
                                return appendTimestamp(nestedDecoded)
                            }
                        } catch (_: Exception) {}
                    }

                    val resReq = Request.Builder()
                        .url(decoded)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", referer)
                        .build()

                    httpClient.newCall(resReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val workerRegex = Regex("""href="([^"]+\.(?:workers\.dev|powerly\.dev)[^"]+)"""", RegexOption.IGNORE_CASE)
                            val match = workerRegex.find(body)
                            if (match != null) {
                                return appendTimestamp(match.groupValues[1])
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type == "tv" || request.type == "series"

        try {
            val cleanTitle = getCleanTitle(request.title)
            val posts = mutableListOf<JSONObject>()

            fun searchWPJson(q: String) {
                try {
                    val encoded = URLEncoder.encode(q, "UTF-8")
                    val url = "$BASE_URL/wp-json/wp/v2/posts?search=$encoded&per_page=100"
                    val req = Request.Builder().url(url).apply { DEFAULT_HEADERS.forEach { (k, v) -> header(k, v) } }.build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            if (body.startsWith("[")) {
                                val arr = JSONArray(body)
                                for (i in 0 until arr.length()) {
                                    val it = arr.optJSONObject(i) ?: continue
                                    posts.add(it)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!request.imdbId.isNullOrEmpty() && request.imdbId.startsWith("tt")) {
                searchWPJson(request.imdbId)
            }
            if (posts.isEmpty() && cleanTitle.isNotEmpty()) {
                searchWPJson(cleanTitle)
            }

            if (posts.isEmpty()) return@withContext emptyList()

            var matchedPost: JSONObject? = null
            if (!request.imdbId.isNullOrEmpty()) {
                for (post in posts) {
                    val content = post.optJSONObject("content")?.optString("rendered").orEmpty()
                    if (content.contains(request.imdbId)) {
                        matchedPost = post
                        break
                    }
                }
            }

            if (matchedPost == null) {
                for (post in posts) {
                    val pTitle = post.optJSONObject("title")?.optString("rendered").orEmpty()
                    val pYear = extractYear(pTitle)
                    if (isStrictMatch(request.title, request.year, pTitle, pYear)) {
                        matchedPost = post
                        break
                    }
                }
            }

            if (matchedPost == null) {
                val targetClean = getCleanTitle(request.title)
                for (post in posts) {
                    val pTitle = post.optJSONObject("title")?.optString("rendered").orEmpty()
                    val pClean = getCleanTitle(pTitle)
                    if (pClean.contains(targetClean) || targetClean.contains(pClean)) {
                        matchedPost = post
                        break
                    }
                }
            }

            if (matchedPost == null) return@withContext emptyList()

            var contentHtml = matchedPost.optJSONObject("content")?.optString("rendered").orEmpty()
            val postTitleRendered = matchedPost.optJSONObject("title")?.optString("rendered")?.ifEmpty { request.title } ?: request.title

            if (isTv && request.season != null) {
                val sHtml = extractSeasonHtml(contentHtml, request.season)
                if (sHtml.isNotEmpty()) {
                    contentHtml = sHtml
                }
            }

            val mvlinkRegex = Regex("""href="(https?:\/\/mvlink\.blog\/(?:web\/)?\d+)"""", RegexOption.IGNORE_CASE)
            val mvMatches = mvlinkRegex.findAll(contentHtml).toList()
            if (mvMatches.isEmpty()) return@withContext emptyList()

            val mvLinks = mutableListOf<Pair<String, String>>()
            for (m in mvMatches) {
                val url = m.groupValues[1]
                val startIdx = (m.range.first - 500).coerceAtLeast(0)
                val precedingContext = contentHtml.substring(startIdx, m.range.first)
                val quality = parseQuality(precedingContext)
                if (quality != "480p") {
                    mvLinks.add(Pair(url, quality))
                }
            }

            val results = mutableListOf<ScraperStreamResult>()
            val seenStreamUrls = mutableSetOf<String>()

            for ((mvUrl, quality) in mvLinks) {
                try {
                    val mvReq = Request.Builder()
                        .url(mvUrl)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Referer", "$BASE_URL/")
                        .build()

                    val mvHtml = httpClient.newCall(mvReq).execute().use { res ->
                        if (res.isSuccessful) res.body?.string().orEmpty() else ""
                    }

                    if (mvHtml.isEmpty()) continue

                    val hshareRegex = Regex("""href="(?:https:\/\/hshare\.ink\/\?id=([^"]+)|https:\/\/hshare\.ink\/dl\/([^"]+))"""", RegexOption.IGNORE_CASE)
                    val hMatches = hshareRegex.findAll(mvHtml)
                    val hshareIds = mutableListOf<String>()

                    for (hm in hMatches) {
                        val id = hm.groupValues[1].ifEmpty { hm.groupValues[2] }
                        if (id.isNotEmpty()) {
                            hshareIds.add(id)
                        }
                    }

                    if (hshareIds.isEmpty()) continue

                    val idsToProcess = if (isTv && request.episode != null) {
                        val epIdx = (request.episode - 1)
                        if (epIdx in 0 until hshareIds.size) {
                            listOf(hshareIds[epIdx])
                        } else hshareIds
                    } else hshareIds

                    for (id in idsToProcess) {
                        val rUrl = bypassHShareAPI(id, mvUrl) ?: continue
                        var downloadHtml: String? = null

                        if (cachedSplashCookie != null && System.currentTimeMillis() < cachedSplashCookieExpiry) {
                            val (_, body) = getWithCookieAndRedirects(rUrl, mvUrl, cachedSplashCookie)
                            if (!body.isNullOrEmpty() && !body.contains("wsidchk")) {
                                downloadHtml = body
                            }
                        }

                        if (downloadHtml == null) {
                            val (_, initialBody) = getWithCookieAndRedirects(rUrl, mvUrl)
                            if (!initialBody.isNullOrEmpty()) {
                                downloadHtml = if (initialBody.contains("wsidchk")) {
                                    solveHShareChallenge(rUrl, initialBody, mvUrl)
                                } else initialBody
                            }
                        }

                        if (downloadHtml.isNullOrEmpty()) continue

                        val nameMatch = Regex("""Name:\s*([^<]+)""", RegexOption.IGNORE_CASE).find(downloadHtml)
                        val rawFileName = nameMatch?.groupValues?.get(1)?.trim()

                        val hcloudRegex = Regex("""href="([^"]+hcloud\.ink[^"]+)"""", RegexOption.IGNORE_CASE)
                        val hcloudMatches = hcloudRegex.findAll(downloadHtml).toList()

                        var serverNum = 1
                        for (hcm in hcloudMatches) {
                            val hcloudUrl = hcm.groupValues[1]
                            val directUrl = resolveHCloudUrl(hcloudUrl, rUrl)
                            if (!directUrl.isNullOrEmpty() && seenStreamUrls.add(directUrl)) {
                                val srvLabel = "Server $serverNum"
                                val isHls = directUrl.contains(".m3u8")

                                results.add(
                                    ScraperStreamResult(
                                        name = "PlayTorrioHTTP",
                                        title = "HindMoviez · $quality · $srvLabel",
                                        description = rawFileName ?: "HindMoviez Stream",
                                        url = directUrl,
                                        quality = quality,
                                        headers = DEFAULT_HEADERS
                                    )
                                )
                                serverNum++
                            }
                        }

                        if (results.isNotEmpty() && !isTv) break
                    }

                    if (results.isNotEmpty() && !isTv) break
                } catch (_: Exception) {}
            }

            results
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping HindMoviez: ${e.message}")
            emptyList()
        }
    }
}
