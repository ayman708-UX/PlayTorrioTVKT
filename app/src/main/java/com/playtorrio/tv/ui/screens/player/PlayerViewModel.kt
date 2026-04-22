package com.playtorrio.tv.ui.screens.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.debrid.DebridResolver
import com.playtorrio.tv.data.streaming.StreamExtractorService
import com.playtorrio.tv.data.skip.SkipSegment
import com.playtorrio.tv.data.skip.SkipSegmentService
import com.playtorrio.tv.data.subtitle.ExternalSubtitle
import com.playtorrio.tv.data.subtitle.SubtitleService
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.torrent.TorrServerService
import com.playtorrio.tv.data.watch.WatchKind
import com.playtorrio.tv.data.watch.WatchProgress
import com.playtorrio.tv.data.watch.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioTrackInfo(
    val index: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String?,
    val language: String?,
    val codec: String?,
    val channelCount: Int?,
    val sampleRate: Int?,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val isBuiltIn: Boolean,
    val isSelected: Boolean,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val externalUrl: String? = null,
    val source: String = ""
)

enum class AspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM_115("Zoom 1.15x"),
    ZOOM_133("Zoom 1.33x")
}

data class SubtitleStyleSettings(
    val size: Float = 100f,
    val textColor: Int = android.graphics.Color.WHITE,
    val backgroundColor: Int = android.graphics.Color.TRANSPARENT,
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = android.graphics.Color.BLACK,
    val bold: Boolean = false,
    val verticalOffset: Float = 0f,
    val subtitleDelayMs: Long = 0L
)

data class PlayerUiState(
    // Loading / connection
    val isConnecting: Boolean = true,
    val connectionStatus: String = "Starting TorrServer…",
    /** True while IPTV stall recovery is in flight — keeps the video surface
     *  visible (no black LoadingOverlay) and shows just a buffering spinner. */
    val isReconnecting: Boolean = false,
    val reconnectStatus: String = "",
    // Playback
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    // Seek preview
    val pendingPreviewSeekPosition: Long? = null,
    val showSeekOverlay: Boolean = false,
    // Metadata
    val title: String = "",
    val logoUrl: String? = null,
    val backdropUrl: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val overview: String? = null,
    val isMovie: Boolean = true,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val tmdbId: Int = 0,
    // Torrent
    val torrentHash: String? = null,
    val speedMbps: Double = 0.0,
    val activePeers: Int = 0,
    // Tracks
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    // Aspect
    val aspectMode: AspectMode = AspectMode.FIT,
    val showAspectIndicator: Boolean = false,
    // Subtitle style
    val subtitleStyle: SubtitleStyleSettings = SubtitleStyleSettings(),
    // Skip segments
    val skipSegments: List<SkipSegment> = emptyList(),
    val activeSkipSegment: SkipSegment? = null,
    // Overlays
    val showControls: Boolean = true,
    val showPauseOverlay: Boolean = false,
    val showSubtitleOverlay: Boolean = false,
    val showAudioOverlay: Boolean = false,
    val showSubtitleStylePanel: Boolean = false,
    // Streaming mode
    val isStreamingMode: Boolean = false,
    val currentSourceIndex: Int = 0,
    val showSourcesPanel: Boolean = false,
    val isSwitchingSource: Boolean = false,
    /** True for direct IPTV (Xtream-Codes) streams — disables source fallback / picker. */
    val isIptv: Boolean = false,
    // Episodes (for series)
    val episodes: List<com.playtorrio.tv.data.model.Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val showEpisodesPanel: Boolean = false,
    val nextEpisode: com.playtorrio.tv.data.model.Episode? = null,
    val isSwitchingEpisode: Boolean = false,
    // Episode source picker (when switching ep in non-streaming mode)
    val showEpisodeSourceOverlay: Boolean = false,
    val episodeOverlayKind: EpisodeOverlayKind = EpisodeOverlayKind.NONE,
    val pendingEpisode: com.playtorrio.tv.data.model.Episode? = null,
    val episodeOverlayTorrents: List<com.playtorrio.tv.data.torrent.TorrentResult> = emptyList(),
    val episodeOverlayStreams: List<com.playtorrio.tv.data.stremio.StremioStream> = emptyList(),
    val isLoadingEpisodeOverlay: Boolean = false,
    // Error
    val error: String? = null
)

