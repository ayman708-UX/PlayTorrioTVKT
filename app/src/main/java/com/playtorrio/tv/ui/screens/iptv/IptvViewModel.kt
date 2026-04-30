package com.playtorrio.tv.ui.screens.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.iptv.IptvCategory
import com.playtorrio.tv.data.iptv.IptvClient
import com.playtorrio.tv.data.iptv.IptvEpisode
import com.playtorrio.tv.data.iptv.IptvPortal
import com.playtorrio.tv.data.iptv.IptvScraper
import com.playtorrio.tv.data.iptv.IptvSection
import com.playtorrio.tv.data.iptv.IptvStore
import com.playtorrio.tv.data.iptv.IptvStream
import com.playtorrio.tv.data.iptv.IptvVerifier
import com.playtorrio.tv.data.iptv.VerifiedPortal
import com.playtorrio.tv.data.iptv.IptvAliveChecker
import com.playtorrio.tv.data.iptv.IptvAliveStore
import com.playtorrio.tv.data.iptv.IptvChannelResultsStore
import com.playtorrio.tv.data.iptv.HardcodedChannel
import com.playtorrio.tv.data.iptv.HardcodedChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where in the IPTV flow the user currently is.
 *  - PORTAL_LIST: empty state + Scrape button + verified portals
 *  - SECTION_PICK: chose a portal, picking Live/Movies/Series
 *  - BROWSER: sidebar (categories + search) + content pane (live/vod/series)
 *  - EPISODE_LIST: chose a series, showing episodes
 */
enum class IptvView {
    PORTAL_LIST,
    SECTION_PICK,
    BROWSER,
    EPISODE_LIST,
    CHANNELS_HUB,
    CHANNEL_RESULTS,
}

/** Sentinel id meaning "all categories" in the browser sidebar. */
const val LIVE_ALL_CATEGORY_ID = "__all__"

private fun keyOf(p: IptvPortal): String =
    "${p.url}|${p.username}|${p.password}".lowercase()
private fun keyOf(v: VerifiedPortal): String = keyOf(v.portal)

data class IptvUiState(
    val view: IptvView = IptvView.PORTAL_LIST,

    // Scrape / verify
    val isScraping: Boolean = false,
    val statusText: String = "",
    val verified: List<VerifiedPortal> = emptyList(),
    /** True when the last scrape still has un-attempted portals to try. */
    val canGetMore: Boolean = false,

    // Edit mode
    val editMode: Boolean = false,
    val selected: Set<String> = emptySet(),

    // Manual add dialog
    val showAddDialog: Boolean = false,
    val isAdding: Boolean = false,
    val addError: String? = null,

    // Browsing
    val activePortal: VerifiedPortal? = null,
    val activeSection: IptvSection? = null,
    val activeSeries: IptvStream? = null,
    val isLoading: Boolean = false,
    val categories: List<IptvCategory> = emptyList(),
    val episodes: List<IptvEpisode> = emptyList(),
    val error: String? = null,

    // Browser (Live / VOD / Series)
    val browserAllStreams: List<IptvStream> = emptyList(),
    val browserSelectedCategoryId: String = LIVE_ALL_CATEGORY_ID,
    val browserSearch: String = "",

    // Live-only verification (LIVE section)
    val liveOnly: Boolean = false,
    val aliveStreamIds: Set<String> = emptySet(),
    val isVerifyingAlive: Boolean = false,
    val aliveChecked: Int = 0,
    val aliveTotal: Int = 0,
    val aliveCount: Int = 0,
    val aliveCheckedAt: Long = 0L,

    // Hardcoded "Channels" hub (sports, news, etc. across all portals)
    val activeHardcoded: HardcodedChannel? = null,
    val channelStatus: String = "",
    val channelIsRunning: Boolean = false,
    val channelResults: List<ChannelHit> = emptyList(),
)

/** A single alive stream found while resolving a HardcodedChannel. */
data class ChannelHit(
    val portal: VerifiedPortal,
    val stream: IptvStream,
    val streamUrl: String,
)

class IptvViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(IptvUiState())
    val ui = _ui.asStateFlow()

    private var scrapeJob: Job? = null
    private var browseJob: Job? = null
    private var aliveJob: Job? = null
    private var channelJob: Job? = null

    private var scrapedAll: List<IptvPortal> = emptyList()
    private val attempted = mutableSetOf<String>()
    /** Reddit pagination cursor for the next `scrapeRedditPage` call. */
    private var redditAfter: String? = null
    /** True once Reddit returns no more pages or no new portals. */
    private var redditExhausted: Boolean = false

    init {
        val saved = IptvStore.load(app)
        if (saved.isNotEmpty()) _ui.value = _ui.value.copy(verified = saved)
    }

    // ── Scrape / verify ────────────────────────────────────────────────

    fun scrape() {
        if (_ui.value.isScraping) return
        scrapeJob?.cancel()
        scrapeJob = viewModelScope.launch {
            attempted.clear()
            scrapedAll = emptyList()
            redditAfter = null
            redditExhausted = false
            _ui.value = _ui.value.copy(
                isScraping = true,
                statusText = "Scraping…",
                editMode = false,
                selected = emptySet(),
            )
            val page = withContext(Dispatchers.IO) {
                IptvScraper.scrapeRedditPage(maxResults = 50, after = null)
            }
            redditAfter = page.nextAfter
            if (page.portals.isEmpty()) {
                redditExhausted = !page.hasMore
                _ui.value = _ui.value.copy(
                    isScraping = false,
                    statusText = "No portals found.",
                )
                return@launch
            }
            scrapedAll = page.portals

            val existing = _ui.value.verified.map { keyOf(it) }.toSet()
            val toTry = page.portals.filter { keyOf(it) !in existing }
            _ui.value = _ui.value.copy(
                statusText = "Found ${page.portals.size} portals. Testing connections…",
            )
            runVerification(toTry, additionalTarget = 5)
        }
    }

    fun getMore() {
        if (_ui.value.isScraping) return
        if (scrapedAll.isEmpty() && redditAfter == null && !redditExhausted) {
            scrape(); return
        }
        scrapeJob?.cancel()
        scrapeJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isScraping = true, statusText = "Getting more…")

            val target = 5
            val startVerified = _ui.value.verified.size
            var safety = 8 // hard cap on pages fetched in one Get More burst

            // Loop: try local pool → if not enough, fetch next Reddit page → repeat.
            while (_ui.value.verified.size - startVerified < target) {
                val existingKeys = _ui.value.verified.map { keyOf(it) }.toSet()
                val pool = scrapedAll.filter {
                    keyOf(it) !in attempted && keyOf(it) !in existingKeys
                }
                val needed = target - (_ui.value.verified.size - startVerified)

                if (pool.isNotEmpty()) {
                    runVerification(pool, additionalTarget = needed)
                    if (_ui.value.verified.size - startVerified >= target) break
                }

                // Pool drained — try to fetch the next Reddit page.
                if (redditExhausted || safety-- <= 0) break
                if (redditAfter.isNullOrEmpty() && scrapedAll.isNotEmpty()) {
                    // Already consumed first page and Reddit gave us no cursor.
                    redditExhausted = true
                    break
                }
                _ui.value = _ui.value.copy(
                    isScraping = true,
                    statusText = "Scraping more…",
                )
                val page = withContext(Dispatchers.IO) {
                    IptvScraper.scrapeRedditPage(maxResults = 50, after = redditAfter)
                }
                redditAfter = page.nextAfter
                val knownKeys = (scrapedAll.map { keyOf(it) } + attempted).toSet()
                val freshPortals = page.portals.filter { keyOf(it) !in knownKeys }
                if (freshPortals.isEmpty() && !page.hasMore) {
                    redditExhausted = true
                    break
                }
                if (freshPortals.isEmpty()) continue // no new ones this page, try next
                scrapedAll = (scrapedAll + freshPortals).distinctBy { keyOf(it) }
            }

            val gained = _ui.value.verified.size - startVerified
            val total = _ui.value.verified.size
            val poolLeft = scrapedAll.count { keyOf(it) !in attempted }
            _ui.value = _ui.value.copy(
                isScraping = false,
                canGetMore = poolLeft > 0 || !redditExhausted,
                statusText = when {
                    gained == 0 && redditExhausted -> "No more sources · $total saved."
                    gained == 0 -> "$total saved · no new working portals this round."
                    else -> "$total working portal${if (total == 1) "" else "s"} saved."
                },
            )
            IptvStore.save(getApplication(), _ui.value.verified)
        }
    }

    private suspend fun runVerification(toTry: List<IptvPortal>, additionalTarget: Int) {
        val newAlive = IptvVerifier.verifyUntil(
            portals = toTry,
            target = additionalTarget,
            onAttempted = { p -> attempted += keyOf(p) },
            onProgress = { checked, total, ali ->
                _ui.value = _ui.value.copy(
                    statusText = "Tested $checked/$total · $ali working",
                )
            },
            onAlive = { v ->
                val merged = (_ui.value.verified + v).distinctBy { keyOf(it) }
                _ui.value = _ui.value.copy(verified = merged)
                IptvStore.save(getApplication(), merged)
            },
        )
        val total = _ui.value.verified.size
        val remaining = scrapedAll.count { keyOf(it) !in attempted }
        _ui.value = _ui.value.copy(
            isScraping = false,
            canGetMore = remaining > 0,
            statusText = when {
                newAlive.isEmpty() && total == 0 -> "No working portals found."
                newAlive.isEmpty() -> "$total saved · no new working portals this round."
                else -> "$total working portal${if (total == 1) "" else "s"} saved."
            },
        )
        IptvStore.save(getApplication(), _ui.value.verified)
    }

    // ── Edit mode ──────────────────────────────────────────────────────

    fun toggleEditMode() {
        val s = _ui.value
        _ui.value = s.copy(
            editMode = !s.editMode,
            selected = if (s.editMode) emptySet() else s.selected,
        )
    }

    fun toggleSelect(portal: VerifiedPortal) {
        val s = _ui.value
        val k = keyOf(portal)
        val sel = s.selected.toMutableSet()
        if (k in sel) sel -= k else sel += k
        _ui.value = s.copy(selected = sel)
    }

    fun toggleSelectAll() {
        val s = _ui.value
        val allKeys = s.verified.map { keyOf(it) }.toSet()
        val allSelected = allKeys.isNotEmpty() && s.selected.containsAll(allKeys)
        _ui.value = s.copy(selected = if (allSelected) emptySet() else allKeys)
    }

    fun deleteSelected() {
        val s = _ui.value
        if (s.selected.isEmpty()) return
        val remaining = s.verified.filterNot { keyOf(it) in s.selected }
        _ui.value = s.copy(
            verified = remaining,
            selected = emptySet(),
            editMode = false,
        )
        IptvStore.save(getApplication(), remaining)
    }

    // ── Manual add ─────────────────────────────────────────────────────

    fun openAddDialog() {
        _ui.value = _ui.value.copy(showAddDialog = true, addError = null)
    }

    fun dismissAddDialog() {
        if (_ui.value.isAdding) return
        _ui.value = _ui.value.copy(showAddDialog = false, addError = null)
    }

    fun addManual(rawUrl: String, username: String, password: String) {
        val url = normalizeUrl(rawUrl)
        val u = username.trim()
        val pw = password.trim()
        if (url.isEmpty() || u.isEmpty() || pw.isEmpty()) {
            _ui.value = _ui.value.copy(addError = "URL, username and password are required.")
            return
        }
        val portal = IptvPortal(url = url, username = u, password = pw, source = "Manual")
        val k = keyOf(portal)
        if (_ui.value.verified.any { keyOf(it) == k }) {
            _ui.value = _ui.value.copy(addError = "This portal is already saved.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isAdding = true, addError = null)
            val verified = withContext(Dispatchers.IO) {
                IptvClient.verifyOrNull(portal, timeoutMs = 8000)
            }
            if (verified == null) {
                _ui.value = _ui.value.copy(
                    isAdding = false,
                    addError = "Could not connect or login. Check the URL and credentials.",
                )
                return@launch
            }
            val merged = (_ui.value.verified + verified).distinctBy { keyOf(it) }
            _ui.value = _ui.value.copy(
                isAdding = false,
                showAddDialog = false,
                addError = null,
                verified = merged,
                statusText = "Added ${verified.name.ifBlank { "portal" }} \u00b7 ${merged.size} saved.",
            )
            IptvStore.save(getApplication(), merged)
        }
    }

    fun addM3u(rawUrl: String, displayName: String) {
        val url = normalizeM3uUrl(rawUrl)
        val name = displayName.trim()
        if (url.isEmpty()) {
            _ui.value = _ui.value.copy(addError = "Playlist URL is required.")
            return
        }
        val portal = IptvPortal(
            url = url,
            username = name, // used as display name
            password = "",
            source = "M3U",
            kind = "m3u",
        )
        val k = keyOf(portal)
        if (_ui.value.verified.any { keyOf(it) == k }) {
            _ui.value = _ui.value.copy(addError = "This playlist is already saved.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isAdding = true, addError = null)
            val verified = withContext(Dispatchers.IO) {
                IptvClient.verifyOrNull(portal, timeoutMs = 15000)
            }
            if (verified == null) {
                _ui.value = _ui.value.copy(
                    isAdding = false,
                    addError = "Could not download or parse the playlist.",
                )
                return@launch
            }
            val merged = (_ui.value.verified + verified).distinctBy { keyOf(it) }
            _ui.value = _ui.value.copy(
                isAdding = false,
                showAddDialog = false,
                addError = null,
                verified = merged,
                statusText = "Added ${verified.name.ifBlank { "playlist" }} \u00b7 ${merged.size} saved.",
            )
            IptvStore.save(getApplication(), merged)
        }
    }

    private fun normalizeM3uUrl(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "http://$s"
        }
        return s
    }

    private fun normalizeUrl(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "http://$s"
        }
        // Strip trailing slashes & player_api.php / get.php paths.
        s = s.trimEnd('/')
        s = s.replace(
            Regex("/(?:player_api|get|portal|index)\\.php.*$", RegexOption.IGNORE_CASE),
            "",
        ).trimEnd('/')
        return s
    }

    // ── Navigation ─────────────────────────────────────────────────────

    fun openPortal(portal: VerifiedPortal) {
        _ui.value = _ui.value.copy(
            view = IptvView.SECTION_PICK,
            activePortal = portal,
            activeSection = null,
            activeSeries = null,
            categories = emptyList(),
            browserAllStreams = emptyList(),
            episodes = emptyList(),
            error = null,
        )
    }

    fun openSection(section: IptvSection) {
        val portal = _ui.value.activePortal ?: return
        browseJob?.cancel()
        aliveJob?.cancel()
        // Restore per-portal alive snapshot + Live-only preference for LIVE.
        val (aliveIds, aliveAt, liveOnlyPref) = if (section == IptvSection.LIVE) {
            val key = IptvAliveStore.portalKey(portal.portal)
            val snap = IptvAliveStore.load(getApplication(), key)
            Triple(
                snap?.aliveIds ?: emptySet(),
                snap?.checkedAt ?: 0L,
                IptvAliveStore.loadLiveOnly(getApplication(), key),
            )
        } else Triple(emptySet(), 0L, false)
        _ui.value = _ui.value.copy(
            view = IptvView.BROWSER,
            activeSection = section,
            isLoading = true,
            categories = emptyList(),
            browserAllStreams = emptyList(),
            browserSelectedCategoryId = LIVE_ALL_CATEGORY_ID,
            browserSearch = "",
            error = null,
            // Reset live-only state and re-seed for LIVE.
            liveOnly = liveOnlyPref,
            aliveStreamIds = aliveIds,
            aliveCheckedAt = aliveAt,
            aliveCount = aliveIds.size,
            aliveTotal = 0,
            aliveChecked = 0,
            isVerifyingAlive = false,
        )
        browseJob = viewModelScope.launch(Dispatchers.IO) {
            val cats = runCatching { IptvClient.categories(portal.portal, section) }
                .getOrDefault(emptyList())
            _ui.value = _ui.value.copy(categories = cats)
            val all = runCatching { IptvClient.streams(portal.portal, section, "") }
                .getOrDefault(emptyList())
            _ui.value = _ui.value.copy(
                isLoading = false,
                browserAllStreams = all,
                error = if (all.isEmpty()) "Nothing returned." else null,
            )
            // Auto-start a fresh verification only if user wants Live-only AND
            // we have no cached snapshot yet (otherwise re-use the cache).
            if (section == IptvSection.LIVE &&
                _ui.value.liveOnly &&
                _ui.value.aliveCheckedAt == 0L &&
                all.isNotEmpty()
            ) {
                startAliveCheck()
            }
        }
    }

    fun selectBrowserCategory(categoryId: String) {
        _ui.value = _ui.value.copy(browserSelectedCategoryId = categoryId)
    }

    fun setBrowserSearch(query: String) {
        _ui.value = _ui.value.copy(browserSearch = query)
    }

    // ── Live-only verification ────────────────────────────────────────

    fun setLiveOnly(enabled: Boolean) {
        val s = _ui.value
        val portal = s.activePortal ?: return
        if (s.activeSection != IptvSection.LIVE) return
        _ui.value = s.copy(liveOnly = enabled)
        IptvAliveStore.saveLiveOnly(
            getApplication(),
            IptvAliveStore.portalKey(portal.portal),
            enabled,
        )
        // First time enabling on this portal -> kick off a check.
        if (enabled && _ui.value.aliveCheckedAt == 0L &&
            _ui.value.browserAllStreams.isNotEmpty() &&
            !_ui.value.isVerifyingAlive
        ) {
            startAliveCheck()
        }
    }

    /** Force a fresh re-check, ignoring any cached snapshot. */
    fun recheckAlive() {
        if (_ui.value.activeSection != IptvSection.LIVE) return
        if (_ui.value.browserAllStreams.isEmpty()) return
        startAliveCheck()
    }

    /** Cancel an in-flight verification and persist whatever's been collected. */
    fun stopAliveCheck() {
        val s = _ui.value
        if (!s.isVerifyingAlive) return
        val portal = s.activePortal ?: return
        aliveJob?.cancel()
        aliveJob = null
        val now = System.currentTimeMillis()
        val ids = s.aliveStreamIds
        IptvAliveStore.save(
            getApplication(),
            IptvAliveStore.portalKey(portal.portal),
            IptvAliveStore.Snapshot(checkedAt = now, aliveIds = ids),
        )
        _ui.value = s.copy(
            isVerifyingAlive = false,
            aliveCheckedAt = now,
            aliveCount = ids.size,
        )
    }

    // ── Hardcoded "Channels" hub ───────────────────────────────────────

    /** Per-channel set of portal keys we've already searched in this VM session. */
    private val channelAttempted = mutableMapOf<String, MutableSet<String>>()
    /** Per-channel Reddit pagination cursor for "Get more channels". */
    private val channelRedditAfter = mutableMapOf<String, String?>()
    /** Per-channel scraped portal pool (still untried for this channel). */
    private val channelScrapedPool = mutableMapOf<String, MutableList<IptvPortal>>()

    fun openChannelsHub() {
        _ui.value = _ui.value.copy(
            view = IptvView.CHANNELS_HUB,
            activeHardcoded = null,
            channelResults = emptyList(),
            channelStatus = "",
            channelIsRunning = false,
        )
    }

    fun stopChannelSearch() {
        channelJob?.cancel()
        channelJob = null
        if (_ui.value.channelIsRunning) {
            _ui.value = _ui.value.copy(channelIsRunning = false)
        }
    }

    /**
     * Open a [HardcodedChannel] result page:
     *  1. Show whatever's cached on disk for this channel immediately.
     *  2. If we have verified portals not yet searched for this channel,
     *     run a scan against them and append any new alive streams.
     *  3. If there are no portals at all, scrape one Reddit page first.
     */
    fun openHardcodedChannel(channel: HardcodedChannel) {
        channelJob?.cancel()
        val cached = IptvChannelResultsStore.load(getApplication(), channel.id)
            .map(::storedToHit)
        _ui.value = _ui.value.copy(
            view = IptvView.CHANNEL_RESULTS,
            activeHardcoded = channel,
            channelResults = cached,
            channelIsRunning = true,
            channelStatus = if (cached.isEmpty()) "Preparing portals…"
            else "${cached.size} cached · scanning for more…",
        )
        channelJob = viewModelScope.launch {
            runChannelScan(channel, scrapeMore = false)
        }
    }

    /**
     * Wipe everything we know about [channel] (cache + tried-portal memory)
     * and re-scan from a fresh state.
     */
    fun searchAgainChannel(channel: HardcodedChannel) {
        channelJob?.cancel()
        IptvChannelResultsStore.clear(getApplication(), channel.id)
        channelAttempted.remove(channel.id)
        channelRedditAfter.remove(channel.id)
        channelScrapedPool.remove(channel.id)
        _ui.value = _ui.value.copy(
            activeHardcoded = channel,
            channelResults = emptyList(),
            channelIsRunning = true,
            channelStatus = "Preparing portals…",
        )
        channelJob = viewModelScope.launch {
            runChannelScan(channel, scrapeMore = false)
        }
    }

    /**
     * "Get more channels": scrape the next Reddit page, verify any new
     * portals, scan them for matches against [channel], alive-check the
     * matches, append to results.
     */
    fun getMoreChannels(channel: HardcodedChannel) {
        if (_ui.value.channelIsRunning) return
        channelJob?.cancel()
        _ui.value = _ui.value.copy(
            channelIsRunning = true,
            channelStatus = "Scraping more portals…",
        )
        channelJob = viewModelScope.launch {
            runChannelScan(channel, scrapeMore = true)
        }
    }

    fun deleteChannelHit(channel: HardcodedChannel, hit: ChannelHit) {
        val current = _ui.value.channelResults
        val next = current.filterNot { it.streamUrl == hit.streamUrl }
        if (next.size == current.size) return
        _ui.value = _ui.value.copy(channelResults = next)
        IptvChannelResultsStore.save(
            getApplication(),
            channel.id,
            next.map(::hitToStored),
        )
    }

    fun deleteChannelHits(channel: HardcodedChannel, urls: Set<String>) {
        if (urls.isEmpty()) return
        val current = _ui.value.channelResults
        val next = current.filterNot { it.streamUrl in urls }
        if (next.size == current.size) return
        _ui.value = _ui.value.copy(channelResults = next)
        IptvChannelResultsStore.save(
            getApplication(),
            channel.id,
            next.map(::hitToStored),
        )
    }

    /**
     * Core channel scan:
     *  - Build a list of portals not-yet-tried for this channel (from
     *    saved verified + already-scraped pool).
     *  - If [scrapeMore] is true OR we have nothing and need a bootstrap,
     *    fetch the next Reddit page and verify new portals into the pool.
     *  - For each portal in parallel: fetch live streams matching keywords.
     *  - Dedupe against existing results by URL → alive-check the rest →
     *    push survivors progressively into [channelResults] and persist.
     */
    private suspend fun runChannelScan(
        channel: HardcodedChannel,
        scrapeMore: Boolean,
    ) {
        val attempted = channelAttempted.getOrPut(channel.id) { mutableSetOf() }
        val pool = channelScrapedPool.getOrPut(channel.id) { mutableListOf() }

        // Bootstrap pool from globally-verified portals on first open.
        val verified = _ui.value.verified
        val poolKeys = pool.map { keyOf(it) }.toSet()
        verified.forEach { vp ->
            val k = keyOf(vp)
            if (k !in attempted && k !in poolKeys) pool += vp.portal
        }

        // Need fresh portals? Scrape the next Reddit page.
        val needsBootstrap = verified.isEmpty() && pool.none { keyOf(it) !in attempted }
        if (scrapeMore || needsBootstrap) {
            val cursor = channelRedditAfter[channel.id]
            val page = withContext(Dispatchers.IO) {
                IptvScraper.scrapeRedditPage(maxResults = 60, after = cursor)
            }
            channelRedditAfter[channel.id] = page.nextAfter
            val knownAll = (pool.map { keyOf(it) } + attempted).toSet()
            val fresh = page.portals.filter { keyOf(it) !in knownAll }
            if (fresh.isNotEmpty()) {
                _ui.value = _ui.value.copy(
                    channelStatus = "Verifying ${fresh.size} new portal" +
                        "${if (fresh.size == 1) "" else "s"}…",
                )
                val newlyVerified = IptvVerifier.verifyUntil(
                    portals = fresh,
                    target = 5,
                    onAlive = { v ->
                        val merged = (_ui.value.verified + v).distinctBy { keyOf(it) }
                        _ui.value = _ui.value.copy(verified = merged)
                        IptvStore.save(getApplication(), merged)
                    },
                    onProgress = { c, t, a ->
                        _ui.value = _ui.value.copy(
                            channelStatus = "Verifying portals $c/$t · $a working",
                        )
                    },
                )
                newlyVerified.forEach { vp ->
                    val k = keyOf(vp)
                    if (k !in attempted && pool.none { keyOf(it) == k }) {
                        pool += vp.portal
                    }
                }
            } else if (page.portals.isEmpty() && !page.hasMore) {
                if (_ui.value.channelResults.isEmpty()) {
                    _ui.value = _ui.value.copy(
                        channelIsRunning = false,
                        channelStatus = "No more portals available from Reddit.",
                    )
                    return
                }
            }
        }

        // Take a working slice of the pool (cap a bit so we don't burn through
        // the entire pool on a single click — leaves headroom for "Get more").
        val toScan = pool.toList().take(8)
        if (toScan.isEmpty()) {
            _ui.value = _ui.value.copy(
                channelIsRunning = false,
                channelStatus = if (_ui.value.channelResults.isEmpty())
                    "No working portals available. Hit Scrape first."
                else "${_ui.value.channelResults.size} alive · no more portals to scan.",
            )
            return
        }

        _ui.value = _ui.value.copy(
            channelStatus = "Searching ${toScan.size} portal" +
                "${if (toScan.size == 1) "" else "s"}…",
        )

        // Mark attempted up-front so a re-entry can move on.
        toScan.forEach { attempted += keyOf(it) }
        pool.removeAll { keyOf(it) in attempted }

        // Fan out across portals — fetch live streams in parallel, filter.
        data class Candidate(
            val portal: VerifiedPortal,
            val stream: IptvStream,
            val url: String,
        )
        val verifiedByKey = _ui.value.verified.associateBy { keyOf(it) }
        val candidates = withContext(Dispatchers.IO) {
            coroutineScope {
                toScan.map { p ->
                    async {
                        val vp = verifiedByKey[keyOf(p)]
                            ?: VerifiedPortal(p, p.url, "", "", "")
                        runCatching {
                            IptvClient.streams(vp.portal, IptvSection.LIVE, "")
                        }.getOrDefault(emptyList())
                            .filter {
                                HardcodedChannels.matches(
                                    it.name, channel.keywords, channel.exclude,
                                )
                            }
                            .map { Candidate(vp, it, IptvClient.streamUrl(vp.portal, it)) }
                    }
                }.awaitAll().flatten()
            }
        }
            .filter { it.url.isNotEmpty() }
            .distinctBy { it.url }

        // Drop any candidates we already have.
        val have = _ui.value.channelResults.map { it.streamUrl }.toHashSet()
        val newCandidates = candidates.filter { it.url !in have }

        if (newCandidates.isEmpty()) {
            _ui.value = _ui.value.copy(
                channelIsRunning = false,
                channelStatus = if (_ui.value.channelResults.isEmpty())
                    "No matching channels found. Try Get more."
                else "${_ui.value.channelResults.size} alive · no new matches.",
            )
            return
        }

        val byUrl = newCandidates.associateBy { it.url }
        val targets = newCandidates.map { it.url to it.url }
        _ui.value = _ui.value.copy(
            channelStatus = "Found ${newCandidates.size} candidate" +
                "${if (newCandidates.size == 1) "" else "s"} · verifying…",
        )

        IptvAliveChecker.launchCheck(
            scope = viewModelScope,
            streams = targets,
            onResult = { id, alive ->
                if (!alive) return@launchCheck
                val c = byUrl[id] ?: return@launchCheck
                val cur = _ui.value.channelResults
                if (cur.any { it.streamUrl == c.url }) return@launchCheck
                val next = cur + ChannelHit(c.portal, c.stream, c.url)
                _ui.value = _ui.value.copy(channelResults = next)
                IptvChannelResultsStore.save(
                    getApplication(),
                    channel.id,
                    next.map(::hitToStored),
                )
            },
            onProgress = { p ->
                val snap = _ui.value
                snap.activeHardcoded ?: return@launchCheck
                _ui.value = snap.copy(
                    channelStatus =
                        "Verifying ${p.checked}/${p.total} · ${snap.channelResults.size} alive",
                )
            },
            onDone = {
                val snap = _ui.value
                _ui.value = snap.copy(
                    channelIsRunning = false,
                    channelStatus = if (snap.channelResults.isEmpty())
                        "No alive streams for ${channel.name}. Try Get more."
                    else "${snap.channelResults.size} alive stream" +
                        "${if (snap.channelResults.size == 1) "" else "s"} saved.",
                )
            },
        ).also { channelJob = it }
    }

    private fun storedToHit(s: IptvChannelResultsStore.StoredHit): ChannelHit {
        val portal = IptvPortal(s.portalUrl, s.portalUser, s.portalPass)
        val vp = VerifiedPortal(portal, s.portalName.ifEmpty { s.portalUrl }, "", "", "")
        val stream = IptvStream(
            streamId = s.streamId,
            name = s.streamName,
            icon = s.streamIcon,
            categoryId = s.streamCategoryId,
            containerExt = s.streamContainerExt,
            kind = s.streamKind,
        )
        return ChannelHit(vp, stream, s.streamUrl)
    }

    private fun hitToStored(h: ChannelHit): IptvChannelResultsStore.StoredHit =
        IptvChannelResultsStore.StoredHit(
            portalUrl = h.portal.portal.url,
            portalUser = h.portal.portal.username,
            portalPass = h.portal.portal.password,
            portalName = h.portal.name,
            streamId = h.stream.streamId,
            streamName = h.stream.name,
            streamIcon = h.stream.icon,
            streamCategoryId = h.stream.categoryId,
            streamContainerExt = h.stream.containerExt,
            streamKind = h.stream.kind,
            streamUrl = h.streamUrl,
        )

    private fun startAliveCheck() {
        val s = _ui.value
        val portal = s.activePortal ?: return
        val streams = s.browserAllStreams
        if (streams.isEmpty()) return
        aliveJob?.cancel()
        val targets = streams.map { it.streamId to IptvClient.streamUrl(portal.portal, it) }
        val key = IptvAliveStore.portalKey(portal.portal)
        // Start fresh: aliveStreamIds will fill as results arrive.
        _ui.value = s.copy(
            isVerifyingAlive = true,
            aliveTotal = streams.size,
            aliveChecked = 0,
            aliveCount = 0,
            aliveStreamIds = emptySet(),
        )
        val collected = HashSet<String>()
        aliveJob = IptvAliveChecker.launchCheck(
            scope = viewModelScope,
            streams = targets,
            onResult = { id, alive ->
                if (alive) {
                    synchronized(collected) { collected += id }
                    val snap = _ui.value
                    _ui.value = snap.copy(
                        aliveStreamIds = snap.aliveStreamIds + id,
                    )
                }
            },
            onProgress = { p ->
                val snap = _ui.value
                _ui.value = snap.copy(
                    aliveChecked = p.checked,
                    aliveTotal = p.total,
                    aliveCount = p.alive,
                )
            },
            onDone = {
                val now = System.currentTimeMillis()
                val ids = synchronized(collected) { collected.toSet() }
                IptvAliveStore.save(
                    getApplication(),
                    key,
                    IptvAliveStore.Snapshot(checkedAt = now, aliveIds = ids),
                )
                val snap = _ui.value
                _ui.value = snap.copy(
                    isVerifyingAlive = false,
                    aliveCheckedAt = now,
                    aliveStreamIds = ids,
                    aliveCount = ids.size,
                )
            },
        )
    }

    fun openSeries(series: IptvStream) {
        val portal = _ui.value.activePortal ?: return
        browseJob?.cancel()
        _ui.value = _ui.value.copy(
            view = IptvView.EPISODE_LIST,
            activeSeries = series,
            isLoading = true,
            episodes = emptyList(),
            error = null,
        )
        browseJob = viewModelScope.launch(Dispatchers.IO) {
            val eps = runCatching {
                IptvClient.seriesEpisodes(portal.portal, series.streamId)
            }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(
                isLoading = false,
                episodes = eps,
                error = if (eps.isEmpty()) "No episodes." else null,
            )
        }
    }

    fun back(): Boolean {
        val s = _ui.value
        return when (s.view) {
            IptvView.PORTAL_LIST -> {
                if (s.editMode) {
                    _ui.value = s.copy(editMode = false, selected = emptySet())
                    true
                } else false
            }
            IptvView.SECTION_PICK -> {
                _ui.value = s.copy(view = IptvView.PORTAL_LIST, activePortal = null)
                true
            }
            IptvView.BROWSER -> {
                aliveJob?.cancel()
                _ui.value = s.copy(
                    view = IptvView.SECTION_PICK,
                    activeSection = null,
                    browserAllStreams = emptyList(),
                    isVerifyingAlive = false,
                )
                true
            }
            IptvView.EPISODE_LIST -> {
                _ui.value = s.copy(view = IptvView.BROWSER, activeSeries = null)
                true
            }
            IptvView.CHANNELS_HUB -> {
                _ui.value = s.copy(view = IptvView.PORTAL_LIST)
                true
            }
            IptvView.CHANNEL_RESULTS -> {
                channelJob?.cancel()
                channelJob = null
                _ui.value = s.copy(
                    view = IptvView.CHANNELS_HUB,
                    activeHardcoded = null,
                    channelResults = emptyList(),
                    channelStatus = "",
                    channelIsRunning = false,
                )
                true
            }
        }
    }
}
