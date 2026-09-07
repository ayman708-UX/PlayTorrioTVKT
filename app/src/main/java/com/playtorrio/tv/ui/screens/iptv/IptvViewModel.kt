package com.playtorrio.tv.ui.screens.iptv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.iptv.channels.HardcodedChannel
import com.playtorrio.tv.core.iptv.channels.HardcodedChannels
import com.playtorrio.tv.core.iptv.model.AliveProgress
import com.playtorrio.tv.core.iptv.model.CatalogSource
import com.playtorrio.tv.core.iptv.model.ChannelHit
import com.playtorrio.tv.core.iptv.model.EpgEntry
import com.playtorrio.tv.core.iptv.model.IptvCategory
import com.playtorrio.tv.core.iptv.model.IptvEpisode
import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.IptvSection
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.model.M3uPlaylist
import com.playtorrio.tv.core.iptv.model.ScrapePage
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import com.playtorrio.tv.core.iptv.network.IptvAliveChecker
import com.playtorrio.tv.core.iptv.network.IptvClient
import com.playtorrio.tv.core.iptv.network.IptvScraper
import com.playtorrio.tv.core.iptv.network.IptvVerifier
import com.playtorrio.tv.core.iptv.network.M3uParser
import com.playtorrio.tv.core.iptv.storage.IptvStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class IptvViewModel @Inject constructor(
    private val storage: IptvStorage
) : ViewModel() {
    companion object {
        private const val TAG = "IptvViewModel"
    }

    private val _scrapeSource = MutableStateFlow(CatalogSource.CLOUD_VAULT)
    val scrapeSource: StateFlow<CatalogSource> = _scrapeSource.asStateFlow()

    fun setScrapeSource(source: CatalogSource) {
        if (_scrapeSource.value != source) {
            _scrapeSource.value = source
            scrapePortals(reset = true)
        }
    }

    fun toggleScrapeSource() {
        val next = if (_scrapeSource.value == CatalogSource.CLOUD_VAULT) CatalogSource.REDDIT else CatalogSource.CLOUD_VAULT
        setScrapeSource(next)
    }

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _verifiedPortals = MutableStateFlow<List<VerifiedPortal>>(emptyList())
    val verifiedPortals: StateFlow<List<VerifiedPortal>> = _verifiedPortals.asStateFlow()

    private val _favoritePortalKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoritePortalKeys: StateFlow<Set<String>> = _favoritePortalKeys.asStateFlow()

    private val _m3uPlaylists = MutableStateFlow<List<M3uPlaylist>>(emptyList())
    val m3uPlaylists: StateFlow<List<M3uPlaylist>> = _m3uPlaylists.asStateFlow()

    // ── Channel Scan State ──
    private val _activeHardcoded = MutableStateFlow<HardcodedChannel?>(null)
    val activeHardcoded: StateFlow<HardcodedChannel?> = _activeHardcoded.asStateFlow()

    private val _channelHits = MutableStateFlow<List<ChannelHit>>(emptyList())
    val channelHits: StateFlow<List<ChannelHit>> = _channelHits.asStateFlow()

    private val _isScanningChannel = MutableStateFlow(false)
    val isScanningChannel: StateFlow<Boolean> = _isScanningChannel.asStateFlow()

    private val _channelScanStatus = MutableStateFlow("")
    val channelScanStatus: StateFlow<String> = _channelScanStatus.asStateFlow()

    private var channelScanJob: Job? = null
    private var scrapeJob: Job? = null

    // ── Portal Browser State ──
    private val _browserCategories = MutableStateFlow<List<IptvCategory>>(emptyList())
    val browserCategories: StateFlow<List<IptvCategory>> = _browserCategories.asStateFlow()

    private val _browserStreams = MutableStateFlow<List<IptvStream>>(emptyList())
    val browserStreams: StateFlow<List<IptvStream>> = _browserStreams.asStateFlow()

    private val _isBrowserLoading = MutableStateFlow(false)
    val isBrowserLoading: StateFlow<Boolean> = _isBrowserLoading.asStateFlow()

    private val _browserSelectedCategoryId = MutableStateFlow("")
    val browserSelectedCategoryId: StateFlow<String> = _browserSelectedCategoryId.asStateFlow()

    private val _browserAliveIds = MutableStateFlow<Set<String>>(emptySet())
    val browserAliveIds: StateFlow<Set<String>> = _browserAliveIds.asStateFlow()

    private val _isAliveChecking = MutableStateFlow(false)
    val isAliveChecking: StateFlow<Boolean> = _isAliveChecking.asStateFlow()

    private val _aliveProgress = MutableStateFlow(AliveProgress(0, 0, 0))
    val aliveProgress: StateFlow<AliveProgress> = _aliveProgress.asStateFlow()

    private val _epgMap = ConcurrentHashMap<String, List<EpgEntry>>()
    private val _streamUrlCache = ConcurrentHashMap<String, String>()

    private var scrapeAfter: String? = null
    private val verifiedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val _attemptedKeys = mutableSetOf<String>()
    private val _pendingPortals = mutableListOf<IptvPortal>()
    private val _pendingKeys = mutableSetOf<String>()
    private val _channelAttemptedPortals = ConcurrentHashMap<String, MutableSet<String>>()
    private var _canGetMore = MutableStateFlow(false)
    val canGetMore: StateFlow<Boolean> = _canGetMore.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = storage.loadVerifiedPortals()
            val favs = storage.loadFavoritePortalKeys()
            _favoritePortalKeys.value = favs
            _verifiedPortals.value = sortFavoritesFirst(stored, favs)
            verifiedKeys.addAll(stored.map { it.credKey })
            _m3uPlaylists.value = storage.loadM3uPlaylists()

            val cachedCandidates = storage.loadCandidatePortals()
            _pendingPortals.addAll(cachedCandidates.filter { !verifiedKeys.contains(it.credKey) })
            _pendingKeys.addAll(_pendingPortals.map { it.credKey })
            _canGetMore.value = _pendingPortals.isNotEmpty()

            // Auto-scrape if no portals available
            if (stored.isEmpty()) {
                scrapePortals(reset = true)
            }
        }
    }

    private fun sortFavoritesFirst(list: List<VerifiedPortal>, favs: Set<String>): List<VerifiedPortal> {
        val f = list.filter { favs.contains(it.key) }
        val r = list.filter { !favs.contains(it.key) }
        return f + r
    }

    fun toggleFavoritePortal(key: String) {
        viewModelScope.launch {
            val favs = _favoritePortalKeys.value.toMutableSet()
            if (favs.contains(key)) favs.remove(key) else favs.add(key)
            _favoritePortalKeys.value = favs
            storage.saveFavoritePortalKeys(favs)
            _verifiedPortals.value = sortFavoritesFirst(_verifiedPortals.value, favs)
        }
    }

    fun deletePortal(portal: VerifiedPortal) {
        viewModelScope.launch {
            val list = _verifiedPortals.value.filter { it.key != portal.key }
            _verifiedPortals.value = list
            verifiedKeys.remove(portal.credKey)
            storage.saveVerifiedPortals(list)
        }
    }

    fun deletePortals(keys: Set<String>) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val toDelete = _verifiedPortals.value.filter { keys.contains(it.key) }
            val list = _verifiedPortals.value.filter { !keys.contains(it.key) }
            _verifiedPortals.value = list
            toDelete.forEach { verifiedKeys.remove(it.credKey) }
            storage.saveVerifiedPortals(list)
        }
    }

    fun deleteAllPortals() {
        viewModelScope.launch {
            _verifiedPortals.value = emptyList()
            verifiedKeys.clear()
            storage.saveVerifiedPortals(emptyList())
        }
    }

    fun deleteChannelHits(channelId: String, streamUrls: Set<String>) {
        if (streamUrls.isEmpty()) return
        viewModelScope.launch {
            val remaining = _channelHits.value.filter { !streamUrls.contains(it.streamUrl) }
            _channelHits.value = remaining
            storage.saveChannelHits(channelId, remaining)
        }
    }

    fun clearAllChannelHits(channelId: String) {
        viewModelScope.launch {
            _channelHits.value = emptyList()
            storage.clearChannelHits(channelId)
        }
    }

    fun addManualPortal(url: String, user: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val p = IptvPortal(url = url.trim(), username = user.trim(), password = pass.trim(), source = "Manual")
            val v = IptvClient.verifyOrNull(p, 8)
            if (v != null) {
                val list = _verifiedPortals.value.toMutableList()
                list.removeAll { it.credKey == v.credKey }
                list.add(0, v)
                _verifiedPortals.value = sortFavoritesFirst(list, _favoritePortalKeys.value)
                verifiedKeys.add(v.credKey)
                storage.saveVerifiedPortals(list)
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun scrapePortals(reset: Boolean = false) {
        if (_isScraping.value) return
        scrapeJob?.cancel()
        scrapeJob = viewModelScope.launch {
            _isScraping.value = true
            _canGetMore.value = false
            _statusText.value = "Finding live portals…"

            if (reset) {
                scrapeAfter = null
                _pendingPortals.clear()
                _pendingKeys.clear()
                _attemptedKeys.clear()
                storage.saveCandidatePortals(emptyList())
            }

            scrapeAndVerify()
        }
    }

    fun getMorePortals() {
        if (_isScraping.value) return
        scrapeJob?.cancel()
        scrapeJob = viewModelScope.launch {
            _isScraping.value = true
            _statusText.value = "Searching for more…"
            scrapeAndVerify()
        }
    }

    private suspend fun scrapeAndVerify() {
        val targetAlive = 5
        val maxPagesPerPress = 40
        val newAlive = mutableListOf<VerifiedPortal>()
        var lastPage: ScrapePage? = null
        var pagesTried = 0
        var exhausted = false

        try {
            while (newAlive.size < targetAlive && pagesTried < maxPagesPerPress) {
                // If pending pool is empty, fetch ONE page at a time
                while (_pendingPortals.isEmpty() && pagesTried < maxPagesPerPress) {
                    pagesTried++
                    _statusText.value = "Discovering candidate portals (batch $pagesTried)…"

                    val page = IptvScraper.scrapeCatalogPage(
                        maxResults = 50,
                        after = scrapeAfter,
                        source = _scrapeSource.value
                    )
                    lastPage = page
                    scrapeAfter = page.nextAfter

                    for (p in page.portals) {
                        if (verifiedKeys.contains(p.credKey)) continue
                        if (_attemptedKeys.contains(p.credKey)) continue
                        if (_pendingKeys.contains(p.credKey)) continue
                        _pendingKeys.add(p.credKey)
                        _pendingPortals.add(p)
                    }

                    storage.saveCandidatePortals(_pendingPortals)

                    if (_pendingPortals.isEmpty() && !page.hasMore) {
                        exhausted = true
                        break
                    }
                }

                if (_pendingPortals.isEmpty()) break

                val remaining = targetAlive - newAlive.size
                _statusText.value = "Checking ${_pendingPortals.size} portals · need $remaining more"

                val snapshot = ArrayList(_pendingPortals)
                val attemptedInBatch = java.util.Collections.synchronizedSet(mutableSetOf<String>())
                IptvVerifier.verifyUntil(
                    portals = snapshot,
                    target = remaining,
                    onAttempted = { p ->
                        attemptedInBatch.add(p.credKey)
                    },
                    onProgress = { c, t, a ->
                        val total = newAlive.size + a
                        _statusText.value = "Checking $c / $t · Active: $total / $targetAlive"
                    },
                    onAlive = { v ->
                        if (verifiedKeys.add(v.credKey)) {
                            newAlive.add(v)
                            val current = _verifiedPortals.value.toMutableList()
                            current.add(v)
                            _verifiedPortals.value = sortFavoritesFirst(current, _favoritePortalKeys.value)
                        }
                    }
                )

                // Remove attempted portals from pending (safe on main coroutine)
                _attemptedKeys.addAll(attemptedInBatch)
                _pendingPortals.removeAll { attemptedInBatch.contains(it.credKey) }
                _pendingKeys.removeAll(attemptedInBatch)
                storage.saveCandidatePortals(_pendingPortals)

                if (newAlive.size < targetAlive &&
                    _pendingPortals.isEmpty() &&
                    (lastPage == null || !lastPage.hasMore)) {
                    exhausted = true
                    break
                }
            }

            if (newAlive.isNotEmpty()) {
                storage.saveVerifiedPortals(_verifiedPortals.value)
            }

            _canGetMore.value = _pendingPortals.isNotEmpty() ||
                    (lastPage?.hasMore ?: _canGetMore.value)

            if (newAlive.isEmpty()) {
                _statusText.value = if (exhausted) {
                    "No live portals found in this source."
                } else if (_canGetMore.value) {
                    "No new live portals found. Tap Get More."
                } else {
                    "No new live portals found."
                }
            } else {
                val msg = if (newAlive.size >= targetAlive) {
                    "Found ${newAlive.size} live portals."
                } else {
                    "Found ${newAlive.size} live portals${if (exhausted) " (source exhausted)." else " (stopped early)."}"
                }
                _statusText.value = if (_pendingPortals.isNotEmpty()) {
                    "$msg (${_pendingPortals.size} more cached)"
                } else msg
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scrape failed: ${e.message}")
            _statusText.value = "Scrape failed: ${e.message}"
        } finally {
            _isScraping.value = false
        }
    }

    fun stopChannelScan() {
        channelScanJob?.cancel()
        channelScanJob = null
        _isScanningChannel.value = false
        _channelScanStatus.value = "Stopped."
    }

    fun openChannel(channel: HardcodedChannel) {
        _activeHardcoded.value = channel
        channelScanJob?.cancel()

        viewModelScope.launch {
            val cachedHits = storage.loadChannelHits(channel.id)
            val hitsMap = ConcurrentHashMap<String, ChannelHit>()
            cachedHits.forEach { hitsMap[it.streamUrl] = it }
            _channelHits.value = hitsMap.values.toList()

            // If no cached hits, scan first 5 portals
            if (cachedHits.isEmpty()) {
                scanChannel(channel, resetAttempted = false)
            } else {
                _channelScanStatus.value = "${cachedHits.size} cached feeds available."
            }
        }
    }

    fun scanChannel(channel: HardcodedChannel, resetAttempted: Boolean = false) {
        channelScanJob?.cancel()
        channelScanJob = viewModelScope.launch {
            _isScanningChannel.value = true
            _activeHardcoded.value = channel

            val attempted = _channelAttemptedPortals.getOrPut(channel.id) { ConcurrentHashMap.newKeySet() }
            if (resetAttempted) {
                attempted.clear()
            }

            val hitsMap = ConcurrentHashMap<String, ChannelHit>()
            _channelHits.value.forEach { hitsMap[it.streamUrl] = it }

            var allPortals = _verifiedPortals.value
            if (allPortals.isEmpty()) {
                _channelScanStatus.value = "Discovering live portals…"
                try {
                    val page = IptvScraper.scrapeCatalogPage(maxResults = 50, after = null)
                    val newPortals = page.portals.filter { !verifiedKeys.contains(it.credKey) }
                    val alive = IptvVerifier.verifyUntil(
                        portals = newPortals,
                        target = 5,
                        onAlive = { v ->
                            val current = _verifiedPortals.value.toMutableList()
                            if (current.none { it.credKey == v.credKey }) {
                                current.add(v)
                                verifiedKeys.add(v.credKey)
                                _verifiedPortals.value = sortFavoritesFirst(current, _favoritePortalKeys.value)
                            }
                        }
                    )
                    storage.saveVerifiedPortals(_verifiedPortals.value)
                    allPortals = _verifiedPortals.value
                } catch (e: Exception) {
                    Log.d(TAG, "Bootstrap failed: ${e.message}")
                }
            }

            val unattempted = allPortals.filter { !attempted.contains(it.key) }
            val toScan = if (unattempted.isNotEmpty()) {
                unattempted.take(8)
            } else if (resetAttempted) {
                allPortals.take(8)
            } else {
                emptyList()
            }

            if (toScan.isEmpty()) {
                _channelScanStatus.value = if (_channelHits.value.isEmpty()) {
                    "All ${allPortals.size} portals scanned. No streams found."
                } else {
                    "All ${allPortals.size} portals scanned (${_channelHits.value.size} streams found)."
                }
                _isScanningChannel.value = false
                return@launch
            }

            toScan.forEach { attempted.add(it.key) }

            _channelScanStatus.value = "Scanning ${toScan.size} portals for '${channel.name}'…"

            data class Candidate(val portal: VerifiedPortal, val stream: IptvStream, val url: String)

            val candidateTasks = toScan.map { portal ->
                async(Dispatchers.IO) {
                    try {
                        val streams = IptvClient.streams(portal.portal, IptvSection.LIVE, "")
                        streams.filter { s ->
                            HardcodedChannels.matches(s.name, channel.keywords, channel.exclude)
                        }.map { s ->
                            Candidate(portal, s, IptvClient.streamUrl(portal.portal, s))
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            val candidateLists = candidateTasks.awaitAll()
            val newCandidates = mutableListOf<Candidate>()
            val seenUrls = mutableSetOf<String>()
            for (list in candidateLists) {
                for (c in list) {
                    if (c.url.isNotEmpty() && !hitsMap.containsKey(c.url) && seenUrls.add(c.url)) {
                        newCandidates.add(c)
                    }
                }
            }

            if (newCandidates.isEmpty()) {
                val remainingPortals = allPortals.count { !attempted.contains(it.key) }
                _channelScanStatus.value = if (_channelHits.value.isEmpty()) {
                    "No matching channels found in ${toScan.size} portals ($remainingPortals remaining). Try Scan More."
                } else {
                    "${_channelHits.value.size} alive · no new matches ($remainingPortals remaining)."
                }
                _isScanningChannel.value = false
                return@launch
            }

            _channelScanStatus.value = "Found ${newCandidates.size} candidate feeds · checking health…"

            val candidateByUrl = newCandidates.associateBy { it.url }
            IptvAliveChecker.launchCheck(
                streams = newCandidates.map { it.url to it.url },
                onResult = { url, alive ->
                    if (alive) {
                        val c = candidateByUrl[url] ?: return@launchCheck
                        if (!hitsMap.containsKey(url)) {
                            val hit = ChannelHit(portal = c.portal, stream = c.stream, streamUrl = c.url)
                            hitsMap[url] = hit
                            _channelHits.value = hitsMap.values.toList()
                            storage.saveChannelHits(channel.id, hitsMap.values.toList())
                        }
                    }
                },
                onProgress = { p ->
                    _channelScanStatus.value = "Sniffing ${p.checked}/${p.total} · ${p.alive} alive"
                },
                onDone = {
                    val remainingPortals = allPortals.count { !attempted.contains(it.key) }
                    _channelScanStatus.value = "Found ${_channelHits.value.size} active feeds ($remainingPortals portals remaining)"
                },
                isCancelled = { !isActive }
            )
            _isScanningChannel.value = false
        }
    }

    fun loadPortalCategories(portal: VerifiedPortal, section: IptvSection) {
        viewModelScope.launch {
            _isBrowserLoading.value = true
            try {
                val cats = IptvClient.categories(portal.portal, section)
                _browserCategories.value = cats
                if (cats.isNotEmpty()) {
                    loadPortalStreams(portal, section, cats.first().id)
                } else {
                    loadPortalStreams(portal, section, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading categories: ${e.message}")
            } finally {
                _isBrowserLoading.value = false
            }
        }
    }

    fun loadPortalStreams(portal: VerifiedPortal, section: IptvSection, categoryId: String) {
        _browserSelectedCategoryId.value = categoryId
        viewModelScope.launch {
            _isBrowserLoading.value = true
            try {
                val streams = IptvClient.streams(portal.portal, section, categoryId)
                _browserStreams.value = streams

                // Load cached alive IDs
                val cachedAlive = storage.loadAliveStreamIds(portal.key)
                _browserAliveIds.value = cachedAlive
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading streams: ${e.message}")
            } finally {
                _isBrowserLoading.value = false
            }
        }
    }

    fun checkAliveForCategory(portal: VerifiedPortal) {
        if (_isAliveChecking.value) return
        val currentStreams = _browserStreams.value
        if (currentStreams.isEmpty()) return

        viewModelScope.launch {
            _isAliveChecking.value = true
            _aliveProgress.value = AliveProgress(0, currentStreams.size, 0)
            val aliveSet = ConcurrentHashMap.newKeySet<String>()
            aliveSet.addAll(_browserAliveIds.value)

            val pairs = currentStreams.map { s ->
                Pair(s.streamId, IptvClient.streamUrl(portal.portal, s))
            }

            IptvAliveChecker.launchCheck(
                streams = pairs,
                onResult = { id, alive ->
                    if (alive) aliveSet.add(id) else aliveSet.remove(id)
                    _browserAliveIds.value = aliveSet.toSet()
                },
                onProgress = { p ->
                    _aliveProgress.value = p
                },
                onDone = {
                    storage.saveAliveStreamIds(portal.key, aliveSet.toSet())
                    _isAliveChecking.value = false
                }
            )
        }
    }

    fun getStreamUrl(portal: VerifiedPortal, stream: IptvStream): String {
        return IptvClient.streamUrl(portal.portal, stream)
    }

    suspend fun getEpg(portal: VerifiedPortal, streamId: String): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (streamId.isEmpty()) return@withContext emptyList()
        val cached = _epgMap[streamId]
        if (cached != null) return@withContext cached
        val res = IptvClient.shortEpg(portal.portal, streamId, limit = 2)
        _epgMap[streamId] = res
        res
    }

    // ── M3U Playlists ──

    fun addM3uPlaylist(name: String, url: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val req = Request.Builder()
                    .url(url.trim())
                    .addHeader("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                    .build()
                val client = OkHttpClient.Builder().build()
                val text = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
                val channels = M3uParser.parse(text)
                if (channels.isNotEmpty()) {
                    val playlist = M3uPlaylist(
                        id = UUID.randomUUID().toString(),
                        name = name.trim().ifEmpty { "M3U Playlist" },
                        url = url.trim(),
                        count = channels.size
                    )
                    val list = _m3uPlaylists.value + playlist
                    _m3uPlaylists.value = list
                    storage.saveM3uPlaylists(list)
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed adding M3U: ${e.message}")
                onComplete(false)
            }
        }
    }

    fun deleteM3uPlaylist(id: String) {
        viewModelScope.launch {
            val list = _m3uPlaylists.value.filter { it.id != id }
            _m3uPlaylists.value = list
            storage.saveM3uPlaylists(list)
        }
    }
}
