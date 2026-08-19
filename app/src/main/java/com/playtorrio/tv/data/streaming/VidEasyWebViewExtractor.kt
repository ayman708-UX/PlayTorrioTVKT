package com.playtorrio.tv.data.streaming

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object VidEasyWebViewExtractor {
    private const val TAG = "VidEasyWebViewExtractor"
    
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(
        context: Context,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): StreamResult? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isFinished = false
            val webView = WebView(context)
            
            fun finish(result: StreamResult?) {
                if (!isFinished) {
                    isFinished = true
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try { webView.destroy() } catch (e: Exception) {}
                    }
                }
            }
            
            val isMovie = season == null
            val targetUrl = if (isMovie) {
                "https://www.cineby.at/movie/$tmdbId?play=true"
            } else {
                "https://www.cineby.at/tv/$tmdbId/$season/$episode?play=true"
            }
            
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            }
            
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    
                    if (url.contains(".m3u8") || url.contains(".mp4")) {
                        Log.i(TAG, "Found stream URL: $url")
                        val headers = request.requestHeaders ?: emptyMap()
                        val referer = headers["Referer"] ?: headers["referer"] ?: "https://www.cineby.at/"
                        
                        finish(
                            StreamResult(
                                url = url,
                                referer = referer,
                                headers = headers
                            )
                        )
                    }
                    
                    return super.shouldInterceptRequest(view, request)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val js = """
                        setTimeout(function() {
                            var btn = document.querySelector('button, .play-btn, #play');
                            if (btn) btn.click();
                            document.body.click();
                        }, 2000);
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
            
            continuation.invokeOnCancellation {
                finish(null)
            }
            
            Log.i(TAG, "Loading URL in WebView: $targetUrl")
            webView.loadUrl(targetUrl)
        }
    }
}
