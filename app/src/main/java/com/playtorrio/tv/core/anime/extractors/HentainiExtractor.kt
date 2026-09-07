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

class HentainiExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "HentainiExtractor"
        private const val SITE = "https://hentaini.com"
        private const val API = "https://admin.hentaini.com/api"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int
    ): AnimeStreamResult? = withContext(Dispatchers.IO) {
        for (query in titleCandidates) {
            val clean = query.trim()
            if (clean.isBlank()) continue

            try {
                val searchUrl = "$API/series?filters[title][\$containsi]=${URLEncoder.encode(clean, "UTF-8")}&pagination[pageSize]=25"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$SITE/")
                    .addHeader("Accept", "application/json")
                    .build()

                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null && dataArr.length() > 0) {
                            for (i in 0 until dataArr.length()) {
                                val item = dataArr.optJSONObject(i) ?: continue
                                val id = item.optInt("id")
                                val episodesUrl = "$API/episodes?filters[series][id]=$id&filters[number]=$episodeNumber&populate=*"
                                val epReq = Request.Builder()
                                    .url(episodesUrl)
                                    .addHeader("User-Agent", USER_AGENT)
                                    .addHeader("Referer", "$SITE/")
                                    .addHeader("Accept", "application/json")
                                    .build()

                                client.newCall(epReq).execute().use { epRes ->
                                    if (epRes.isSuccessful) {
                                        val epBody = epRes.body?.string().orEmpty()
                                        val epJson = JSONObject(epBody)
                                        val epData = epJson.optJSONArray("data")
                                        if (epData != null && epData.length() > 0) {
                                            val firstEp = epData.optJSONObject(0)
                                            val attributes = firstEp?.optJSONObject("attributes") ?: firstEp
                                            val videoUrl = attributes?.optString("videoUrl")
                                                ?.ifBlank { attributes.optString("url") }
                                                ?.ifBlank { attributes.optString("streamUrl") }

                                            if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("http")) {
                                                val tracks = mutableListOf<AnimeStreamTrack>()
                                                val subArr = attributes?.optJSONArray("subtitles") ?: attributes?.optJSONArray("tracks")
                                                if (subArr != null) {
                                                    for (tIdx in 0 until subArr.length()) {
                                                        val t = subArr.optJSONObject(tIdx) ?: continue
                                                        val f = t.optString("url").ifBlank { t.optString("file") }
                                                        if (f.isNotBlank()) {
                                                            tracks.add(
                                                                AnimeStreamTrack(
                                                                    url = f,
                                                                    label = t.optString("label", "English"),
                                                                    lang = t.optString("lang", "en")
                                                                )
                                                            )
                                                        }
                                                    }
                                                }

                                                return@withContext AnimeStreamResult(
                                                    streamUrl = videoUrl,
                                                    serverName = "Hentaini",
                                                    category = "SUB",
                                                    quality = "1080p",
                                                    tracks = tracks,
                                                    isDirectMp4 = videoUrl.contains(".mp4"),
                                                    headers = mapOf(
                                                        "User-Agent" to USER_AGENT,
                                                        "Referer" to "$SITE/",
                                                        "Origin" to SITE
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
                Log.w(TAG, "Hentaini error: ${e.message}")
            }
        }
        null
    }
}
