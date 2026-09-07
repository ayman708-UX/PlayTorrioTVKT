package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.model.AnimeStreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class TryEmbedExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "TryEmbedExtractor"
        private const val BASE_URL = "https://tryembed.us.cc"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }

    suspend fun extractAll(
        anilistId: Int,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        try {
            val cat = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"
            val embedUrl = "$BASE_URL/embed/anime/$anilistId/$episodeNumber/$cat?autoSkip=true"

            val embedReq = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://google.com/")
                .build()

            var html = ""
            var cookieHeader = ""
            client.newCall(embedReq).execute().use { res ->
                if (!res.isSuccessful) return@withContext results
                html = res.body?.string().orEmpty()
                val cookies = res.headers("Set-Cookie")
                if (cookies.isNotEmpty()) {
                    cookieHeader = cookies.joinToString("; ") { it.substringBefore(";") }
                }
            }

            val nonceMatch = Regex("""EMBED_NONCE\s*=\s*["']([^"']+)["']""").find(html)
            val nonce = nonceMatch?.groupValues?.get(1) ?: ""

            val encodedNonce = URLEncoder.encode(nonce, "UTF-8")
            val apiUrl = "$BASE_URL/api/stream_data?id=$anilistId&episode=$episodeNumber&audio=$cat&nonce=$encodedNonce"

            val apiReqBuilder = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .addHeader("Origin", BASE_URL)
                .addHeader("X-Embed-Nonce", nonce)
            if (cookieHeader.isNotBlank()) {
                apiReqBuilder.addHeader("Cookie", cookieHeader)
            }

            client.newCall(apiReqBuilder.build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext results
                val bodyStr = res.body?.string().orEmpty()
                val data = JSONObject(bodyStr)
                val providers = data.optJSONArray("providers") ?: return@withContext results

                val globalTracks = mutableListOf<AnimeStreamTrack>()
                val captionsArr = data.optJSONArray("captions")
                if (captionsArr != null) {
                    for (i in 0 until captionsArr.length()) {
                        val item = captionsArr.optJSONObject(i) ?: continue
                        val trackUrl = item.optString("url").ifBlank { item.optString("file") }
                        if (trackUrl.isNotBlank()) {
                            globalTracks.add(
                                AnimeStreamTrack(
                                    url = trackUrl,
                                    label = item.optString("label", "Subtitles"),
                                    lang = item.optString("lang", "en"),
                                    isDefault = item.optBoolean("default", false)
                                )
                            )
                        }
                    }
                }

                var introStart: Int? = null
                var introEnd: Int? = null
                val introObj = data.optJSONObject("intro")
                if (introObj != null) {
                    introStart = introObj.optInt("start")
                    introEnd = introObj.optInt("end")
                }

                var outroStart: Int? = null
                var outroEnd: Int? = null
                val outroObj = data.optJSONObject("outro")
                if (outroObj != null) {
                    outroStart = outroObj.optInt("start")
                    outroEnd = outroObj.optInt("end")
                }

                val reqHeaders = buildMap {
                    put("Referer", embedUrl)
                    put("Origin", BASE_URL)
                    put("User-Agent", USER_AGENT)
                    if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
                }

                for (i in 0 until providers.length()) {
                    val prov = providers.optJSONObject(i) ?: continue
                    val serverName = prov.optString("name").ifBlank { prov.optString("id", "Server") }
                    val qualities = prov.optJSONArray("qualities") ?: continue
                    if (qualities.length() == 0) continue

                    var streamUrl: String? = null
                    for (q in 0 until qualities.length()) {
                        val qObj = qualities.optJSONObject(q) ?: continue
                        val direct = qObj.optString("directUrl")
                        val token = qObj.optString("token")
                        val fallbackToken = qObj.optString("fallbackToken")

                        if (direct.isNotBlank()) {
                            streamUrl = direct
                            break
                        } else if (token.isNotBlank()) {
                            streamUrl = "$BASE_URL/s/$token.m3u8"
                            break
                        } else if (fallbackToken.isNotBlank()) {
                            streamUrl = "$BASE_URL/s/$fallbackToken.m3u8"
                            break
                        }
                    }

                    if (!streamUrl.isNullOrBlank()) {
                        val tracks = ArrayList(globalTracks)
                        val provCaptions = prov.optJSONArray("captions")
                        if (provCaptions != null) {
                            for (c in 0 until provCaptions.length()) {
                                val cObj = provCaptions.optJSONObject(c) ?: continue
                                val tUrl = cObj.optString("url").ifBlank { cObj.optString("file") }
                                if (tUrl.isNotBlank() && tracks.none { it.url == tUrl }) {
                                    tracks.add(
                                        AnimeStreamTrack(
                                            url = tUrl,
                                            label = cObj.optString("label", "Subtitles"),
                                            lang = cObj.optString("lang", "en"),
                                            isDefault = cObj.optBoolean("default", false)
                                        )
                                    )
                                }
                            }
                        }

                        results.add(
                            AnimeStreamResult(
                                streamUrl = streamUrl,
                                serverName = "TryEmbed ($serverName)",
                                category = cat.uppercase(),
                                quality = "Auto",
                                headers = reqHeaders,
                                tracks = tracks,
                                introStart = introStart,
                                introEnd = introEnd,
                                outroStart = outroStart,
                                outroEnd = outroEnd
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TryEmbed error: ${e.message}")
        }
        results
    }
}
