package com.playtorrio.tv.core.anime.arabic

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ArabicResolvedServer(
    val name: String,
    val displayName: String,
    val iframeUrl: String
)

data class ArabicResolvedStream(
    val server: ArabicResolvedServer,
    val url: String,
    val quality: String,
    val type: String,
    val headers: Map<String, String>
)

@Singleton
class AnimeArabicExtractor @Inject constructor(
    private val megaProxy: MegaProxy
) {

    companion object {
        private const val TAG = "AnimeArabicExtractor"
        private const val FLARE_URL = "https://patrimoines-en-mouvement.org/lib/flare/v3.php"
        private const val XOR_KEY = "AQWXZSCED@@POIUYTRR159"

        private const val FALLBACK_NAME = "KwQdDUVLRBELIQgCEhY="
        private const val FALLBACK_BOOL = "no"
        private const val FALLBACK_SAN = "KwQdDUVLRBELIQgCEhY="
        private const val FALLBACK_MWSEM = "U29yY2VyeSBGaWdodCxKdWp1dHN1IEthaXNlbixKSks="

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val DISPLAY_NAMES = mapOf(
            "wit" to "Zen-2",
            "rift" to "Zen",
            "riftv2" to "Zen V2",
            "shof" to "Shof",
            "blkom" to "Blkom",
            "animeify" to "Animeify",
            "topcinema" to "TopCinema",
            "kuudere" to "Kuudere"
        )

        private val SERVER_ORDER = listOf("wit", "rift", "riftv2", "shof", "blkom", "animeify", "kuudere", "topcinema")

        fun decryptXorBase64(data: String): String? {
            return try {
                val decoded = Base64.decode(data.trim(), Base64.DEFAULT)
                val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
                val out = ByteArray(decoded.size)
                for (i in decoded.indices) {
                    out[i] = (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                }
                String(out, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        private fun qualityRank(q: String): Int {
            val m = Regex("""(\d{3,4})""").find(q) ?: return 0
            return m.groupValues[1].toIntOrNull() ?: 0
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cachedApiFirst: String? = null
    private var cachedApiSec: String? = null
    private var flareCachedAt: Long = 0L
    private val flareTtlMs = 10 * 60 * 1000L

    private suspend fun getFlare(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedApiFirst != null && cachedApiSec != null && (now - flareCachedAt) < flareTtlMs) {
            return@withContext Pair(cachedApiFirst!!, cachedApiSec!!)
        }

        try {
            val req = Request.Builder()
                .url(FLARE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "${AnimeArabicService.BASE_URL}/")
                .header("Accept", "application/json,*/*")
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                val first = json.optString("first", "")
                val sec = json.optString("sec", "")
                if (first.isNotBlank() && sec.isNotBlank()) {
                    cachedApiFirst = first
                    cachedApiSec = sec
                    flareCachedAt = now
                    return@withContext Pair(first, sec)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getFlare error: ${e.message}")
        }
        null
    }

    suspend fun discoverServers(watchPath: String): List<ArabicResolvedServer> = withContext(Dispatchers.IO) {
        if (watchPath.isBlank()) return@withContext emptyList()
        val hashIdx = watchPath.indexOf('#')
        val path = if (hashIdx >= 0) watchPath.substring(0, hashIdx) else watchPath
        val frag = if (hashIdx >= 0) watchPath.substring(hashIdx + 1) else ""
        if (frag.isBlank()) return@withContext emptyList()

        val segs = path.split("-")
        val pe = if (segs.size > 1) segs.last() else ""
        if (pe.isBlank()) return@withContext emptyList()

        val flare = getFlare() ?: return@withContext emptyList()
        val apiFirst = flare.first
        val apiSec = flare.second

        var name = FALLBACK_NAME
        var san = FALLBACK_SAN
        var mwsem = FALLBACK_MWSEM
        var boolStr = FALLBACK_BOOL

        try {
            val pageReq = Request.Builder()
                .url("${AnimeArabicService.BASE_URL}$path")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(pageReq).execute().use { res ->
                val pageHtml = res.body?.string().orEmpty()
                fun pluck(key: String): String? {
                    val m = Regex("""const\s+$key\s*=\s*"([^"]*)"""").find(pageHtml)
                    return m?.groupValues?.get(1)
                }
                pluck("name")?.takeIf { it.isNotBlank() }?.let { name = it }
                pluck("san")?.takeIf { it.isNotBlank() }?.let { san = it }
                pluck("mwsem")?.takeIf { it.isNotBlank() }?.let { mwsem = it }
                pluck("bool")?.takeIf { it.isNotBlank() }?.let { boolStr = it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Page token parse failed, using fallbacks: ${e.message}")
        }

        val j1 = try {
            val firstBody = FormBody.Builder()
                .add("pe", pe)
                .add("hash", frag)
                .build()
            val req1 = Request.Builder()
                .url(apiFirst)
                .header("User-Agent", USER_AGENT)
                .header("Origin", AnimeArabicService.BASE_URL)
                .header("Referer", "${AnimeArabicService.BASE_URL}/")
                .header("Accept", "application/json, text/plain, */*")
                .post(firstBody)
                .build()

            client.newCall(req1).execute().use { res1 ->
                val r1 = res1.body?.string().orEmpty()
                JSONObject(r1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "API first call failed: ${e.message}")
            return@withContext emptyList()
        }

        val aid = j1.optString("a", "")
        val binfo = j1.optString("b", "")
        val cep = j1.optString("c", "")
        val dkeyn = j1.optString("d", "")
        if (dkeyn.isBlank() || aid.isBlank() || binfo.isBlank()) {
            return@withContext emptyList()
        }

        val j2 = try {
            val secBody = FormBody.Builder()
                .add("keyn", dkeyn)
                .add("name", name)
                .add("pe", cep)
                .add("bool", boolStr)
                .add("id", aid)
                .add("info", binfo)
                .add("san", san)
                .add("mwsem", mwsem)
                .build()
            val req2 = Request.Builder()
                .url(apiSec)
                .header("User-Agent", USER_AGENT)
                .header("Origin", AnimeArabicService.BASE_URL)
                .header("Referer", "${AnimeArabicService.BASE_URL}/")
                .header("Accept", "application/json, text/plain, */*")
                .post(secBody)
                .build()

            client.newCall(req2).execute().use { res2 ->
                val r2 = res2.body?.string().orEmpty()
                JSONObject(r2)
            }
        } catch (e: Exception) {
            Log.w(TAG, "API sec call failed: ${e.message}")
            return@withContext emptyList()
        }

        val serversObj = j2.optJSONObject("servers")
        val rawList = j2.optJSONArray("servers")
        val serversMap = mutableMapOf<String, String>()

        if (serversObj != null) {
            val keys = serversObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                serversMap[k] = serversObj.optString(k, "")
            }
        } else if (rawList != null) {
            for (i in 0 until rawList.length()) {
                val entry = rawList.optJSONObject(i)
                if (entry != null) {
                    val sName = entry.optString("name", entry.optString("server", entry.optString("id", "")))
                    val sEnc = entry.optString("enc", entry.optString("value", entry.optString("url", "")))
                    if (sName.isNotBlank() && sEnc.isNotBlank()) {
                        serversMap[sName] = sEnc
                    }
                } else {
                    val str = rawList.optString(i, "")
                    if (str.isNotBlank()) serversMap["srv$i"] = str
                }
            }
        }

        val out = mutableListOf<ArabicResolvedServer>()
        for ((sName, enc) in serversMap) {
            if (enc.isBlank()) continue
            val url = decryptXorBase64(enc) ?: continue
            if (!url.startsWith("http")) continue
            val displayName = DISPLAY_NAMES[sName.lowercase()] ?: sName.replaceFirstChar { it.uppercase() }
            out.add(ArabicResolvedServer(
                name = sName,
                displayName = displayName,
                iframeUrl = url
            ))
        }

        out
    }

    suspend fun resolveEpisode(
        watchPath: String,
        episodeNumber: Int = 1,
        animeTitle: String = ""
    ): List<AnimeStreamResult> = withContext(Dispatchers.IO) {
        val servers = discoverServers(watchPath)
        if (servers.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val deferred = servers.map { server ->
                async { scrapeServer(server) }
            }
            val results = deferred.awaitAll().flatten()

            fun rank(name: String): Int {
                val idx = SERVER_ORDER.indexOf(name.lowercase())
                return if (idx < 0) 999 else idx
            }

            val sorted = results.sortedWith(
                compareBy<ArabicResolvedStream> { rank(it.server.name) }
                    .thenByDescending { qualityRank(it.quality) }
            )

            sorted.map { stream ->
                val qLabel = if (stream.quality.isNotBlank()) " • ${stream.quality}" else ""
                val serverTitle = "⚡ ${stream.server.displayName}$qLabel"
                val isMp4 = stream.type == "video" || stream.url.contains(".mp4")

                AnimeStreamResult(
                    streamUrl = stream.url,
                    serverName = serverTitle,
                    category = "SUB",
                    quality = if (stream.quality.isNotBlank()) stream.quality else "Auto",
                    headers = stream.headers,
                    tracks = emptyList(), // embedded in video
                    isDirectMp4 = isMp4
                )
            }
        }
    }

    private suspend fun scrapeServer(server: ArabicResolvedServer): List<ArabicResolvedStream> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(server.iframeUrl)
        val host = uri.host?.lowercase().orEmpty()

        if (host.contains("mega.nz") || host.contains("mega.co.nz")) {
            return@withContext try {
                val mega = megaProxy.resolve(server.iframeUrl)
                if (mega != null) {
                    listOf(
                        ArabicResolvedStream(
                            server = server,
                            url = mega.url,
                            quality = "HD",
                            type = "video",
                            headers = emptyMap()
                        )
                    )
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        try {
            val origin = "${uri.scheme}://${uri.host}" + if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "$origin/",
                "Origin" to origin
            )

            val req = Request.Builder()
                .url(server.iframeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "${AnimeArabicService.BASE_URL}/")
                .header("Accept", "text/html,*/*")
                .build()

            val html = client.newCall(req).execute().use { res ->
                res.body?.string().orEmpty()
            }

            val out = mutableListOf<ArabicResolvedStream>()
            val blockRegex = Regex(
                """src\s*:\s*['"]([^'"]+)['"](?:[^{}]*?label\s*:\s*['"]([^'"]*)['"])?(?:[^{}]*?res\s*:\s*['"]?([0-9a-zA-Z]+)['"]?)?"""
            )
            val seen = mutableSetOf<String>()

            for (m in blockRegex.findAll(html)) {
                val url = m.groupValues[1].trim()
                if (url.isBlank() || !url.startsWith("http")) continue
                if (!seen.add(url)) continue
                val label = m.groupValues.getOrNull(2)?.trim().orEmpty()
                val res = m.groupValues.getOrNull(3)?.trim().orEmpty()
                val quality = if (label.isNotBlank()) label else if (res.isNotBlank()) "${res}p" else ""
                val lower = url.lowercase()
                val type = if (lower.contains(".m3u8")) "hls" else "video"

                out.add(ArabicResolvedStream(
                    server = server,
                    url = url,
                    quality = quality,
                    type = type,
                    headers = headers
                ))
            }

            out.sortedByDescending { qualityRank(it.quality) }
        } catch (e: Exception) {
            Log.w(TAG, "Scrape server ${server.name} failed: ${e.message}")
            emptyList()
        }
    }
}
