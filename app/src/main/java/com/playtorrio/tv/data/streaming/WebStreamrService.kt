package com.playtorrio.tv.data.streaming

import android.util.Base64
import android.util.Log
import com.playtorrio.tv.data.api.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

private const val WTAG = "WebStreamr"
private const val WUA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

object WebStreamrService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry points
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xpass — multi-backup HLS player at play.xpass.top. Each title page
     * exposes a `backups=[…]` JS array of playlist endpoints; each playlist
     * resolves to JSON containing one or more `.m3u8` source URLs. We try
     * each backup in declared order and return the first working m3u8.
     */
    suspend fun extractXpass(tmdbId: Int, season: Int?, episode: Int?): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                val isMovie = season == null
                val pageUrl = if (isMovie) "https://play.xpass.top/e/movie/$tmdbId"
                              else "https://play.xpass.top/e/tv/$tmdbId/$season/$episode"
                Log.i(WTAG, "[Xpass] Page: $pageUrl")

                val html = httpGetWithHeaders(pageUrl, mapOf(
                    "Referer" to "https://play.xpass.top/"
                )) ?: return@withContext null.also { Log.w(WTAG, "[Xpass] Page fetch failed") }

                // Pull the backups=[ … ] array. JSON has objects of the form
                // {"id":"…","name":"TIK 1","url":"/mdata/…/playlist.json","dl":false}
                val backupsMatch = Regex("""var\s+backups\s*=\s*(\[.*?])\s*</script>""", RegexOption.DOT_MATCHES_ALL)
                    .find(html)
                    ?: Regex("""var\s+backups\s*=\s*(\[.*?])""", RegexOption.DOT_MATCHES_ALL).find(html)
                    ?: return@withContext null.also { Log.w(WTAG, "[Xpass] backups[] not found in page") }

                val backupsArr = org.json.JSONArray(backupsMatch.groupValues[1])
                Log.i(WTAG, "[Xpass] ${backupsArr.length()} backup playlist(s)")

                for (i in 0 until backupsArr.length()) {
                    val obj = backupsArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val rel  = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val abs  = if (rel.startsWith("http")) rel
                               else "https://play.xpass.top$rel"
                    val plJson = httpGetWithHeaders(abs, mapOf("Referer" to pageUrl))
                    if (plJson == null) {
                        Log.w(WTAG, "[Xpass] $name: playlist fetch failed ($abs)")
                        continue
                    }
                    // playlist[0].sources[].file
                    val first: String? = try {
                        var pick: String? = null
                        val root = JSONObject(plJson)
                        val playlist = root.optJSONArray("playlist")
                        val item = playlist?.optJSONObject(0)
                        val sources = item?.optJSONArray("sources")
                        if (sources != null) {
                            for (j in 0 until sources.length()) {
                                val s = sources.optJSONObject(j) ?: continue
                                val file = s.optString("file")
                                if (file.contains(".m3u8", ignoreCase = true)) { pick = file; break }
                            }
                        }
                        pick
                    } catch (e: Exception) {
                        Log.w(WTAG, "[Xpass] $name: parse failed: ${e.message}")
                        null
                    }
                    if (first == null) continue

                    Log.i(WTAG, "[Xpass] $name → $first")
                    return@withContext StreamResult(
                        url = first,
                        referer = "https://play.xpass.top/"
                    )
                }
                Log.w(WTAG, "[Xpass] No playable backups")
                null
            } catch (e: Exception) {
                Log.e(WTAG, "[Xpass] failed", e)
                null
            }
        }

    /** 4KHDHub — multi-language movies + series. Full scraper chain. */
    suspend fun extractFourKHDHub(tmdbId: Int, season: Int?, episode: Int?): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                val isMovie = season == null
                val (title, year) = getTmdbTitleAndYear(tmdbId, isMovie)
                    ?: return@withContext null.also { Log.w(WTAG, "[4KHDHub] TMDB lookup failed") }

                val baseUrl = getFinalUrl("https://4khdhub.dad") ?: "https://4khdhub.dad"
                Log.i(WTAG, "[4KHDHub] Base URL: $baseUrl")

                val pageUrl = findFourKHDHubPageUrl(baseUrl, title, year, isMovie)
                    ?: return@withContext null.also { Log.w(WTAG, "[4KHDHub] Page not found: $title ($year)") }
                Log.i(WTAG, "[4KHDHub] Page: $pageUrl")

                val html = httpGet(pageUrl)
                    ?: return@withContext null.also { Log.w(WTAG, "[4KHDHub] Failed to fetch page") }
                val doc = Jsoup.parse(html)

                val redirectUrls: List<String> = if (season != null && episode != null) {
                    val sPad = season.toString().padStart(2, '0')
                    val ePad = episode.toString().padStart(2, '0')
                    doc.select(".episode-item")
                        .filter { it.select(".episode-title").text().contains("S$sPad") }
                        .flatMap { item ->
                            item.select(".episode-download-item")
                                .filter { it.text().contains("Episode-$ePad") }
                                .flatMap { dl -> extractHdHubRedirectLinks(dl.html() ?: "") }
                        }
                } else {
                    doc.select(".download-item")
                        .flatMap { el -> extractHdHubRedirectLinks(el.html() ?: "") }
                }

                for (redirectUrl in redirectUrls) {
                    Log.i(WTAG, "[4KHDHub] Resolving: $redirectUrl")
                    val hubUrl = resolveHdHubRedirectUrl(redirectUrl) ?: continue
                    Log.i(WTAG, "[4KHDHub] Hub URL: $hubUrl")
                    val stream = extractFromHubUrl(hubUrl, referer = pageUrl) ?: continue
                    Log.i(WTAG, "[4KHDHub] ✅ Stream: ${stream.url}")
                    return@withContext stream
                }
                null
            } catch (e: Exception) {
                Log.w(WTAG, "[4KHDHub] Error: ${e.message}")
                null
            }
        }

    /** HDHub4u — multi-language movies + series. Uses IMDB ID + Typesense search. */
    suspend fun extractHDHub4u(tmdbId: Int, season: Int?, episode: Int?): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                val isMovie = season == null
                val imdbId = getImdbId(tmdbId, isMovie)
                    ?: return@withContext null.also { Log.w(WTAG, "[HDHub4u] No IMDB ID for tmdb:$tmdbId") }
                Log.i(WTAG, "[HDHub4u] IMDB ID: $imdbId")

                val pageUrls = findHDHub4uPageUrls(imdbId, season)
                if (pageUrls.isEmpty()) {
                    Log.w(WTAG, "[HDHub4u] No pages found for $imdbId")
                    return@withContext null
                }

                for (pageUrl in pageUrls) {
                    Log.i(WTAG, "[HDHub4u] Fetching: $pageUrl")
                    val html = httpGetWithHeaders(pageUrl, mapOf("Referer" to "https://new5.hdhub4u.fo"))
                        ?: continue
                    val doc = Jsoup.parse(html)

                    val hurlList: List<String> = if (episode != null) {
                        val ePad = episode.toString().padStart(2, '0')
                        doc.select("a:contains(EPiSODE $episode), a:contains(EPiSODE $ePad)")
                            .map { it.attr("href") }
                            .filter { it.isNotBlank() }
                    } else {
                        // For movies: direct HubDrive links + gadgetsweb redirect links
                        val hubDrive = doc.select("a[href*=hubdrive]")
                            .filter { !it.text().contains("⚡") }
                            .map { it.attr("href") }
                        val gadget  = doc.select("a[href*=gadgetsweb]")
                            .map { it.attr("href") }
                        hubDrive + gadget
                    }

                    for (hurl in hurlList) {
                        if (hurl.isBlank()) continue
                        Log.i(WTAG, "[HDHub4u] Processing: $hurl")

                        // Collect hubdrive/hubcloud URLs, possibly via intermediate page
                        val hubUrls: List<String> = when {
                            hurl.contains("hubdrive") || hurl.contains("hubcloud") -> listOf(hurl)
                            else -> {
                                // gadgetsweb-style redirect → resolves to an intermediate hub-links page
                                val intermediate = resolveHdHubRedirectUrl(hurl) ?: continue
                                Log.i(WTAG, "[HDHub4u] Intermediate: $intermediate")
                                if (intermediate.contains("hubdrive") || intermediate.contains("hubcloud")) {
                                    listOf(intermediate)
                                } else {
                                    // Fetch intermediate page and collect all hubdrive links inside it
                                    val midHtml = httpGetWithHeaders(intermediate, mapOf("Referer" to pageUrl))
                                        ?: continue
                                    Jsoup.parse(midHtml).select("a[href*=hubdrive]")
                                        .filter { !it.text().contains("⚡") }
                                        .map { it.attr("href") }
                                        .filter { it.isNotBlank() }
                                        .also { Log.i(WTAG, "[HDHub4u] ${it.size} hubdrive links in intermediate") }
                                }
                            }
                        }

                        for (hubUrl in hubUrls) {
                            Log.i(WTAG, "[HDHub4u] Hub URL: $hubUrl")
                            val stream = extractFromHubUrl(hubUrl, referer = pageUrl) ?: continue
                            Log.i(WTAG, "[HDHub4u] ✅ Stream: ${stream.url}")
                            return@withContext stream
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(WTAG, "[HDHub4u] Error: ${e.message}")
                null
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // FlixerTV — returns embed URL to be loaded in the WebView extractor
    // search/{slug} → season/episode IDs → /ajax/episode/sources/{serverId}
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetches the videostr.net embed URL for the given title; the caller loads it in a WebView. */
    suspend fun extractFlixerTVEmbed(tmdbId: Int, season: Int?, episode: Int?): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                val isMovie = season == null
                val (title, _) = getTmdbTitleAndYear(tmdbId, isMovie)
                    ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] TMDB lookup failed") }

                // Slug: lowercase, replace spaces/specials with hyphens
                val slug = title.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                val searchUrl = "https://theflixertv.to/search/$slug"
                Log.i(WTAG, "[FlixerTV] Searching: $searchUrl")
                val searchHtml = httpGetWithHeaders(searchUrl, mapOf("Referer" to "https://theflixertv.to"))
                    ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Search failed for: $title") }

                val searchDoc = Jsoup.parse(searchHtml)
                // Cards: /tv/watch-...-full-{id} for shows, /movie/watch-...-full-{id} for movies
                val typePrefix = if (isMovie) "/movie/watch-" else "/tv/watch-"
                val pagePath = searchDoc.select("a[href*='$typePrefix']")
                    .firstOrNull { el ->
                        val cardTitle = el.attr("title").lowercase()
                        val titleWords = title.lowercase().split(" ").filter { it.length > 2 }
                        titleWords.count { cardTitle.contains(it) } >=
                            (titleWords.size.coerceAtLeast(1) * 0.6).toInt()
                    }?.attr("href")
                    // fallback: first result of the right type
                    ?: searchDoc.select("a[href*='$typePrefix']").firstOrNull()?.attr("href")
                    ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] No $typePrefix result for: $title") }

                val showId = pagePath.substringAfterLast('-').trimEnd('/')
                if (showId.toLongOrNull() == null) return@withContext null
                    .also { Log.w(WTAG, "[FlixerTV] Could not parse ID from: $pagePath") }

                val pageUrl = "https://theflixertv.to$pagePath"
                Log.i(WTAG, "[FlixerTV] Page: $pageUrl (id=$showId)")

                val serverPreference = listOf("AKCloud", "MegaCloud", "UpCloud")

                val episodeId: String
                if (!isMovie && season != null && episode != null) {
                    // 1. Get season list → find season by number
                    val seasonHtml = httpGetWithHeaders(
                        "https://theflixertv.to/ajax/season/list/$showId",
                        mapOf("Referer" to pageUrl, "X-Requested-With" to "XMLHttpRequest")
                    ) ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Season list failed") }
                    val seasonDoc = Jsoup.parse(seasonHtml)
                    val seasonId = seasonDoc.select("a[data-id]")
                        .firstOrNull { it.text().trim().equals("Season $season", ignoreCase = true) }
                        ?.attr("data-id")
                        ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Season $season not found") }
                    Log.i(WTAG, "[FlixerTV] Season $season id=$seasonId")

                    // 2. Get episodes → find by number
                    val epHtml = httpGetWithHeaders(
                        "https://theflixertv.to/ajax/season/episodes/$seasonId",
                        mapOf("Referer" to pageUrl, "X-Requested-With" to "XMLHttpRequest")
                    ) ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Episode list failed") }
                    val epDoc = Jsoup.parse(epHtml)
                    episodeId = epDoc.select(".eps-item[data-id]")
                        .firstOrNull { el ->
                            el.select(".episode-number").text().trim()
                                .startsWith("Episode $episode")
                        }?.attr("data-id")
                        ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Episode $episode not found in season $season") }
                    Log.i(WTAG, "[FlixerTV] Episode $season×$episode id=$episodeId")
                } else {
                    episodeId = showId
                }

                // 3. Server list for this episode/movie
                val serverHtml = httpGetWithHeaders(
                    "https://theflixertv.to/ajax/episode/servers/$episodeId",
                    mapOf("Referer" to pageUrl, "X-Requested-With" to "XMLHttpRequest")
                ) ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Server list failed for id=$episodeId") }
                val serverDoc = Jsoup.parse(serverHtml)
                val servers = serverDoc.select("a[data-id]")
                    .map { it.attr("data-id") to it.select("span").text() }
                Log.i(WTAG, "[FlixerTV] Servers: $servers")

                val serverId = serverPreference.firstNotNullOfOrNull { pref ->
                    servers.firstOrNull { (_, name) -> name.contains(pref, ignoreCase = true) }?.first
                } ?: servers.firstOrNull()?.first
                    ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] No server found") }
                Log.i(WTAG, "[FlixerTV] Using: ${servers.firstOrNull { it.first == serverId }?.second} ($serverId)")

                // 4. Get embed URL
                val sourceJson = httpGetWithHeaders(
                    "https://theflixertv.to/ajax/episode/sources/$serverId",
                    mapOf("Referer" to pageUrl, "X-Requested-With" to "XMLHttpRequest")
                ) ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] Sources fetch failed") }
                val embedUrl = JSONObject(sourceJson).optString("link").takeIf { it.isNotBlank() }
                    ?: return@withContext null.also { Log.w(WTAG, "[FlixerTV] No embed link in: $sourceJson") }

                Log.i(WTAG, "[FlixerTV] ✅ Embed URL: $embedUrl")
                StreamResult(embedUrl, pageUrl)

            } catch (e: Exception) {
                Log.w(WTAG, "[FlixerTV] Error: ${e.message}")
                null
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // TMDB helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun getTmdbTitleAndYear(tmdbId: Int, isMovie: Boolean): Pair<String, Int>? {
        return try {
            if (isMovie) {
                val d = TmdbClient.api.getMovieDetails(tmdbId, TmdbClient.API_KEY)
                val year = d.releaseDate?.take(4)?.toIntOrNull() ?: return null
                (d.title ?: return null) to year
            } else {
                val d = TmdbClient.api.getTvDetails(tmdbId, TmdbClient.API_KEY)
                val year = d.firstAirDate?.take(4)?.toIntOrNull() ?: return null
                (d.name ?: return null) to year
            }
        } catch (e: Exception) {
            Log.w(WTAG, "TMDB title lookup failed: ${e.message}")
            null
        }
    }

    private suspend fun getImdbId(tmdbId: Int, isMovie: Boolean): String? {
        return try {
            val ids = if (isMovie) {
                TmdbClient.api.getMovieExternalIds(tmdbId, TmdbClient.API_KEY)
            } else {
                TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY)
            }
            ids.imdbId?.takeIf { it.startsWith("tt") }
        } catch (e: Exception) {
            Log.w(WTAG, "IMDB ID lookup failed: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4KHDHub scraper
    // ─────────────────────────────────────────────────────────────────────────

    private fun findFourKHDHubPageUrl(baseUrl: String, title: String, year: Int, isMovie: Boolean): String? {
        val searchHtml = httpGet("$baseUrl/?s=${title.replace(" ", "+")}") ?: return null
        val doc = Jsoup.parse(searchHtml)
        val typeLabel = if (isMovie) "Movies" else "Series"
        return doc.select(".movie-card")
            .filter { el -> el.select(".movie-card-format").any { it.text().contains(typeLabel) } }
            .filter { el ->
                // Skip year filter for series: S5 of a 2019 show is listed as 2025 on 4KHDHub
                if (!isMovie) return@filter true
                val cardYear = el.select(".movie-card-meta").text().trim().toIntOrNull()
                cardYear != null && kotlin.math.abs(cardYear - year) <= 1
            }
            .firstNotNullOfOrNull { el ->
                val cardTitle = el.select(".movie-card-title").text()
                    .replace(Regex("""\[.*?]"""), "").trim()
                val dist = levenshtein(cardTitle.lowercase(), title.lowercase())
                if (dist < 5 || (cardTitle.lowercase().contains(title.lowercase()) && dist < 16)) {
                    val href = el.attr("href")
                    if (href.startsWith("http")) href else "$baseUrl$href"
                } else null
            }
    }

    private fun extractHdHubRedirectLinks(html: String): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("a")
            .filter { it.text().contains("HubCloud") || it.text().contains("HubDrive") }
            .mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HDHub4u scraper
    // ─────────────────────────────────────────────────────────────────────────

    private fun findHDHub4uPageUrls(imdbId: String, season: Int?): List<String> {
        val searchUrl = "https://search.pingora.fyi/collections/post/documents/search?query_by=imdb_id&q=$imdbId"
        val json = httpGetWithHeaders(searchUrl, mapOf("Referer" to "https://new5.hdhub4u.fo"))
            ?: return emptyList()
        return try {
            val hits = JSONObject(json).getJSONArray("hits")
            (0 until hits.length())
                .map { hits.getJSONObject(it).getJSONObject("document") }
                .filter { doc ->
                    if (doc.optString("imdb_id") != imdbId) return@filter false
                    if (season != null) {
                        val t = doc.optString("post_title")
                        val s = season.toString()
                        t.contains("Season $s") || t.contains("S$s") ||
                                t.contains("S${s.padStart(2, '0')}")
                    } else true
                }
                .map { doc ->
                    val p = doc.optString("permalink")
                    if (p.startsWith("http")) p else "https://new5.hdhub4u.fo$p"
                }
        } catch (e: Exception) {
            Log.w(WTAG, "[HDHub4u] Search parse error: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hub extraction chain: HubDrive → HubCloud → final URL
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractFromHubUrl(hubUrl: String, referer: String): StreamResult? = when {
        hubUrl.contains("hubdrive") -> extractFromHubDrive(hubUrl, referer)
        hubUrl.contains("hubcloud") -> extractFromHubCloud(hubUrl, referer)
        else                        -> null
    }

    private fun extractFromHubDrive(hubDriveUrl: String, referer: String): StreamResult? {
        val html = httpGetWithHeaders(hubDriveUrl, mapOf("Referer" to referer)) ?: return null
        val doc  = Jsoup.parse(html)
        val hubCloudUrl = doc.select("a:contains(HubCloud)").firstOrNull()?.attr("href") ?: return null
        return extractFromHubCloud(hubCloudUrl, referer = hubDriveUrl)
    }

    private fun extractFromHubCloud(hubCloudUrl: String, referer: String): StreamResult? {
        val html = httpGetWithHeaders(hubCloudUrl, mapOf("Referer" to referer)) ?: return null
        // match: var url = '...'
        val linksPageUrl = Regex("""var url ?= ?'(.*?)'""").find(html)?.groupValues?.get(1)
            ?: return null
        val linksHtml = httpGetWithHeaders(linksPageUrl, mapOf("Referer" to hubCloudUrl)) ?: return null
        val doc = Jsoup.parse(linksHtml)

        // 1. PixelServer — direct download URL, most reliable
        doc.select("a").firstOrNull { it.text().contains("PixelServer") }?.let { el ->
            val href = el.attr("href")
            val userUrl  = href.replace("/api/file/", "/u/")
            val apiUrl   = userUrl.replace("/u/", "/api/file/") + "?download"
            Log.i(WTAG, "HubCloud PixelServer: $apiUrl")
            return StreamResult(apiUrl, userUrl)
        }

        // 2. FSLv2 — HLS stream from SaveFiles
        doc.select("a").firstOrNull { it.text().contains("FSLv2") }?.let { el ->
            val fslUrl  = el.attr("href")
            val fslHtml = httpGetWithHeaders(fslUrl, mapOf("Referer" to linksPageUrl)) ?: return@let
            val m3u8    = Regex("""file:"(.*?)"""").find(fslHtml)?.groupValues?.get(1) ?: return@let
            Log.i(WTAG, "HubCloud FSLv2: $m3u8")
            return StreamResult(m3u8, fslUrl)
        }

        // 3. FSL — MP4 from Fsst
        doc.select("a").firstOrNull { it.text().contains("FSL") && !it.text().contains("FSLv2") }
            ?.let { el ->
                val fslUrl  = el.attr("href")
                val fslHtml = httpGetWithHeaders(fslUrl, mapOf("Referer" to linksPageUrl)) ?: return@let
                val files   = Regex("""file:"(.*?)"""").find(fslHtml)?.groupValues?.get(1) ?: return@let
                val lastEntry = files.split(",").lastOrNull() ?: return@let
                val fileHref  = Regex("""\[?(\d*)p?]?(.*)""").find(lastEntry)?.groupValues?.get(2)
                    ?.trim() ?: return@let
                val finalUrl = getFinalUrl(fileHref) ?: fileHref
                Log.i(WTAG, "HubCloud FSL: $finalUrl")
                return StreamResult(finalUrl, fslUrl)
            }

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HD-Hub redirect resolver  (TS: hd-hub-helper.ts)
    // atob(JSON.parse(atob(rot13(atob(atob(encoded))))).o)
    // ─────────────────────────────────────────────────────────────────────────

    private fun resolveHdHubRedirectUrl(redirectUrl: String): String? = try {
        val html    = httpGet(redirectUrl) ?: return null
        val encoded = Regex("""'o','(.*?)'""").find(html)?.groupValues?.get(1) ?: return null
        val step1   = Base64.decode(encoded, Base64.DEFAULT)            // first atob
        val step2   = Base64.decode(step1, Base64.DEFAULT)              // second atob
        val step3   = rot13(String(step2, Charsets.ISO_8859_1))         // rot13
        val step4   = Base64.decode(step3, Base64.DEFAULT)              // third atob
        val json    = JSONObject(String(step4, Charsets.UTF_8))
        String(Base64.decode(json.getString("o"), Base64.DEFAULT))      // decode 'o'
    } catch (e: Exception) {
        Log.w(WTAG, "resolveHdHubRedirectUrl failed for $redirectUrl: ${e.message}")
        null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun httpGet(url: String): String? = httpGetWithHeaders(url, emptyMap())

    private fun httpGetWithHeaders(url: String, headers: Map<String, String>): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", WUA)
        headers.forEach { (k, v) -> req.header(k, v) }
        client.newCall(req.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(WTAG, "HTTP error [$url]: ${e.message}")
        null
    }

    /** Follow HTTP redirects and return the final URL string (does not read body). */
    private fun getFinalUrl(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", WUA).build()
        client.newCall(req).execute().use { resp -> resp.request.url.toString() }
    } catch (e: Exception) { null }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun rot13(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in 'A'..'M', in 'a'..'m' -> c + 13
                in 'N'..'Z', in 'n'..'z' -> c - 13
                else                     -> c
            }
        )
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
