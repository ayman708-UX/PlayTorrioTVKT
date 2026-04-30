package com.playtorrio.tv.data.iptv

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lightweight #EXTM3U playlist fetcher + parser.
 *
 * Only LIVE streams are surfaced — VOD/series M3U conventions vary too much
 * across providers to be worth handling generically. Channels are grouped by
 * `group-title` if present, otherwise placed in a single "All Channels" group.
 */
object M3uParser {
    private const val TAG = "M3uParser"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    /** In-memory cache: playlist URL → parsed channels. */
    private val cache = mutableMapOf<String, List<IptvStream>>()

    data class Header(val playlistName: String?)

    /**
     * Fetch and parse the playlist. Returns null on network failure or if the
     * response doesn't look like a valid M3U.
     */
    fun fetchAndParse(url: String, timeoutMs: Long = 15_000): Pair<Header, List<IptvStream>>? {
        val text = httpGet(url, timeoutMs) ?: return null
        if (!text.contains("#EXTM3U", ignoreCase = true)) {
            Log.w(TAG, "Not an M3U playlist: $url")
            return null
        }
        val parsed = parse(text)
        cache[url] = parsed.second
        return parsed
    }

    /** Get cached channels (or fetch if not cached). */
    fun getChannels(url: String): List<IptvStream> {
        cache[url]?.let { return it }
        return fetchAndParse(url)?.second ?: emptyList()
    }

    fun parse(text: String): Pair<Header, List<IptvStream>> {
        var playlistName: String? = null
        val out = mutableListOf<IptvStream>()
        var pendingName: String? = null
        var pendingGroup = ""
        var pendingLogo = ""
        var idx = 0
        for (raw in text.lineSequence().map { it.trim() }) {
            if (raw.isEmpty()) continue
            if (raw.startsWith("#EXTM3U", true)) {
                playlistName = attr(raw, "x-tvg-name") ?: attr(raw, "url-tvg")
                continue
            }
            if (raw.startsWith("#EXTINF", true)) {
                pendingGroup = attr(raw, "group-title").orEmpty()
                pendingLogo = attr(raw, "tvg-logo").orEmpty()
                pendingName = raw.substringAfterLast(',', "").trim().ifEmpty {
                    attr(raw, "tvg-name") ?: "Channel"
                }
                continue
            }
            if (raw.startsWith("#")) continue
            // Stream URL line
            val name = pendingName ?: continue
            pendingName = null
            // Determine container extension from URL path.
            val cleaned = raw.substringBefore('?').substringBefore('#')
            val ext = cleaned.substringAfterLast('.', "ts").lowercase().take(4)
            val safeExt = if (ext.matches(Regex("[a-z0-9]{2,4}"))) ext else "ts"
            out += IptvStream(
                streamId = "m3u_${idx++}",
                name = name,
                icon = pendingLogo,
                categoryId = pendingGroup.ifEmpty { "" },
                containerExt = safeExt,
                kind = "live",
                directUrl = raw,
            )
            pendingGroup = ""
            pendingLogo = ""
        }
        return Header(playlistName) to out
    }

    /** Extract `key="value"` from an #EXTINF line. */
    private fun attr(line: String, key: String): String? {
        val pat = Regex("""${Regex.escape(key)}="([^"]*)"""", RegexOption.IGNORE_CASE)
        return pat.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    private fun httpGet(url: String, timeoutMs: Long): String? = try {
        val c = client.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        c.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetch failed [$url]: ${e.message}")
        null
    }
}
