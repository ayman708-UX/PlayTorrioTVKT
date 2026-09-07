package com.playtorrio.tv.ui.screens.music

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
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
import com.playtorrio.tv.core.music.model.MusicTrack

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    onBackPress: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    // When back button is pressed, stop the song and return
    BackHandler {
        viewModel.stopPlayback()
        onBackPress()
    }

    // When leaving this screen for any reason, stop playback
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }

    val currentTrack by viewModel.currentPlayingTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLoadingAudio by viewModel.isLoadingAudio.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val isRepeatEnabled by viewModel.isRepeatEnabled.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()

    val backFocusRequester = remember { FocusRequester() }
    val posterFocusRequester = remember { FocusRequester() }
    val scrubberFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val prevFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val shuffleFocusRequester = remember { FocusRequester() }
    val repeatFocusRequester = remember { FocusRequester() }
    val queueFocusRequester = remember { FocusRequester() }

    val queueListState = rememberLazyListState()

    // Auto-focus the poster first so user can press down to seekbar and buttons
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            posterFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Scroll queue to current track
    LaunchedEffect(currentTrack?.id) {
        val index = queue.indexOfFirst { it.id == currentTrack?.id }
        if (index >= 0) {
            queueListState.animateScrollToItem(index)
        }
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
                            viewModel.playNext()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            viewModel.playPrevious()
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
        val backdropUrl = currentTrack?.thumbnailUrl.orEmpty()
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
                            onClick = {
                                viewModel.stopPlayback()
                                onBackPress()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .focusRequester(backFocusRequester)
                                .focusProperties {
                                    down = posterFocusRequester
                                    right = queueFocusRequester
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
                                text = "NOW PLAYING",
                                color = Color(0xFFC084FC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    val currentIdx = queue.indexOfFirst { it.id == currentTrack?.id }
                    if (currentIdx >= 0 && queue.isNotEmpty()) {
                        Text(
                            text = "Track ${currentIdx + 1} of ${queue.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Two-Column Layout: Player Controls (Left) vs Up Next Queue (Right) ---
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
                                    right = queueFocusRequester
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
                            if (currentTrack != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(currentTrack?.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = currentTrack?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(vSpacing))

                        // Song Title
                        Text(
                            text = currentTrack?.title ?: "No Track Selected",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // Artist & Album
                        Text(
                            text = (currentTrack?.artist ?: "Unknown Artist") +
                                (if (!currentTrack?.album.isNullOrBlank()) "  •  ${currentTrack?.album}" else ""),
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
                                    right = queueFocusRequester
                                }
                                .focusable()
                                .onFocusChanged { isTimelineFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                viewModel.seekRelative(-10_000L)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                viewModel.seekRelative(10_000L)
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
                                    text = formatMillis(currentPositionMs),
                                    color = if (isTimelineFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (isTimelineFocused) {
                                    Text(
                                        text = "◄  Seek  ►",
                                        color = Color(0xFFC084FC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = if (durationMs > 0L) formatMillis(durationMs) else (currentTrack?.durationText ?: "0:00"),
                                    color = if (isTimelineFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(vSpacing))

                        // Media Controls Row (Focusable: Up goes back to Scrubber)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Shuffle Button
                            Surface(
                                onClick = { viewModel.toggleShuffle() },
                                modifier = Modifier
                                    .size(controlsSize - 14.dp)
                                    .focusRequester(shuffleFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        right = prevFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isShuffleEnabled) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffleEnabled) Color(0xFFC084FC) else Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Previous Track Button
                            Surface(
                                onClick = { viewModel.playPrevious() },
                                modifier = Modifier
                                    .size(controlsSize - 8.dp)
                                    .focusRequester(prevFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = shuffleFocusRequester
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
                                        contentDescription = "Previous",
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
                                        left = prevFocusRequester
                                        right = nextFocusRequester
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

                            // Next Track Button
                            Surface(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier
                                    .size(controlsSize - 8.dp)
                                    .focusRequester(nextFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = playPauseFocusRequester
                                        right = repeatFocusRequester
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
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Repeat Button
                            Surface(
                                onClick = { viewModel.toggleRepeat() },
                                modifier = Modifier
                                    .size(controlsSize - 14.dp)
                                    .focusRequester(repeatFocusRequester)
                                    .focusProperties {
                                        up = scrubberFocusRequester
                                        left = nextFocusRequester
                                        right = queueFocusRequester
                                    },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isRepeatEnabled) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = Color(0xFF8B5CF6)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (isRepeatEnabled) Color(0xFFC084FC) else Color.White.copy(alpha = 0.75f),
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

                    // ==================== RIGHT COLUMN: UP NEXT QUEUE ====================
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
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Up Next",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "${queue.size} songs",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (queue.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No upcoming tracks",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = queueListState,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                                    val isThisTrackPlaying = track.id == currentTrack?.id

                                    QueueTrackItemCard(
                                        track = track,
                                        index = index + 1,
                                        isPlaying = isThisTrackPlaying,
                                        modifier = if (index == 0) {
                                            Modifier
                                                .focusRequester(queueFocusRequester)
                                                .focusProperties { left = playPauseFocusRequester }
                                        } else {
                                            Modifier.focusProperties { left = playPauseFocusRequester }
                                        },
                                        onClick = {
                                            viewModel.playTrack(track, queue)
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
private fun QueueTrackItemCard(
    track: MusicTrack,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index or Equalizer
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "$index",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isPlaying) Color(0xFFC084FC) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            if (!track.durationText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = track.durationText,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
