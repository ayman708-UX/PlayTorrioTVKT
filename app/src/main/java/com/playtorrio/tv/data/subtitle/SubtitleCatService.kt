package com.playtorrio.tv.data.subtitle

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * SubtitleCat scraper — Kotlin port of subtitlecat_service.dart.
 *
 * Mirrors subtitlecat.com:
 *   • Search:   https://www.subtitlecat.com/index.php?search=<query>
 *   • Detail:   https://www.subtitlecat.com/subs/<id>/<name>.html
 *   • Direct:   <a id="download_<lang>" href="/subs/<id>/<name>-<lang>.srt">
 *   • Missing language → translate orig SRT via Google Translate
 *     (translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=<lang>&dt=t&q=<text>)
 *     replicating subtitlecat's /js/translate.js.
 *
 * Translation is exposed lazily via an embedded NanoHTTPD on 127.0.0.1 so the
 * player can side-load the translated SRT through a normal URL.
 */
object SubtitleCatService {

    private const val TAG = "SubtitleCat"
    private const val ORIGIN = "https://www.subtitlecat.com"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    // ── Caches ────────────────────────────────────────────────────────────────
    private val searchMutex = Mutex()
    private val detailMutex = Mutex()
    private val translationMutex = Mutex()
    private val searchCache = HashMap<String, List<SearchHit>>()
    private val detailCache = HashMap<String, DetailPage>()
    private val translationCache = HashMap<String, String>()
    private val translationInflight = HashMap<String, Deferred<String>>()

    // ── Public API ────────────────────────────────────────────────────────────

    fun buildQuery(title: String, year: Int?, season: Int?, episode: Int?): String {
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
        if (season != null && episode != null) {
            val s = season.toString().padStart(2, '0')
            val e = episode.toString().padStart(2, '0')
            return "$cleanTitle S${s}E$e"
        }
        if (year != null && year > 0) return "$cleanTitle $year"
        return cleanTitle
    }

