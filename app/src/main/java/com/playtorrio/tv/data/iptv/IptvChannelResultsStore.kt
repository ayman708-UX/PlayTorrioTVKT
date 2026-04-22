package com.playtorrio.tv.data.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-HardcodedChannel persisted list of alive stream "hits" found across
 * the user's portals. We persist enough to play the stream offline (full
 * playable URL) plus the source portal so the UI can display it.
 *
 * Keyed by [HardcodedChannel.id].
 */
object IptvChannelResultsStore {
    private const val PREFS = "iptv_channel_results"

    data class StoredHit(
        val portalUrl: String,
        val portalUser: String,
        val portalPass: String,
        val portalName: String,
        val streamId: String,
        val streamName: String,
        val streamIcon: String,
        val streamCategoryId: String,
        val streamContainerExt: String,
        val streamKind: String,
        val streamUrl: String,
    )

    fun load(ctx: Context, channelId: String): List<StoredHit> {
        val raw = prefs(ctx).getString("ch_$channelId", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<StoredHit>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += StoredHit(
                    portalUrl = o.optString("pu"),
                    portalUser = o.optString("uu"),
                    portalPass = o.optString("pp"),
                    portalName = o.optString("pn"),
                    streamId = o.optString("sid"),
                    streamName = o.optString("sn"),
                    streamIcon = o.optString("si"),
                    streamCategoryId = o.optString("scid"),
                    streamContainerExt = o.optString("sce"),
                    streamKind = o.optString("sk", "live"),
                    streamUrl = o.optString("url"),
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, channelId: String, hits: List<StoredHit>) {
        val arr = JSONArray()
        hits.forEach { h ->
            arr.put(
                JSONObject()
                    .put("pu", h.portalUrl)
                    .put("uu", h.portalUser)
                    .put("pp", h.portalPass)
                    .put("pn", h.portalName)
                    .put("sid", h.streamId)
                    .put("sn", h.streamName)
                    .put("si", h.streamIcon)
                    .put("scid", h.streamCategoryId)
                    .put("sce", h.streamContainerExt)
                    .put("sk", h.streamKind)
                    .put("url", h.streamUrl)
            )
        }
        prefs(ctx).edit().putString("ch_$channelId", arr.toString()).apply()
    }

    fun clear(ctx: Context, channelId: String) {
        prefs(ctx).edit().remove("ch_$channelId").apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
