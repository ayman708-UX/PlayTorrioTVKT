package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.model.AnimeStreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class MegaPlayExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "MegaPlayExtractor"
        private const val BASE_URL = "https://megaplay.buzz"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }

    suspend fun extract(
        anilistId: Int,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): AnimeStreamResult? = withContext(Dispatchers.IO) {
        try {
            val cat = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"
            val playerUrl = "$BASE_URL/stream/ani/$anilistId/$episodeNumber/$cat"

            val playerReq = Request.Builder()
                .url(playerUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://megaplay.buzz/api")
                .addHeader("Cookie", "SITE_TOTAL_ID=ce655f0eea754f2888ea98ded373e3b5")
                .build()

            var html = ""
            client.newCall(playerReq).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                html = response.body?.string().orEmpty()
            }

            var doc = Jsoup.parse(html)
            var dataId: String? = doc.getElementById("megaplay-player")?.attr("data-id")
                ?: doc.select("[data-id]").attr("data-id").ifBlank { null }

            var currentDomain = BASE_URL
            var currentReferer = playerUrl

            if (dataId.isNullOrBlank()) {
                val iframeSrc = doc.select("iframe").attr("src")
                if (iframeSrc.isNotBlank()) {
                    val fullIframeUrl = when {
                        iframeSrc.startsWith("//") -> "https:$iframeSrc"
                        iframeSrc.startsWith("http") -> iframeSrc
                        else -> "$BASE_URL$iframeSrc"
                    }
                    val uri = android.net.Uri.parse(fullIframeUrl)
                    currentDomain = "${uri.scheme}://${uri.host}"
                    currentReferer = fullIframeUrl

                    val iframeReq = Request.Builder()
                        .url(fullIframeUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", playerUrl)
                        .build()

                    client.newCall(iframeReq).execute().use { res ->
                        if (res.isSuccessful) {
                            html = res.body?.string().orEmpty()
                            doc = Jsoup.parse(html)
                            dataId = doc.select("[data-id]").attr("data-id")
                            if (dataId.isNullOrBlank()) {
                                val match = Regex("""data-id="(\d+)"""").find(html)
                                dataId = match?.groupValues?.get(1)
                            }
                        }
                    }
                }
            }

            if (dataId.isNullOrBlank()) {
                return@withContext null
            }

            val sourcesUrl = "$currentDomain/stream/getSources?id=$dataId&id=$dataId"
            val sourcesReq = Request.Builder()
                .url(sourcesUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", currentReferer)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Cookie", "SITE_TOTAL_ID=ce655f0eea754f2888ea98ded373e3b5")
                .build()

            client.newCall(sourcesReq).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val bodyStr = res.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val sources = json.optJSONObject("sources") ?: return@withContext null
                val fileUrl = sources.optString("file")
                if (fileUrl.isBlank()) return@withContext null

                val tracks = mutableListOf<AnimeStreamTrack>()
                val tracksArr = json.optJSONArray("tracks")
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) {
                        val t = tracksArr.optJSONObject(i) ?: continue
                        val f = t.optString("file")
                        val k = t.optString("kind", "subtitles")
                        if (f.isNotBlank() && !k.equals("thumbnails", ignoreCase = true)) {
                            tracks.add(
                                AnimeStreamTrack(
                                    url = f,
                                    label = t.optString("label", "Unknown"),
                                    kind = k,
                                    isDefault = t.optBoolean("default", false)
                                )
                            )
                        }
                    }
                }

                var introStart: Int? = null
                var introEnd: Int? = null
                val introObj = json.optJSONObject("intro")
                if (introObj != null) {
                    introStart = introObj.optInt("start")
                    introEnd = introObj.optInt("end")
                }

                var outroStart: Int? = null
                var outroEnd: Int? = null
                val outroObj = json.optJSONObject("outro")
                if (outroObj != null) {
                    outroStart = outroObj.optInt("start")
                    outroEnd = outroObj.optInt("end")
                }

                val headers = mapOf(
                    "Referer" to "$currentDomain/",
                    "Origin" to currentDomain,
                    "User-Agent" to USER_AGENT,
                    "Cookie" to "SITE_TOTAL_ID=ce655f0eea754f2888ea98ded373e3b5"
                )

                return@withContext AnimeStreamResult(
                    streamUrl = fileUrl,
                    serverName = "MegaPlay",
                    category = cat.uppercase(),
                    quality = "Auto",
                    headers = headers,
                    tracks = tracks,
                    introStart = introStart,
                    introEnd = introEnd,
                    outroStart = outroStart,
                    outroEnd = outroEnd
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "MegaPlay error: ${e.message}")
            null
        }
    }
}
