package com.playtorrio.tv.core.scraper

import android.util.Log
import com.playtorrio.tv.core.network.CdnHeaderResolver
import com.playtorrio.tv.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Fast pre-flight HTTP liveness check for PlayTorrioHTTP streams.
 * Probes the stream URL with resolved CDN headers (User-Agent, Referer, Origin)
 * and verifies that it does not return HTTP 403 Forbidden, 404 Not Found, 5xx server errors,
 * or connection timeouts before presenting it on the UI.
 */
@Singleton
class HttpStreamLivenessValidator @Inject constructor() {

    companion object {
        private const val TAG = "HttpStreamValidator"
        private const val TIMEOUT_SECONDS = 4L
    }

    private val httpClient: OkHttpClient by lazy {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Checks whether the HTTP stream is currently reachable and serving media content.
     * Returns true if status is 200..399, false for 403 Forbidden, 404, 5xx, or network errors.
     */
    suspend fun isStreamAlive(url: String, headers: Map<String, String>? = null): Boolean =
        withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()
            if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
                !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext false
            }

            val resolvedHeaders = CdnHeaderResolver.resolveStreamHeaders(trimmedUrl, headers)
            val requestBuilder = Request.Builder()
                .url(trimmedUrl)
                .get()
                .header("Range", "bytes=0-1024")
                .header("Accept", "*/*")

            resolvedHeaders.forEach { (key, value) ->
                if (!key.equals("Range", ignoreCase = true) && !key.equals("Accept", ignoreCase = true)) {
                    requestBuilder.header(key, value)
                }
            }

            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val code = response.code
                    val isAlive = code in 200..399
                    if (!isAlive) {
                        Log.d(TAG, "Stream rejected [HTTP $code]: $trimmedUrl")
                    }
                    isAlive
                }
            } catch (e: Exception) {
                Log.d(TAG, "Stream probe failed (${e.javaClass.simpleName}): $trimmedUrl - ${e.message}")
                false
            }
        }
}
