package com.playtorrio.tv.core.iptv.network

import android.util.Base64
import android.util.Log
import com.playtorrio.tv.core.iptv.model.EpgEntry
import com.playtorrio.tv.core.iptv.model.IptvCategory
import com.playtorrio.tv.core.iptv.model.IptvEpisode
import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.IptvSection
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object IptvClient {
    private const val TAG = "IptvClient"
    private const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun enc(s: String): String = try {
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    } catch (_: Exception) {
        s
    }

    private suspend fun httpGet(url: String, timeoutSec: Long = 10): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("Accept", "application/json, */*")
                .build()
            val customClient = if (timeoutSec == 10L) httpClient else {
                httpClient.newBuilder()
                    .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                    .readTimeout(timeoutSec, TimeUnit.SECONDS)
                    .build()
            }
            customClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun login(p: IptvPortal, timeoutSec: Long = 6): JSONObject? {
        val url = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}"
        val text = httpGet(url, timeoutSec) ?: return null
        return try {
            val root = JSONObject(text)
            val info = root.optJSONObject("user_info") ?: root
            val auth = info.optString("auth")
            val status = info.optString("status").lowercase()
            val ok = auth == "1" || status == "active" || (root.has("user_info") && auth != "0" && status != "disabled" && status != "banned" && status != "expired")
            if (!ok) null else info
        } catch (_: Exception) {
            null
        }
    }

    suspend fun verifyOrNull(p: IptvPortal, timeoutSec: Long = 6): VerifiedPortal? {
        val info = login(p, timeoutSec) ?: return null
        val username = info.optString("username").ifEmpty { p.username }
        val expDate = info.optString("exp_date")
        val maxConnections = info.optString("max_connections").ifEmpty { "1" }
        val activeConnections = info.optString("active_cons").ifEmpty { "0" }

        return VerifiedPortal(
            portal = p,
            name = username,
            expiry = formatExpiry(expDate),
            maxConnections = maxConnections,
            activeConnections = activeConnections
        )
    }

    private fun formatExpiry(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unlimited"
        val ts = raw.toLongOrNull() ?: return "Unlimited"
        return try {
            val d = Date(ts * 1000)
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            sdf.format(d)
        } catch (_: Exception) {
            raw
        }
    }

    suspend fun categories(p: IptvPortal, kind: IptvSection): List<IptvCategory> {
        val action = when (kind) {
            IptvSection.LIVE -> "get_live_categories"
            IptvSection.VOD -> "get_vod_categories"
            IptvSection.SERIES -> "get_series_categories"
        }
        val url = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}&action=$action"
        val text = httpGet(url, 8) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            val list = mutableListOf<IptvCategory>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    IptvCategory(
                        id = o.optString("category_id"),
                        name = o.optString("category_name")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun streams(p: IptvPortal, kind: IptvSection, categoryId: String): List<IptvStream> {
        val action = when (kind) {
            IptvSection.LIVE -> "get_live_streams"
            IptvSection.VOD -> "get_vod_streams"
            IptvSection.SERIES -> "get_series"
        }
        val base = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}&action=$action"
        val url = if (categoryId.isEmpty()) base else "$base&category_id=${enc(categoryId)}"
        val text = httpGet(url, 15) ?: return emptyList()

        return try {
            val arr = JSONArray(text)
            val list = mutableListOf<IptvStream>()
            val kindStr = when (kind) {
                IptvSection.LIVE -> "live"
                IptvSection.VOD -> "vod"
                IptvSection.SERIES -> "series"
            }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val streamId = o.optString("stream_id").ifEmpty { o.optString("series_id") }
                if (streamId.isEmpty()) continue

                val ext = o.optString("container_extension").ifEmpty {
                    if (kind == IptvSection.LIVE) "m3u8" else "mp4"
                }

                list.add(
                    IptvStream(
                        streamId = streamId,
                        name = o.optString("name").ifEmpty { "Stream $streamId" },
                        icon = o.optString("stream_icon").ifEmpty { o.optString("cover") },
                        categoryId = o.optString("category_id"),
                        containerExt = ext,
                        kind = kindStr,
                        epgChannelId = o.optString("epg_channel_id")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun episodes(p: IptvPortal, seriesId: String): List<IptvEpisode> {
        val url = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}&action=get_series_info&series_id=${enc(seriesId)}"
        val text = httpGet(url, 12) ?: return emptyList()

        return try {
            val root = JSONObject(text)
            val epsObj = root.optJSONObject("episodes") ?: return emptyList()
            val list = mutableListOf<IptvEpisode>()

            val keys = epsObj.keys()
            while (keys.hasNext()) {
                val sKey = keys.next()
                val seasonNum = sKey.toIntOrNull() ?: 1
                val arr = epsObj.optJSONArray(sKey) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    if (id.isEmpty()) continue
                    val info = o.optJSONObject("info")
                    list.add(
                        IptvEpisode(
                            id = id,
                            title = o.optString("title").ifEmpty { "Episode ${o.optInt("episode_num", i + 1)}" },
                            containerExt = o.optString("container_extension").ifEmpty { "mp4" },
                            season = seasonNum,
                            episode = o.optInt("episode_num", i + 1),
                            plot = info?.optString("plot").orEmpty(),
                            image = info?.optString("movie_image").orEmpty()
                        )
                    )
                }
            }
            list.sortWith(compareBy({ it.season }, { it.episode }))
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun shortEpg(p: IptvPortal, streamId: String, limit: Int = 2): List<EpgEntry> {
        if (streamId.isEmpty()) return emptyList()
        val url = "${p.url}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}&action=get_short_epg&stream_id=${enc(streamId)}&limit=$limit"
        val text = httpGet(url, 6) ?: return emptyList()

        return try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("epg_listings") ?: JSONArray()
            val list = mutableListOf<EpgEntry>()

            fun decode64(s: String?): String {
                if (s.isNullOrBlank()) return ""
                return try {
                    String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
                } catch (_: Exception) {
                    s
                }
            }

            fun parseTs(v: Any?): Long? {
                if (v == null) return null
                val str = v.toString()
                val secs = str.toLongOrNull()
                if (secs != null && secs > 1000000000L) {
                    return secs * 1000L
                }
                return try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(str)?.time
                } catch (_: Exception) {
                    null
                }
            }

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val start = parseTs(o.opt("start_timestamp")) ?: parseTs(o.opt("start"))
                val stop = parseTs(o.opt("stop_timestamp")) ?: parseTs(o.opt("end"))
                if (start != null && stop != null) {
                    list.add(
                        EpgEntry(
                            title = decode64(o.optString("title")),
                            description = decode64(o.optString("description")),
                            start = start,
                            stop = stop
                        )
                    )
                }
            }
            list.sortBy { it.start }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun streamUrl(p: IptvPortal, s: IptvStream): String {
        val user = enc(p.username)
        val pass = enc(p.password)
        return when (s.kind) {
            "live" -> "${p.url}/live/$user/$pass/${s.streamId}.${s.containerExt}"
            "vod" -> "${p.url}/movie/$user/$pass/${s.streamId}.${s.containerExt}"
            else -> ""
        }
    }

    fun episodeUrl(p: IptvPortal, e: IptvEpisode): String {
        val user = enc(p.username)
        val pass = enc(p.password)
        return "${p.url}/series/$user/$pass/${e.id}.${e.containerExt}"
    }
}
