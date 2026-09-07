package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.model.AnimeStreamTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ReCloudExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "ReCloudExtractor"
        private const val BASE_URL = "https://cdn.4animo.xyz"
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
            val embedUrl = "$BASE_URL/embed/ani/$anilistId/$episodeNumber/$cat?k=1"

            val embedReq = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://google.com/")
                .build()

            var html = ""
            client.newCall(embedReq).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                html = response.body?.string().orEmpty()
            }

            val match = Regex("""var\s+sourcesUrl\s*=\s*['"]([^'"]+)['"]""").find(html)
            val sourcesPath = match?.groupValues?.get(1) ?: return@withContext null
            val sourcesUrl = if (sourcesPath.startsWith("http")) sourcesPath else "$BASE_URL$sourcesPath"

            val sourcesReq = Request.Builder()
                .url(sourcesUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .addHeader("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(sourcesReq).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val bodyStr = res.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val sources = json.optJSONArray("sources") ?: return@withContext null
                if (sources.length() == 0) return@withContext null

                val firstSource = sources.optJSONObject(0) ?: return@withContext null
                val filePath = firstSource.optString("file")
                if (filePath.isBlank()) return@withContext null

                val streamUrl = if (filePath.startsWith("http")) filePath else "$BASE_URL$filePath"

                val tracks = mutableListOf<AnimeStreamTrack>()
                val tracksArr = json.optJSONArray("tracks")
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) {
                        val item = tracksArr.optJSONObject(i) ?: continue
                        val trackFile = item.optString("file")
                        val fullTrackFile = if (trackFile.startsWith("http")) {
                            trackFile
                        } else if (trackFile.isNotBlank()) {
                            "$BASE_URL$trackFile"
                        } else ""

                        if (fullTrackFile.isNotBlank()) {
                            tracks.add(
                                AnimeStreamTrack(
                                    url = fullTrackFile,
                                    label = item.optString("label", "Subtitles"),
                                    kind = item.optString("kind", "subtitles"),
                                    isDefault = item.optBoolean("default", false)
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
                    "Referer" to "$BASE_URL/",
                    "Origin" to BASE_URL,
                    "User-Agent" to USER_AGENT
                )

                return@withContext AnimeStreamResult(
                    streamUrl = streamUrl,
                    serverName = "ReCloud",
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
            Log.w(TAG, "ReCloud error: ${e.message}")
            null
        }
    }
}
