package com.playtorrio.tv.core.music

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import com.playtorrio.tv.core.music.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicPlayerController"

@Singleton
class MusicPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicService: InnerTubeMusicService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _isRepeatEnabled = MutableStateFlow(false)
    val isRepeatEnabled: StateFlow<Boolean> = _isRepeatEnabled.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null

    init {
        initPlayer()
    }

    private fun createDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
    }

    private fun initPlayer() {
        if (player != null) return
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(createDataSourceFactory())
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        _isLoading.value = false
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
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
                            playNext()
                        }
                        Player.STATE_IDLE -> {
                            _isLoading.value = false
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Playback error: ${error.message}", error)
                    _error.value = error.message ?: "Playback error"
                    _isLoading.value = false
                    _isPlaying.value = false
                    stopProgressUpdates()
                }
            })
        }
    }

    fun playTrack(track: MusicTrack, newQueue: List<MusicTrack> = emptyList()) {
        _currentTrack.value = track
        _error.value = null
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (_queue.value.none { it.id == track.id }) {
            _queue.value = listOf(track)
        }

        _isLoading.value = true
        _currentPositionMs.value = 0L
        _durationMs.value = 0L

        scope.launch {
            try {
                val audioUrl = musicService.resolveAudioStream(track.id)
                if (audioUrl.isNullOrBlank()) {
                    _error.value = "Failed to resolve audio stream for ${track.title}"
                    _isLoading.value = false
                    return@launch
                }

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album.orEmpty())
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                initPlayer()
                player?.let { p ->
                    p.stop()
                    p.setMediaItem(mediaItem)
                    p.prepare()
                    p.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback for ${track.title}: ${e.message}", e)
                _error.value = e.message ?: "Failed to play"
                _isLoading.value = false
            }
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun toggleRepeat() {
        _isRepeatEnabled.value = !_isRepeatEnabled.value
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        val current = p.currentPosition
        val duration = _durationMs.value
        val target = (current + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun playNext() {
        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        if (_isRepeatEnabled.value) {
            seekTo(0L)
            player?.play()
            return
        }

        if (_isShuffleEnabled.value && currentQueue.size > 1) {
            val candidates = currentQueue.filter { it.id != current.id }
            val randomTrack = candidates.randomOrNull() ?: currentQueue[0]
            playTrack(randomTrack, currentQueue)
            return
        }

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex in currentQueue.indices && currentIndex + 1 < currentQueue.size) {
            val nextTrack = currentQueue[currentIndex + 1]
            playTrack(nextTrack, currentQueue)
        } else if (currentQueue.isNotEmpty()) {
            // Loop back to start
            playTrack(currentQueue[0], currentQueue)
        }
    }

    fun playPrevious() {
        val p = player
        if (p != null && p.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            val prevTrack = currentQueue[currentIndex - 1]
            playTrack(prevTrack, currentQueue)
        } else {
            seekTo(0L)
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                player?.let { p ->
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                delay(400L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun stop() {
        stopProgressUpdates()
        player?.stop()
        player?.clearMediaItems()
        _currentTrack.value = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _error.value = null
    }

    fun release() {
        stopProgressUpdates()
        player?.release()
        player = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentTrack.value = null
    }
}
