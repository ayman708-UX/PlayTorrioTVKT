package com.playtorrio.tv.data.iptv

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Xtream-Codes player_api client. Login + categories + streams.
 * No connection pooling needed — Android TV usage is sporadic.
 */
object IptvClient {
    private const val TAG = "IptvClient"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val UA = "VLC/3.0.20 LibVLC/3.0.20"

    /** Login. Returns the parsed user_info JSON or null if auth failed. */
    fun login(p: IptvPortal, timeoutMs: Long = 6000): JSONObject? {
        val url = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}"
        val text = httpGet(url, timeoutMs) ?: return null
        return runCatching {
            val root = JSONObject(text)
            val info = root.optJSONObject("user_info") ?: root
            val auth = info.opt("auth")?.toString()
            val status = info.optString("status").lowercase()
            val ok = (auth == "1") || status == "active" || root.has("user_info")
            if (ok) info else null
        }.getOrNull()
    }

    fun verifyOrNull(p: IptvPortal, timeoutMs: Long = 6000): VerifiedPortal? {
        val info = login(p, timeoutMs) ?: return null
        return VerifiedPortal(
            portal = p,
            name = info.optString("username").ifEmpty { p.username },
            expiry = formatExpiry(info.optString("exp_date")),
            maxConnections = info.optString("max_connections", "1"),
            activeConnections = info.optString("active_cons", "0"),
        )
    }

    fun categories(p: IptvPortal, kind: IptvSection): List<IptvCategory> {
        val action = when (kind) {
            IptvSection.LIVE -> "get_live_categories"
            IptvSection.VOD -> "get_vod_categories"
            IptvSection.SERIES -> "get_series_categories"
        }
        val url = "${p.url}/player_api.php?username=${enc(p.username)}" +
            "&password=${enc(p.password)}&action=$action"
        val text = httpGet(url, 8000) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                IptvCategory(
                    id = o.optString("category_id"),
                    name = o.optString("category_name"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun streams(p: IptvPortal, kind: IptvSection, categoryId: String): List<IptvStream> {
        val action = when (kind) {
            IptvSection.LIVE -> "get_live_streams"
            IptvSection.VOD -> "get_vod_streams"
            IptvSection.SERIES -> "get_series"
        }
        val base = "${p.url}/player_api.php?username=${enc(p.username)}" +
            "&password=${enc(p.password)}&action=$action"
        val url = if (categoryId.isEmpty()) base else "$base&category_id=${enc(categoryId)}"
        val text = httpGet(url, 15000) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ext = when (kind) {
                    IptvSection.LIVE -> "ts"
                    IptvSection.VOD -> o.optString("container_extension", "mp4").ifEmpty { "mp4" }
                    IptvSection.SERIES -> "" // resolved per-episode
                }
                val id = when (kind) {
                    IptvSection.SERIES -> o.optString("series_id").ifEmpty { o.optString("id") }
                    else -> o.optString("stream_id").ifEmpty { o.optString("id") }
                }
                IptvStream(
                    streamId = id,
                    name = o.optString("name").ifEmpty { o.optString("title") },
                    icon = o.optString("stream_icon").ifEmpty { o.optString("cover") },
                    categoryId = o.optString("category_id"),
                    containerExt = ext,
                    kind = when (kind) {
                        IptvSection.LIVE -> "live"
                        IptvSection.VOD -> "vod"
                        IptvSection.SERIES -> "series"
                    },
                )
            }
        }.getOrElse { emptyList() }
    }

    /** Episodes for a series id, flattened across seasons. */
    fun seriesEpisodes(p: IptvPortal, seriesId: String): List<IptvEpisode> {
        val url = "${p.url}/player_api.php?username=${enc(p.username)}" +
            "&password=${enc(p.password)}&action=get_series_info&series_id=${enc(seriesId)}"
        val text = httpGet(url, 15000) ?: return emptyList()
        return runCatching {
            val root = JSONObject(text)
            val episodesObj = root.optJSONObject("episodes") ?: return@runCatching emptyList()
            val out = mutableListOf<IptvEpisode>()
            episodesObj.keys().forEach { seasonKey ->
                val arr = episodesObj.optJSONArray(seasonKey) ?: return@forEach
                val seasonNum = seasonKey.toIntOrNull() ?: 0
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val info = o.optJSONObject("info")
                    out += IptvEpisode(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        containerExt = o.optString("container_extension", "mp4")
                            .ifEmpty { "mp4" },
                        season = seasonNum,
                        episode = o.optInt("episode_num"),
                        plot = info?.optString("plot").orEmpty(),
                        image = info?.optString("movie_image").orEmpty(),
                    )
                }
            }
            out.sortedWith(compareBy({ it.season }, { it.episode }))
        }.getOrElse { emptyList() }
    }

    fun streamUrl(p: IptvPortal, s: IptvStream): String =
        when (s.kind) {
            "live" -> "${p.url}/live/${enc(p.username)}/${enc(p.password)}/${s.streamId}.${s.containerExt}"
            "vod" -> "${p.url}/movie/${enc(p.username)}/${enc(p.password)}/${s.streamId}.${s.containerExt}"
            else -> ""
        }

    fun episodeUrl(p: IptvPortal, e: IptvEpisode): String =
        "${p.url}/series/${enc(p.username)}/${enc(p.password)}/${e.id}.${e.containerExt}"

    // ── helpers ─────────────────────────────────────────────────────────

    private fun httpGet(url: String, timeoutMs: Long): String? {
        return try {
            val c = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val req = Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json,*/*")
                .build()
            c.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: Exception) {
            Log.v(TAG, "GET fail $url: ${e.message}")
            null
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    private fun formatExpiry(raw: String?): String {
        val ts = raw?.toLongOrNull() ?: return "Unknown"
        return runCatching {
            SimpleDateFormat("dd MMM yyyy", Locale.UK).format(Date(ts * 1000L))
        }.getOrDefault(raw)
    }
}
