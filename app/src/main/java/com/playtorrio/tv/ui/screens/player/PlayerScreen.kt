package com.playtorrio.tv.ui.screens.player

import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Date
import java.util.concurrent.TimeUnit

// ── Theme Colors ──
private val AccentColor = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val GoldStar = Color(0xFFFFD700)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsState()

    val activity = LocalContext.current as? android.app.Activity
    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val progressBarFocusRequester = remember { FocusRequester() }
    val skipButtonFocusRequester = remember { FocusRequester() }

    // Focus management when overlays change
    LaunchedEffect(
        state.showControls,
        state.showSubtitleOverlay,
        state.showAudioOverlay,
        state.showQualityOverlay,
        state.showPauseOverlay,
        state.showSubtitleStylePanel,
        state.showSourcesPanel,
        state.showEpisodesPanel,
        state.showEpisodeSourceOverlay,
    ) {
        if (state.showControls &&
            !state.showSubtitleOverlay &&
            !state.showAudioOverlay &&
            !state.showQualityOverlay &&
            !state.showSubtitleStylePanel &&
            !state.showSourcesPanel &&
            !state.showEpisodesPanel &&
            !state.showEpisodeSourceOverlay
        ) {
            delay(250)
            try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
        } else if (!state.showControls &&
            !state.showSubtitleOverlay &&
            !state.showAudioOverlay &&
            !state.showQualityOverlay &&
            !state.showPauseOverlay &&
            !state.showSubtitleStylePanel &&
            !state.showEpisodesPanel &&
            !state.showEpisodeSourceOverlay
        ) {
            try { containerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        containerFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(containerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                // Back/Escape: hierarchical dismiss
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                ) {
                    return@onPreviewKeyEvent when (keyEvent.nativeKeyEvent.action) {
                        KeyEvent.ACTION_DOWN -> true
                        KeyEvent.ACTION_UP -> {
                            handleBackPress(viewModel, state) { activity?.finish() }
                            true
                        }
                        else -> true
                    }
                }
                false
            }
            .onKeyEvent { keyEvent ->
                if (state.isConnecting || state.error != null) return@onKeyEvent false

                // When any panel is open, let it handle keys
                val panelOpen = state.showSubtitleOverlay ||
                        state.showAudioOverlay ||
                        state.showQualityOverlay ||
                        state.showSubtitleStylePanel ||
                        state.showSourcesPanel ||
                        state.showEpisodesPanel ||
                        state.showEpisodeSourceOverlay
                if (panelOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!state.showControls) {
                                viewModel.commitPreviewSeek()
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    // Pause overlay: center to resume, d-pad to show controls
                    if (state.showPauseOverlay) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                viewModel.dismissPauseOverlay()
                            }
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                viewModel.showControls()
                            }
                            else -> {
                                viewModel.dismissPauseOverlay()
                            }
                        }
                        return@onKeyEvent true
                    }

                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!state.showControls) {
                                val hasNext = !state.isMovie && state.nextEpisode != null && !state.isSwitchingEpisode
                                val nearEnd = state.duration > 0 &&
                                    state.currentPosition >= (state.duration * 0.90).toLong()
                                val nextEpVisible = hasNext && nearEnd
                                if (nextEpVisible) {
                                    viewModel.playNextEpisode()
                                } else if (state.activeSkipSegment != null) {
                                    viewModel.skipActiveSegment()
                                } else {
                                    viewModel.togglePlayPause()
                                }
                                true
                            } else false // let focused button handle it
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!state.showControls) {
                                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                                val stepMs = when {
                                    repeatCount >= 8 -> 30_000L
                                    repeatCount >= 3 -> 20_000L
                                    else -> 10_000L
                                }
                                val isLeft = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                val deltaMs = if (isLeft) -stepMs else stepMs
                                viewModel.previewSeekBy(deltaMs)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!state.showControls) {
                                viewModel.showControls()
                            } else {
                                try {
                                    progressBarFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                    viewModel.hideControls()
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!state.showControls) {
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.togglePlayPause(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!state.isPlaying) viewModel.togglePlayPause(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.seekForward(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.seekBackward(); true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── Error state ──
        if (state.error != null) {
            ErrorOverlay(state)
            return@Box
        }

        // ── Loading state ──
        if (state.isConnecting && state.torrentHash == null) {
            LoadingOverlay(state)
            return@Box
        }

        // ── Video ──
        VideoRenderer(viewModel, state)

        // ── Custom external-subtitle overlay ──
        // Renders subtitles parsed by SubtitleCueParser so that delay changes
        // apply instantly without reloading the player.
        if (state.customSubtitleLabel != null) {
            CustomSubtitleOverlay(state)
        }

        // ── Buffering indicator ──
        if ((state.isBuffering || state.isConnecting || state.isReconnecting) && !state.showPauseOverlay) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AccentColor,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                    if (state.isReconnecting && state.reconnectStatus.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.reconnectStatus,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    }
                    if (state.torrentHash != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "%.1f MB/s • %d peers".format(state.speedMbps, state.activePeers),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // ── Pause overlay ──
        PauseOverlay(state, viewModel)

        // ── Skip + Next Episode floating buttons ──
        // Skip = whenever a skip segment is active (any type).
        // Next Episode = only when we're past 95% of the runtime.
        // When both happen at once, they sit side by side: Skip on the left,
        // Next Episode on the right (DPAD ←/→ moves between them).
        val skipSeg = state.activeSkipSegment
        val panelsOpen = state.showEpisodesPanel || state.showEpisodeSourceOverlay ||
            state.showSourcesPanel || state.showSubtitleOverlay ||
            state.showAudioOverlay || state.showQualityOverlay || state.showQualityOverlay || state.showSubtitleStylePanel
        val hasNextEp = !state.isMovie && state.nextEpisode != null && !state.isSwitchingEpisode
        val nearEnd = state.duration > 0 &&
            state.currentPosition >= (state.duration * 0.90).toLong()
        val showSkip = skipSeg != null && !panelsOpen
        val showNextEp = hasNextEp && !panelsOpen && nearEnd
        val bothVisible = showSkip && showNextEp
        val leftSkipFocusRequester = remember { FocusRequester() }

        // Plain conditional render (no AnimatedVisibility) so focus traversal
        // works the moment the buttons appear.
        if (showSkip || showNextEp) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = if (state.showControls) 140.dp else 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showSkip) {
                    skipSeg?.let { seg ->
                        SkipButton(
                            label = seg.type.label,
                            focusRequester = if (bothVisible) leftSkipFocusRequester else skipButtonFocusRequester,
                            downFocusRequester = progressBarFocusRequester,
                            rightFocusRequester = if (bothVisible) skipButtonFocusRequester else null,
                            onClick = { viewModel.skipActiveSegment() }
                        )
                    }
                }

                if (showNextEp) {
                    SkipButton(
                        label = "Next Episode",
                        focusRequester = skipButtonFocusRequester,
                        downFocusRequester = progressBarFocusRequester,
                        leftFocusRequester = if (bothVisible) leftSkipFocusRequester else null,
                        onClick = { viewModel.playNextEpisode() }
                    )
                }
            }
        }

        // ── Aspect ratio indicator ──
        AnimatedVisibility(
            visible = state.showAspectIndicator,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            AspectRatioIndicator(state.aspectMode.label)
        }

        // ── Seek overlay (when controls hidden, D-pad seeking) ──
        AnimatedVisibility(
            visible = state.showSeekOverlay && !state.showControls &&
                !state.showPauseOverlay && state.error == null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SeekOverlay(state)
        }

        // ── Controls overlay ──
        AnimatedVisibility(
            visible = state.showControls && state.error == null &&
                !state.showPauseOverlay &&
                !state.showSubtitleOverlay &&
                !state.showAudioOverlay &&
                !state.showQualityOverlay &&
                !state.showSubtitleStylePanel &&
                !state.showSourcesPanel &&
                !state.showEpisodesPanel &&
                !state.showEpisodeSourceOverlay,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            PlayerControlsOverlay(
                state = state,
                viewModel = viewModel,
                playPauseFocusRequester = playPauseFocusRequester,
                progressBarFocusRequester = progressBarFocusRequester,
                skipButtonFocusRequester = skipButtonFocusRequester
            )
        }

        // ── Source switching overlay ──
        if (state.isSwitchingSource || state.isSwitchingEpisode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AccentColor,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (state.isSwitchingEpisode) "Switching episode…" else "Switching source…",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ── Sources panel ──
        StreamSourcesPanel(state, viewModel)

        // ── Episodes panel ──
        EpisodesPanel(state, viewModel)

        // ── Episode source picker overlay ──
        EpisodeSourceOverlay(state, viewModel)

        // ── Subtitle selection overlay ──
        SubtitleSelectionOverlay(state, viewModel)

        // ── Audio selection overlay ──
        AudioSelectionOverlay(state, viewModel)

        // ── Quality selection overlay ──
        QualitySelectionOverlay(state, viewModel)

        // ── Subtitle style panel ──
        SubtitleStylePanel(state, viewModel)
    }
}

private fun handleBackPress(viewModel: PlayerViewModel, state: PlayerUiState, onExit: () -> Unit) {
    when {
        state.showSubtitleStylePanel -> viewModel.hideSubtitleStylePanel()
        state.showSubtitleOverlay -> viewModel.hideSubtitleOverlay()
        state.showAudioOverlay -> viewModel.hideAudioOverlay()
        state.showQualityOverlay -> viewModel.hideQualityOverlay()
        state.showSourcesPanel -> viewModel.dismissSourcesPanel()
        state.showEpisodeSourceOverlay -> viewModel.dismissEpisodeSourceOverlay()
        state.showEpisodesPanel -> viewModel.dismissEpisodesPanel()
        state.showPauseOverlay -> viewModel.dismissPauseOverlay()
        state.showControls -> viewModel.hideControls()
        else -> onExit()
    }
}

// ════════════════════════════════════════════════════════════
// VIDEO RENDERER
// ════════════════════════════════════════════════════════════

@Composable
private fun VideoRenderer(viewModel: PlayerViewModel, state: PlayerUiState) {
    val subtitleStyle = state.subtitleStyle

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = viewModel.player
                useController = false
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            playerView.player = viewModel.player
            playerView.keepScreenOn = state.isPlaying || state.isBuffering
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

            when (state.aspectMode) {
                AspectMode.FIT -> { playerView.scaleX = 1f; playerView.scaleY = 1f }
                AspectMode.FILL -> { playerView.scaleX = 1.78f; playerView.scaleY = 1.78f }
                AspectMode.ZOOM_115 -> { playerView.scaleX = 1.15f; playerView.scaleY = 1.15f }
                AspectMode.ZOOM_133 -> { playerView.scaleX = 1.33f; playerView.scaleY = 1.33f }
            }

            // Apply subtitle style
            playerView.subtitleView?.apply {
                val baseFontSize = 24f
                val scaledFontSize = baseFontSize * (subtitleStyle.size / 100f)
                setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledFontSize)
                setApplyEmbeddedFontSizes(false)

                val typeface = if (subtitleStyle.bold) {
                    android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    android.graphics.Typeface.DEFAULT
                }

                val edgeType = if (subtitleStyle.outlineEnabled) {
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                }

                setStyle(
                    androidx.media3.ui.CaptionStyleCompat(
                        subtitleStyle.textColor,
                        subtitleStyle.backgroundColor,
                        android.graphics.Color.TRANSPARENT,
                        edgeType,
                        subtitleStyle.outlineColor,
                        typeface
                    )
                )
                setApplyEmbeddedStyles(false)

                val bottomPaddingFraction = (0.06f + (subtitleStyle.verticalOffset / 250f)).coerceIn(0f, 0.4f)
                setBottomPaddingFraction(bottomPaddingFraction)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ════════════════════════════════════════════════════════════
// CUSTOM SUBTITLE OVERLAY
// ════════════════════════════════════════════════════════════

@Composable
private fun CustomSubtitleOverlay(state: PlayerUiState) {
    if (state.customSubtitleCues.isEmpty()) return
    val style = state.subtitleStyle
    val effectivePosition = state.currentPosition - style.subtitleDelayMs
    val cue = remember(effectivePosition, state.customSubtitleCues) {
        com.playtorrio.tv.data.subtitle.SubtitleCueParser
            .cueAt(state.customSubtitleCues, effectivePosition)
    } ?: return

    val textColor = Color(style.textColor)
    val bgColor = Color(style.backgroundColor)
    val outlineColor = Color(style.outlineColor)
    val fontSizeSp = (24f * (style.size / 100f)).sp
    val bottomFraction = (0.06f + (style.verticalOffset / 250f)).coerceIn(0f, 0.4f)

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = containerHeight * bottomFraction, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                cue.text.split('\n').forEach { line ->
                    if (line.isBlank()) return@forEach
                    val textModifier = if (bgColor.alpha > 0f) {
                        Modifier
                            .background(bgColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    } else Modifier
                    Text(
                        text = line,
                        modifier = textModifier,
                        color = textColor,
                        fontSize = fontSizeSp,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = if (style.outlineEnabled) {
                                Shadow(color = outlineColor, blurRadius = 6f)
                            } else null
                        )
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// CONTROLS OVERLAY
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerControlsOverlay(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    playPauseFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester,
    skipButtonFocusRequester: FocusRequester
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Bottom content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Title info
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!state.isMovie && state.seasonNumber != null && state.episodeNumber != null) {
                    val episodeInfo = buildString {
                        append("S${state.seasonNumber}E${state.episodeNumber}")
                        state.episodeTitle?.let { append(" • $it") }
                    }
                    Text(
                        text = episodeInfo,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.year != null) {
                    Text(
                        text = state.year,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }

                if (state.torrentHash != null && state.speedMbps > 0 && !state.isPlaying) {
                    Text(
                        text = "%.1f MB/s • %d peers".format(state.speedMbps, state.activePeers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            val nextEpVisible = !state.isMovie && state.nextEpisode != null && !state.isSwitchingEpisode &&
                state.duration > 0 && state.currentPosition >= (state.duration * 0.90).toLong()
            val skipOrNextVisible = state.activeSkipSegment != null || nextEpVisible
            ProgressBar(
                currentPosition = state.pendingPreviewSeekPosition ?: state.currentPosition,
                duration = state.duration,
                onSeekPreview = { delta -> viewModel.previewSeekBy(delta) },
                onSeekCommit = { viewModel.commitPreviewSeek() },
                focusRequester = progressBarFocusRequester,
                downFocusRequester = playPauseFocusRequester,
                onUpPressed = if (skipOrNextVisible) {
                    { try { skipButtonFocusRequester.requestFocus() } catch (_: Exception) {} }
                } else null,
                onFocused = { viewModel.scheduleControlsHide() }
            )

            Spacer(Modifier.height(16.dp))

            // Control buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause
                    ControlButton(
                        icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        onClick = { viewModel.togglePlayPause() },
                        focusRequester = playPauseFocusRequester,
                        upFocusRequester = progressBarFocusRequester,
                        onDownKey = { viewModel.hideControls() },
                        onFocused = { viewModel.scheduleControlsHide() }
                    )

                    // Subtitles
                    if (state.subtitleTracks.isNotEmpty()) {
                        ControlButton(
                            icon = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = { viewModel.showSubtitleOverlay() },
                            upFocusRequester = progressBarFocusRequester,
                            onDownKey = { viewModel.hideControls() },
                            onFocused = { viewModel.scheduleControlsHide() }
                        )
                    }

                    // Audio
                    if (state.audioTracks.size > 1) {
                        ControlButton(
                            icon = Icons.Filled.Audiotrack,
                            contentDescription = "Audio",
                            onClick = { viewModel.showAudioOverlay() },
                            upFocusRequester = progressBarFocusRequester,
                            onDownKey = { viewModel.hideControls() },
                            onFocused = { viewModel.scheduleControlsHide() }
                        )
                    }

                    // Quality (only when stream offers multiple variants)
                    if (state.videoTracks.size > 1) {
                        ControlButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Quality",
                            onClick = { viewModel.showQualityOverlay() },
                            upFocusRequester = progressBarFocusRequester,
                            onDownKey = { viewModel.hideControls() },
                            onFocused = { viewModel.scheduleControlsHide() }
                        )
                    }

                    // Aspect ratio
                    ControlButton(
                        icon = Icons.Filled.AspectRatio,
                        contentDescription = "Aspect Ratio",
                        onClick = { viewModel.cycleAspectRatio() },
                        upFocusRequester = progressBarFocusRequester,
                        onDownKey = { viewModel.hideControls() },
                        onFocused = { viewModel.scheduleControlsHide() }
                    )

                    // Sources (streaming mode only, but not for IPTV)
                    if (state.isStreamingMode && !state.isIptv) {
                        ControlButton(
                            icon = Icons.Filled.Layers,
                            contentDescription = "Sources",
                            onClick = { viewModel.showSourcesPanel() },
                            upFocusRequester = progressBarFocusRequester,
                            onDownKey = { viewModel.hideControls() },
                            onFocused = { viewModel.scheduleControlsHide() }
                        )
                    }

                    // Episodes (series only, not IPTV)
                    if (!state.isMovie && !state.isIptv) {
                        ControlButton(
                            icon = Icons.Filled.PlaylistPlay,
                            contentDescription = "Episodes",
                            onClick = { viewModel.showEpisodesPanel() },
                            upFocusRequester = progressBarFocusRequester,
                            onDownKey = { viewModel.hideControls() },
                            onFocused = { viewModel.scheduleControlsHide() }
                        )
                    }
                }

                // Time display
                Text(
                    text = "${formatTime(state.pendingPreviewSeekPosition ?: state.currentPosition)} / ${formatTime(state.duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// CONTROL BUTTON
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onDownKey: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else Modifier
            )
            .onPreviewKeyEvent { keyEvent ->
                if (upFocusRequester != null &&
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    try { upFocusRequester.requestFocus() } catch (_: Exception) {}
                    true
                } else if (onDownKey != null &&
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onDownKey()
                    true
                } else false
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            },
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════
// PROGRESS BAR
// ════════════════════════════════════════════════════════════

@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekPreview: (Long) -> Unit = {},
    onSeekCommit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null
) {
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isFocused) 10.dp else 6.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null) {
                    Modifier.focusProperties {
                        upFocusRequester?.let { up = it }
                        downFocusRequester?.let { down = it }
                    }
                } else Modifier
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekCommit()
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            downFocusRequester?.let {
                                try { it.requestFocus() } catch (_: Exception) {}
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onUpPressed != null) {
                                onUpPressed()
                                return@onPreviewKeyEvent true
                            }
                            upFocusRequester?.let {
                                try { it.requestFocus() } catch (_: Exception) {}
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekPreview(-10_000L); true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekPreview(10_000L); true
                        }
                        else -> false
                    }
                } else false
            }
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.3f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentColor)
        )
    }
}

