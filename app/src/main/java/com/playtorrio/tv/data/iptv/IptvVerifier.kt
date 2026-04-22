package com.playtorrio.tv.data.iptv

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Verifies portals with bounded concurrency, stopping early once [target]
 * portals have authenticated successfully. Tuned conservatively (4 parallel)
 * for Android TV boxes.
 */
object IptvVerifier {
    private const val TAG = "IptvVerifier"
    private const val PARALLEL = 4

    /**
     * @param onProgress (checked, total, aliveSoFar)
     */
    suspend fun verifyUntil(
        portals: List<IptvPortal>,
        target: Int = 5,
        onProgress: (checked: Int, total: Int, alive: Int) -> Unit = { _, _, _ -> },
        onAlive: (VerifiedPortal) -> Unit = {},
        onAttempted: (IptvPortal) -> Unit = {},
    ): List<VerifiedPortal> = withContext(Dispatchers.IO) {
        if (portals.isEmpty()) return@withContext emptyList()

        val mutex = Mutex()
        var nextIdx = 0
        var checked = 0
        val alive = mutableListOf<VerifiedPortal>()

        try {
            coroutineScope {
                val workers = (0 until PARALLEL.coerceAtMost(portals.size)).map {
                    async {
                        while (isActive) {
                            // claim next index
                            val idx = mutex.withLock {
                                if (alive.size >= target) -1 else nextIdx++
                            }
                            if (idx < 0 || idx >= portals.size) break
                            onAttempted(portals[idx])
                            val v = runCatching {
                                IptvClient.verifyOrNull(portals[idx])
                            }.getOrNull()
                            Log.d(TAG, "verify[$idx] ${portals[idx].url} ${portals[idx].username} -> ${if (v != null) "OK" else "FAIL"}")

                            mutex.withLock {
                                checked++
                                if (v != null && alive.size < target) {
                                    alive += v
                                    onAlive(v)
                                }
                                onProgress(checked, portals.size, alive.size)
                                if (alive.size >= target) {
                                    // signal siblings to stop
                                    this@coroutineScope.cancel()
                                }
                            }
                        }
                    }
                }
                workers.awaitAll()
            }
        } catch (_: CancellationException) {
            // expected when target reached
        }

        alive.toList()
    }
}
