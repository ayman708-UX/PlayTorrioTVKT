package com.playtorrio.tv.ui.screens.audiobook

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.core.audiobook.model.AudiobookChapter
import com.playtorrio.tv.ui.components.P2pConsentDialog

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    onBackPress: () -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel()
) {
    val handleExit = {
        viewModel.closePlayer()
        onBackPress()
    }

    // When back button is pressed, stop audio, clear torrent, and return
    BackHandler {
        handleExit()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closePlayer()
        }
    }

    val currentBook by viewModel.currentPlayingBook.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLoadingAudio by viewModel.isLoadingAudio.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()
    val needsP2pConsent by viewModel.needsP2pConsent.collectAsStateWithLifecycle()

    val backFocusRequester = remember { FocusRequester() }
    val posterFocusRequester = remember { FocusRequester() }
    val scrubberFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val prevChapterFocusRequester = remember { FocusRequester() }
    val nextChapterFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val chapterListFocusRequester = remember { FocusRequester() }

    val chapterListState = rememberLazyListState()

    // Auto-focus the poster first so user can navigate down to controls or right to chapters
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            posterFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Scroll chapter list to currently playing chapter
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            chapterListState.animateScrollToItem(currentChapterIndex)
        }
    }

    // P2P Consent dialog if required for AudiobookBay
    if (needsP2pConsent) {
        P2pConsentDialog(
            onEnableP2p = { viewModel.onP2pConsentGranted() },
            onDismiss = { viewModel.onP2pConsentDismissed() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070912))
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!isPlaying) viewModel.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (isPlaying) viewModel.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            viewModel.playNextChapter()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            viewModel.playPreviousChapter()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.seekRelative(15_000L)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.seekRelative(-15_000L)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // --- Dynamic Ambient Blurred Backdrop ---
        val backdropUrl = currentBook?.coverImage.orEmpty()
        if (backdropUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(65.dp)
            )

            // Multi-layered deep vignette & dark glass scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xCC090C19),
                                Color(0xF205070E),
                                Color(0xFF030408)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070912).copy(alpha = 0.65f))
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight

            // Proportional layout sizing based on actual screen height
            val posterSize = when {
                screenHeight < 450.dp -> 150.dp
                screenHeight < 550.dp -> 180.dp
                screenHeight < 700.dp -> 210.dp
                else -> 250.dp
            }
            val vSpacing = when {
                screenHeight < 500.dp -> 6.dp
                screenHeight < 650.dp -> 8.dp
                else -> 12.dp
            }
            val controlsSize = when {
                screenHeight < 500.dp -> 48.dp
                else -> 56.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp, vertical = 18.dp)
            ) {
                // --- Top Bar: Back Button & Pill ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = handleExit,
                            modifier = Modifier
                                .size(36.dp)
                                .focusRequester(backFocusRequester)
                                .focusProperties {
                                    down = posterFocusRequester
                                    right = chapterListFocusRequester
                                },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.10f),
                                focusedContainerColor = Color(0xFF8B5CF6)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "NOW LISTENING",
                                color = Color(0xFFC084FC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
                        Text(
                            text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Two-Column Layout: Player Controls (Left) vs Chapter List (Right) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // ==================== LEFT COLUMN: PLAYER & CONTROLS ====================
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Focusable Poster with glow on focus
                        Surface(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(posterSize)
                                .shadow(24.dp, RoundedCornerShape(20.dp))
                                .focusRequester(posterFocusRequester)
                                .focusProperties {
                                    up = backFocusRequester
                                    down = scrubberFocusRequester
                                    right = chapterListFocusRequester
                                },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF131726),
                                focusedContainerColor = Color(0xFF1E2438)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f))),
                                focusedBorder = Border(BorderStroke(3.dp, Color(0xFFC084FC)))
                            ),
                            scale = ClickableSurfaceDefaults.scale(scale = 1.0f, focusedScale = 1.04f)
                        ) {
                            if (currentBook != null && currentBook?.coverImage?.isNotBlank() == true) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(currentBook?.coverImage)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = currentBook?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(vSpacing))

                        // Book Title
                        Text(
                            text = currentBook?.title ?: "No Audiobook Selected",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // Current Chapter Title & Author
                        val activeChapterTitle = chapters.getOrNull(currentChapterIndex)?.title
                            ?: (currentBook?.author?.ifBlank { currentBook?.source } ?: "")
                        Text(
                            text = activeChapterTitle,
                            color = Color(0xFFA78BFA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )

                        Spacer(modifier = Modifier.height(vSpacing))

                        // Timeline Scrubber (Focusable on Android TV: Down from Poster, Up from Controls)
                        val progress = if (durationMs > 0L) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        var isTimelineFocused by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTimelineFocused) Color(0xFF1E2438) else Color.Transparent)
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        if (isTimelineFocused) Color(0xFF8B5CF6) else Color.Transparent
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .focusRequester(scrubberFocusRequester)
                                .focusProperties {
                                    up = posterFocusRequester
                                    down = playPauseFocusRequester
                                    right = chapterListFocusRequester
                                }
                                .focusable()
                                .onFocusChanged { isTimelineFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                viewModel.seekRelative(-15_000L)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                viewModel.seekRelative(15_000L)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                viewModel.togglePlayPause()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isTimelineFocused) 7.dp else 5.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF8B5CF6),
                                trackColor = Color.White.copy(alpha = 0.18f)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatAudioMillis(currentPositionMs),
                                    color = if (isTimelineFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (isTimelineFocused) {
                                    Text(
                                        text = "◄  Seek 15s  ►",
                                        color = Color(0xFFC084FC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = if (durationMs > 0L) formatAudioMillis(durationMs) else "--:--",
                                    color = if (isTimelineFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(vSpacing))

                        // Media Controls Row: Speed | -15s | Prev Chapter | Play/Pause | Next Chapter | +15s
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Playback Speed Button (1.0x -> 1.25x -> 1.5x -> 1.75x -> 2.0x -> 1.0x)
                            Surface(
                                onClick = {
                                    val nextSpeed = when (playbackSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 1.75f
                                        1.75f -> 2.0f
                                        else -> 1.0f
                                    }
                                    viewModel.setPlaybackSpeed(nextSpeed)
                                },
                                modifier = Modifier
                                    .size(controlsSize - 14.dp)
                                    .focusRequester(speedFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        right = rewindFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (playbackSpeed != 1.0f) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = if (playbackSpeed != 1.0f) Color(0xFFC084FC) else Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Seek -15s Rewind
                            Surface(
                                onClick = { viewModel.seekRelative(-15_000L) },
                                modifier = Modifier
                                    .size(controlsSize - 12.dp)
                                    .focusRequester(rewindFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = speedFocusRequester
                                        right = prevChapterFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 15s",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Previous Chapter Button
                            Surface(
                                onClick = { viewModel.playPreviousChapter() },
                                modifier = Modifier
                                    .size(controlsSize - 8.dp)
                                    .focusRequester(prevChapterFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = rewindFocusRequester
                                        right = playPauseFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Chapter",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Play / Pause Hero Button
                            Surface(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(controlsSize)
                                    .focusRequester(playPauseFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = prevChapterFocusRequester
                                        right = nextChapterFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF8B5CF6),
                                    focusedContainerColor = Color(0xFFA78BFA)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (isLoadingAudio) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            // Next Chapter Button
                            Surface(
                                onClick = { viewModel.playNextChapter() },
                                modifier = Modifier
                                    .size(controlsSize - 8.dp)
                                    .focusRequester(nextChapterFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = playPauseFocusRequester
                                        right = forwardFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Chapter",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Seek +15s Fast Forward
                            Surface(
                                onClick = { viewModel.seekRelative(15_000L) },
                                modifier = Modifier
                                    .size(controlsSize - 12.dp)
                                    .focusRequester(forwardFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = nextChapterFocusRequester
                                        right = chapterListFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 15s",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Error badge if any
                        if (playerError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = playerError ?: "",
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // ==================== RIGHT COLUMN: CHAPTER LIST ====================
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0E1220).copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Chapters",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "${chapters.size} parts",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (chapters.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chapters loaded",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = chapterListState,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(chapters, key = { idx, ch -> "${ch.title}_$idx" }) { index, chapter ->
                                    val isThisChapterPlaying = index == currentChapterIndex

                                    QueueChapterItemCard(
                                        chapter = chapter,
                                        index = index + 1,
                                        isPlaying = isThisChapterPlaying,
                                        modifier = if (index == 0) {
                                            Modifier
                                                .focusRequester(chapterListFocusRequester)
                                                .focusProperties { left = playPauseFocusRequester }
                                        } else {
                                            Modifier.focusProperties { left = playPauseFocusRequester }
                                        },
                                        onClick = {
                                            viewModel.playChapter(index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueChapterItemCard(
    chapter: AudiobookChapter,
    index: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isPlaying) Color(0xFF8B5CF6).copy(alpha = 0.22f)
            else if (isFocused) Color(0xFF1E2438)
            else Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color(0xFF262D46)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isPlaying) Color(0xFF8B5CF6).copy(alpha = 0.6f)
                    else Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color(0xFFC084FC)),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Chapter Number
            Text(
                text = "$index",
                color = if (isPlaying) Color(0xFFC084FC) else Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(26.dp)
            )

            // Chapter Title
            Text(
                text = chapter.title,
                color = if (isPlaying) Color(0xFFC084FC) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatAudioMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