// ════════════════════════════════════════════════════════════
// SEEK OVERLAY (shown when D-pad seeking without controls)
// ════════════════════════════════════════════════════════════

@Composable
private fun SeekOverlay(state: PlayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        ProgressBar(
            currentPosition = state.pendingPreviewSeekPosition ?: state.currentPosition,
            duration = state.duration
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${formatTime(state.pendingPreviewSeekPosition ?: state.currentPosition)} / ${formatTime(state.duration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// ASPECT RATIO INDICATOR
// ════════════════════════════════════════════════════════════

@Composable
private fun AspectRatioIndicator(text: String) {
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color = Color(0xFF3B3B3B), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

// ════════════════════════════════════════════════════════════
// SKIP SEGMENT BUTTON
// ════════════════════════════════════════════════════════════

@Composable
private fun SkipButton(
    label: String,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = if (isFocused) AccentColor else AccentColor.copy(alpha = 0.85f)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(150),
        label = "skipScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .focusRequester(focusRequester)
            .focusProperties {
                down = downFocusRequester
                upFocusRequester?.let { up = it }
                leftFocusRequester?.let { left = it }
                rightFocusRequester?.let { right = it }
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER
                    )
                ) {
                    onClick()
                    true
                } else false
            }
            .then(
                if (isFocused) Modifier
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Color.White,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

// ════════════════════════════════════════════════════════════
// PAUSE OVERLAY
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PauseOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    AnimatedVisibility(
        visible = state.showPauseOverlay && state.error == null,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                        )
                    )
                    drawRect(Color.Black.copy(alpha = 0.34f))
                    drawRect(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.6f),
                                0.3f to Color.Black.copy(alpha = 0.4f),
                                0.6f to Color.Black.copy(alpha = 0.2f),
                                1f to Color.Transparent
                            )
                        )
                    )
                    drawContent()
                }
                .padding(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 120.dp)
        ) {
            // Clock (top-right)
            PauseOverlayClock(modifier = Modifier.align(Alignment.TopEnd))

            // Metadata (bottom-left)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "You are watching",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))

                if (state.logoUrl != null) {
                    AsyncImage(
                        model = state.logoUrl,
                        contentDescription = state.title,
                        modifier = Modifier.height(96.dp).widthIn(max = 400.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.year?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                    }
                    state.rating?.let {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, null, tint = GoldStar, modifier = Modifier.size(14.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                        }
                    }
                    if (!state.isMovie && state.seasonNumber != null && state.episodeNumber != null) {
                        Text(
                            "• S${state.seasonNumber}E${state.episodeNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                if (!state.isMovie && state.episodeTitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.episodeTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2
                    )
                }

                if (state.overview != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.56f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }

                // Progress mini-bar
                Spacer(Modifier.height(16.dp))
                ProgressBar(
                    currentPosition = state.currentPosition,
                    duration = state.duration
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatTime(state.currentPosition)} / ${formatTime(state.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PauseOverlayClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Text(
        text = formatter.format(Date(nowMillis)),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Normal,
            fontSize = 34.sp
        ),
        color = Color.White.copy(alpha = 0.95f),
        modifier = modifier
    )
}

// ════════════════════════════════════════════════════════════
// SUBTITLE SELECTION OVERLAY (Language Folders + Style Rail)
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SubtitleSelectionOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    val firstFocusRequester = remember { FocusRequester() }
    val styleFocusRequester = remember { FocusRequester() }

    // Group subtitles by language
    val languageGroups = remember(state.subtitleTracks) {
        val groups = mutableMapOf<String, MutableList<SubtitleTrackInfo>>()
        for (track in state.subtitleTracks) {
            val lang = track.language?.let { languageDisplay(it) } ?: "Unknown"
            groups.getOrPut(lang) { mutableListOf() }.add(track)
        }
        groups.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<SubtitleTrackInfo>>> { it.key.equals("English", true) }
                .thenBy { it.key }
        ).map { it.key to it.value }
    }

    var selectedLanguage by remember(state.showSubtitleOverlay) { mutableStateOf<String?>(null) }
    var showStyleRail by remember(state.showSubtitleOverlay) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.showSubtitleOverlay,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp)
                .focusProperties {
                    exit = { FocusRequester.Cancel }
                }
                .focusGroup()
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Language rail
                    RailColumn(width = 200.dp, title = "Languages") {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                            modifier = Modifier.heightIn(max = 500.dp)
                        ) {
                            item(key = "__off__") {
                                OverlayCard(
                                    label = "None",
                                    isSelected = state.subtitleTracks.none { it.isSelected },
                                    onClick = {
                                        viewModel.disableSubtitles()
                                        selectedLanguage = null
                                        showStyleRail = false
                                    },
                                    focusRequester = if (selectedLanguage == null && !showStyleRail) firstFocusRequester else null
                                )
                            }

                            item(key = "__style__") {
                                OverlayCard(
                                    label = "✦  Style & Delay",
                                    isSelected = showStyleRail,
                                    onClick = {
                                        showStyleRail = true
                                        selectedLanguage = null
                                    }
                                )
                            }

                            items(languageGroups, key = { it.first }) { (language, _) ->
                                val hasSelected = languageGroups
                                    .firstOrNull { it.first == language }?.second
                                    ?.any { it.isSelected } == true

                                OverlayCard(
                                    label = language,
                                    isSelected = hasSelected,
                                    onClick = {
                                        selectedLanguage = language
                                        showStyleRail = false
                                    },
                                    badge = languageGroups.first { it.first == language }.second.size.toString()
                                )
                            }
                        }
                    }

                    // Subtitle options rail (when language selected)
                    AnimatedVisibility(
                        visible = selectedLanguage != null,
                        enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                        exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                    ) {
                        val tracks = languageGroups.firstOrNull { it.first == selectedLanguage }?.second
                            ?: emptyList()

                        RailColumn(width = 350.dp, title = selectedLanguage ?: "") {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                                modifier = Modifier.heightIn(max = 500.dp)
                            ) {
                                items(tracks, key = { it.id }) { track ->
                                    OverlayCard(
                                        label = track.label,
                                        isSelected = track.isSelected,
                                        onClick = {
                                            viewModel.enableSubtitleTrack()
                                            viewModel.selectSubtitle(track)
                                        },
                                        badge = if (!track.isBuiltIn) track.source.uppercase() else null
                                    )
                                }
                            }
                        }
                    }

                    // Style rail (when style selected)
                    AnimatedVisibility(
                        visible = showStyleRail,
                        enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                        exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                    ) {
                        SubtitleStyleRail(
                            style = state.subtitleStyle,
                            viewModel = viewModel,
                            focusRequester = styleFocusRequester
                        )
                    }
                }
            }
        }

        LaunchedEffect(state.showSubtitleOverlay) {
            if (state.showSubtitleOverlay) {
                delay(100)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }

        LaunchedEffect(showStyleRail) {
            if (showStyleRail) {
                delay(200)
                try { styleFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

// NuvioTV-style subtitle style rail
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleStyleRail(
    style: SubtitleStyleSettings,
    viewModel: PlayerViewModel,
    focusRequester: FocusRequester
) {
    val textColors = listOf(
        Color.White to "White",
        Color(0xFFD9D9D9) to "Gray",
        Color(0xFFFFD700) to "Gold",
        Color(0xFF00E5FF) to "Cyan",
        Color(0xFFFF5C5C) to "Red",
        Color(0xFF00FF88) to "Lime"
    )
    val outlineColors = listOf(
        Color.Black to "Black",
        Color(0xFF1A1A1A) to "Dark",
        Color(0xFF333333) to "Gray",
        Color(0xFF000080) to "Navy"
    )

    RailColumn(width = 280.dp, title = "Style") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            // Font size
            item(key = "style_size") {
                StyleSectionCard(title = "Font Size") {
                    StepperRow(
                        value = "${style.size.toInt()}%",
                        onDecrease = {
                            viewModel.updateSubtitleStyle { it.copy(size = (it.size - 10f).coerceAtLeast(50f)) }
                        },
                        onIncrease = {
                            viewModel.updateSubtitleStyle { it.copy(size = (it.size + 10f).coerceAtMost(200f)) }
                        },
                        decreaseFocusRequester = focusRequester
                    )
                }
            }

            // Bold
            item(key = "style_bold") {
                StyleSectionCard(title = "Bold") {
                    ToggleCard(
                        label = if (style.bold) "On" else "Off",
                        isEnabled = style.bold,
                        onClick = { viewModel.updateSubtitleStyle { it.copy(bold = !it.bold) } }
                    )
                }
            }

            // Text color
            item(key = "style_textcolor") {
                StyleSectionCard(title = "Text Color") {
                    ColorChipRow(
                        colors = textColors,
                        selectedArgb = style.textColor,
                        onColorSelected = { argb ->
                            viewModel.updateSubtitleStyle { it.copy(textColor = argb) }
                        }
                    )
                }
            }

            // Outline
            item(key = "style_outline") {
                StyleSectionCard(title = "Outline") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleCard(
                            label = if (style.outlineEnabled) "On" else "Off",
                            isEnabled = style.outlineEnabled,
                            onClick = { viewModel.updateSubtitleStyle { it.copy(outlineEnabled = !it.outlineEnabled) } }
                        )
                        if (style.outlineEnabled) {
                            ColorChipRow(
                                colors = outlineColors,
                                selectedArgb = style.outlineColor,
                                onColorSelected = { argb ->
                                    viewModel.updateSubtitleStyle { it.copy(outlineColor = argb) }
                                }
                            )
                        }
                    }
                }
            }

            // Vertical offset
            item(key = "style_offset") {
                StyleSectionCard(title = "Position") {
                    StepperRow(
                        value = "${style.verticalOffset.toInt()}",
                        onDecrease = {
                            viewModel.updateSubtitleStyle { it.copy(verticalOffset = (it.verticalOffset - 5f).coerceAtLeast(-20f)) }
                        },
                        onIncrease = {
                            viewModel.updateSubtitleStyle { it.copy(verticalOffset = (it.verticalOffset + 5f).coerceAtMost(100f)) }
                        }
                    )
                }
            }

            // Subtitle delay
            item(key = "style_delay") {
                val delayMs = style.subtitleDelayMs
                val delayText = if (delayMs == 0L) "0s" else "%+.2fs".format(delayMs / 1000.0)
                StyleSectionCard(title = "Subtitle Delay") {
                    StepperRow(
                        value = delayText,
                        onDecrease = {
                            viewModel.updateSubtitleStyle { it.copy(subtitleDelayMs = (it.subtitleDelayMs - 250L).coerceAtLeast(-10_000L)) }
                        },
                        onIncrease = {
                            viewModel.updateSubtitleStyle { it.copy(subtitleDelayMs = (it.subtitleDelayMs + 250L).coerceAtMost(10_000L)) }
                        }
                    )
                }
            }

            // Reset
            item(key = "style_reset") {
                OverlayCard(
                    label = "Reset Defaults",
                    isSelected = false,
                    onClick = { viewModel.updateSubtitleStyle { SubtitleStyleSettings() } }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StyleSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.5f)
        )
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StepperRow(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            onClick = onDecrease,
            modifier = Modifier
                .size(36.dp)
                .then(if (decreaseFocusRequester != null) Modifier.focusRequester(decreaseFocusRequester) else Modifier),
            colors = CardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.25f)
            ),
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("−", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
        Card(
            onClick = onIncrease,
            modifier = Modifier.size(36.dp),
            colors = CardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.25f)
            ),
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleCard(
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isEnabled) AccentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (isEnabled) AccentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isEnabled) AccentColor else Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ColorChipRow(
    colors: List<Pair<Color, String>>,
    selectedArgb: Int,
    onColorSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { (color, _) ->
            val argb = color.toArgbInt()
            val isSelected = argb == selectedArgb
            Card(
                onClick = { onColorSelected(argb) },
                modifier = Modifier.size(32.dp),
                colors = CardDefaults.colors(
                    containerColor = color,
                    focusedContainerColor = color
                ),
                shape = CardDefaults.shape(CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.2f),
                border = if (isSelected) CardDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                        shape = CircleShape
                    )
                ) else CardDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                        shape = CircleShape
                    )
                )
            ) {}
        }
    }
}

