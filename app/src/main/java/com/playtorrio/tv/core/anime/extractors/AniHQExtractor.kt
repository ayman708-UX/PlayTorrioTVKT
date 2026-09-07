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

class AniHQExtractor(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "AniHQExtractor"
        private const val BASE_URL = "https://anihq.cc"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private fun cleanSlug(title: String): String {
        return title.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .replace(Regex("""\s+"""), "-")
            .trim('-')
    }

    private fun rot13(s: String): String {
        val out = StringBuilder()
        for (c in s) {
            when (c) {
                in 'A'..'Z' -> out.append(((c.code - 65 + 13) % 26 + 65).toChar())
                in 'a'..'z' -> out.append(((c.code - 97 + 13) % 26 + 97).toChar())
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun base64Decode(str: String): ByteArray {
        val pad = when (str.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        val padded = str + pad
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (_: Throwable) {
            android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        }
    }

    private data class VoeResult(
        val streamUrl: String,
        val tracks: List<AnimeStreamTrack> = emptyList()
    )

    private fun extractVoe(voeUrl: String): VoeResult? {
        try {
            var voeReq = Request.Builder()
                .url(voeUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/")
                .build()

            var html = ""
            client.newCall(voeReq).execute().use { res ->
                if (res.isSuccessful) {
                    html = res.body?.string().orEmpty()
                }
            }

            val redirectMatch = Regex("""window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]""").find(html)
            if (redirectMatch != null) {
                val redirUrl = redirectMatch.groupValues[1]
                voeReq = Request.Builder()
                    .url(redirUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$BASE_URL/")
                    .build()
                client.newCall(voeReq).execute().use { res ->
                    if (res.isSuccessful) html = res.body?.string().orEmpty()
                }
            }

            val scriptMatch = Regex("""<script[^>]*type\s*=\s*["']application/json["'][^>]*>\s*\[\s*"(.*?)"\s*\]""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("""<script[^>]*type\s*=\s*["']application/json["'][^>]*>\["(.*?)"\]""").find(html)

            if (scriptMatch != null) {
                var str = scriptMatch.groupValues[1].replace("\r", "").replace("\n", "")
                str = rot13(str)

                val junk = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
                for (j in junk) {
                    str = str.replace(j, "")
                }

                val decoded1 = String(base64Decode(str), Charsets.UTF_8)
                val shifted = StringBuilder()
                for (c in decoded1) {
                    shifted.append((c.code - 3).toChar())
                }

                val reversed = shifted.toString().reversed()
                val finalJsonStr = String(base64Decode(reversed), Charsets.UTF_8)
                val finalData = JSONObject(finalJsonStr)

                val streamUrl = finalData.optString("source").ifBlank { finalData.optString("file") }
                if (streamUrl.startsWith("http")) {
                    val tracks = mutableListOf<AnimeStreamTrack>()
                    val subArr = finalData.optJSONArray("tracks") ?: finalData.optJSONArray("subtitles")
                    if (subArr != null) {
                        for (tIdx in 0 until subArr.length()) {
                            val t = subArr.optJSONObject(tIdx) ?: continue
                            val f = t.optString("file").ifBlank { t.optString("url") }
                            val kind = t.optString("kind", "subtitles")
                            if (f.isNotBlank() && !kind.equals("thumbnails", ignoreCase = true)) {
                                tracks.add(
                                    AnimeStreamTrack(
                                        url = f,
                                        label = t.optString("label", t.optString("lang", "English")),
                                        lang = t.optString("lang", "en"),
                                        kind = kind,
                                        isDefault = t.optBoolean("default", false)
                                    )
                                )
                            }
                        }
                    }

                    // Also check HTML for <track> elements
                    val trackRegex = Regex("""<track[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                    for (match in trackRegex.findAll(html)) {
                        val trackTag = match.value
                        val src = match.groupValues[1]
                        if (src.isNotBlank() && tracks.none { it.url == src }) {
                            val label = Regex("""label=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trackTag)?.groupValues?.get(1) ?: "Subtitles"
                            val srclang = Regex("""srclang=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trackTag)?.groupValues?.get(1) ?: "en"
                            val isThumb = trackTag.contains("thumbnails", ignoreCase = true)
                            if (!isThumb) {
                                tracks.add(AnimeStreamTrack(url = src, label = label, lang = srclang))
                            }
                        }
                    }

                    return VoeResult(streamUrl = streamUrl, tracks = tracks)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun extract(
        titleCandidates: List<String>,
        episodeNumber: Int,
        category: String = "sub"
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeStreamResult>()
        val isDub = category.equals("dub", ignoreCase = true)
        val typeSuffix = if (isDub) "english-dubbed" else "english-subbed"

        for (title in titleCandidates) {
            val clean = cleanSlug(title)
            if (clean.isBlank()) continue

            try {
                val directUrl = "$BASE_URL/watch/$clean-episode-$episodeNumber-$typeSuffix/"
                val directReq = Request.Builder()
                    .url(directUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .build()

                var html = ""
                var statusCode = 0
                client.newCall(directReq).execute().use { res ->
                    statusCode = res.code
                    if (res.isSuccessful) {
                        html = res.body?.string().orEmpty()
                    }
                }

                if (statusCode != 200 || html.isBlank()) {
                    val sUrl = "$BASE_URL/search?keyword=${URLEncoder.encode(title, "UTF-8")}"
                    val sReq = Request.Builder()
                        .url(sUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                    client.newCall(sReq).execute().use { sRes ->
                        if (sRes.isSuccessful) {
                            val sBody = sRes.body?.string().orEmpty()
                            val sMatch = Regex("""href="(https://anihq\.cc/watch/[^"]+-episode-$episodeNumber-$typeSuffix/)"""", RegexOption.IGNORE_CASE).find(sBody)
                            val foundUrl = sMatch?.groupValues?.get(1)
                            if (!foundUrl.isNullOrBlank()) {
                                val rReq = Request.Builder().url(foundUrl).addHeader("User-Agent", USER_AGENT).build()
                                client.newCall(rReq).execute().use { rRes ->
                                    if (rRes.isSuccessful) html = rRes.body?.string().orEmpty()
                                }
                            }
                        }
                    }
                }

                if (html.isNotBlank()) {
                    val voeMatch = Regex("""data-video=["'](https?://[^"']*voe[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""href=["'](https?://[^"']*voe[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""<iframe[^>]*src=["'](https?://[^"']*voe[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)

                    val voeUrl = voeMatch?.groupValues?.get(1)
                    if (!voeUrl.isNullOrBlank()) {
                        val voeRes = extractVoe(voeUrl)
                        if (voeRes != null && voeRes.streamUrl.isNotBlank()) {
                            results.add(
                                AnimeStreamResult(
                                    streamUrl = voeRes.streamUrl,
                                    serverName = "AniHQ (VOE)",
                                    category = if (isDub) "DUB" else "SUB",
                                    quality = "1080p",
                                    tracks = voeRes.tracks,
                                    headers = mapOf(
                                        "User-Agent" to USER_AGENT,
                                        "Referer" to "$BASE_URL/",
                                        "Origin" to BASE_URL
                                    )
                                )
                            )
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AniHQ error: ${e.message}")
            }
        }
        results
    }
}
