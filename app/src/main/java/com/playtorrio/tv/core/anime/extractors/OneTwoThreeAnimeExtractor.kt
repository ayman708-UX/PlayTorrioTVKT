package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class OneTwoThreeAnimeExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "123AnimeExtractor"
        private const val BASE_URL = "https://123animehub.cc"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int
    ): AnimeStreamResult? = withContext(Dispatchers.IO) {
        for (title in titleCandidates) {
            val clean = title.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
            if (clean.isBlank()) continue

            try {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val searchUrl = "$BASE_URL/ajax/film/search?keyword=$encoded&_=${System.currentTimeMillis()}"

                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", BASE_URL)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Accept", "application/json")
                    .build()

                var slug: String? = null
                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val html = json.optString("html")
                        val match = Regex("""href="/anime/([^"]+)"""").find(html)
                        slug = match?.groupValues?.get(1)
                    }
                }

                if (!slug.isNullOrBlank()) {
                    val epUrl = "$BASE_URL/ajax/episode/info?epr=$slug/$episodeNumber&ts=1&_=${System.currentTimeMillis()}"
                    val epReq = Request.Builder()
                        .url(epUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$BASE_URL/anime/$slug")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()

                    client.newCall(epReq).execute().use { res ->
                        if (res.isSuccessful) {
                            val body = res.body?.string().orEmpty()
                            val json = JSONObject(body)
                            val targetEmbed = json.optString("target")
                            if (targetEmbed.startsWith("http")) {
                                val embedMatch = Regex("""/embed-[^/]+/([A-Za-z0-9+/=]+)$""").find(targetEmbed)
                                val sourceId = embedMatch?.groupValues?.get(1)
                                if (!sourceId.isNullOrBlank()) {
                                    val uri = java.net.URI(targetEmbed)
                                    val origin = "${uri.scheme}://${uri.host}"

                                    val embedReq = Request.Builder()
                                        .url(targetEmbed)
                                        .addHeader("User-Agent", USER_AGENT)
                                        .build()

                                    var cookieHeader: String? = null
                                    client.newCall(embedReq).execute().use { eRes ->
                                        if (eRes.isSuccessful) {
                                            val setCookie = eRes.header("Set-Cookie")
                                            if (!setCookie.isNullOrBlank()) {
                                                cookieHeader = setCookie.substringBefore(";").trim()
                                            }
                                        }
                                    }

                                    val srcReqBuilder = Request.Builder()
                                        .url("$origin/hs/getSources?id=$sourceId")
                                        .addHeader("Referer", targetEmbed)
                                        .addHeader("Accept", "*/*")
                                        .addHeader("User-Agent", USER_AGENT)

                                    if (!cookieHeader.isNullOrBlank()) {
                                        srcReqBuilder.addHeader("Cookie", cookieHeader!!)
                                    }

                                    client.newCall(srcReqBuilder.build()).execute().use { sRes ->
                                        if (sRes.isSuccessful) {
                                            val sBody = sRes.body?.string().orEmpty()
                                            val sJson = JSONObject(sBody)
                                            var streamUrl: String? = null

                                            val sourcesObj = sJson.opt("sources")
                                            if (sourcesObj is String && sourcesObj.isNotBlank()) {
                                                streamUrl = sourcesObj
                                            } else if (sourcesObj is org.json.JSONArray && sourcesObj.length() > 0) {
                                                val first = sourcesObj.optJSONObject(0)
                                                streamUrl = first?.optString("file")
                                                    ?.ifBlank { first.optString("src") }
                                                    ?.ifBlank { first.optString("url") }
                                            }

                                            if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                                                return@withContext AnimeStreamResult(
                                                    streamUrl = streamUrl,
                                                    serverName = "123Anime (EchoVideo)",
                                                    category = "SUB",
                                                    quality = "1080p",
                                                    headers = mapOf(
                                                        "Referer" to "https://play2.echovideo.ru/",
                                                        "Origin" to "https://play2.echovideo.ru",
                                                        "User-Agent" to USER_AGENT
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "123Anime error: ${e.message}")
            }
        }
        null
    }
}