    /**
     * Search SubtitleCat and return entries shaped for [SubtitleService].
     * Translatable languages are exposed via the embedded translate server.
     */
    suspend fun fetchAll(
        title: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        maxResults: Int = 10
    ): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
        val query = buildQuery(title, year, season, episode)
        try {
            val hits = search(query)
            if (hits.isEmpty()) return@withContext emptyList()

            val picks = hits.take(maxResults)
            val translateBaseUrl = SubtitleTranslateServer.baseUrl()

            // Fetch every detail page in parallel — each variant on subtitlecat
            // is its own page and the user wants subtitles from ALL of them.
            val details: List<DetailPage?> = coroutineScope {
                picks.map { hit ->
                    async {
                        try {
                            fetchDetail(hit.detailUrl)
                        } catch (e: Exception) {
                            Log.w(TAG, "detail page failed (${hit.detailUrl}): ${e.message}")
                            null
                        }
                    }
                }.awaitAll()
            }

            val out = mutableListOf<ExternalSubtitle>()
            val seenDirect = HashSet<String>()
            val translatedLangs = HashSet<String>()
            var idx = 0

            for ((i, detail) in details.withIndex()) {
                if (detail == null) continue

                // Direct downloads — one entry per (variant, language).
                for (ln in detail.directLanguages) {
                    val key = "${ln.code}|${ln.url}"
                    if (!seenDirect.add(key)) continue
                    out.add(
                        ExternalSubtitle(
                            id = "subtitlecat_${idx++}",
                            url = ln.url,
                            language = ln.code,
                            displayName = "${ln.label} ${i + 1} - subtitlecat",
                            format = "srt",
                            source = "subtitlecat",
                            isHearingImpaired = false,
                            fileName = null,
                            downloadCount = 0,
                        )
                    )
                }

                // Translation entries — only from the first (best) match, and
                // only when the embedded translate server is available.
                if (i == 0 && translateBaseUrl != null && detail.folder.isNotEmpty()) {
                    for (ln in detail.translatableLanguages) {
                        if (translatedLangs.contains(ln.code)) continue
                        if (detail.directLanguages.any { it.code == ln.code }) continue
                        translatedLangs.add(ln.code)

                        val origUrl = "$ORIGIN${detail.folder}${detail.origFilename}"
                        val tUrl = "$translateBaseUrl/subtitlecat-translate" +
                            "?orig=${urlEncode(origUrl)}" +
                            "&tl=${urlEncode(ln.code)}" +
                            "&name=${urlEncode(detail.baseName)}"

                        out.add(
                            ExternalSubtitle(
                                id = "subtitlecat_${idx++}",
                                url = tUrl,
                                language = ln.code,
                                displayName = "${ln.label} (translated) - subtitlecat",
                                format = "srt",
                                source = "subtitlecat",
                                isHearingImpaired = false,
                                fileName = null,
                                downloadCount = 0,
                            )
                        )
                    }
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "fetchAll error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Translate the orig SRT at [origUrl] into [targetLang] and return the
     * assembled SRT text. Mirrors translate_file() in /js/translate.js.
     */
    suspend fun translateSrt(origUrl: String, targetLang: String): String {
        val key = "$origUrl|$targetLang"
        translationMutex.withLock {
            translationCache[key]?.let { return it }
        }
        val existing = translationMutex.withLock { translationInflight[key] }
        if (existing != null) return existing.await()

        return coroutineScope {
            val deferred = async(Dispatchers.IO) {
                translateSrtInternal(origUrl, targetLang)
            }
            translationMutex.withLock { translationInflight[key] = deferred }
            try {
                val res = deferred.await()
                translationMutex.withLock {
                    translationCache[key] = res
                }
                res
            } finally {
                translationMutex.withLock { translationInflight.remove(key) }
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private suspend fun search(query: String): List<SearchHit> {
        searchMutex.withLock { searchCache[query]?.let { return it } }
        val url = "$ORIGIN/index.php?search=${urlEncode(query)}"
        val body = httpGetText(url) ?: throw RuntimeException("search empty")
        val hits = parseSearchResults(body)
        searchMutex.withLock { searchCache[query] = hits }
        return hits
    }

    private val searchAnchorRe = Regex(
        "<a\\s+href=\"(subs/(\\d+)/([^\"]+\\.html))\"[^>]*>([^<]*)</a>",
        RegexOption.IGNORE_CASE
    )

    private fun parseSearchResults(html: String): List<SearchHit> {
        val out = mutableListOf<SearchHit>()
        val seen = HashSet<String>()
        for (m in searchAnchorRe.findAll(html)) {
            val relPath = m.groupValues[1]
            if (!seen.add(relPath)) continue
            out.add(
                SearchHit(
                    detailUrl = "$ORIGIN/$relPath",
                    title = stripHtml(m.groupValues[4]),
                )
            )
        }
        return out
    }

    // ── Detail page ───────────────────────────────────────────────────────────

    private suspend fun fetchDetail(detailUrl: String): DetailPage {
        detailMutex.withLock { detailCache[detailUrl]?.let { return it } }
        val body = httpGetText(detailUrl) ?: throw RuntimeException("detail empty")
        val parsed = parseDetailPage(body)
        detailMutex.withLock { detailCache[detailUrl] = parsed }
        return parsed
    }

    private val downloadRe = Regex(
        "<a\\s+id=\"download_([A-Za-z0-9-]+)\"[^>]*href=\"(/subs/\\d+/[^\"]+\\.srt)\"",
        RegexOption.IGNORE_CASE
    )
    private val translateRe = Regex(
        "translate_from_server_folder\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)",
        RegexOption.IGNORE_CASE
    )
    private val dashLangRe = Regex("-([A-Za-z0-9-]+)\\.srt$")
    private val origSuffixRe = Regex("-orig\\.srt$")

    private fun parseDetailPage(html: String): DetailPage {
        val directs = mutableListOf<LangEntry>()
        val directCodes = HashSet<String>()
        for (m in downloadRe.findAll(html)) {
            val code = m.groupValues[1]
            val href = m.groupValues[2]
            val norm = normalizeLang(code)
            directs.add(
                LangEntry(
                    code = norm,
                    label = languageLabel(code),
                    url = "$ORIGIN$href",
                )
            )
            directCodes.add(norm)
        }

        val translatables = mutableListOf<LangEntry>()
        var folder = ""
        var origFilename = ""
        for (m in translateRe.findAll(html)) {
            val code = m.groupValues[1]
            origFilename = m.groupValues[2]
            folder = m.groupValues[3]
            val norm = normalizeLang(code)
            if (directCodes.contains(norm)) continue
            translatables.add(LangEntry(code = norm, label = languageLabel(code)))
        }

        if (folder.isEmpty() && directs.isNotEmpty()) {
            val firstUrl = directs[0].url
            val path = URL(firstUrl).path
            val lastSlash = path.lastIndexOf('/')
            folder = path.substring(0, lastSlash) + "/"
            val fname = path.substring(lastSlash + 1)
            val base = fname.replace(dashLangRe, "")
            origFilename = "$base-orig.srt"
        }

        val baseName = origFilename.replace(origSuffixRe, "")
        return DetailPage(
            directLanguages = directs,
            translatableLanguages = translatables,
            folder = folder,
            origFilename = origFilename,
            baseName = baseName,
        )
    }

    // ── Translation pipeline ──────────────────────────────────────────────────

    private val srtNumRe = Regex("^[0-9 \\r]*$")
    private val srtTsRe = Regex("^[0-9,: ]*-->[0-9,: \\r]*$")
    private val fontOpenRe = Regex("<font[^>]*>", RegexOption.IGNORE_CASE)
    private val fontCloseRe = Regex("</font>", RegexOption.IGNORE_CASE)

    private suspend fun translateSrtInternal(origUrl: String, targetLang: String): String {
        val body = httpGetText(origUrl) ?: throw RuntimeException("orig empty")
        val srcLines = body.split('\n')
        val translated = arrayOfNulls<String>(srcLines.size)

        val charsPerBatch = 500
        val batches = mutableListOf<String>()
        val linesInBatch = mutableListOf<List<Int>>()

        var curBatch = StringBuilder()
        var curChars = 0
        var curIndices = mutableListOf<Int>()

        fun flush() {
            if (curIndices.isEmpty() && curBatch.isEmpty()) return
            batches.add(curBatch.toString())
            linesInBatch.add(curIndices)
            curBatch = StringBuilder()
            curChars = 0
            curIndices = mutableListOf()
        }

        for (i in srcLines.indices) {
            val line = srcLines[i]
            if (srtNumRe.matches(line) || srtTsRe.matches(line)) {
                translated[i] = line
                continue
            }
            val cleaned = line
                .replace(fontOpenRe, "")
                .replace(fontCloseRe, "")
                .replace("&", "and")
            if (curChars + cleaned.length + 1 < charsPerBatch) {
                if (curBatch.isEmpty()) {
                    curBatch.append(cleaned)
                } else {
                    curBatch.append('\n').append(cleaned)
                }
                curChars += cleaned.length + 1
                curIndices.add(i)
            } else {
                flush()
                curBatch.append(cleaned)
                curChars = cleaned.length + 1
                curIndices.add(i)
            }
        }
        flush()

        Log.i(
            TAG,
            "translating 1 SRT (${srcLines.size} lines, ${batches.size} chunks) → $targetLang"
        )

        val parallel = 8
        val nextIndex = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            val workers = List(parallel) {
                async(Dispatchers.IO) {
                    while (true) {
                        val b = nextIndex.getAndIncrement()
                        if (b >= batches.size) return@async
                        val batch = batches[b]
                        val indices = linesInBatch[b]
                        if (indices.isEmpty()) continue
                        try {
                            val translatedLines = translateBatch(batch, targetLang)
                            if (translatedLines.size == indices.size) {
                                for (k in indices.indices) {
                                    translated[indices[k]] = translatedLines[k]
                                }
                            } else {
                                val origPieces = batch.split('\n')
                                for (k in indices.indices) {
                                    val src = if (k < origPieces.size) origPieces[k] else ""
                                    if (src.trim().isEmpty()) {
                                        translated[indices[k]] = src
                                        continue
                                    }
                                    try {
                                        val one = translateBatch(src, targetLang)
                                        translated[indices[k]] =
                                            if (one.isNotEmpty()) one.joinToString("\n") else src
                                    } catch (_: Exception) {
                                        translated[indices[k]] = src
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "batch $b failed: ${e.message}")
                            val origPieces = batch.split('\n')
                            for (k in indices.indices) {
                                translated[indices[k]] =
                                    if (k < origPieces.size) origPieces[k] else ""
                            }
                        }
                    }
                }
            }
            workers.awaitAll()
        }

        val sb = StringBuilder()
        for (i in translated.indices) {
            if (i > 0) sb.append('\n')
            sb.append(translated[i] ?: "")
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun translateBatch(text: String, tl: String): List<String> {
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=auto&tl=${urlEncode(tl)}&dt=t&q=${urlEncode(text)}"
        val body = httpGetText(url, accept = "*/*")
            ?: throw RuntimeException("gtx empty")

        val root = JSONTokener(body).nextValue() as? JSONArray ?: return emptyList()
        if (root.length() == 0) return emptyList()
        val segments = root.opt(0) as? JSONArray ?: return emptyList()
        val sb = StringBuilder()
        for (k in 0 until segments.length()) {
            val seg = segments.opt(k) as? JSONArray ?: continue
            if (seg.length() == 0) continue
            val piece = seg.opt(0) as? String ?: continue
            sb.append(piece)
        }
        return sb.toString().split('\n')
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    private fun httpGetText(url: String, accept: String? = null): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty(
                "Accept",
                accept ?: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code for $url")
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            // SubtitleCat serves SRT mixed latin-1 / utf-8; UTF-8 with malformed
            // tolerance is the best practical default.
            String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private val htmlTagRe = Regex("<[^>]+>")
    private fun stripHtml(s: String): String = s.replace(htmlTagRe, "").trim()

    private fun normalizeLang(code: String): String {
        val c = code.lowercase()
        return when (c) {
            "iw" -> "he"
            "jw" -> "jv"
            "in" -> "id"
            else -> c
        }
    }

    private fun languageLabel(code: String): String {
        val c = code.lowercase()
        return LANG_LABELS[c] ?: code
    }

    private val LANG_LABELS = mapOf(
        "af" to "Afrikaans", "ak" to "Akan", "sq" to "Albanian", "am" to "Amharic",
        "ar" to "Arabic", "hy" to "Armenian", "az" to "Azerbaijani", "eu" to "Basque",
        "be" to "Belarusian", "bem" to "Bemba", "bn" to "Bengali", "bh" to "Bihari",
        "bs" to "Bosnian", "br" to "Breton", "bg" to "Bulgarian", "km" to "Cambodian",
        "ca" to "Catalan", "ceb" to "Cebuano", "chr" to "Cherokee", "ny" to "Chichewa",
        "zh-cn" to "Chinese (S)", "zh-tw" to "Chinese (T)", "co" to "Corsican",
        "hr" to "Croatian", "cs" to "Czech", "da" to "Danish", "nl" to "Dutch",
        "en" to "English", "eo" to "Esperanto", "et" to "Estonian", "ee" to "Ewe",
        "fo" to "Faroese", "tl" to "Filipino", "fi" to "Finnish", "fr" to "French",
        "fy" to "Frisian", "gaa" to "Ga", "gl" to "Galician", "ka" to "Georgian",
        "de" to "German", "el" to "Greek", "gn" to "Guarani", "gu" to "Gujarati",
        "ht" to "Haitian", "ha" to "Hausa", "haw" to "Hawaiian", "iw" to "Hebrew",
        "he" to "Hebrew", "hi" to "Hindi", "hmn" to "Hmong", "hu" to "Hungarian",
        "is" to "Icelandic", "ig" to "Igbo", "id" to "Indonesian", "in" to "Indonesian",
        "ia" to "Interlingua", "ga" to "Irish", "it" to "Italian", "ja" to "Japanese",
        "jw" to "Javanese", "jv" to "Javanese", "kn" to "Kannada", "kk" to "Kazakh",
        "rw" to "Kinyarwanda", "rn" to "Kirundi", "kg" to "Kongo", "ko" to "Korean",
        "kri" to "Krio", "ku" to "Kurdish", "ckb" to "Kurdish (Sorani)", "ky" to "Kyrgyz",
        "lo" to "Laothian", "la" to "Latin", "lv" to "Latvian", "ln" to "Lingala",
        "lt" to "Lithuanian", "loz" to "Lozi", "lg" to "Luganda", "ach" to "Luo",
        "lb" to "Luxembourgish", "mk" to "Macedonian", "mg" to "Malagasy",
        "ms" to "Malay", "ml" to "Malayalam", "mt" to "Maltese", "mi" to "Maori",
        "mr" to "Marathi", "mfe" to "Mauritian Creole", "mo" to "Moldavian",
        "mn" to "Mongolian", "sr-me" to "Montenegrin", "my" to "Burmese",
        "ne" to "Nepali", "pcm" to "Nigerian Pidgin", "nso" to "Northern Sotho",
        "no" to "Norwegian", "nn" to "Norwegian Nynorsk", "oc" to "Occitan",
        "or" to "Oriya", "om" to "Oromo", "ps" to "Pashto", "fa" to "Persian",
        "pl" to "Polish", "pt" to "Portuguese", "pt-br" to "Portuguese (BR)",
        "pt-pt" to "Portuguese (PT)", "pa" to "Punjabi", "qu" to "Quechua",
        "ro" to "Romanian", "rm" to "Romansh", "nyn" to "Runyakitara", "ru" to "Russian",
        "gd" to "Scots Gaelic", "sr" to "Serbian", "sh" to "Serbo-Croatian",
        "st" to "Sesotho", "tn" to "Setswana", "crs" to "Seychellois Creole",
        "sn" to "Shona", "sd" to "Sindhi", "si" to "Sinhalese", "sk" to "Slovak",
        "sl" to "Slovenian", "so" to "Somali", "es" to "Spanish",
        "es-419" to "Spanish (LatAm)", "su" to "Sundanese", "sw" to "Swahili",
        "sv" to "Swedish", "tg" to "Tajik", "ta" to "Tamil", "tt" to "Tatar",
        "te" to "Telugu", "th" to "Thai", "ti" to "Tigrinya", "to" to "Tonga",
        "lua" to "Tshiluba", "tum" to "Tumbuka", "tr" to "Turkish", "tk" to "Turkmen",
        "tw" to "Twi", "ug" to "Uighur", "uk" to "Ukrainian", "ur" to "Urdu",
        "uz" to "Uzbek", "vi" to "Vietnamese", "cy" to "Welsh", "wo" to "Wolof",
        "xh" to "Xhosa", "yi" to "Yiddish", "yo" to "Yoruba", "zu" to "Zulu",
    )

    // ── Internal data ─────────────────────────────────────────────────────────

    private data class SearchHit(val detailUrl: String, val title: String)

    private data class LangEntry(val code: String, val label: String, val url: String = "")

    private data class DetailPage(
        val directLanguages: List<LangEntry>,
        val translatableLanguages: List<LangEntry>,
        val folder: String,
        val origFilename: String,
        val baseName: String,
    )
}

/**
 * Embedded NanoHTTPD server bound to 127.0.0.1 that serves on-demand
 * SubtitleCat translations to the player.
 *
 * GET /subtitlecat-translate?orig=<srt-url>&tl=<lang>&name=<basename>
 *   → translated SRT (text/plain; charset=utf-8)
 */
internal object SubtitleTranslateServer {
    private const val TAG = "SubtitleCatSrv"
    private var server: Server? = null
    private var url: String? = null

    @Synchronized
    fun baseUrl(): String? {
        if (url != null) return url
        return try {
            val s = Server()
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            url = "http://127.0.0.1:${s.listeningPort}"
            Log.i(TAG, "started at $url")
            url
        } catch (e: Exception) {
            Log.w(TAG, "failed to start: ${e.message}")
            null
        }
    }

    private class Server : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(session: IHTTPSession): Response {
            if (session.uri != "/subtitlecat-translate") {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "not found"
                )
            }
            val params = session.parameters
            val orig = params["orig"]?.firstOrNull().orEmpty()
            val tl = params["tl"]?.firstOrNull().orEmpty()
            if (orig.isEmpty() || tl.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "text/plain", "missing params"
                )
            }
            return try {
                val srt = runBlocking { SubtitleCatService.translateSrt(orig, tl) }
                val bytes = srt.toByteArray(Charsets.UTF_8)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/plain; charset=utf-8",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                ).apply {
                    addHeader("Cache-Control", "public, max-age=86400")
                    addHeader("Access-Control-Allow-Origin", "*")
                }
            } catch (e: Exception) {
                Log.w(TAG, "translate failed: ${e.message}")
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", "translate failed"
                )
            }
        }
    }
}
