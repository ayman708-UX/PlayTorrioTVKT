package com.playtorrio.tv.core.audiobook.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookChapter
import com.playtorrio.tv.core.audiobook.repository.AudiobookProgressRepository
import com.playtorrio.tv.core.debrid.DirectDebridPlayableResult
import com.playtorrio.tv.core.debrid.DirectDebridResolver
import com.playtorrio.tv.core.torrent.TorrentService
import com.playtorrio.tv.core.torrent.TorrentSettings
import com.playtorrio.tv.data.local.DebridSettingsDataStore
import com.playtorrio.tv.domain.model.Stream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudiobookPlayerController"

@Singleton
class AudiobookPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressRepository: AudiobookProgressRepository,
    private val directDebridResolver: DirectDebridResolver,
    private val debridSettingsDataStore: DebridSettingsDataStore,
    private val torrentSettings: TorrentSettings,
    private val torrentService: TorrentService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()

    private val _chapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val chapters: StateFlow<List<AudiobookChapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // P2P Consent handling for AudiobookBay torrents
    private val _needsP2pConsent = MutableStateFlow(false)
    val needsP2pConsent: StateFlow<Boolean> = _needsP2pConsent.asStateFlow()

    private data class PendingTorrentPlay(
        val book: Audiobook,
        val chapters: List<AudiobookChapter>,
        val chapterIndex: Int,
        val startPositionMs: Long
    )
    private var pendingTorrentPlay: PendingTorrentPlay? = null

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var playJob: Job? = null

    init {
        initPlayer()
    }

    private fun createDataSourceFactory(customHeaders: Map<String, String>? = null): DefaultHttpDataSource.Factory {
        val factory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")

        customHeaders?.let { headers ->
            factory.setDefaultRequestProperties(headers)
        }
        return factory
    }

    private fun initPlayer(customHeaders: Map<String, String>? = null) {
        player?.release()
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(createDataSourceFactory(customHeaders))

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playbackParameters = PlaybackParameters(_playbackSpeed.value)
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            _isLoading.value = false
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                            saveCurrentProgress()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isLoading.value = true
                            }
                            Player.STATE_READY -> {
                                _isLoading.value = false
                                _durationMs.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                            }
                            Player.STATE_ENDED -> {
                                _isPlaying.value = false
                                _isLoading.value = false
                                stopProgressUpdates()
                                saveCurrentProgress()
                                playNextChapter()
                            }
                            Player.STATE_IDLE -> {
                                _isLoading.value = false
                            }
                        }
                    }

                    override fun onPlayerError(err: PlaybackException) {
                        Log.e(TAG, "Audiobook playback error: ${err.message}", err)
                        _error.value = err.message ?: "Playback error"
                        _isLoading.value = false
                        _isPlaying.value = false
                        stopProgressUpdates()
                    }
                })
            }
    }

    fun playBook(
        book: Audiobook,
        chapterList: List<AudiobookChapter>,
        startChapterIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        _currentBook.value = book
        _chapters.value = chapterList
        _error.value = null

        val targetIndex = startChapterIndex.coerceIn(0, (chapterList.size - 1).coerceAtLeast(0))
        playChapter(targetIndex, startPositionMs)
    }

    fun playChapter(index: Int, startPositionMs: Long = 0L) {
        val chapterList = _chapters.value
        if (chapterList.isEmpty() || index !in chapterList.indices) return

        val book = _currentBook.value ?: return
        val chapter = chapterList[index]
        _currentChapterIndex.value = index
        _isLoading.value = true
        _error.value = null
        _currentPositionMs.value = startPositionMs
        _durationMs.value = 0L

        playJob?.cancel()
        playJob = scope.launch {
            try {
                var streamUrl: String = chapter.url
                var headers: Map<String, String>? = chapter.httpHeaders

                // Check if this is an AudiobookBay / Torrent chapter
                if (chapter.isTorrent) {
                    val debridSettings = debridSettingsDataStore.settings.first()
                    val activeCredential = debridSettings.activeResolverCredential
                    val hasDebrid = debridSettings.canResolvePlayableLinks &&
                        activeCredential != null &&
                        debridSettings.apiKeyFor(activeCredential.provider.id).isNotBlank()

                    if (hasDebrid) {
                        // STRICTLY resolve via Debrid ONLY. NEVER fall back to P2P if Debrid is configured.
                        Log.d(TAG, "Attempting Debrid resolution for chapter: ${chapter.title}")
                        try {
                            val st = Stream(
                                name = "AudiobookBay",
                                title = chapter.title,
                                description = null,
                                url = chapter.url.takeIf { it.startsWith("magnet:") },
                                ytId = null,
                                infoHash = chapter.infoHash,
                                fileIdx = chapter.torrentFileIndex,
                                externalUrl = null,
                                behaviorHints = com.playtorrio.tv.domain.model.StreamBehaviorHints(filename = chapter.title),
                                addonName = "AudiobookBay",
                                addonLogo = null,
                                sources = chapter.trackers
                            )
                            when (val debridResult = directDebridResolver.resolveToPlayableStream(st, null, null)) {
                                is DirectDebridPlayableResult.Success -> {
                                    val resolvedUrl = debridResult.stream.getStreamUrl()
                                    if (!resolvedUrl.isNullOrBlank()) {
                                        streamUrl = resolvedUrl
                                        headers = null // Debrid CDN direct stream
                                        Log.d(TAG, "Resolved via Debrid successfully: $resolvedUrl")
                                    } else {
                                        _error.value = "Debrid returned empty stream URL"
                                        _isLoading.value = false
                                        return@launch
                                    }
                                }
                                DirectDebridPlayableResult.MissingApiKey -> {
                                    _error.value = "Debrid API key is missing. Check Settings."
                                    _isLoading.value = false
                                    return@launch
                                }
                                DirectDebridPlayableResult.NotCached -> {
                                    _error.value = "Torrent added to Debrid and downloading. Please retry shortly."
                                    _isLoading.value = false
                                    return@launch
                                }
                                DirectDebridPlayableResult.Stale -> {
                                    _error.value = "Debrid stream timed out or expired. Please retry."
                                    _isLoading.value = false
                                    return@launch
                                }
                                DirectDebridPlayableResult.Error -> {
                                    _error.value = "Debrid resolution failed. Check your Debrid account."
                                    _isLoading.value = false
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Debrid resolution error: ${e.message}", e)
                            _error.value = "Debrid error: ${e.message ?: "Unknown error"}"
                            _isLoading.value = false
                            return@launch
                        }
                    } else {
                        // User has NO Debrid configured at all. Only here can P2P be considered.
                        val torrentData = torrentSettings.settings.first()
                        if (!torrentData.p2pEnabled) {
                            // Prompt P2P warning dialog
                            _needsP2pConsent.value = true
                            pendingTorrentPlay = PendingTorrentPlay(book, chapterList, index, startPositionMs)
                            _isLoading.value = false
                            return@launch
                        }

                        // P2P is explicitly enabled: Start TorrServer stream
                        Log.d(TAG, "Starting TorrServer stream for torrent chapter: ${chapter.title}")
                        val localUrl = torrentService.startStream(
                            infoHash = chapter.infoHash ?: "",
                            fileIdx = chapter.torrentFileIndex,
                            filename = chapter.title,
                            trackers = chapter.trackers
                        )
                        streamUrl = localUrl
                        headers = null
                    }
                }

                // Prepare ExoPlayer
                initPlayer(headers)
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(chapter.title)
                    .setArtist(book.title)
                    .setAlbumTitle(book.author.ifBlank { book.source })
                    .setArtworkUri(if (book.coverImage.isNotBlank()) Uri.parse(book.coverImage) else null)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                player?.let { p ->
                    p.stop()
                    p.setMediaItem(mediaItem)
                    p.prepare()
                    if (startPositionMs > 0L) {
                        p.seekTo(startPositionMs)
                    }
                    p.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing chapter: ${e.message}", e)
                _error.value = e.message ?: "Failed to play chapter"
                _isLoading.value = false
            }
        }
    }

    fun onP2pConsentGranted() {
        _needsP2pConsent.value = false
        torrentSettings.setP2pEnabled(true)
        val pending = pendingTorrentPlay ?: return
        pendingTorrentPlay = null
        playBook(pending.book, pending.chapters, pending.chapterIndex, pending.startPositionMs)
    }

    fun onP2pConsentDismissed() {
        _needsP2pConsent.value = false
        pendingTorrentPlay = null
        _isLoading.value = false
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        saveCurrentProgress()
    }

    fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        val current = p.currentPosition
        val duration = _durationMs.value
        val target = (current + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun playNextChapter() {
        val nextIdx = _currentChapterIndex.value + 1
        if (nextIdx in _chapters.value.indices) {
            playChapter(nextIdx, 0L)
        }
    }

    fun playPreviousChapter() {
        val p = player
        if (p != null && p.currentPosition > 5_000L) {
            seekTo(0L)
            return
        }
        val prevIdx = _currentChapterIndex.value - 1
        if (prevIdx in _chapters.value.indices) {
            playChapter(prevIdx, 0L)
        } else {
            seekTo(0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player?.playbackParameters = PlaybackParameters(speed)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            var counter = 0
            while (isActive) {
                player?.let { p ->
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                // Save progress every ~4 seconds
                counter++
                if (counter >= 10) {
                    counter = 0
                    saveCurrentProgress()
                }
                delay(400L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun saveCurrentProgress() {
        val book = _currentBook.value ?: return
        val chList = _chapters.value
        val idx = _currentChapterIndex.value
        val chTitle = chList.getOrNull(idx)?.title ?: "Chapter ${idx + 1}"
        val pos = _currentPositionMs.value
        val dur = _durationMs.value

        scope.launch(Dispatchers.IO) {
            progressRepository.saveProgress(
                book = book,
                chapterIndex = idx,
                chapterTitle = chTitle,
                positionMs = pos,
                durationMs = dur
            )
        }
    }

    fun stop() {
        Log.d(TAG, "stop() called - stopping ExoPlayer and TorrServer")
        playJob?.cancel()
        playJob = null
        stopProgressUpdates()
        saveCurrentProgress()
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        torrentService.stopStream()
        _currentBook.value = null
        _chapters.value = emptyList()
        _isPlaying.value = false
        _isLoading.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _error.value = null
    }

    fun release() {
        playJob?.cancel()
        playJob = null
        stopProgressUpdates()
        saveCurrentProgress()
        player?.release()
        player = null
        torrentService.stopStream()
        _isPlaying.value = false
        _isLoading.value = false
        _currentBook.value = null
    }
}
