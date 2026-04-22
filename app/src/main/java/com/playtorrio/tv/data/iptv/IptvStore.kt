package com.playtorrio.tv.data.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny SharedPreferences-backed store for verified IPTV portals so the user
 * doesn't have to re-scrape on every launch.
 */
object IptvStore {
    private const val PREFS = "iptv_store"
    private const val KEY_VERIFIED = "verified_portals"

    fun load(ctx: Context): List<VerifiedPortal> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERIFIED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toVerified() }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, list: List<VerifiedPortal>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VERIFIED, arr.toString())
            .apply()
    }

    private fun VerifiedPortal.toJson(): JSONObject = JSONObject().apply {
        put("url", portal.url)
        put("username", portal.username)
        put("password", portal.password)
        put("source", portal.source)
        put("name", name)
        put("expiry", expiry)
        put("max", maxConnections)
        put("active", activeConnections)
    }

    private fun JSONObject.toVerified(): VerifiedPortal = VerifiedPortal(
        portal = IptvPortal(
            url = optString("url"),
            username = optString("username"),
            password = optString("password"),
            source = optString("source"),
        ),
        name = optString("name"),
        expiry = optString("expiry"),
        maxConnections = optString("max", "1"),
        activeConnections = optString("active", "0"),
    )
}