// ════════════════════════════════════════════════════════════
// AUDIO SELECTION OVERLAY
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun AudioSelectionOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    val firstFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showAudioOverlay,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp)
                .focusProperties {
                    exit = { FocusRequester.Cancel }
                }
                .focusGroup()
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                RailColumn(width = 400.dp, title = "Tracks") {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        modifier = Modifier.heightIn(max = 500.dp)
                    ) {
                        itemsIndexed(state.audioTracks) { index, track ->
                            val displayName = track.label
                                ?: track.language?.let { languageDisplay(it) }
                                ?: "Track ${track.index + 1}"
                            val metadata = listOfNotNull(
                                track.codec,
                                track.channelCount?.let { "$it ch" },
                                track.sampleRate?.let { "${it / 1000} kHz" }
                            ).joinToString(" • ")

                            AudioTrackCard(
                                name = displayName,
                                metadata = metadata,
                                isSelected = track.isSelected,
                                onClick = { viewModel.selectAudioTrack(track) },
                                focusRequester = if (index == 0) firstFocusRequester else null
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(state.showAudioOverlay) {
            if (state.showAudioOverlay) {
                delay(100)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AudioTrackCard(
    name: String,
    metadata: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) AccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, null, tint = AccentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) AccentColor else Color.White
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// SUBTITLE STYLE PANEL
// ════════════════════════════════════════════════════════════

// SubtitleStylePanel is now integrated into SubtitleSelectionOverlay as a rail
@Composable
private fun SubtitleStylePanel(state: PlayerUiState, viewModel: PlayerViewModel) {
    // No-op: style is now part of the subtitle overlay
}

// ════════════════════════════════════════════════════════════
// QUALITY SELECTION OVERLAY
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun QualitySelectionOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    val firstFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showQualityOverlay,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                RailColumn(width = 400.dp, title = "Variants") {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        modifier = Modifier.heightIn(max = 500.dp)
                    ) {
                        item {
                            AudioTrackCard(
                                name = "Auto",
                                metadata = "Adapt to bandwidth",
                                isSelected = state.isQualityAuto,
                                onClick = { viewModel.selectAutoQuality() },
                                focusRequester = firstFocusRequester
                            )
                        }
                        itemsIndexed(state.videoTracks) { _, track ->
                            AudioTrackCard(
                                name = track.displayName(),
                                metadata = track.metadata(),
                                isSelected = !state.isQualityAuto && track.isSelected,
                                onClick = { viewModel.selectVideoTrack(track) },
                                focusRequester = null
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(state.showQualityOverlay) {
            if (state.showQualityOverlay) {
                delay(100)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// STREAM SOURCES PANEL
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamSourcesPanel(state: PlayerUiState, viewModel: PlayerViewModel) {
    val sources = com.playtorrio.tv.data.streaming.StreamExtractorService.SOURCES
    val firstFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showSourcesPanel,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 3 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 3 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                RailColumn(width = 280.dp, title = "Available Sources") {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        modifier = Modifier.heightIn(max = 500.dp)
                    ) {
                        itemsIndexed(sources) { index, source ->
                            val isCurrentSource = source.index == state.currentSourceIndex
                            SourceItemCard(
                                name = source.name,
                                isSelected = isCurrentSource,
                                onClick = {
                                    if (!isCurrentSource) viewModel.switchToSource(source.index)
                                },
                                focusRequester = if (index == 0) firstFocusRequester else null
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(state.showSourcesPanel) {
            if (state.showSourcesPanel) {
                delay(120)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceItemCard(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) AccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = AccentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) AccentColor else Color.White
                )
            }
            if (isSelected) {
                Text(
                    text = "PLAYING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = AccentColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

// ════════════════════════════════════════════════════════════
// SHARED OVERLAY COMPONENTS
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailColumn(
    width: androidx.compose.ui.unit.Dp,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.width(width)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
            ),
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OverlayCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    badge: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) AccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, null, tint = AccentColor, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) AccentColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = AccentSecondary.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentSecondary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// LOADING & ERROR OVERLAYS
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingOverlay(state: PlayerUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "gradient"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Animated background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.15f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentColor.copy(alpha = 0.3f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(
                            x = 540f + 200f * kotlin.math.cos(gradientOffset * 2 * Math.PI).toFloat(),
                            y = 400f + 100f * kotlin.math.sin(gradientOffset * 2 * Math.PI).toFloat()
                        ),
                        radius = 600f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (state.title.isNotEmpty()) {
                if (state.logoUrl != null) {
                    AsyncImage(
                        model = state.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.widthIn(max = 300.dp).heightIn(max = 80.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(state.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }

                if (state.seasonNumber != null && state.episodeNumber != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "S${"%02d".format(state.seasonNumber)}E${"%02d".format(state.episodeNumber)}" +
                                (state.episodeTitle?.let { " • $it" } ?: ""),
                        color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(40.dp))
            }

            CircularProgressIndicator(
                color = AccentColor,
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = state.connectionStatus,
                color = Color.White.copy(alpha = 0.72f * pulseAlpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            if (state.torrentHash != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "%.1f MB/s • %d peers".format(state.speedMbps, state.activePeers),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorOverlay(state: PlayerUiState) {
    val exitFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { exitFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFF87171),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = state.error ?: "Unknown error",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(16.dp))
            Card(
                onClick = { /* Activity finish handled by back press */ },
                modifier = Modifier
                    .focusRequester(exitFocusRequester)
                    .focusable(),
                colors = CardDefaults.colors(
                    containerColor = AccentColor,
                    focusedContainerColor = AccentColor
                ),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Press Back to Return",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// UTILS
// ════════════════════════════════════════════════════════════

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun languageDisplay(code: String): String {
    // If it's already a display name (more than 3 chars, not an ISO code), return as-is
    if (code.length > 3) return code.replaceFirstChar { it.uppercase() }
    return try {
        java.util.Locale(code).displayLanguage.replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { code }
}

// ════════════════════════════════════════════════════════════
// EPISODES PANEL (right-side list of episodes for current season)
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodesPanel(state: PlayerUiState, viewModel: PlayerViewModel) {
    val firstFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showEpisodesPanel,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 3 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 3 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(440.dp)
                    .background(Color(0xFF0B0B12).copy(alpha = 0.96f))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Season ${state.seasonNumber ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.episodes.size} episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(16.dp))

                if (state.isLoadingEpisodes && state.episodes.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentColor,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                } else if (state.episodes.isEmpty()) {
                    Text(
                        "No episodes found for this season.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
                    ) {
                        itemsIndexed(state.episodes) { index, episode ->
                            val isCurrent = episode.episodeNumber == state.episodeNumber
                            EpisodeCard(
                                episode = episode,
                                isCurrent = isCurrent,
                                focusRequester = if (index == 0) firstFocusRequester else null,
                                onClick = { viewModel.pickEpisode(episode) }
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(state.showEpisodesPanel, state.episodes.size) {
            if (state.showEpisodesPanel && state.episodes.isNotEmpty()) {
                delay(150)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: com.playtorrio.tv.data.model.Episode,
    isCurrent: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) AccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.13f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 130.dp, height = 76.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                if (!episode.stillUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.stillUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isCurrent) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "E${episode.episodeNumber} • ${episode.name ?: "Episode ${episode.episodeNumber}"}",
                    color = if (isCurrent) AccentColor else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                episode.runtimeFormatted?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// EPISODE SOURCE PICKER (torrents OR addon streams)
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeSourceOverlay(state: PlayerUiState, viewModel: PlayerViewModel) {
    val firstFocusRequester = remember { FocusRequester() }
    val ep = state.pendingEpisode

    AnimatedVisibility(
        visible = state.showEpisodeSourceOverlay && ep != null,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 3 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 3 }
    ) {
        if (ep == null) return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(520.dp)
                    .background(Color(0xFF0B0B12).copy(alpha = 0.97f))
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Pick source",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "S${ep.seasonNumber ?: state.seasonNumber}E${ep.episodeNumber} • ${ep.name ?: ""}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))

                when {
                    state.isLoadingEpisodeOverlay -> {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentColor,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    state.episodeOverlayKind == EpisodeOverlayKind.TORRENT -> {
                        if (state.episodeOverlayTorrents.isEmpty()) {
                            Text(
                                "No torrents found.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                itemsIndexed(state.episodeOverlayTorrents) { idx, t ->
                                    SourceListItemCard(
                                        title = t.name,
                                        subtitle = "${t.size} • ${t.seeders} seeders • ${t.source}",
                                        focusRequester = if (idx == 0) firstFocusRequester else null,
                                        onClick = { viewModel.pickEpisodeTorrent(t) }
                                    )
                                }
                            }
                        }
                    }
                    state.episodeOverlayKind == EpisodeOverlayKind.ADDON_STREAM -> {
                        if (state.episodeOverlayStreams.isEmpty()) {
                            Text(
                                "No addon streams found.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                itemsIndexed(state.episodeOverlayStreams) { idx, s ->
                                    SourceListItemCard(
                                        title = s.name ?: s.title ?: "Stream",
                                        subtitle = listOfNotNull(
                                            s.addonName,
                                            s.title?.takeIf { it != s.name },
                                            s.description?.lines()?.firstOrNull()
                                        ).joinToString(" • "),
                                        focusRequester = if (idx == 0) firstFocusRequester else null,
                                        onClick = { viewModel.pickEpisodeStremioStream(s) }
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        LaunchedEffect(
            state.showEpisodeSourceOverlay,
            state.episodeOverlayTorrents.size,
            state.episodeOverlayStreams.size
        ) {
            if (state.showEpisodeSourceOverlay) {
                delay(150)
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceListItemCard(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentColor),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