enum class EpisodeOverlayKind { NONE, TORRENT, ADDON_STREAM }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    var player: ExoPlayer? = null; private set
    private var currentStreamUrl: String? = null
    private val addedExternalSubConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
    private val addedExternalSubLabels = mutableMapOf<String, String>() // url -> label
    private var pendingSubtitleLabel: String? = null // auto-select after reload
    private var positionJob: Job? = null
    private var controlsHideJob: Job? = null
    private var statsJob: Job? = null
    private var seekOverlayHideJob: Job? = null
    private var streamRetryJob: Job? = null
    private var startupRetryCount: Int = 0
    private val maxStartupRetries: Int = 4

    // ── IPTV stall watchdog & tiered recovery ────────────────────────────────
    // Live IPTV streams frequently freeze without emitting onPlayerError —
    // the socket dies, the HLS chunk loader hangs, or the upstream just stops
    // serving segments. We poll player state every second and trigger staged
    // recovery when buffering exceeds a threshold or the playhead stops moving.
    private var iptvWatchdogJob: Job? = null
    private var iptvRetryAttempt: Int = 0
    private val iptvMaxRetries: Int = 8
    /** Wallclock timestamp (ms) when STATE_BUFFERING began. 0 = not buffering. */
    private var iptvBufferingSinceMs: Long = 0L
    /** Last observed currentPosition while playing. */
    private var iptvLastPositionMs: Long = -1L
    /** Wallclock timestamp (ms) when iptvLastPositionMs was last updated. */
    private var iptvLastPositionAt: Long = 0L
    /** Wallclock timestamp (ms) when the last successful progress was seen. Used by detector 3. */
    private var iptvLastHealthyAt: Long = 0L
    /** Wallclock timestamp (ms) when the current healthy-playback streak began. 0 = no active streak. */
    private var iptvHealthyStreakStartMs: Long = 0L
    /** Buffering longer than this with playWhenReady=true → stall. */
    private val iptvBufferingStallMs: Long = 6_000L
    /** Position not advancing for this long while READY+isPlaying → frozen. */
    private val iptvFrozenPositionMs: Long = 5_000L
    /** Reset retry counter after this much continuous healthy playback. */
    private val iptvHealthyResetMs: Long = 6_000L
    /** Unix-time (ms) of the playhead at the moment recovery started. Used to
     *  resume at the same wallclock position post-recovery so the user doesn't
     *  miss the seconds spent reconnecting. C.TIME_UNSET = no saved position. */
    private var iptvSavedUnixMs: Long = C.TIME_UNSET

    // ── Continue-watching context (set once by PlayerActivity from intent extras) ──
    private var currentMagnetUri: String? = null
    private var resumePosterUrl: String? = null
    private var resumeImdbId: String? = null
    private var resumeAddonId: String? = null
    private var resumeStremioType: String? = null
    private var resumeStremioId: String? = null
    private var resumeStreamPickKey: String? = null
    private var resumeStreamPickName: String? = null
    private var resumeFileIdx: Int? = null
    private var pendingSeekMs: Long? = null
    private var lastProgressSaveAt: Long = 0L
    /** Captured from createStreamingPlayer so episode-switching can reuse the same headers. */
    private var currentReferer: String = ""

    fun setResumeContext(
        posterUrl: String?,
        imdbId: String?,
        addonId: String?,
        stremioType: String?,
        stremioId: String?,
        streamPickKey: String?,
        streamPickName: String?,
        resumePositionMs: Long?,
        fileIdx: Int?,
    ) {
        resumePosterUrl = posterUrl
        resumeImdbId = imdbId?.takeIf { it.isNotBlank() }
        resumeAddonId = addonId?.takeIf { it.isNotBlank() }
        resumeStremioType = stremioType?.takeIf { it.isNotBlank() }
        resumeStremioId = stremioId?.takeIf { it.isNotBlank() }
        resumeStreamPickKey = streamPickKey?.takeIf { it.isNotBlank() }
        resumeStreamPickName = streamPickName?.takeIf { it.isNotBlank() }
        resumeFileIdx = fileIdx
        pendingSeekMs = resumePositionMs
    }

    private fun resetStartupRetryState() {
        startupRetryCount = 0
        streamRetryJob?.cancel()
        streamRetryJob = null
    }

    /** Source priority order matching StreamingSplash. */
    private val sourcePriorityOrder = listOf(8, 3, 2)
    private val orderedSourceIndices: List<Int> by lazy {
        buildList {
            sourcePriorityOrder.forEach { idx ->
                StreamExtractorService.SOURCES.find { it.index == idx }?.let { add(idx) }
            }
            StreamExtractorService.SOURCES
                .filterNot { src -> sourcePriorityOrder.contains(src.index) }
                .forEach { add(it.index) }
        }
    }
    /** Track sources we already failed on this session so we don't cycle back. */
    private val failedSourceIndices = mutableSetOf<Int>()

    private fun tryNextSource(failedError: PlaybackException) {
        val state = _uiState.value
        // IPTV streams have no fallback sources — surface the error directly.
        if (state.isIptv) {
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    error = "Stream failed: ${failedError.errorCodeName}",
                )
            }
            return
        }
        failedSourceIndices.add(state.currentSourceIndex)
        Log.i(TAG, "tryNextSource: failed=${state.currentSourceIndex}, failedSet=$failedSourceIndices, tmdbId=${state.tmdbId} s=${state.seasonNumber} e=${state.episodeNumber}")

        val nextIdx = orderedSourceIndices.firstOrNull { it !in failedSourceIndices }
        if (nextIdx != null) {
            val sourceName = StreamExtractorService.SOURCES.find { it.index == nextIdx }?.name ?: "source"
            Log.i(TAG, "Stream failed (${failedError.errorCodeName}), switching to $sourceName")
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    connectionStatus = "Source failed, trying $sourceName…"
                )
            }
            switchToSource(nextIdx)
        } else {
            Log.w(TAG, "All sources exhausted after ${failedError.errorCodeName}")
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    error = "All sources failed: ${failedError.errorCodeName}"
                )
            }
        }
    }

    private fun scheduleStreamStartupRetry(exo: ExoPlayer, error: PlaybackException) {
        val url = currentStreamUrl ?: return
        if (streamRetryJob?.isActive == true) return

        // In streaming mode, don't retry the same source — find another one
        if (_uiState.value.isStreamingMode) {
            tryNextSource(error)
            return
        }

        if (startupRetryCount >= maxStartupRetries) {
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    error = "Stream failed to start after retries: ${error.errorCodeName}"
                )
            }
            return
        }

        val attempt = startupRetryCount + 1
        val delayMs = (attempt * 2_000L).coerceAtMost(8_000L)
        _uiState.update {
            it.copy(
                isConnecting = true,
                connectionStatus = "Stream delayed, retrying ($attempt/$maxStartupRetries)…"
            )
        }

        streamRetryJob = viewModelScope.launch {
            delay(delayMs)
            if (player !== exo) return@launch

            startupRetryCount = attempt
            Log.w(TAG, "Retrying stream startup attempt $attempt due to ${error.errorCodeName}")

            exo.stop()
            exo.clearMediaItems()
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    fun initPlayer(
        magnetUri: String,
        title: String,
        logoUrl: String?,
        backdropUrl: String?,
        year: String?,
        rating: String?,
        overview: String?,
        isMovie: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        tmdbId: Int
    ) {
        if (player != null) return

        currentMagnetUri = magnetUri

        _uiState.update {
            it.copy(
                title = title,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                year = year,
                rating = rating,
                overview = overview,
                isMovie = isMovie,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                tmdbId = tmdbId,
                isConnecting = true,
                connectionStatus = "Starting TorrServer…"
            )
        }

        val context = getApplication<Application>()

        // Do TorrServer setup → get stream URL → create player
        viewModelScope.launch {
            try {
                if (AppPreferences.debridEnabled) {
                    _uiState.update { it.copy(connectionStatus = "Resolving via debrid…") }
                    val debridUrl = DebridResolver.resolve(magnetUri, isMovie, seasonNumber, episodeNumber)
                        ?: throw IllegalStateException(
                            "Debrid is enabled, but this torrent is not cached or could not be resolved."
                        )

                    _uiState.update {
                        it.copy(
                            connectionStatus = "Starting debrid stream…",
                            isConnecting = false
                        )
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        createPlayer(debridUrl)
                    }
                } else {
                    _uiState.update { it.copy(connectionStatus = "Connecting to TorrServer…") }
                    TorrServerService.ensureInitialized(context)

                    _uiState.update { it.copy(connectionStatus = "Adding torrent…") }
                    val result = TorrServerService.startStreaming(
                        context = context,
                        magnetUri = magnetUri,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber
                    )

                    _uiState.update {
                        it.copy(
                            torrentHash = result.hash,
                            connectionStatus = "Starting playback…"
                        )
                    }

                    // Start stats poller only for TorrServer mode
                    startStatsPoller(result.hash)

                    // Create player on main thread
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        createPlayer(result.url)
                    }
                }

                // Fetch external subtitles in background
                launch {
                    val subs = SubtitleService.fetchSubtitles(
                        tmdbId = tmdbId,
                        season = seasonNumber,
                        episode = episodeNumber
                    )
                    _uiState.update { it.copy(externalSubtitles = subs) }
                    updateSubtitleTrackList()

                    // Also fetch from Stremio subtitle addons
                    fetchStremioSubtitles(tmdbId, isMovie, seasonNumber, episodeNumber)
                }

                // Fetch skip segments in background
                launch {
                    val segments = SkipSegmentService.fetchSegments(tmdbId, isMovie, seasonNumber, episodeNumber)
                    _uiState.update { it.copy(skipSegments = segments) }
                    Log.i(TAG, "Skip segments loaded: ${segments.size}")
                }

                // Eagerly load season for series so Next Episode button can appear.
                if (!isMovie && seasonNumber != null) {
                    launch { loadEpisodesForCurrentSeries() }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Stream setup failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = e.message ?: "Failed to start stream"
                    )
                }
            }
        }
    }

    private fun createPlayer(streamUrl: String) {
        val context = getApplication<Application>()
        resetStartupRetryState()

        // Custom buffer for torrent streams: bigger buffer = fewer stalls
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,   // min buffer before playback starts
                120_000,  // max buffer to keep loaded
                2_500,    // playback start threshold
                5_000     // rebuffer threshold
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Use FFmpeg decoders as fallback for unsupported audio codecs (AC3, EAC3, DTS, TrueHD, etc.)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val msFactory = DefaultMediaSourceFactory(context)
        currentStreamUrl = streamUrl

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(msFactory)
            .setLoadControl(loadControl)
            .build()
        player = exo

        // Set audio attributes for proper audio routing on Android TV
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        exo.setAudioAttributes(audioAttrs, false)
        exo.volume = 1.0f

        Log.d(TAG, "Creating player with stream URL: $streamUrl")
        val mediaItem = MediaItem.fromUri(streamUrl)
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.update {
                    it.copy(
                        isBuffering = state == Player.STATE_BUFFERING,
                        isPlaying = exo.isPlaying,
                        isConnecting = if (state == Player.STATE_READY) false else it.isConnecting
                    )
                }
                if (state == Player.STATE_READY) {
                    resetStartupRetryState()
                    _uiState.update {
                        it.copy(
                            duration = exo.duration.coerceAtLeast(0),
                            isConnecting = false
                        )
                    }
                    Log.d(TAG, "Player ready. Volume: ${exo.volume}, AudioFormat: ${exo.audioFormat}")
                    updateTracks()
                    autoSelectPendingSubtitle()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _uiState.update { it.copy(isPlaying = playing) }
                if (!playing && exo.playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(showPauseOverlay = true, showControls = false) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracks()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error (createPlayer): ${error.errorCodeName}", error)
                scheduleStreamStartupRetry(exo, error)
            }
        })

        startPositionUpdater()
        scheduleControlsHide()
    }

    private fun updateTracks() {
        val exo = player ?: return
        val tracks = exo.currentTracks

        // Audio tracks
        val audioTracks = mutableListOf<AudioTrackInfo>()
        var audioIdx = 0
        for ((groupIdx, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                audioTracks.add(AudioTrackInfo(
                    index = audioIdx++,
                    groupIndex = groupIdx,
                    trackIndex = i,
                    label = format.label,
                    language = format.language,
                    codec = format.codecs,
                    channelCount = if (format.channelCount > 0) format.channelCount else null,
                    sampleRate = if (format.sampleRate > 0) format.sampleRate else null,
                    isSelected = group.isTrackSelected(i)
                ))
            }
        }
        Log.d(TAG, "Found ${audioTracks.size} audio tracks")

        _uiState.update { it.copy(audioTracks = audioTracks) }
        updateSubtitleTrackList()
    }

    /**
     * Fetches subtitles from installed Stremio subtitle addons (e.g. OpenSubtitles),
     * converts them to ExternalSubtitle, and merges with existing results.
     */
    private suspend fun fetchStremioSubtitles(
        tmdbId: Int, isMovie: Boolean, season: Int?, episode: Int?
    ) {
        try {
            val addons = StremioAddonRepository.getAddons()
            Log.i(TAG, "Stremio subtitle fetch: ${addons.size} addons installed")
            if (addons.isEmpty()) return

            // Stremio uses IMDB IDs – convert from TMDB
            val ids = if (isMovie) {
                TmdbClient.api.getMovieExternalIds(tmdbId, TmdbClient.API_KEY)
            } else {
                TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY)
            }
            val imdbId = ids.imdbId?.takeIf { it.startsWith("tt") } ?: run {
                Log.i(TAG, "Stremio subs: no IMDB ID for tmdb=$tmdbId")
                return
            }

            val type = if (isMovie) "movie" else "series"
            val stremioId = if (isMovie) imdbId
                else if (season != null && episode != null) "$imdbId:$season:$episode"
                else imdbId

            Log.i(TAG, "Stremio subs: querying type=$type id=$stremioId")

            val stremioSubs = StremioService.getSubtitles(
                addons = addons, type = type, id = stremioId
            )
            Log.i(TAG, "Stremio subs: got ${stremioSubs.size} total")
            if (stremioSubs.isEmpty()) return

            val converted = stremioSubs.mapIndexed { idx, s ->
                val lang = s.langCode?.lowercase()
                    ?: s.lang.split(" ").firstOrNull()?.lowercase()
                    ?: s.lang.lowercase()
                val displayName = s.name ?: s.title ?: "${lang.uppercase()} ${idx + 1}"
                val format = when {
                    s.url.contains(".srt", true) -> "srt"
                    s.url.contains(".vtt", true) -> "vtt"
                    s.url.contains(".ass", true) || s.url.contains(".ssa", true) -> "ass"
                    else -> "srt"
                }
                ExternalSubtitle(
                    id = "stremio_$idx",
                    url = s.url,
                    language = lang,
                    displayName = displayName,
                    format = format,
                    source = "stremio",
                    isHearingImpaired = displayName.contains("hearing", true)
                        || displayName.contains("HI", false),
                    downloadCount = 0
                )
            }

            // Merge with existing, dedup by URL
            val existing = _uiState.value.externalSubtitles
            val existingUrls = existing.map { it.url }.toSet()
            val newSubs = converted.filter { it.url !in existingUrls }
            if (newSubs.isNotEmpty()) {
                Log.i(TAG, "Stremio addons: ${newSubs.size} new subtitles")
                _uiState.update { it.copy(externalSubtitles = existing + newSubs) }
                updateSubtitleTrackList()
            }
        } catch (e: Exception) {
            Log.i(TAG, "Stremio subtitle fetch failed: ${e.message}")
        }
    }

    private fun updateSubtitleTrackList() {
        val exo = player ?: return
        val tracks = exo.currentTracks
        val subTracks = mutableListOf<SubtitleTrackInfo>()

        // All ExoPlayer subtitle tracks (built-in + externally added)
        for ((groupIdx, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language ?: "und"
                val label = format.label ?: languageToDisplay(lang)
                subTracks.add(SubtitleTrackInfo(
                    id = "builtin_${groupIdx}_$i",
                    label = label,
                    language = lang,
                    isBuiltIn = true,
                    isSelected = group.isTrackSelected(i),
                    groupIndex = groupIdx,
                    trackIndex = i
                ))
            }
        }

        // External subtitles not yet added to the player
        for (ext in _uiState.value.externalSubtitles) {
            if (ext.url in addedExternalSubLabels) continue
            subTracks.add(SubtitleTrackInfo(
                id = ext.id,
                label = "${ext.displayName} (${ext.source})",
                language = ext.language,
                isBuiltIn = false,
                isSelected = false,
                externalUrl = ext.url,
                source = ext.source
            ))
        }

        _uiState.update { it.copy(subtitleTracks = subTracks) }
    }

    // ── Playback controls ──

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
            _uiState.update { it.copy(showPauseOverlay = false) }
            showControls()
        }
    }

    fun seekForward(ms: Long = 10_000) {
        val exo = player ?: return
        exo.seekTo((exo.currentPosition + ms).coerceAtMost(exo.duration))
    }

    fun seekBackward(ms: Long = 10_000) {
        val exo = player ?: return
        exo.seekTo((exo.currentPosition - ms).coerceAtLeast(0))
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceIn(0, player?.duration ?: 0))
    }

    fun skipActiveSegment() {
        val seg = _uiState.value.activeSkipSegment ?: return
        val exo = player ?: return
        val target = if (seg.endMs == Long.MAX_VALUE) exo.duration else seg.endMs
        Log.i(TAG, "Skipping ${seg.type.label}: seeking to ${target}ms")
        exo.seekTo(target.coerceAtMost(exo.duration))
        _uiState.update { it.copy(activeSkipSegment = null) }
    }

    // ── Preview seek (accumulates before committing) ──

    fun previewSeekBy(deltaMs: Long) {
        val exo = player ?: return
        val currentPreview = _uiState.value.pendingPreviewSeekPosition ?: exo.currentPosition
        val newPos = (currentPreview + deltaMs).coerceIn(0, exo.duration)
        _uiState.update {
            it.copy(pendingPreviewSeekPosition = newPos, showSeekOverlay = true)
        }
        seekOverlayHideJob?.cancel()
        seekOverlayHideJob = viewModelScope.launch {
            delay(2000)
            commitPreviewSeek()
        }
    }

    fun commitPreviewSeek() {
        val pending = _uiState.value.pendingPreviewSeekPosition ?: return
        player?.seekTo(pending)
        _uiState.update {
            it.copy(pendingPreviewSeekPosition = null, showSeekOverlay = false)
        }
        seekOverlayHideJob?.cancel()
    }

    // ── Audio ──

    fun selectAudioTrack(track: AudioTrackInfo) {
        val exo = player ?: return
        val groups = exo.currentTracks.groups
        if (track.groupIndex < 0 || track.groupIndex >= groups.size) return

        val group = groups[track.groupIndex]
        Log.d(TAG, "Selecting audio track: group=${track.groupIndex}, track=${track.trackIndex}, lang=${track.language}")
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        updateTracks()
    }

    // ── Subtitles ──

    fun selectSubtitle(track: SubtitleTrackInfo) {
        val exo = player ?: return

        if (track.isBuiltIn) {
            // Select built-in track
            val groups = exo.currentTracks.groups
            if (track.groupIndex >= 0 && track.groupIndex < groups.size) {
                val group = groups[track.groupIndex]
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                    .build()
            }
        } else if (track.externalUrl != null) {
            // Add external subtitle to MediaItem as SubtitleConfiguration
            val url = currentStreamUrl ?: return

            val mimeType = when {
                track.externalUrl.contains(".srt", true) || track.id.contains("srt") -> MimeTypes.APPLICATION_SUBRIP
                track.externalUrl.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                track.externalUrl.contains(".ass", true) || track.externalUrl.contains(".ssa", true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }

            // Extract clean display name (without source suffix)
            val cleanLabel = track.label.replace(" (${track.source})", "").trim()
            Log.d(TAG, "Adding external subtitle: $cleanLabel, url=${track.externalUrl}, mime=$mimeType")

            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.externalUrl))
                .setMimeType(mimeType)
                .setLanguage(track.language)
                .setLabel(cleanLabel)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            // Track it before reload — set pending so it auto-selects after prepare
            addedExternalSubConfigs.add(subConfig)
            addedExternalSubLabels[track.externalUrl] = cleanLabel
            pendingSubtitleLabel = cleanLabel

            // Rebuild MediaItem with all accumulated external subs
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setSubtitleConfigurations(addedExternalSubConfigs.toList())
                .build()

            val currentPos = exo.currentPosition
            val wasPlaying = exo.isPlaying
            exo.setMediaItem(mediaItem, currentPos)
            exo.prepare()
            exo.playWhenReady = wasPlaying

            // Enable text tracks
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        updateSubtitleTrackList()
    }

    private fun autoSelectPendingSubtitle() {
        val label = pendingSubtitleLabel ?: return
        pendingSubtitleLabel = null
        val exo = player ?: return

        for (group in exo.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.label == label) {
                    Log.d(TAG, "Auto-selecting subtitle: $label")
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    updateSubtitleTrackList()
                    return
                }
            }
        }
    }

    fun disableSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        updateSubtitleTrackList()
    }

    fun enableSubtitleTrack() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        updateSubtitleTrackList()
    }

    // ── Subtitle style ──

    fun updateSubtitleStyle(transform: (SubtitleStyleSettings) -> SubtitleStyleSettings) {
        _uiState.update { it.copy(subtitleStyle = transform(it.subtitleStyle)) }
    }

    // ── Aspect ratio ──

    fun cycleAspectRatio() {
        val modes = AspectMode.entries
        val current = _uiState.value.aspectMode
        val next = modes[(current.ordinal + 1) % modes.size]
        _uiState.update { it.copy(aspectMode = next, showAspectIndicator = true) }

        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(showAspectIndicator = false) }
        }
    }

    // ── Overlay visibility ──

    fun showControls() {
        _uiState.update { it.copy(showControls = true, showPauseOverlay = false) }
        scheduleControlsHide()
    }

    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }

    fun toggleControls() {
        if (_uiState.value.showControls) hideControls() else showControls()
    }

    fun dismissPauseOverlay() {
        player?.play()
        _uiState.update { it.copy(showPauseOverlay = false) }
        showControls()
    }

    fun showSubtitleOverlay() {
        _uiState.update { it.copy(showSubtitleOverlay = true, showControls = false) }
    }

    fun hideSubtitleOverlay() {
        _uiState.update { it.copy(showSubtitleOverlay = false) }
        showControls()
    }

    fun showAudioOverlay() {
        _uiState.update { it.copy(showAudioOverlay = true, showControls = false) }
    }

    fun hideAudioOverlay() {
        _uiState.update { it.copy(showAudioOverlay = false) }
        showControls()
    }

    fun showSubtitleStylePanel() {
        _uiState.update { it.copy(showSubtitleStylePanel = true, showControls = false) }
    }

    fun hideSubtitleStylePanel() {
        _uiState.update { it.copy(showSubtitleStylePanel = false) }
        showControls()
    }

    fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(5000)
            if (_uiState.value.isPlaying &&
                !_uiState.value.showSubtitleOverlay &&
                !_uiState.value.showAudioOverlay &&
                !_uiState.value.showSubtitleStylePanel
            ) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    private fun startPositionUpdater() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                player?.let { exo ->
                    val pos = exo.currentPosition.coerceAtLeast(0)
                    val dur = exo.duration

                    // Auto-seek to remembered position once duration is known.
                    pendingSeekMs?.let { target ->
                        if (dur > 0) {
                            val safe = target.coerceIn(0L, (dur - 5_000L).coerceAtLeast(0L))
                            Log.i(TAG, "Auto-seek (resume) to ${safe}ms (duration=$dur)")
                            exo.seekTo(safe)
                            pendingSeekMs = null
                        }
                    }

                    val segments = _uiState.value.skipSegments
                    val active = segments.find { seg ->
                        val end = if (seg.endMs == Long.MAX_VALUE) exo.duration else seg.endMs
                        pos in seg.startMs until end
                    }
                    _uiState.update { it.copy(currentPosition = pos, activeSkipSegment = active) }

                    // Throttled progress save (~every 3s while we're past auto-seek).
                    val now = System.currentTimeMillis()
                    if (pendingSeekMs == null && now - lastProgressSaveAt > 3_000L) {
                        lastProgressSaveAt = now
                        saveCurrentProgress(pos, dur.coerceAtLeast(0L))
                    }
                }
                delay(500)
            }
        }
    }

    private fun startStatsPoller(hash: String) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (true) {
                val stats = TorrServerService.getTorrentStats(hash)
                if (stats != null) {
                    _uiState.update {
                        it.copy(speedMbps = stats.speedMbps, activePeers = stats.activePeers)
                    }
                }
                delay(3000)
            }
        }
    }

    private fun languageToDisplay(code: String): String {
        return try {
            java.util.Locale(code).displayLanguage
        } catch (_: Exception) { code }
    }

    override fun onCleared() {
        super.onCleared()

        // Final progress save before tearing things down.
        try {
            val exo = player
            if (exo != null) {
                saveCurrentProgress(exo.currentPosition.coerceAtLeast(0L), exo.duration.coerceAtLeast(0L))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Final progress save failed: ${e.message}")
        }

        positionJob?.cancel()
        controlsHideJob?.cancel()
        statsJob?.cancel()
        seekOverlayHideJob?.cancel()
        streamRetryJob?.cancel()
        iptvWatchdogJob?.cancel()
        player?.release()
        player = null

        // Clean up torrent on a non-viewModelScope thread
        _uiState.value.torrentHash?.let { hash ->
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                TorrServerService.removeTorrent(hash)
            }
        }
    }

    /**
     * Public hook so the hosting Activity can flush the latest progress
     * synchronously (e.g. from onPause) before another Activity resumes
     * and re-reads the store.
     */
    fun flushProgress() {
        try {
            val exo = player ?: return
            saveCurrentProgress(
                exo.currentPosition.coerceAtLeast(0L),
                exo.duration.coerceAtLeast(0L),
            )
        } catch (e: Exception) {
            Log.w(TAG, "flushProgress failed: ${e.message}")
        }
    }

    /**
     * Builds a [WatchProgress] from the current UI state + resume context and
     * upserts it into the store. Silently no-ops when there is not enough info
     * (no tmdbId AND no addonId / stremioId) or when the position is too small.
     */
    private fun saveCurrentProgress(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        val s = _uiState.value
        if (s.title.isBlank()) return

        val kind = when {
            resumeAddonId != null -> WatchKind.ADDON_STREAM
            currentMagnetUri != null -> WatchKind.MAGNET
            s.isStreamingMode -> WatchKind.STREAMING
            else -> return
        }

        // For ADDON_STREAM with no tmdb, we still need stremio key parts.
        if (s.tmdbId <= 0 &&
            (kind != WatchKind.ADDON_STREAM ||
                resumeAddonId.isNullOrBlank() ||
                resumeStremioId.isNullOrBlank())
        ) return

        val key = WatchProgress.makeKey(
            kind = kind,
            tmdbId = s.tmdbId,
            isMovie = s.isMovie,
            seasonNumber = s.seasonNumber,
            episodeNumber = s.episodeNumber,
            addonId = resumeAddonId,
            stremioType = resumeStremioType,
            stremioId = resumeStremioId,
        )

        val entry = WatchProgress(
            key = key,
            kind = kind,
            tmdbId = s.tmdbId,
            imdbId = resumeImdbId,
            isMovie = s.isMovie,
            title = s.title,
            episodeTitle = s.episodeTitle,
            seasonNumber = s.seasonNumber,
            episodeNumber = s.episodeNumber,
            posterUrl = resumePosterUrl,
            backdropUrl = s.backdropUrl,
            logoUrl = s.logoUrl,
            year = s.year,
            rating = s.rating,
            overview = s.overview,
            sourceIndex = if (kind == WatchKind.STREAMING) s.currentSourceIndex else null,
            magnetUri = if (kind == WatchKind.MAGNET) currentMagnetUri else null,
            fileIdx = if (kind == WatchKind.MAGNET) resumeFileIdx else null,
            addonId = resumeAddonId,
            stremioType = resumeStremioType,
            stremioId = resumeStremioId,
            streamPickKey = resumeStreamPickKey,
            streamPickName = resumeStreamPickName,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
        )

        try {
            WatchProgressStore.upsert(entry)
        } catch (e: Exception) {
            Log.w(TAG, "saveCurrentProgress failed: ${e.message}")
        }
    }

    // ── STREAMING MODE ────────────────────────────────────────────────────────

    fun initStreamingPlayer(
        streamUrl: String,
        referer: String,
        sourceIndex: Int,
        title: String,
        logoUrl: String?,
        backdropUrl: String?,
        year: String?,
        rating: String?,
        overview: String?,
        isMovie: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        tmdbId: Int,
        isIptv: Boolean = false,
    ) {
        if (player != null) return
        failedSourceIndices.clear()

        _uiState.update {
            it.copy(
                title = title,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                year = year,
                rating = rating,
                overview = overview,
                isMovie = isMovie,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                tmdbId = tmdbId,
                isConnecting = true,
                connectionStatus = "Loading stream…",
                isStreamingMode = true,
                isIptv = isIptv,
                currentSourceIndex = sourceIndex,
            )
        }

        val context = getApplication<Application>()
        viewModelScope.launch {
            // IPTV streams have no TMDB context — skip subtitle/skip-segment/episode fetches.
            if (!isIptv) {
                launch {
                    val subs = SubtitleService.fetchSubtitles(
                        tmdbId = tmdbId,
                        season = seasonNumber,
                        episode = episodeNumber
                    )
                    _uiState.update { it.copy(externalSubtitles = subs) }
                    updateSubtitleTrackList()

                    // Also fetch from Stremio subtitle addons
                    fetchStremioSubtitles(tmdbId, isMovie, seasonNumber, episodeNumber)
                }

                launch {
                    val segments = SkipSegmentService.fetchSegments(tmdbId, isMovie, seasonNumber, episodeNumber)
                    _uiState.update { it.copy(skipSegments = segments) }
                    Log.i(TAG, "Skip segments loaded (streaming): ${segments.size}")
                }
                if (!isMovie && seasonNumber != null) {
                    launch { loadEpisodesForCurrentSeries() }
                }
            }
            withContext(Dispatchers.Main) {
                if (isIptv) {
                    createIptvPlayer(streamUrl, referer)
                } else {
                    createStreamingPlayer(streamUrl, referer)
                }
            }
        }
    }

    private fun createStreamingPlayer(streamUrl: String, referer: String) {
        val context = getApplication<Application>()
        resetStartupRetryState()
        currentReferer = referer

        val headers = buildMap<String, String> {
            if (referer.isNotBlank()) {
                put("Referer", referer)
                // Derive origin as scheme + host (e.g. "https://lordflix.org")
                val origin = try {
                    val uri = android.net.Uri.parse(referer)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { referer.trimEnd('/') }
                put("Origin", origin)
            }
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)

        val msFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 120_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(msFactory)
            .setLoadControl(loadControl)
            .build()
        player = exo

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        exo.setAudioAttributes(audioAttrs, false)
        exo.volume = 1.0f

        currentStreamUrl = streamUrl
        // Let ExoPlayer auto-detect from Content-Type header. Works for .m3u8 (HLS),
        // .mp4 (progressive), and direct download URLs from 4KHDHub/HDHub4u.
        val mediaItem = MediaItem.fromUri(streamUrl)
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.update {
                    it.copy(
                        isBuffering = state == Player.STATE_BUFFERING,
                        isPlaying = exo.isPlaying,
                        isConnecting = if (state == Player.STATE_READY) false else it.isConnecting
                    )
                }
                if (state == Player.STATE_READY) {
                    resetStartupRetryState()
                    _uiState.update {
                        it.copy(duration = exo.duration.coerceAtLeast(0), isConnecting = false)
                    }
                    updateTracks()
                    autoSelectPendingSubtitle()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _uiState.update { it.copy(isPlaying = playing) }
                if (!playing && exo.playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(showPauseOverlay = true, showControls = false) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) { updateTracks() }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error (createStreamingPlayer): ${error.errorCodeName}", error)
                scheduleStreamStartupRetry(exo, error)
            }
        })

        startPositionUpdater()
        scheduleControlsHide()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  IPTV player path — dedicated builder with stall watchdog + tiered
    //  recovery. Used whenever isIptv=true. Live IPTV portals are flaky:
    //  segments stop arriving, sockets die silently, and ExoPlayer often
    //  doesn't surface a PlaybackException for these freezes. We watch
    //  player state ourselves and escalate recovery in stages.
    // ─────────────────────────────────────────────────────────────────────────
    private fun createIptvPlayer(streamUrl: String, referer: String) {
        val context = getApplication<Application>()
        resetStartupRetryState()
        stopIptvWatchdog()
        iptvRetryAttempt = 0
        currentReferer = referer
        currentStreamUrl = streamUrl

        val headers = buildMap<String, String> {
            if (referer.isNotBlank()) {
                put("Referer", referer)
                val origin = try {
                    val uri = android.net.Uri.parse(referer)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { referer.trimEnd('/') }
                put("Origin", origin)
            }
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        // Custom error policy: be very patient with transient HTTP errors that
        // are typical of overloaded IPTV portals (502/503/504, socket resets).
        val errorPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
            override fun getRetryDelayMsFor(
                loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                // Exponential-ish backoff capped at 5s so we keep retrying
                // chunk loads aggressively but don't hammer a dead portal.
                val attempt = loadErrorInfo.errorCount.coerceAtLeast(1)
                return (1_000L * attempt).coerceAtMost(5_000L)
            }
        }

        val msFactory = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)

        // Live-tuned buffer: small + reactive. Big buffers hide stalls and
        // delay our watchdog's reaction.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                4_000,    // min buffer
                30_000,   // max buffer
                1_500,    // playback start threshold
                3_000     // rebuffer threshold
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(msFactory)
            .setLoadControl(loadControl)
            .build()
        player = exo

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        exo.setAudioAttributes(audioAttrs, false)
        exo.volume = 1.0f

        Log.i(TAG, "Creating IPTV player for $streamUrl")
        exo.setMediaItem(MediaItem.fromUri(streamUrl))
        exo.prepare()
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.update {
                    it.copy(
                        isBuffering = state == Player.STATE_BUFFERING,
                        isPlaying = exo.isPlaying,
                        isConnecting = if (state == Player.STATE_READY) false else it.isConnecting
                    )
                }
                when (state) {
                    Player.STATE_BUFFERING -> {
                        if (iptvBufferingSinceMs == 0L) {
                            iptvBufferingSinceMs = System.currentTimeMillis()
                        }
                    }
                    Player.STATE_READY -> {
                        iptvBufferingSinceMs = 0L
                        iptvLastHealthyAt = System.currentTimeMillis()
                        // Try to restore the wallclock position we were at
                        // before recovery, so the user doesn't miss the
                        // seconds spent reconnecting. Falls back silently if
                        // the stream has no PROGRAM-DATE-TIME / DVR window.
                        tryRestoreIptvPosition(exo)
                        _uiState.update {
                            it.copy(
                                duration = exo.duration.coerceAtLeast(0),
                                isConnecting = false,
                                isReconnecting = false,
                                reconnectStatus = ""
                            )
                        }
                        updateTracks()
                        autoSelectPendingSubtitle()
                    }
                    Player.STATE_ENDED -> {
                        // Live streams should never END normally — treat as a stall.
                        Log.w(TAG, "IPTV stream ended unexpectedly — recovering")
                        recoverIptvStream("stream-ended")
                    }
                    else -> { /* IDLE handled by error/recovery path */ }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _uiState.update { it.copy(isPlaying = playing) }
                if (!playing && exo.playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(showPauseOverlay = true, showControls = false) }
                }
            }

            override fun onTracksChanged(tracks: Tracks) { updateTracks() }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "IPTV playback error: ${error.errorCodeName}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Standard Media3 fix: jump to live edge and re-prepare.
                    Log.i(TAG, "BEHIND_LIVE_WINDOW — seeking to live edge")
                    try {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                    } catch (e: Exception) {
                        Log.w(TAG, "BEHIND_LIVE_WINDOW recovery failed: ${e.message}")
                        recoverIptvStream("behind-live-window-failed")
                    }
                    return
                }
                recoverIptvStream("error:${error.errorCodeName}")
            }
        })

        startIptvWatchdog(exo)
        startPositionUpdater()
        scheduleControlsHide()
    }

    private fun startIptvWatchdog(exo: ExoPlayer) {
        stopIptvWatchdog()
        iptvLastPositionMs = -1L
        iptvLastPositionAt = System.currentTimeMillis()
        iptvLastHealthyAt = System.currentTimeMillis()
        iptvHealthyStreakStartMs = 0L
        iptvBufferingSinceMs = 0L

        iptvWatchdogJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val current = player ?: return@launch
                if (current !== exo) return@launch  // player rebuilt — old watchdog dies
                if (!_uiState.value.isIptv) return@launch

                val now = System.currentTimeMillis()
                val state = current.playbackState
                val pos = current.currentPosition

                // ── Position tracking + healthy streak ────────────────────
                if (state == Player.STATE_READY && current.isPlaying) {
                    if (pos != iptvLastPositionMs) {
                        iptvLastPositionMs = pos
                        iptvLastPositionAt = now
                        iptvLastHealthyAt = now
                        if (iptvHealthyStreakStartMs == 0L) iptvHealthyStreakStartMs = now
                        if (iptvRetryAttempt > 0 &&
                            (now - iptvHealthyStreakStartMs) >= iptvHealthyResetMs
                        ) {
                            Log.i(TAG, "IPTV healthy streak — resetting retry counter (was $iptvRetryAttempt)")
                            iptvRetryAttempt = 0
                        }
                    } else {
                        iptvHealthyStreakStartMs = 0L
                    }
                } else {
                    iptvHealthyStreakStartMs = 0L
                }

                // ── Stall detector 1: stuck buffering ──────────────────────
                if (state == Player.STATE_BUFFERING && current.playWhenReady && iptvBufferingSinceMs > 0L) {
                    val bufferedFor = now - iptvBufferingSinceMs
                    if (bufferedFor >= iptvBufferingStallMs) {
                        Log.w(TAG, "IPTV buffering stall detected (${bufferedFor}ms) — recovering")
                        iptvBufferingSinceMs = 0L
                        recoverIptvStream("buffering-stall")
                        continue
                    }
                }

                // ── Stall detector 2: STATE_READY + isPlaying but position frozen ─────
                // Gated on iptvLastPositionMs >= 0L so we don't fire before the first
                // real position tick — avoids false positives during initial buffering.
                if (state == Player.STATE_READY && current.isPlaying && iptvLastPositionMs >= 0L) {
                    val frozenFor = now - iptvLastPositionAt
                    if (frozenFor >= iptvFrozenPositionMs) {
                        Log.w(TAG, "IPTV position frozen for ${frozenFor}ms at $pos — recovering")
                        iptvLastPositionAt = now
                        recoverIptvStream("position-frozen")
                        continue
                    }
                }

                // ── Stall detector 3: READY + playWhenReady but not playing ────────
                // Catches audio focus loss, surface theft, renderer stalls — cases
                // where ExoPlayer reports READY but isPlaying stays false.
                if (state == Player.STATE_READY && current.playWhenReady && !current.isPlaying) {
                    val stalledFor = now - iptvLastHealthyAt
                    if (stalledFor >= iptvFrozenPositionMs) {
                        Log.w(TAG, "IPTV ready+playWhenReady but not playing for ${stalledFor}ms — recovering")
                        iptvLastHealthyAt = now
                        recoverIptvStream("ready-not-playing")
                        continue
                    }
                }
            }
        }
    }

    private var lastIptvRecoveryAt: Long = 0L

    private fun stopIptvWatchdog() {
        iptvWatchdogJob?.cancel()
        iptvWatchdogJob = null
    }

    /**
     * After an IPTV recovery completes (player reaches STATE_READY), try to
     * seek back to the wallclock position the user was at when the stall hit.
     * This avoids losing the seconds we spent reconnecting.
     *
     * Mechanics: we saved a unix timestamp before recovery (windowStartTimeMs
     * + currentPosition). The new live window has its own windowStartTimeMs.
     * The position within the new window that corresponds to our saved unix
     * time is `savedUnix - newWindow.windowStartTimeMs`. If that value falls
     * within the new window's duration, we seek there. Otherwise we leave the
     * player at its default (live edge).
     *
     * Only works when the upstream emits PROGRAM-DATE-TIME (HLS) or has a
     * known availabilityStartTime (DASH). Most public IPTV portals do.
     */
    private fun tryRestoreIptvPosition(exo: ExoPlayer) {
        val savedUnix = iptvSavedUnixMs
        if (savedUnix == C.TIME_UNSET) return
        // One-shot: clear so we don't re-seek on subsequent STATE_READY events
        // (e.g. after the seek itself triggers BUFFERING → READY).
        iptvSavedUnixMs = C.TIME_UNSET
        try {
            val timeline = exo.currentTimeline
            if (timeline.isEmpty) return
            val win = timeline.getWindow(exo.currentMediaItemIndex, androidx.media3.common.Timeline.Window())
            val newStart = win.windowStartTimeMs
            if (newStart == C.TIME_UNSET) return
            val target = savedUnix - newStart
            val winDur = win.durationMs
            if (winDur == C.TIME_UNSET || winDur <= 0L) return
            // Clamp inside window with a small safety margin from the live edge
            // so we don't immediately bump into BehindLiveWindowException at the
            // tail or trigger another recovery at the head.
            val safeTarget = target.coerceIn(2_000L, (winDur - 2_000L).coerceAtLeast(2_000L))
            // Only resume if the saved position actually falls inside the new
            // window — if recovery took longer than the DVR window, our spot
            // has scrolled out and the best we can do is the live edge.
            if (target < 0L || target > winDur) {
                Log.i(TAG, "IPTV restore skipped: saved offset=$target outside window 0..$winDur")
                return
            }
            Log.i(TAG, "IPTV restore: seeking to ${safeTarget}ms (savedUnix=$savedUnix, newStart=$newStart, winDur=$winDur)")
            exo.seekTo(safeTarget)
        } catch (e: Exception) {
            Log.w(TAG, "IPTV restore failed: ${e.message}")
        }
    }

    /**
     * Tiered IPTV recovery:
     *   Attempt 1-2: seekToDefaultPosition() + prepare()  — cheapest, fixes
     *               most live-edge / dropped-segment hiccups.
     *   Attempt 3-4: stop() + clearMediaItems() + setMediaItem() + prepare()
     *               — fresh MediaSource, new HTTP connections.
     *   Attempt 5+:  release() and rebuild via createIptvPlayer() — what
     *               "exit and reopen the channel" does manually.
     *   After iptvMaxRetries, surface error.
     */
    private fun recoverIptvStream(reason: String) {
        val exo = player ?: return
        if (!_uiState.value.isIptv) return
        val url = currentStreamUrl ?: return
        val referer = currentReferer

        val now = System.currentTimeMillis()
        // Throttle: never run two recoveries within 1.5s of each other.
        if (now - lastIptvRecoveryAt < 1_500L) {
            Log.i(TAG, "IPTV recovery throttled (reason=$reason)")
            return
        }
        lastIptvRecoveryAt = now

        if (iptvRetryAttempt >= iptvMaxRetries) {
            Log.e(TAG, "IPTV recovery exhausted after $iptvRetryAttempt attempts (reason=$reason)")
            stopIptvWatchdog()
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    isReconnecting = false,
                    reconnectStatus = "",
                    error = "Stream unavailable. Please try another source."
                )
            }
            return
        }

        iptvRetryAttempt += 1
        val attempt = iptvRetryAttempt

        // Capture the wallclock position we're currently at so we can resume
        // exactly here after recovery (instead of jumping forward to the new
        // live edge and losing the seconds spent reconnecting). Only works
        // for streams that expose a windowStartTimeMs (HLS with EXT-X-PROGRAM-
        // DATE-TIME or DASH with availabilityStartTime). For others this
        // stays C.TIME_UNSET and we fall back to the live edge.
        iptvSavedUnixMs = try {
            val timeline = exo.currentTimeline
            if (!timeline.isEmpty) {
                val win = timeline.getWindow(exo.currentMediaItemIndex, androidx.media3.common.Timeline.Window())
                if (win.windowStartTimeMs != C.TIME_UNSET) {
                    val pos = exo.currentPosition.coerceAtLeast(0L)
                    win.windowStartTimeMs + pos
                } else C.TIME_UNSET
            } else C.TIME_UNSET
        } catch (_: Exception) { C.TIME_UNSET }
        if (iptvSavedUnixMs != C.TIME_UNSET) {
            Log.i(TAG, "IPTV recovery saved unix=$iptvSavedUnixMs (will try to resume)")
        }

        // Exponential-ish backoff with a cap.
        val backoffMs = when (attempt) {
            1 -> 500L
            2 -> 1_000L
            3 -> 2_000L
            4 -> 3_000L
            5 -> 4_000L
            6 -> 6_000L
            else -> 8_000L
        }

        _uiState.update {
            it.copy(
                isReconnecting = true,
                reconnectStatus = "Reconnecting… ($attempt/$iptvMaxRetries)"
            )
        }
        Log.w(TAG, "IPTV recover #$attempt (reason=$reason, backoff=${backoffMs}ms)")

        viewModelScope.launch {
            delay(backoffMs)
            // Player may have been rebuilt or released during the delay — always
            // bail if the instance changed; the new player has its own watchdog.
            val cur = player ?: return@launch
            if (cur !== exo) return@launch
            if (!_uiState.value.isIptv) return@launch

            // Reset trackers BEFORE touching the player so a watchdog tick
            // racing the listener can't trigger another recovery on stale data.
            iptvBufferingSinceMs = 0L
            iptvLastPositionMs = -1L
            iptvLastPositionAt = System.currentTimeMillis()
            iptvHealthyStreakStartMs = 0L

            try {
                when {
                    attempt <= 2 -> {
                        // Tier 1: seek to live edge + re-prepare.
                        Log.i(TAG, "IPTV recover tier 1: seekToDefault + prepare")
                        cur.seekToDefaultPosition()
                        cur.prepare()
                        cur.playWhenReady = true
                    }
                    attempt <= 4 -> {
                        // Tier 2: full re-prepare with fresh MediaItem.
                        Log.i(TAG, "IPTV recover tier 2: full re-prepare")
                        cur.stop()
                        cur.clearMediaItems()
                        cur.setMediaItem(MediaItem.fromUri(url))
                        cur.prepare()
                        cur.playWhenReady = true
                    }
                    else -> {
                        // Tier 3: nuke and rebuild player from scratch.
                        Log.i(TAG, "IPTV recover tier 3: rebuild player")
                        stopIptvWatchdog()
                        try { cur.release() } catch (_: Exception) {}
                        if (player === cur) player = null
                        // Preserve the current attempt counter across rebuild
                        // so we don't loop forever if rebuild also stalls.
                        val savedAttempt = iptvRetryAttempt
                        createIptvPlayer(url, referer)
                        iptvRetryAttempt = savedAttempt
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "IPTV recover attempt $attempt threw: ${e.message}")
            }
        }
    }

    fun showSourcesPanel() {
        _uiState.update { it.copy(showSourcesPanel = true, showControls = false) }
    }

    fun dismissSourcesPanel() {
        _uiState.update { it.copy(showSourcesPanel = false) }
        showControls()
    }

    fun switchToSource(sourceIdx: Int) {
        val state = _uiState.value
        Log.i(TAG, "switchToSource($sourceIdx) tmdbId=${state.tmdbId} season=${state.seasonNumber} episode=${state.episodeNumber} isMovie=${state.isMovie}")
        _uiState.update { it.copy(showSourcesPanel = false, isSwitchingSource = true) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = StreamExtractorService.extract(
                context = context,
                sourceIdx = sourceIdx,
                tmdbId = state.tmdbId,
                season = if (state.isMovie) null else state.seasonNumber,
                episode = if (state.isMovie) null else state.episodeNumber,
                timeoutMs = 20_000L
            )
            if (result != null) {
                withContext(Dispatchers.Main) {
                    player?.release()
                    player = null
                    _uiState.update {
                        it.copy(
                            isSwitchingSource = false,
                            currentSourceIndex = sourceIdx,
                            isConnecting = true,
                            connectionStatus = "Loading ${StreamExtractorService.SOURCES.find { it.index == sourceIdx }?.name ?: "source"}…"
                        )
                    }
                    createStreamingPlayer(result.url, result.referer)
                }
            } else {
                // Extraction failed for this source — fall back to the next one in priority order.
                Log.w(TAG, "Extraction returned null for source $sourceIdx, falling back")
                failedSourceIndices.add(sourceIdx)
                val nextIdx = orderedSourceIndices.firstOrNull { it !in failedSourceIndices }
                if (nextIdx != null) {
                    val nextName = StreamExtractorService.SOURCES.find { it.index == nextIdx }?.name ?: "source"
                    val failedName = StreamExtractorService.SOURCES.find { it.index == sourceIdx }?.name ?: "source"
                    Log.i(TAG, "Falling back from $failedName to $nextName")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isSwitchingSource = false,
                                isConnecting = true,
                                connectionStatus = "$failedName failed, trying $nextName…"
                            )
                        }
                        switchToSource(nextIdx)
                    }
                } else {
                    Log.w(TAG, "All sources exhausted during extraction fallback")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isSwitchingSource = false,
                                isConnecting = false,
                                error = "All sources failed to load"
                            )
                        }
                        showControls()
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Episodes panel + in-place episode switching
    // ════════════════════════════════════════════════════════════════════════

    fun showEpisodesPanel() {
        val s = _uiState.value
        if (s.isMovie) return
        _uiState.update { it.copy(showEpisodesPanel = true, showControls = false) }
        if (s.episodes.isEmpty() && !s.isLoadingEpisodes) {
            loadEpisodesForCurrentSeries()
        }
    }

    fun dismissEpisodesPanel() {
        _uiState.update { it.copy(showEpisodesPanel = false) }
        showControls()
    }

    fun loadEpisodesForCurrentSeries() {
        val s = _uiState.value
        val tvId = s.tmdbId.takeIf { it > 0 } ?: return
        val season = s.seasonNumber ?: return
        if (s.isLoadingEpisodes) return
        _uiState.update { it.copy(isLoadingEpisodes = true) }
        viewModelScope.launch {
            try {
                val seasonData = TmdbClient.api.getTvSeason(tvId, season, TmdbClient.API_KEY)
                val eps = (seasonData.episodes ?: emptyList()).map {
                    if (it.seasonNumber == null) it.copy(seasonNumber = season) else it
                }
                _uiState.update { it.copy(episodes = eps, isLoadingEpisodes = false) }
                computeNextEpisode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load season episodes", e)
                _uiState.update { it.copy(isLoadingEpisodes = false) }
            }
        }
    }

    private fun computeNextEpisode() {
        val s = _uiState.value
        val curEp = s.episodeNumber ?: return
        val next = s.episodes.firstOrNull { it.episodeNumber == curEp + 1 }
        _uiState.update { it.copy(nextEpisode = next) }
    }

    /** User picked an episode in the panel. Branches by playback mode/origin. */
    fun pickEpisode(episode: com.playtorrio.tv.data.model.Episode) {
        val s = _uiState.value
        if (s.isSwitchingEpisode) return
        // Skip no-op
        if (episode.seasonNumber == s.seasonNumber && episode.episodeNumber == s.episodeNumber) {
            dismissEpisodesPanel()
            return
        }

        if (s.isStreamingMode) {
            switchToStreamingEpisode(episode)
            return
        }

        // Non-streaming: open source picker overlay matching original origin
        when {
            resumeAddonId != null && resumeStremioType != null -> {
                openEpisodeSourceOverlay(episode, EpisodeOverlayKind.ADDON_STREAM)
            }
            currentMagnetUri != null -> {
                openEpisodeSourceOverlay(episode, EpisodeOverlayKind.TORRENT)
            }
            else -> {
                // Default to torrent search
                openEpisodeSourceOverlay(episode, EpisodeOverlayKind.TORRENT)
            }
        }
    }

    fun playNextEpisode() {
        val ep = _uiState.value.nextEpisode ?: return
        pickEpisode(ep)
    }

    private fun openEpisodeSourceOverlay(
        episode: com.playtorrio.tv.data.model.Episode,
        kind: EpisodeOverlayKind
    ) {
        _uiState.update {
            it.copy(
                showEpisodesPanel = false,
                showEpisodeSourceOverlay = true,
                episodeOverlayKind = kind,
                pendingEpisode = episode,
                episodeOverlayTorrents = emptyList(),
                episodeOverlayStreams = emptyList(),
                isLoadingEpisodeOverlay = true,
            )
        }
        viewModelScope.launch {
            try {
                when (kind) {
                    EpisodeOverlayKind.TORRENT -> {
                        val s = _uiState.value
                        val sNum = episode.seasonNumber ?: s.seasonNumber
                        val results = com.playtorrio.tv.data.torrent.TorrentSearchService.search(
                            com.playtorrio.tv.data.torrent.TorrentSearchRequest(
                                title = s.title,
                                seasonNumber = sNum,
                                episodeNumber = episode.episodeNumber,
                                isMovie = false
                            )
                        )
                        _uiState.update {
                            it.copy(episodeOverlayTorrents = results, isLoadingEpisodeOverlay = false)
                        }
                    }
                    EpisodeOverlayKind.ADDON_STREAM -> {
                        val imdb = resumeImdbId ?: ensureImdbIdForCurrent()
                        if (imdb == null) {
                            _uiState.update { it.copy(isLoadingEpisodeOverlay = false) }
                            return@launch
                        }
                        val sNum = episode.seasonNumber ?: _uiState.value.seasonNumber
                        val videoId = "$imdb:${sNum}:${episode.episodeNumber}"
                        val addons = com.playtorrio.tv.data.stremio.StremioAddonRepository.getAddons()
                        val streams = com.playtorrio.tv.data.stremio.StremioService.getStreams(
                            addons = addons,
                            type = "series",
                            id = videoId,
                            preferredAddonId = resumeAddonId
                        )
                        // If we know the original addon, prioritize its streams to top
                        val sorted = if (resumeAddonId != null) {
                            streams.sortedByDescending { it.addonId == resumeAddonId }
                        } else streams
                        _uiState.update {
                            it.copy(episodeOverlayStreams = sorted, isLoadingEpisodeOverlay = false)
                        }
                    }
                    EpisodeOverlayKind.NONE -> {
                        _uiState.update { it.copy(isLoadingEpisodeOverlay = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Episode source overlay load failed", e)
                _uiState.update { it.copy(isLoadingEpisodeOverlay = false) }
            }
        }
    }

    private suspend fun ensureImdbIdForCurrent(): String? {
        val s = _uiState.value
        val tmdbId = s.tmdbId.takeIf { it > 0 } ?: return null
        return runCatching {
            TmdbClient.api.getTvExternalIds(tmdbId, TmdbClient.API_KEY).imdbId
        }.getOrNull()?.takeIf { it.startsWith("tt") }
    }

    fun dismissEpisodeSourceOverlay() {
        _uiState.update {
            it.copy(
                showEpisodeSourceOverlay = false,
                pendingEpisode = null,
                episodeOverlayTorrents = emptyList(),
                episodeOverlayStreams = emptyList(),
                isLoadingEpisodeOverlay = false,
            )
        }
        showControls()
    }

    fun pickEpisodeTorrent(torrent: com.playtorrio.tv.data.torrent.TorrentResult) {
        val ep = _uiState.value.pendingEpisode ?: return
        switchToMagnetEpisode(torrent.magnetLink, fileIdx = null, episode = ep)
    }

    fun pickEpisodeStremioStream(stream: com.playtorrio.tv.data.stremio.StremioStream) {
        val ep = _uiState.value.pendingEpisode ?: return
        when (val route = com.playtorrio.tv.data.stremio.StremioService.routeStream(stream)) {
            is com.playtorrio.tv.data.stremio.StreamRoute.DirectUrl -> {
                switchToDirectUrlEpisode(route.url, route.headers, episode = ep, stream = stream)
            }
            is com.playtorrio.tv.data.stremio.StreamRoute.Torrent -> {
                switchToMagnetEpisode(route.magnet, route.fileIdx, episode = ep, stream = stream)
            }
            else -> {
                Log.w(TAG, "Unsupported stream route for episode switch: $route")
                _uiState.update { it.copy(error = "Unsupported stream type") }
            }
        }
    }

    /** Streaming-mode switch: re-extract for new episode + swap player in-place. */
    private fun switchToStreamingEpisode(episode: com.playtorrio.tv.data.model.Episode) {
        val s = _uiState.value
        val tmdbId = s.tmdbId.takeIf { it > 0 } ?: return
        val sNum = episode.seasonNumber ?: s.seasonNumber ?: return
        val eNum = episode.episodeNumber
        flushProgressInternal()
        _uiState.update {
            it.copy(
                showEpisodesPanel = false,
                isSwitchingEpisode = true,
                isConnecting = true,
                connectionStatus = "Loading S${sNum}E${eNum}…",
                seasonNumber = sNum,
                episodeNumber = eNum,
                episodeTitle = episode.name,
                duration = 0L,
                currentPosition = 0L,
                activeSkipSegment = null,
                skipSegments = emptyList(),
                externalSubtitles = emptyList(),
                nextEpisode = null,
            )
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val result = com.playtorrio.tv.data.streaming.StreamExtractorService.extract(
                    context = context,
                    sourceIdx = s.currentSourceIndex,
                    tmdbId = tmdbId,
                    season = sNum,
                    episode = eNum,
                    timeoutMs = 20_000L,
                )
                if (result == null) {
                    _uiState.update {
                        it.copy(
                            isSwitchingEpisode = false,
                            isConnecting = false,
                            error = "Failed to extract stream for next episode"
                        )
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    addedExternalSubConfigs.clear()
                    addedExternalSubLabels.clear()
                    player?.release()
                    player = null
                    _uiState.update { it.copy(isSwitchingEpisode = false) }
                    createStreamingPlayer(result.url, result.referer)
                }
                refetchSubsAndSkipForEpisode(episode)
                computeNextEpisode()
            } catch (e: Exception) {
                Log.e(TAG, "switchToStreamingEpisode failed", e)
                _uiState.update {
                    it.copy(isSwitchingEpisode = false, isConnecting = false, error = e.message)
                }
            }
        }
    }

    private fun switchToDirectUrlEpisode(
        url: String,
        headers: Map<String, String>?,
        episode: com.playtorrio.tv.data.model.Episode,
        stream: com.playtorrio.tv.data.stremio.StremioStream,
    ) {
        val sNum = episode.seasonNumber ?: _uiState.value.seasonNumber ?: return
        val eNum = episode.episodeNumber
        flushProgressInternal()
        // Update resume context for new pick
        resumeStreamPickName = stream.name ?: stream.title
        resumeStreamPickKey = stream.url ?: stream.infoHash
        currentMagnetUri = null
        _uiState.update {
            it.copy(
                showEpisodeSourceOverlay = false,
                pendingEpisode = null,
                isSwitchingEpisode = true,
                isConnecting = true,
                connectionStatus = "Loading S${sNum}E${eNum}…",
                seasonNumber = sNum,
                episodeNumber = eNum,
                episodeTitle = episode.name,
                duration = 0L,
                currentPosition = 0L,
                activeSkipSegment = null,
                skipSegments = emptyList(),
                externalSubtitles = emptyList(),
                nextEpisode = null,
            )
        }
        viewModelScope.launch {
            try {
                // Tear down current torrent if any
                val oldHash = _uiState.value.torrentHash
                if (!oldHash.isNullOrBlank()) {
                    runCatching { com.playtorrio.tv.data.torrent.TorrServerService.removeTorrent(oldHash) }
                }
                withContext(Dispatchers.Main) {
                    addedExternalSubConfigs.clear()
                    addedExternalSubLabels.clear()
                    player?.release()
                    player = null
                    _uiState.update { it.copy(isSwitchingEpisode = false, torrentHash = null) }
                    val referer = headers?.get("Referer") ?: headers?.get("referer") ?: ""
                    if (referer.isNotBlank()) {
                        createStreamingPlayer(url, referer)
                    } else {
                        createPlayer(url)
                    }
                }
                refetchSubsAndSkipForEpisode(episode)
                computeNextEpisode()
            } catch (e: Exception) {
                Log.e(TAG, "switchToDirectUrlEpisode failed", e)
                _uiState.update {
                    it.copy(isSwitchingEpisode = false, isConnecting = false, error = e.message)
                }
            }
        }
    }

    private fun switchToMagnetEpisode(
        magnet: String,
        fileIdx: Int?,
        episode: com.playtorrio.tv.data.model.Episode,
        stream: com.playtorrio.tv.data.stremio.StremioStream? = null,
    ) {
        val sNum = episode.seasonNumber ?: _uiState.value.seasonNumber ?: return
        val eNum = episode.episodeNumber
        flushProgressInternal()
        currentMagnetUri = magnet
        resumeFileIdx = fileIdx
        if (stream != null) {
            resumeStreamPickName = stream.name ?: stream.title
            resumeStreamPickKey = stream.infoHash ?: stream.url
        }
        _uiState.update {
            it.copy(
                showEpisodeSourceOverlay = false,
                pendingEpisode = null,
                isSwitchingEpisode = true,
                isConnecting = true,
                connectionStatus = "Switching torrent…",
                seasonNumber = sNum,
                episodeNumber = eNum,
                episodeTitle = episode.name,
                duration = 0L,
                currentPosition = 0L,
                activeSkipSegment = null,
                skipSegments = emptyList(),
                externalSubtitles = emptyList(),
                nextEpisode = null,
            )
        }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // 1. Tear down old torrent
                val oldHash = _uiState.value.torrentHash
                if (!oldHash.isNullOrBlank()) {
                    runCatching { com.playtorrio.tv.data.torrent.TorrServerService.removeTorrent(oldHash) }
                }
                statsJob?.cancel()
                // 2. Resolve new
                if (AppPreferences.debridEnabled) {
                    _uiState.update { it.copy(connectionStatus = "Resolving via debrid…") }
                    val debridUrl = com.playtorrio.tv.data.debrid.DebridResolver.resolve(magnet, isMovie = false, season = sNum, episode = eNum)
                        ?: throw IllegalStateException("Debrid could not resolve this torrent")
                    withContext(Dispatchers.Main) {
                        addedExternalSubConfigs.clear()
                        addedExternalSubLabels.clear()
                        player?.release()
                        player = null
                        _uiState.update { it.copy(isSwitchingEpisode = false, torrentHash = null) }
                        createPlayer(debridUrl)
                    }
                } else {
                    _uiState.update { it.copy(connectionStatus = "Adding torrent…") }
                    com.playtorrio.tv.data.torrent.TorrServerService.ensureInitialized(context)
                    val result = com.playtorrio.tv.data.torrent.TorrServerService.startStreaming(
                        context = context,
                        magnetUri = magnet,
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                        fileIdx = fileIdx,
                    )
                    withContext(Dispatchers.Main) {
                        addedExternalSubConfigs.clear()
                        addedExternalSubLabels.clear()
                        player?.release()
                        player = null
                        _uiState.update {
                            it.copy(
                                isSwitchingEpisode = false,
                                torrentHash = result.hash,
                            )
                        }
                        startStatsPoller(result.hash)
                        createPlayer(result.url)
                    }
                }
                refetchSubsAndSkipForEpisode(episode)
                computeNextEpisode()
            } catch (e: Exception) {
                Log.e(TAG, "switchToMagnetEpisode failed", e)
                _uiState.update {
                    it.copy(isSwitchingEpisode = false, isConnecting = false, error = e.message ?: "Switch failed")
                }
            }
        }
    }

    private suspend fun refetchSubsAndSkipForEpisode(episode: com.playtorrio.tv.data.model.Episode) {
        val s = _uiState.value
        val tmdbId = s.tmdbId.takeIf { it > 0 } ?: return
        val sNum = episode.seasonNumber ?: s.seasonNumber ?: return
        val eNum = episode.episodeNumber
        coroutineScope {
            launch {
                runCatching {
                    val subs = com.playtorrio.tv.data.subtitle.SubtitleService.fetchSubtitles(
                        tmdbId = tmdbId,
                        season = sNum,
                        episode = eNum,
                    )
                    _uiState.update { it.copy(externalSubtitles = subs) }
                    withContext(Dispatchers.Main) { updateSubtitleTrackList() }
                    fetchStremioSubtitles(tmdbId, false, sNum, eNum)
                }
            }
            launch {
                runCatching {
                    val segs = com.playtorrio.tv.data.skip.SkipSegmentService.fetchSegments(
                        tmdbId, false, sNum, eNum
                    )
                    _uiState.update { it.copy(skipSegments = segs) }
                }
            }
        }
    }

    /** Synchronous best-effort flush for episode switch. */
    private fun flushProgressInternal() {
        runCatching { flushProgress() }
    }
}
