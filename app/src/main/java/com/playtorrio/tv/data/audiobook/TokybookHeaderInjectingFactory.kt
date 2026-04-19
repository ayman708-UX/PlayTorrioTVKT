package com.playtorrio.tv.data.audiobook

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Wraps an upstream HttpDataSource.Factory and injects the per-request `x-track-src`
 * header that tokybook.com requires for every chunk fetch (m3u8 + .ts segments).
 *
 * The header value is the URL-encoded path of the request itself, e.g.
 * for GET https://tokybook.com/api/v1/public/audio/Some Folder/file.ts
 *   x-track-src: /api/v1/public/audio/Some%20Folder/file.ts
 *
 * For non-tokybook hosts (audiozaic, golden, …) this is a no-op pass-through.
 */
@UnstableApi
class TokybookHeaderInjectingFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        TokybookHeaderInjectingDataSource(upstream.createDataSource())
}

@UnstableApi
private class TokybookHeaderInjectingDataSource(
    private val inner: DataSource,
) : DataSource by inner {

    override fun open(dataSpec: DataSpec): Long {
        val host = dataSpec.uri.host
        if (host != "tokybook.com") return inner.open(dataSpec)

        // `Uri.path` returns the percent-decoded path, so re-encode each segment.
        val rawPath = dataSpec.uri.path ?: return inner.open(dataSpec)
        val encodedPath = rawPath.split('/').joinToString("/") { Uri.encode(it) }

        val mergedHeaders = HashMap(dataSpec.httpRequestHeaders).apply {
            put("x-track-src", encodedPath)
        }
        val rewritten = dataSpec.buildUpon().setHttpRequestHeaders(mergedHeaders).build()
        return inner.open(rewritten)
    }
}
