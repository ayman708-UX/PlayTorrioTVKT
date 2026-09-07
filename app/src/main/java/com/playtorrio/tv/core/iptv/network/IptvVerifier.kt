package com.playtorrio.tv.core.iptv.network

import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object IptvVerifier {
    private const val PARALLEL = 12

    suspend fun verifyUntil(
        portals: List<IptvPortal>,
        target: Int = 5,
        onProgress: ((checked: Int, total: Int, alive: Int) -> Unit)? = null,
        onAlive: ((VerifiedPortal) -> Unit)? = null,
        onAttempted: ((IptvPortal) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): List<VerifiedPortal> = withContext(Dispatchers.IO) {
        if (portals.isEmpty()) return@withContext emptyList()

        val aliveList = java.util.Collections.synchronizedList(mutableListOf<VerifiedPortal>())
        val checkedCounter = AtomicInteger(0)
        val stopped = AtomicBoolean(false)
        val semaphore = Semaphore(PARALLEL)

        kotlinx.coroutines.coroutineScope {
            val jobs = portals.map { portal ->
                launch {
                    if (stopped.get() || isCancelled?.invoke() == true) return@launch
                    if (aliveList.size >= target) {
                        stopped.set(true)
                        return@launch
                    }

                    semaphore.withPermit {
                        if (stopped.get() || isCancelled?.invoke() == true) return@withPermit
                        onAttempted?.invoke(portal)

                        val v = try {
                            IptvClient.verifyOrNull(portal)
                        } catch (_: Exception) {
                            null
                        }

                        val checked = checkedCounter.incrementAndGet()
                        if (v != null && aliveList.size < target) {
                            aliveList.add(v)
                            onAlive?.invoke(v)
                        }

                        onProgress?.invoke(checked, portals.size, aliveList.size)
                        if (aliveList.size >= target) {
                            stopped.set(true)
                            coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
                        }
                    }
                }
            }
            jobs.joinAll()
        }

        aliveList.toList()
    }
}
