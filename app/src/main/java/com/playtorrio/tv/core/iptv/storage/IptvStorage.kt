package com.playtorrio.tv.core.iptv.storage

import android.content.Context
import android.content.SharedPreferences
import com.playtorrio.tv.core.iptv.model.ChannelHit
import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.model.M3uPlaylist
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("playtorrio_iptv_store", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VERIFIED_PORTALS = "pt_iptv_verified_portals"
        private const val KEY_CANDIDATE_PORTALS = "pt_iptv_candidate_portals"
        private const val KEY_ATTEMPTED_KEYS = "pt_iptv_attempted_portal_keys"
        private const val KEY_FAVORITE_PORTALS = "pt_iptv_favorite_portal_keys"
        private const val KEY_M3U_PLAYLISTS = "pt_iptv_m3u_playlists"
    }

    // ── Candidate Portals Pool (Persisted so 10,000 portals from paste.sh aren't lost) ──

    suspend fun loadCandidatePortals(): List<IptvPortal> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_CANDIDATE_PORTALS, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<IptvPortal>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    IptvPortal(
                        url = o.optString("u"),
                        username = o.optString("un"),
                        password = o.optString("pw"),
                        source = o.optString("s")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCandidatePortals(portals: List<IptvPortal>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (p in portals) {
            val o = JSONObject().apply {
                put("u", p.url)
                put("un", p.username)
                put("pw", p.password)
                put("s", p.source)
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY_CANDIDATE_PORTALS, arr.toString()).apply()
    }

    suspend fun loadAttemptedKeys(): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_ATTEMPTED_KEYS, emptySet()) ?: emptySet()
    }

    suspend fun saveAttemptedKeys(keys: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY_ATTEMPTED_KEYS, keys).apply()
    }

    // ── Verified Portals ──

    suspend fun loadVerifiedPortals(): List<VerifiedPortal> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_VERIFIED_PORTALS, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VerifiedPortal>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    VerifiedPortal(
                        portal = IptvPortal(
                            url = o.optString("url"),
                            username = o.optString("username"),
                            password = o.optString("password"),
                            source = o.optString("source")
                        ),
                        name = o.optString("name"),
                        expiry = o.optString("expiry"),
                        maxConnections = o.optString("max", "1"),
                        activeConnections = o.optString("active", "0")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveVerifiedPortals(list: List<VerifiedPortal>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (v in list) {
            val o = JSONObject().apply {
                put("url", v.portal.url)
                put("username", v.portal.username)
                put("password", v.portal.password)
                put("source", v.portal.source)
                put("name", v.name)
                put("expiry", v.expiry)
                put("max", v.maxConnections)
                put("active", v.activeConnections)
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY_VERIFIED_PORTALS, arr.toString()).apply()
    }

    // ── Favorite Portals ──

    suspend fun loadFavoritePortalKeys(): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_FAVORITE_PORTALS, emptySet()) ?: emptySet()
    }

    suspend fun saveFavoritePortalKeys(keys: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY_FAVORITE_PORTALS, keys).apply()
    }

    // ── Alive Stream Cache per Portal ──

    suspend fun loadAliveStreamIds(portalKey: String): Set<String> = withContext(Dispatchers.IO) {
        val raw = prefs.getString("pt_iptv_alive_$portalKey", null) ?: return@withContext emptySet()
        try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("ids") ?: return@withContext emptySet()
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun saveAliveStreamIds(portalKey: String, ids: Set<String>) = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("ids", JSONArray(ids.toList()))
        }
        prefs.edit().putString("pt_iptv_alive_$portalKey", obj.toString()).apply()
    }

    // ── Channel Results Store ──

    suspend fun loadChannelHits(channelId: String): List<ChannelHit> = withContext(Dispatchers.IO) {
        val raw = prefs.getString("pt_iptv_ch_$channelId", null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChannelHit>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val portal = VerifiedPortal(
                    portal = IptvPortal(
                        url = o.optString("pu"),
                        username = o.optString("uu"),
                        password = o.optString("pp"),
                        source = "Cached"
                    ),
                    name = o.optString("pn"),
                    expiry = "Active"
                )
                val stream = IptvStream(
                    streamId = o.optString("sid"),
                    name = o.optString("sn"),
                    icon = o.optString("si"),
                    categoryId = o.optString("scid"),
                    containerExt = o.optString("sce"),
                    kind = o.optString("sk", "live"),
                    epgChannelId = o.optString("epg")
                )
                list.add(
                    ChannelHit(
                        portal = portal,
                        stream = stream,
                        streamUrl = o.optString("url")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveChannelHits(channelId: String, hits: List<ChannelHit>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (h in hits) {
            val o = JSONObject().apply {
                put("pu", h.portal.portal.url)
                put("uu", h.portal.portal.username)
                put("pp", h.portal.portal.password)
                put("pn", h.portal.name)
                put("sid", h.stream.streamId)
                put("sn", h.stream.name)
                put("si", h.stream.icon)
                put("scid", h.stream.categoryId)
                put("sce", h.stream.containerExt)
                put("sk", h.stream.kind)
                put("epg", h.stream.epgChannelId)
                put("url", h.streamUrl)
            }
            arr.put(o)
        }
        prefs.edit().putString("pt_iptv_ch_$channelId", arr.toString()).apply()
    }

    suspend fun clearChannelHits(channelId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove("pt_iptv_ch_$channelId").apply()
    }

    // ── Channel Favorited URLs ──

    suspend fun loadChannelFavorites(channelId: String): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet("pt_iptv_chfav_$channelId", emptySet()) ?: emptySet()
    }

    suspend fun saveChannelFavorites(channelId: String, urls: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet("pt_iptv_chfav_$channelId", urls).apply()
    }

    // ── M3U Playlists Store ──

    suspend fun loadM3uPlaylists(): List<M3uPlaylist> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_M3U_PLAYLISTS, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<M3uPlaylist>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    M3uPlaylist(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        url = o.optString("url"),
                        count = o.optInt("count", 0),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveM3uPlaylists(playlists: List<M3uPlaylist>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (p in playlists) {
            val o = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("url", p.url)
                put("count", p.count)
                put("addedAt", p.addedAt)
            }
            arr.put(o)
        }
        prefs.edit().putString(KEY_M3U_PLAYLISTS, arr.toString()).apply()
    }
}
