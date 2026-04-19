package com.playtorrio.tv.data.trailer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

@UnstableApi
class YoutubeChunkedDataSourceFactory(
    private val clientUserAgent: String? = null,
    private val chunkSizeBytes: Long = 10L * 1024 * 1024
) : DataSource.Factory {

    companion object {
        private const val DEFAULT_YT_USER_AGENT =
            "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip"
    }

    override fun createDataSource(): DataSource {
        val ua = clientUserAgent ?: DEFAULT_YT_USER_AGENT
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setDefaultRequestProperties(mapOf(
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        return YoutubeChunkedDataSource(upstream, chunkSizeBytes)
    }

    private class YoutubeChunkedDataSource(
        private val upstream: DefaultHttpDataSource,
        private val chunkSize: Long
    ) : DataSource {

        private var isYouTubeStream = false
        private var totalContentLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
        private var originalDataSpec: DataSpec? = null

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val host = dataSpec.uri.host.orEmpty()
            isYouTubeStream = host.contains("googlevideo.com")
            if (!isYouTubeStream) return upstream.open(dataSpec)

            originalDataSpec = dataSpec
            currentChunkStart = dataSpec.position
            totalContentLength = dataSpec.length
            return openNextChunk()
        }

        private fun openNextChunk(): Long {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val end = if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                minOf(currentChunkStart + chunkSize - 1, currentChunkStart + totalContentLength - 1)
            } else {
                currentChunkStart + chunkSize - 1
            }
            currentChunkEnd = end

            val rangedUri = spec.uri.buildUpon()
                .appendQueryParameter("range", "$currentChunkStart-$currentChunkEnd")
                .build()

            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()

            bytesReadInChunk = 0
            upstream.open(chunkedSpec)
            return if (totalContentLength != C.LENGTH_UNSET.toLong()) totalContentLength else C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isYouTubeStream) return upstream.read(buffer, offset, length)

            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                val received = bytesReadInChunk
                upstream.close()
                if (received < (currentChunkEnd - currentChunkStart + 1)) return C.RESULT_END_OF_INPUT

                currentChunkStart += received
                if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                    totalContentLength -= received
                    if (totalContentLength <= 0) return C.RESULT_END_OF_INPUT
                }
                openNextChunk()
                return upstream.read(buffer, offset, length)
            }

            bytesReadInChunk += bytesRead
            return bytesRead
        }

        override fun getUri(): Uri? = upstream.uri
        override fun close() {
            upstream.close()
            originalDataSpec = null
        }
    }
}
