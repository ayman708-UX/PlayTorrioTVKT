package com.playtorrio.tv.data.streaming

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val TAG = "StreamExtractor"

data class StreamResult(
    val url: String,
    val referer: String
)

object StreamExtractorService {

    enum class SourceType { WEBVIEW, HTTP_API, HTTP_SCRAPER }

    data class Source(
        val index: Int,
        val name: String,
        val referer: String,
        val type: SourceType = SourceType.WEBVIEW
    )

    val SOURCES = listOf(
        Source(1, "111Movies", "https://111movies.net/"),
        Source(2, "Videasy",   "https://player.videasy.net/"),
        Source(3, "Vidlink",   "https://vidlink.pro/"),
        Source(4, "RgShows",   "https://www.rgshows.ru/", SourceType.HTTP_API),
        Source(5, "4KHDHub",   "https://4khdhub.dad/",   SourceType.HTTP_SCRAPER),
        Source(6, "HDHub4u",   "https://new5.hdhub4u.fo/", SourceType.HTTP_SCRAPER),
        Source(7, "FlixerTV",  "https://theflixertv.to/",  SourceType.HTTP_SCRAPER),
        Source(8, "VsEmbed",   "https://vsembed.ru/"),
        Source(9, "Xpass",     "https://play.xpass.top/", SourceType.HTTP_SCRAPER)
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun buildUrl(sourceIdx: Int, tmdbId: Int, season: Int?, episode: Int?): String {
        val isMovie = season == null
        return when (sourceIdx) {
            1 -> if (isMovie) "https://111movies.net/movie/$tmdbId"
                 else "https://111movies.net/tv/$tmdbId/$season/$episode"
            2 -> if (isMovie) "https://player.videasy.net/movie/$tmdbId"
                 else "https://player.videasy.net/tv/$tmdbId/$season/$episode"
            3 -> if (isMovie) "https://vidlink.pro/movie/$tmdbId"
                 else "https://vidlink.pro/tv/$tmdbId/$season/$episode"
            4 -> if (isMovie) "https://api.rgshows.ru/main/movie/$tmdbId"
                 else "https://api.rgshows.ru/main/tv/$tmdbId/$season/$episode"
            8 -> if (isMovie) "https://vsembed.ru/embed/movie/$tmdbId"
                 else "https://vsembed.ru/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
            // 5 = 4KHDHub, 6 = HDHub4u: URLs built internally by WebStreamrService
            else -> throw IllegalArgumentException("Unknown source: $sourceIdx")
        }
    }

    private fun isStreamUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        if (url.contains("HLSPlayer") || url.contains("HLSFragment") ||
            url.contains("HLSLoader")  || url.contains("HLSDecrypter")) return false
        if (url.matches(Regex(""".*\.(wasm|mjs|js|css|html?|png|jpe?g|gif|svg|ico|json|xml)(\?.*)?$""",
                              RegexOption.IGNORE_CASE))) return false
        if (url.contains(".m3u8")) return true
        return false
    }

    private fun isStaticAsset(url: String): Boolean {
        return url.matches(Regex(""".*\.(wasm|mjs|js|css|html?|png|jpe?g|gif|svg|ico|json|xml|woff2?|ttf|eot)(\?.*)?$""",
                                 RegexOption.IGNORE_CASE))
    }

    suspend fun extract(
        context: Context,
        sourceIdx: Int,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        timeoutMs: Long = 25_000L
    ): StreamResult? {
        val source = SOURCES.find { it.index == sourceIdx } ?: return null
        return when (source.type) {
            SourceType.WEBVIEW      -> {
                // VsEmbed wraps its real player in a cloudnestra iframe behind
                // a sandbox that prevents the WebView from loading it. Resolve
                // the inner iframe URL ourselves and load it directly.
                if (sourceIdx == 8) {
                    val inner = resolveVsEmbedInner(buildUrl(sourceIdx, tmdbId, season, episode))
                    if (inner != null) {
                        val innerSource = source.copy(referer = "https://vsembed.ru/")
                        extractWebView(context, innerSource, inner, timeoutMs)
                    } else null
                } else {
                    extractWebView(context, source, buildUrl(sourceIdx, tmdbId, season, episode), timeoutMs)
                }
            }
            SourceType.HTTP_API     -> {
                // RgShows is behind Cloudflare and frequently hangs OkHttp's request
                // until the read timeout fires. Cap it aggressively so we don't waste
                // the user's time before falling back to other sources.
                val apiTimeout = if (sourceIdx == 4) 3_000L else timeoutMs
                withTimeoutOrNull(apiTimeout) {
                    extractHttpApi(source, sourceIdx, tmdbId, season, episode)
                }
            }
            SourceType.HTTP_SCRAPER -> withTimeoutOrNull(timeoutMs) {
                when (sourceIdx) {
                    5 -> WebStreamrService.extractFourKHDHub(tmdbId, season, episode)
                    6 -> WebStreamrService.extractHDHub4u(tmdbId, season, episode)
                    9 -> WebStreamrService.extractXpass(tmdbId, season, episode)
                    7 -> {
                        val embedResult = WebStreamrService.extractFlixerTVEmbed(tmdbId, season, episode)
                        if (embedResult != null) {
                            val embedSource = SOURCES.find { it.index == 7 }!!.copy(referer = embedResult.referer)
                            extractWebView(context, embedSource, embedResult.url, timeoutMs - 5_000L)
                        } else null
                    }
                    else -> null
                }
            }
        }
    }

