package com.playtorrio.tv.core.anime.extractors

import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class VidNestExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "VidNestExtractor"
        private const val BASE_URL = "https://vidnest.fun"
        private const val API_BASE_URL = "https://new.vidnest.fun"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val ALPHABET = "RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="
        private val REV_MAP = IntArray(256) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i
            }
        }
    }

    private fun decrypt(payload: String): JSONObject? {
        try {
            val len = payload.length
            val bytes = ByteArray(len)
            var j = 0
            var i = 0
            while (i < len) {
                val c0 = if (i < len) REV_MAP[payload[i].code] else 64
                val c1 = if (i + 1 < len) REV_MAP[payload[i + 1].code] else 64
                val c2 = if (i + 2 < len) REV_MAP[payload[i + 2].code] else 64
                val c3 = if (i + 3 < len) REV_MAP[payload[i + 3].code] else 64

                bytes[j++] = ((c0 shl 2) or (c1 shr 4)).toByte()
                if (c2 != 64) bytes[j++] = (((c1 and 15) shl 4) or (c2 shr 2)).toByte()
                if (c3 != 64) bytes[j++] = (((c2 and 3) shl 6) or c3).toByte()
                i += 4
            }
            val jsonStr = String(bytes, 0, j, Charsets.UTF_8)
            return JSONObject(jsonStr)
        } catch (_: Exception) {
            return null
        }
    }

    suspend fun extract(
        anilistId: Int,
        episodeNumber: Int,
        category: String // "sub" or "dub"
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        try {
            val catParam = if (category.equals("dub", ignoreCase = true)) "dub" else "sub"
            val endpoint = "$API_BASE_URL/hianime/anime/$anilistId/$episodeNumber/$catParam"

            val req = Request.Builder()
                .url(endpoint)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("Referer", "$BASE_URL/")
                .addHeader("Origin", BASE_URL)
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext results
                val bodyStr = res.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val dataObj = if (json.optBoolean("encrypted", false)) {
                    decrypt(json.optString("data"))
                } else {
                    json.optJSONObject("data")
                } ?: return@withContext results

                val proxyHeaders = JSONObject().apply {
                    put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
                    put("accept", "*/*")
                    put("origin", "https://megaplay.buzz")
                    put("referer", "https://megaplay.buzz/")
                }.toString()

                val tracks = mutableListOf<com.playtorrio.tv.core.anime.model.AnimeStreamTrack>()
                val tracksArr = dataObj.optJSONArray("tracks")
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) {
                        val t = tracksArr.optJSONObject(i) ?: continue
                        val f = t.optString("file").ifBlank { t.optString("url") }
                        val k = t.optString("kind", "subtitles")
                        if (f.isNotBlank() && !k.equals("thumbnails", ignoreCase = true)) {
                            val trackUrl = if (f.contains("cdn.imgnex.top") || f.contains("megacloud")) {
                                "https://megacloud.animanga.fun/ts-proxy?url=${java.net.URLEncoder.encode(f, "UTF-8")}&headers=${java.net.URLEncoder.encode(proxyHeaders, "UTF-8")}"
                            } else {
                                f
                            }
                            tracks.add(
                                com.playtorrio.tv.core.anime.model.AnimeStreamTrack(
                                    url = trackUrl,
                                    label = t.optString("label", "English"),
                                    lang = t.optString("lang", "en"),
                                    kind = k,
                                    isDefault = t.optBoolean("default", false)
                                )
                            )
                        }
                    }
                }

                val introObj = dataObj.optJSONObject("intro")
                val outroObj = dataObj.optJSONObject("outro")

                val sources = dataObj.optJSONArray("sources") ?: return@withContext results
                if (sources.length() > 0) {
                    val file = sources.optJSONObject(0)?.optString("file")
                    if (!file.isNullOrBlank()) {
                        val proxiedUrl = "https://megacloud.animanga.fun/proxy?url=${java.net.URLEncoder.encode(file, "UTF-8")}&headers=${java.net.URLEncoder.encode(proxyHeaders, "UTF-8")}"

                        results.add(
                            AnimeStreamResult(
                                streamUrl = proxiedUrl,
                                serverName = "VidNest (HiAnime)",
                                category = catParam.uppercase(),
                                quality = "1080p",
                                tracks = tracks,
                                introStart = introObj?.optInt("start"),
                                introEnd = introObj?.optInt("end"),
                                outroStart = outroObj?.optInt("start"),
                                outroEnd = outroObj?.optInt("end"),
                                headers = mapOf(
                                    "User-Agent" to USER_AGENT,
                                    "Referer" to "$BASE_URL/",
                                    "Origin" to BASE_URL
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VidNest error: ${e.message}")
        }
        results
    }
}
