package com.playtorrio.tv.data.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-portal cache of "alive" live channel IDs + the user's "Live only"
 * preference for that portal. Keyed by `portalKey(portal)`.
 */
object IptvAliveStore {
    private const val PREFS = "iptv_alive_store"

    data class Snapshot(
        val checkedAt: Long,
        val aliveIds: Set<String>,
    )

    fun portalKey(p: IptvPortal): String =
        "${p.url}|${p.username}|${p.password}".lowercase()

    fun load(ctx: Context, key: String): Snapshot? {
        val raw = prefs(ctx).getString("alive_$key", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("ids") ?: JSONArray()
            val ids = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) ids += arr.getString(i)
            Snapshot(checkedAt = o.optLong("at", 0L), aliveIds = ids)
        }.getOrNull()
    }

    fun save(ctx: Context, key: String, snapshot: Snapshot) {
        val arr = JSONArray()
        snapshot.aliveIds.forEach { arr.put(it) }
        val o = JSONObject().apply {
            put("at", snapshot.checkedAt)
            put("ids", arr)
        }
        prefs(ctx).edit().putString("alive_$key", o.toString()).apply()
    }

    fun clear(ctx: Context, key: String) {
        prefs(ctx).edit().remove("alive_$key").remove("liveonly_$key").apply()
    }

    fun loadLiveOnly(ctx: Context, key: String): Boolean =
        prefs(ctx).getBoolean("liveonly_$key", false)

    fun saveLiveOnly(ctx: Context, key: String, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("liveonly_$key", enabled).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
