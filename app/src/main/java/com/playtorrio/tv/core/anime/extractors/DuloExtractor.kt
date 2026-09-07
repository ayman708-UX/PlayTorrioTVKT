package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.model.AnimeStreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DuloExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "DuloExtractor"
        private val DOMAINS = listOf("https://dulo.gd", "https://dulo.cx")
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun extract(
        anilistId: Int,
        episodeNumber: Int
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        for (domain in DOMAINS) {
            try {
                val sessionReq = Request.Builder()
                    .url("$domain/api/session")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$domain/")
                    .addHeader("Origin", domain)
                    .build()

                var sessionCookie: String? = null
                client.newCall(sessionReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val setCookie = res.header("Set-Cookie")
                        if (!setCookie.isNullOrBlank()) {
                            val match = Regex("""(__Host-amri_session=[^;]+)""").find(setCookie)
                            sessionCookie = match?.groupValues?.get(1) ?: setCookie.substringBefore(";")
                        }
                    }
                }

                val payload = JSONObject().apply {
                    put("type", "anime")
                    put("anilistId", anilistId)
                    put("episode", episodeNumber)
                }

                val sourceReqBuilder = Request.Builder()
                    .url("$domain/api/source")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Referer", "$domain/")
                    .addHeader("Origin", domain)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

                if (!sessionCookie.isNullOrBlank()) {
                    sourceReqBuilder.addHeader("Cookie", sessionCookie!!)
                }

                val playbackHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://d.dulo.gd/",
                    "Origin" to "https://d.dulo.gd"
                )

                client.newCall(sourceReqBuilder.build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val reader = res.body?.charStream()?.buffered() ?: return@use
                        reader.forEachLine { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data:")) {
                                val dataStr = trimmed.substring(5).trim()
                                if (dataStr.isNotEmpty() && dataStr.startsWith("{")) {
                                    try {
                                        val obj = JSONObject(dataStr)
                                        val sources = obj.optJSONArray("sources")
                                        if (sources != null) {
                                            val globalSubs = obj.optJSONArray("subtitles") ?: obj.optJSONArray("tracks") ?: obj.optJSONArray("captions")
                                            for (k in 0 until sources.length()) {
                                                val s = sources.optJSONObject(k) ?: continue
                                                val url = s.optString("url")
                                                val title = s.optString("title").ifBlank { "Source ${k + 1}" }
                                                val quality = s.optString("quality").ifBlank { "1080p" }
                                                if (url.startsWith("http")) {
                                                    val tracks = mutableListOf<AnimeStreamTrack>()
                                                    val subArr = s.optJSONArray("subtitles") ?: s.optJSONArray("tracks") ?: s.optJSONArray("captions") ?: globalSubs
                                                    if (subArr != null) {
                                                        for (tIdx in 0 until subArr.length()) {
                                                            val t = subArr.optJSONObject(tIdx) ?: continue
                                                            val f = t.optString("url").ifBlank { t.optString("file") }
                                                            val kind = t.optString("kind", "subtitles")
                                                            if (f.isNotBlank() && !kind.equals("thumbnails", ignoreCase = true)) {
                                                                tracks.add(
                                                                    AnimeStreamTrack(
                                                                        url = f,
                                                                        label = t.optString("label", t.optString("lang", "Subtitles")),
                                                                        lang = t.optString("lang", "en"),
                                                                        kind = kind,
                                                                        isDefault = t.optBoolean("default", false)
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }

                                                    results.add(
                                                        AnimeStreamResult(
                                                            streamUrl = url,
                                                            serverName = "Dulo ($title)",
                                                            category = "SUB",
                                                            quality = quality,
                                                            tracks = tracks,
                                                            headers = playbackHeaders
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }

                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.w(TAG, "Dulo extraction error on $domain: ${e.message}")
            }
        }
        results
    }
}