    // ── VsEmbed iframe resolver ───────────────────────────────────────────────

    /**
     * vsembed.ru wraps a cloudnestra player in a sandboxed iframe. Loading the
     * outer page in the WebView fails to bootstrap the inner player. Fetch the
     * embed page over HTTP, scrape the cloudnestra iframe src, then optionally
     * dereference one more level (`/rcp/...` → `/prorcp/...`) so the WebView
     * lands on the actual player document.
     */
    private suspend fun resolveVsEmbedInner(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            val outer = httpClient.newCall(
                Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", ua)
                    .header("Referer", "https://vsembed.ru/")
                    .build()
            ).execute().use { it.body?.string() } ?: return@withContext null

            val iframeMatch = Regex("""<iframe[^>]*id=["']player_iframe["'][^>]*src=["']([^"']+)["']""")
                .find(outer) ?: return@withContext null
            val rawSrc = iframeMatch.groupValues[1]
            val rcpUrl = when {
                rawSrc.startsWith("//") -> "https:$rawSrc"
                rawSrc.startsWith("http") -> rawSrc
                else -> "https://cloudnestra.com$rawSrc"
            }
            Log.i(TAG, "[VsEmbed] Resolved rcp iframe → $rcpUrl")

            // Try to dereference one more level to /prorcp/... (the player itself)
            val rcp = httpClient.newCall(
                Request.Builder()
                    .url(rcpUrl)
                    .header("User-Agent", ua)
                    .header("Referer", "https://vsembed.ru/")
                    .build()
            ).execute().use { it.body?.string() }
            val proRcpMatch = rcp?.let {
                Regex("""src:\s*['"](/prorcp/[^'"]+)['"]""").find(it)
            }
            return@withContext if (proRcpMatch != null) {
                val pro = "https://cloudnestra.com" + proRcpMatch.groupValues[1]
                Log.i(TAG, "[VsEmbed] Resolved prorcp player → $pro")
                pro
            } else {
                rcpUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "[VsEmbed] iframe resolve failed: ${e.message}")
            null
        }
    }

    // ── HTTP-API extraction (no WebView) ──────────────────────────────────────

    private suspend fun extractHttpApi(
        source: Source,
        sourceIdx: Int,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): StreamResult? = withContext(Dispatchers.IO) {
        val apiUrl = buildUrl(sourceIdx, tmdbId, season, episode)
        Log.i(TAG, "[${source.name}] HTTP API → $apiUrl")
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Referer", source.referer)
                .header("Origin",  source.referer.trimEnd('/'))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                .build()
            val body = httpClient.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "[${source.name}] HTTP ${resp.code}")
                    return@withContext null
                }
                resp.body?.string()
            } ?: return@withContext null
            val streamUrl = JSONObject(body).getJSONObject("stream").getString("url")
            if (streamUrl.isBlank()) return@withContext null
            Log.i(TAG, "[${source.name}] ✅ Stream found via API: $streamUrl")
            StreamResult(streamUrl, source.referer)
        } catch (e: Exception) {
            Log.w(TAG, "[${source.name}] ❌ API failed: ${e.message}")
            null
        }
    }

    // ── WebView extraction ────────────────────────────────────────────────────

    /** Extract a stream from [pageUrl] using an embedded WebView. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractWebView(
        context: Context,
        source: Source,
        pageUrl: String,
        timeoutMs: Long
    ): StreamResult? = withContext(Dispatchers.Main) {
        Log.i(TAG, "[${source.name}] Starting extraction → $pageUrl")
        val resumed = AtomicBoolean(false)
        var webView: WebView? = null

        val result: StreamResult? = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->

                fun reportStream(streamUrl: String, referer: String) {
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        Log.i(TAG, "[${source.name}] ✅ Stream found: $streamUrl")
                        cont.resume(StreamResult(streamUrl, referer.ifBlank { source.referer }))
                    }
                }

                val jsInterface = object {
                    @JavascriptInterface
                    fun onStreamFound(foundUrl: String) {
                        val trimmed = foundUrl.trim()
                        if (isStreamUrl(trimmed)) {
                            Log.i(TAG, "[${source.name}] JS hook → stream: $trimmed")
                            reportStream(trimmed, source.referer)
                        }
                    }
                    @JavascriptInterface
                    fun onPlayerSource(foundUrl: String) {
                        // Called specifically from JWPlayer source extraction — trust it more
                        val trimmed = foundUrl.trim()
                        if (trimmed.startsWith("https://") && !isStaticAsset(trimmed)) {
                            Log.i(TAG, "[${source.name}] JWPlayer source → stream: $trimmed")
                            reportStream(trimmed, source.referer)
                        }
                    }
                }

                // Wipe cookies + localStorage so stale sessions don't serve wrong content
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()

                webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/133.0.0.0 Safari/537.36"
                    }

                    measure(
                        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, 800, 450)

                    addJavascriptInterface(jsInterface, "StreamSniffer")

                    webViewClient = object : WebViewClient() {

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail
                        ): Boolean {
                            Log.e(TAG, "[${source.name}] Renderer process gone (crash=${detail.didCrash()})")
                            if (resumed.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(null)
                            }
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val reqUrl = request.url.toString()
                            if (isStreamUrl(reqUrl)) {
                                Log.i(TAG, "[${source.name}] Intercepted stream: $reqUrl")
                                val referer =
                                    request.requestHeaders["Referer"]
                                    ?: request.requestHeaders["referer"]
                                    ?: source.referer
                                reportStream(reqUrl, referer)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            Log.i(TAG, "[${source.name}] Page finished: $url")
                            view.evaluateJavascript("""
                                (function() {
                                    // Hook XHR for m3u8 interception
                                    try {
                                        var _open = XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open = function(method, reqUrl) {
                                            if (reqUrl && /\.m3u8/i.test(String(reqUrl))) {
                                                try { window.StreamSniffer.onStreamFound(String(reqUrl)); } catch(e) {}
                                            }
                                            return _open.apply(this, arguments);
                                        };
                                        var _fetch = window.fetch;
                                        window.fetch = function(resource, init) {
                                            var u = typeof resource === 'string' ? resource
                                                    : (resource && resource.url ? resource.url : '');
                                            if (u && /\.m3u8/i.test(u)) {
                                                try { window.StreamSniffer.onStreamFound(u); } catch(e) {}
                                            }
                                            return _fetch.apply(this, arguments);
                                        };
                                    } catch(e) {}

                                    // Read JWPlayer source directly (works even with autoplay=0)
                                    function tryJwPlayer() {
                                        try {
                                            var jw = window.jwplayer;
                                            if (!jw) return false;
                                            // Try all player instances
                                            var ids = ['megacloud-player', 'player', 'jwplayer-container'];
                                            for (var i = 0; i < ids.length; i++) {
                                                try {
                                                    var p = jw(ids[i]);
                                                    if (!p) continue;
                                                    // Try to get source from playlist item
                                                    var item = p.getPlaylistItem ? p.getPlaylistItem() : null;
                                                    if (item) {
                                                        if (item.file && /https?:/.test(item.file)) {
                                                            window.StreamSniffer.onPlayerSource(item.file);
                                                            return true;
                                                        }
                                                        if (item.sources) {
                                                            for (var s = 0; s < item.sources.length; s++) {
                                                                var f = item.sources[s].file || item.sources[s].src;
                                                                if (f && /https?:/.test(f)) {
                                                                    window.StreamSniffer.onPlayerSource(f);
                                                                    return true;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    // Force play to trigger m3u8 fetch
                                                    try { p.play(); } catch(er) {}
                                                    // Hook on play to capture stream
                                                    try {
                                                        p.on('firstFrame levelsChanged', function() {
                                                            try {
                                                                var it = p.getPlaylistItem();
                                                                var src = it && (it.file || (it.sources && it.sources[0] && (it.sources[0].file || it.sources[0].src)));
                                                                if (src) window.StreamSniffer.onPlayerSource(src);
                                                            } catch(er) {}
                                                        });
                                                    } catch(er) {}
                                                } catch(er) {}
                                            }
                                        } catch(e) {}
                                        return false;
                                    }

                                    function clickInDoc(doc, win) {
                                        try {
                                            var cx = (win.innerWidth || 800) / 2;
                                            var cy = (win.innerHeight || 450) / 2;
                                            var el = doc.elementFromPoint(cx, cy);
                                            if (el) {
                                                try { el.click(); } catch(er) {}
                                                // Synthesize a real mouse event sequence
                                                try {
                                                    ['mousedown','mouseup','click'].forEach(function(type) {
                                                        var ev = new win.MouseEvent(type, {
                                                            bubbles: true, cancelable: true, view: win,
                                                            clientX: cx, clientY: cy, button: 0
                                                        });
                                                        el.dispatchEvent(ev);
                                                    });
                                                } catch(er) {}
                                                try {
                                                    ['pointerdown','pointerup'].forEach(function(type) {
                                                        var ev = new win.PointerEvent(type, {
                                                            bubbles: true, cancelable: true, view: win,
                                                            clientX: cx, clientY: cy, button: 0,
                                                            pointerType: 'mouse', isPrimary: true
                                                        });
                                                        el.dispatchEvent(ev);
                                                    });
                                                } catch(er) {}
                                                try {
                                                    ['touchstart','touchend'].forEach(function(type) {
                                                        var ev = new win.TouchEvent(type, {
                                                            bubbles: true, cancelable: true, view: win
                                                        });
                                                        el.dispatchEvent(ev);
                                                    });
                                                } catch(er) {}
                                            }
                                            doc.querySelectorAll(
                                                'video, button, [role=button], .play-btn, #play, ' +
                                                '.vjs-big-play-button, .jw-display-icon-container, ' +
                                                '.jw-icon-display, .jw-media, #megacloud-player, ' +
                                                '#pl_but, .play, .play_button, .player-container'
                                            ).forEach(function(e) {
                                                try { e.click(); if (e.tagName === 'VIDEO') e.play(); } catch(er) {}
                                            });
                                        } catch(e) {}
                                    }

                                    function walkFrames(win) {
                                        try {
                                            clickInDoc(win.document, win);
                                        } catch(e) {}
                                        try {
                                            var frames = win.frames;
                                            for (var i = 0; i < frames.length; i++) {
                                                try { walkFrames(frames[i]); } catch(er) {}
                                            }
                                        } catch(e) {}
                                    }

                                    function tryClick() {
                                        try {
                                            if (tryJwPlayer()) return;
                                            walkFrames(window);
                                        } catch(e) {}
                                        setTimeout(tryClick, 1000);
                                    }
                                    setTimeout(tryClick, 500);
                                })();
                            """, null)
                        }
                    }

                    loadUrl(pageUrl, mapOf("Referer" to source.referer))
                }

                // Periodically dispatch a real touch to the WebView center so cross-origin
                // iframe players (e.g. cloudnestra inside vsembed) actually start playback.
                val wv = webView!!
                val tapRunnable = object : Runnable {
                    override fun run() {
                        if (resumed.get() || !cont.isActive) return
                        try {
                            val cx = (wv.width.takeIf { it > 0 } ?: 800).toFloat() / 2f
                            val cy = (wv.height.takeIf { it > 0 } ?: 450).toFloat() / 2f
                            val now = SystemClock.uptimeMillis()
                            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
                            val up   = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP,   cx, cy, 0)
                            wv.dispatchTouchEvent(down)
                            wv.dispatchTouchEvent(up)
                            down.recycle(); up.recycle()
                        } catch (_: Exception) {}
                        wv.postDelayed(this, 1500)
                    }
                }
                wv.postDelayed(tapRunnable, 1500)

                cont.invokeOnCancellation {
                    webView?.apply { stopLoading(); destroy() }
                    webView = null
                }
            }
        }

        webView?.apply { stopLoading(); clearCache(true); destroy() }
        webView = null
        if (result == null) Log.w(TAG, "[${source.name}] ❌ Timed out / no stream found")
        result
    }
}

