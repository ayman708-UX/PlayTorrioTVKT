package com.playtorrio.tv.data.reader

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Coil [ImageLoader] specialised for comic page images. Replaces the local
 * "comic-proxy" HTTP server from the Flutter port — image hosts hotlink-block
 * unless the request carries the right Referer + UA, so we add them here via
 * an OkHttp interceptor.
 */
object ComicImageLoader {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: build(context.applicationContext).also { loader = it }
    }

    /** Picks the right Referer for the host the image is being fetched from. */
    private fun refererFor(host: String?): String = when {
        host == null -> "https://readcomiconline.li/"
        host.contains("readcomicsonline.ru") -> "https://readcomicsonline.ru/"
        host.contains("rconet.biz") -> "https://readcomiconline.li/"
        host.contains("compsci88.com") -> "https://weebcentral.com/"
        host.contains("weebcentral.com") -> "https://weebcentral.com/"
        else -> "https://readcomiconline.li/"
    }

    private fun build(appContext: Context): ImageLoader {
        val ok = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val req = original.newBuilder()
                    .header("User-Agent", UA)
                    .header("Referer", refererFor(original.url.host))
                    .header(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Dest", "image")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .build()
                chain.proceed(req)
            })
            .build()

        return ImageLoader.Builder(appContext)
            .okHttpClient(ok)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("reader_image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
