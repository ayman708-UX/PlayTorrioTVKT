package com.playtorrio.tv.data.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

object VsEmbedExtractor {
    private const val TAG = "VsEmbedExtractor"

    suspend fun extract(
        client: OkHttpClient,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): StreamResult? = withContext(Dispatchers.IO) {
        val isMovie = season == null
        val embedUrl = if (isMovie) {
            "https://vsembed.ru/embed/movie/$tmdbId"
        } else {
            "https://vsembed.ru/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode" // wait, original JS used /embed/tv/tmdb/s/e but also ?tmdb= depending on version. Let's use old working one.
        }
        
        val actualEmbedUrl = if (isMovie) "https://vsembed.ru/embed/movie/$tmdbId" else "https://vsembed.ru/embed/tv/$tmdbId/$season/$episode"
        
        Log.i(TAG, "Fetching embed page: $actualEmbedUrl")
        
        try {
            val req1 = Request.Builder().url(actualEmbedUrl).build()
            val html1 = client.newCall(req1).execute().use { it.body?.string() } ?: return@withContext null
            
            val iframeRegex = Regex("""<iframe[^>]+src="([^"]+rcp[^"]+)"""", RegexOption.IGNORE_CASE)
            val iframeMatch = iframeRegex.find(html1) ?: return@withContext null
            
            var rcpUrl = iframeMatch.groupValues[1]
            if (rcpUrl.startsWith("//")) {
                rcpUrl = "https:$rcpUrl"
            }
            
            Log.i(TAG, "Found rcp URL: $rcpUrl")
            
            val req2 = Request.Builder()
                .url(rcpUrl)
                .header("Referer", "https://vsembed.ru/")
                .build()
            val html2 = client.newCall(req2).execute().use { it.body?.string() } ?: return@withContext null
            
            val proMatch = Regex("""src:\s*'(/prorcp/[^']+)'""").find(html2) ?: return@withContext null
            val prorcpUrl = URL(URL(rcpUrl), proMatch.groupValues[1]).toString()
            
            Log.i(TAG, "Found prorcp URL: $prorcpUrl")
            
            val req3 = Request.Builder()
                .url(prorcpUrl)
                .header("Referer", "https://vsembed.ru/")
                .build()
            val html3 = client.newCall(req3).execute().use { it.body?.string() } ?: return@withContext null
            
            val masterUrlsMatch = Regex("""var master_urls = "([^"]+)"""").find(html3) ?: return@withContext null
            val rawMasterUrls = masterUrlsMatch.groupValues[1]
            
            Log.i(TAG, "Found raw master_urls: $rawMasterUrls")
            
            val hostMatch = Regex("""https://([^/]+)/pl""").find(rawMasterUrls) ?: return@withContext null
            val host = hostMatch.groupValues[1]
            
            val tokenReq = Request.Builder()
                .url("https://$host/generate.php")
                .header("Referer", "https://cloudorchestranova.com/")
                .build()
            val token = client.newCall(tokenReq).execute().use { it.body?.string() } ?: return@withContext null
            
            Log.i(TAG, "Fetched token: $token")
            
            val finalUrls = rawMasterUrls.split(" or ")
                .map { it.replace("__TOKEN__", token) }
                .filter { !it.contains("__TOKEN") }
                
            if (finalUrls.isEmpty()) {
                Log.w(TAG, "No final URLs after token replacement")
                return@withContext null
            }
            
            Log.i(TAG, "Found Final Stream URL: ${finalUrls[0]}")
            
            return@withContext StreamResult(
                url = finalUrls[0],
                referer = "https://cloudorchestranova.com/",
                headers = mapOf(
                    "Referer" to "https://cloudorchestranova.com/",
                    "Origin" to "https://cloudorchestranova.com"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "VsEmbed extraction failed", e)
            return@withContext null
        }
    }
}
