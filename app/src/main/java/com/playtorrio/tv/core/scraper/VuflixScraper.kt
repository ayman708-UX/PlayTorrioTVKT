package com.playtorrio.tv.core.scraper

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VuflixScraper : StreamScraper {
    override val name: String = "PlayTorrioHTTP"

    companion object {
        private const val TAG = "VuflixScraper"
        private const val API_BASE = "https://vuflix.co"
        private const val REFERER = "https://vuflix.co/"
        private const val ORIGIN = "https://vuflix.co"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val defaultHeaders = mapOf(
            "User-Agent" to UA,
            "Referer" to REFERER,
            "Origin" to ORIGIN,
            "Accept" to "application/json, text/plain, */*"
        )

        private data class ProviderInfo(val id: String, val name: String)
        private data class UnwrappedUrl(val url: String, val headers: Map<String, String>)

        private val fallbackProviders = listOf(
            ProviderInfo("vsembed", "Sigma"),
            ProviderInfo("moonflix", "Source 40"),
            ProviderInfo("megasource", "Source 39"),
            ProviderInfo("hdghar", "Source 44"),
            ProviderInfo("moviebox", "Pi"),
            ProviderInfo("cineplay", "4K"),
            ProviderInfo("huhu", "Beta"),
            ProviderInfo("bingr", "Upsilon"),
            ProviderInfo("onlyflix", "Gamma"),
            ProviderInfo("vaplayer", "Alpha"),
            ProviderInfo("flixhqz", "Gamma"),
            ProviderInfo("castle", "Source 40"),
            ProviderInfo("cinejoy", "4K2"),
            ProviderInfo("filesun", "Tau"),
            ProviderInfo("yoru", "Yoru")
        )

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private fun unwrapUrl(rawUrl: String): UnwrappedUrl {
            if (rawUrl.isBlank()) return UnwrappedUrl("", defaultHeaders)
            if (!rawUrl.contains("v-relay?t=") && !rawUrl.contains("a-relay?t=")) {
                return UnwrappedUrl(rawUrl, defaultHeaders)
            }
            try {
                val uri = java.net.URI(rawUrl)
                val query = uri.query.orEmpty()
                val token = query.split("&")
                    .firstOrNull { it.startsWith("t=") }
                    ?.substringAfter("t=")

                if (!token.isNullOrEmpty()) {
                    val jsonBytes = Base64.decode(token, Base64.DEFAULT)
                    val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
                    val directUrl = json.optString("u").trim()
                    val hMap = LinkedHashMap(defaultHeaders)
                    val hObj = json.optJSONObject("h")
                    if (hObj != null) {
                        val keys = hObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            hMap[k] = hObj.optString(k)
                        }
                    }
                    if (directUrl.isNotBlank()) {
                        return UnwrappedUrl(directUrl, hMap)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed unwrapping Vuflix token: ${e.message}")
            }
            return UnwrappedUrl(rawUrl, defaultHeaders)
        }
    }

    override suspend fun scrape(request: ScraperMediaRequest): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val isTv = request.type.equals("tv", ignoreCase = true) || request.type.equals("series", ignoreCase = true)
        val mediaType = if (isTv) "tv" else "movie"

        val tmdbId = request.tmdbId ?: TmdbScraperHelper.resolveTmdbId(
            imdbId = request.imdbId,
            title = request.title,
            type = mediaType,
            year = request.year
        ) ?: return@withContext emptyList()

        val providers = fallbackProviders
        val tasks = providers.map { prov ->
            async {
                val list = mutableListOf<ScraperStreamResult>()
                try {
                    val urlBuilder = "$API_BASE/api/player/sources".toHttpUrl().newBuilder()
                        .addQueryParameter("type", mediaType)
                        .addQueryParameter("tmdbId", tmdbId.toString())
                        .addQueryParameter("provider", prov.id)

                    if (isTv) {
                        urlBuilder.addQueryParameter("season", (request.season ?: 1).toString())
                        urlBuilder.addQueryParameter("episode", (request.episode ?: 1).toString())
                    }

                    val req = Request.Builder()
                        .url(urlBuilder.build())
                        .apply { defaultHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val json = JSONObject(res.body?.string().orEmpty())
                            val sourcesList = json.optJSONArray("sources")
                            if (sourcesList != null) {
                                for (i in 0 until sourcesList.length()) {
                                    val item = sourcesList.getJSONObject(i)
                                    val providerName = item.optString("providerName").ifEmpty {
                                        item.optString("publicLabel").ifEmpty { prov.name }
                                    }
                                    val primaryRawUrl = item.optString("url").trim()
                                    val itemType = item.optString("type").ifEmpty { "hls" }.uppercase()

                                    val qualities = item.optJSONArray("qualities")
                                    if (qualities != null && qualities.length() > 0) {
                                        for (qIdx in 0 until qualities.length()) {
                                            val qObj = qualities.getJSONObject(qIdx)
                                            val qRawUrl = qObj.optString("url").trim()
                                            if (qRawUrl.isNotBlank()) {
                                                val unwrapped = unwrapUrl(qRawUrl)
                                                if (unwrapped.url.startsWith("http")) {
                                                    val qQuality = qObj.optString("quality").ifEmpty { "Auto" }
                                                    val qType = qObj.optString("type").ifEmpty { itemType }.uppercase()
                                                    list.add(
                                                        ScraperStreamResult(
                                                            name = "PlayTorrioHTTP",
                                                            title = "[Vuflix - $providerName] $qQuality",
                                                            description = "$providerName • $qQuality • $qType",
                                                            url = unwrapped.url,
                                                            quality = qQuality,
                                                            headers = unwrapped.headers
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else if (primaryRawUrl.isNotBlank()) {
                                        val unwrapped = unwrapUrl(primaryRawUrl)
                                        if (unwrapped.url.startsWith("http")) {
                                            val quality = item.optString("quality").ifEmpty { "Auto" }
                                            list.add(
                                                ScraperStreamResult(
                                                    name = "PlayTorrioHTTP",
                                                    title = "[Vuflix - $providerName] $quality",
                                                    description = "$providerName • $quality • $itemType",
                                                    url = unwrapped.url,
                                                    quality = quality,
                                                    headers = unwrapped.headers
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Vuflix provider ${prov.name} failed: ${e.message}")
                }
                list
            }
        }

        val allResults = tasks.awaitAll().flatten()
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<ScraperStreamResult>()
        for (r in allResults) {
            if (seen.add(r.url)) {
                deduped.add(r)
            }
        }
        deduped
    }
}
