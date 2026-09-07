package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class LunaExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "LunaExtractor"
        private const val ACTION_FETCH_SOURCES = "afb0491c5516f9fff5fcb464d627638df76062f8"
        private const val WATCH_URL = "https://luna-stream.me/anime/watch/21/gogoanime/1"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val TEXT_MEDIA_TYPE = "text/plain;charset=UTF-8".toMediaType()

        private val PROVIDERS = listOf(
            "megaplay" to "Helios",
            "gogoanime" to "Quasar",
            "zoro" to "Zenith",
            "anibd" to "Nova",
            "pahe" to "Polaris",
            "animepahe" to "Vega"
        )
    }

    private fun parseRscResponse(text: String): JSONObject? {
        for (line in text.lines()) {
            if (line.startsWith("1:")) {
                try {
                    val jsonStr = line.substring(2)
                    return JSONObject(jsonStr)
                } catch (_: Exception) {}
            }
        }
        return null
    }

    suspend fun extract(
        anilistId: Int,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): List<AnimeStreamResult> = coroutineScope {
        val subtype = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"

        val deferreds = PROVIDERS.map { (providerId, providerName) ->
            async(Dispatchers.IO) {
                val providerResults = mutableListOf<AnimeStreamResult>()
                try {
                    val bodyArray = JSONArray().apply {
                        put(anilistId)
                        put(providerId)
                        put("$episodeNumber")
                        put(episodeNumber)
                        put(subtype)
                        put(JSONObject.NULL)
                    }

                    val req = Request.Builder()
                        .url(WATCH_URL)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Next-Action", ACTION_FETCH_SOURCES)
                        .addHeader("Accept", "text/x-component")
                        .addHeader("Referer", WATCH_URL)
                        .addHeader("Origin", "https://luna-stream.me")
                        .post(bodyArray.toString().toRequestBody(TEXT_MEDIA_TYPE))
                        .build()

                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use
                        val bodyText = res.body?.string().orEmpty()
                        val parsed = parseRscResponse(bodyText) ?: return@use
                        val sources = parsed.optJSONArray("sources") ?: return@use

                        for (i in 0 until sources.length()) {
                            val sObj = sources.optJSONObject(i) ?: continue
                            var streamUrl = sObj.optString("url")
                            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                streamUrl = streamUrl.replace(
                                    "https://api.luna-stream.mehttps://api.luna-stream.me",
                                    "https://api.luna-stream.me"
                                )
                                val q = sObj.optString("quality").ifBlank { "1080p" }
                                providerResults.add(
                                    AnimeStreamResult(
                                        streamUrl = streamUrl,
                                        serverName = "Luna ($providerName)",
                                        category = subtype.uppercase(),
                                        quality = q,
                                        headers = mapOf(
                                            "User-Agent" to USER_AGENT,
                                            "Referer" to "https://luna-stream.me/",
                                            "Origin" to "https://luna-stream.me"
                                        )
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Luna error for $providerName: ${e.message}")
                }
                providerResults
            }
        }
        deferreds.awaitAll().flatten()
    }
}
