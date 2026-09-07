package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AniPMExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "AniPMExtractor"
        private const val BASE_URL = "https://ani.pm"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    suspend fun extract(
        anilistId: Int,
        episodeNumber: Int,
        category: String, // "sub" or "dub"
        title: String? = null
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        try {
            val titleParam = if (!title.isNullOrBlank()) "&title=" + java.net.URLEncoder.encode(title, "UTF-8") else ""
            val uri = "$BASE_URL/api/anime/src/servers?ep=$episodeNumber&anilistId=$anilistId$titleParam"
            val req = Request.Builder()
                .url(uri)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext results
                val bodyStr = res.body?.string().orEmpty()
                val data = JSONObject(bodyStr)
                val targetAud = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"
                val list = data.optJSONArray(targetAud) ?: return@withContext results

                for (i in 0 until list.length()) {
                    val srv = list.optJSONObject(i) ?: continue
                    val rawUrl = srv.optString("url")
                    if (rawUrl.isBlank()) continue

                    val streamUrl = if (rawUrl.startsWith("/")) "$BASE_URL$rawUrl" else rawUrl
                    val provider = srv.optString("provider").ifBlank { "AniPM" }

                    results.add(
                        AnimeStreamResult(
                            streamUrl = streamUrl,
                            serverName = "AniPM ($provider)",
                            category = targetAud.uppercase(),
                            quality = "1080p",
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "$BASE_URL/",
                                "Origin" to BASE_URL
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniPM error: ${e.message}")
        }
        results
    }
}
