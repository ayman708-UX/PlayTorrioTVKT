package com.playtorrio.tv.ui.screens.audiobook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.data.audiobook.AudiobookDetail
import com.playtorrio.tv.data.audiobook.TokybookHeaderInjectingFactory
import kotlinx.coroutines.delay
import kotlin.math.max

private val PlayerAccent = Color(0xFFF59E0B)
private val PlayerBg = Color(0xFF0A0A0F)
private val PlayerPanel = Color(0xFF14141F)
private val PlayerPanelLight = Color(0xFF1E1E2E)
private val PlayerDim = Color.White.copy(alpha = 0.55f)

private const val SEEK_STEP_MS = 10_000L

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudiobookPlayerScreen(
    book: AudiobookDetail,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onClose: () -> Unit,
    initialChapter: Int = 0,
    initialPositionMs: Long = 0L,
    onProgress: (chapterIndex: Int, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val player = remember(book.id) {
        // Static headers required by the host (e.g. tokybook needs Referer + Origin +
        // x-audiobook-id + x-stream-token; audiozaic needs Referer for its CDN).
        val staticHeaders = buildMap {
            book.referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            putAll(book.extraHeaders)
        }
        val ua = staticHeaders["User-Agent"]
            ?: "Mozilla/5.0 (Linux; Android 13; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        val baseHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                val defaults = staticHeaders.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                if (defaults.isNotEmpty()) setDefaultRequestProperties(defaults)
            }
        // Tokybook also requires an `x-track-src` header that varies per request URL —
        // the wrapper computes it from the URI path on every chunk fetch.
        val httpFactory = TokybookHeaderInjectingFactory(baseHttpFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1f
            }
    }

    var currentChapter by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // Resume target seeded from caller (continue-listening). Once consumed (after the
    // ExoPlayer reports a real duration and we seek), it's set back to null.
    var pendingSeekMs by remember(book.id) { mutableLongStateOf(initialPositionMs) }

    fun loadChapter(idx: Int, autoplay: Boolean = true, seekMs: Long = 0L) {
        val safeIdx = idx.coerceIn(0, book.chapters.lastIndex)
        currentChapter = safeIdx
        val ch = book.chapters[safeIdx]
        player.setMediaItem(MediaItem.fromUri(ch.mp3Url))
        player.prepare()
        if (seekMs > 0L) {
            // Best-effort: ExoPlayer accepts seekTo before duration is known and will
            // honour it once the media is prepared.
            player.seekTo(seekMs)
            positionMs = seekMs
        }
        player.playWhenReady = autoplay
    }

    LaunchedEffect(book.id) {
        val startIdx = initialChapter.coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))
        loadChapter(startIdx, seekMs = initialPositionMs.coerceAtLeast(0L))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (currentChapter < book.chapters.lastIndex) loadChapter(currentChapter + 1)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            // Final write of the current position so the user can resume next time.
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            try { onProgress(currentChapter, pos, dur) } catch (_: Exception) {}
            player.removeListener(listener); player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            // Honour a queued resume seek as soon as duration is known.
            if (pendingSeekMs > 0L && durationMs > 0L) {
                val target = pendingSeekMs.coerceIn(0L, (durationMs - 500L).coerceAtLeast(0L))
                if (kotlin.math.abs(player.currentPosition - target) > 1_500L) {
                    player.seekTo(target)
                    positionMs = target
                }
                pendingSeekMs = 0L
            }
            delay(250)
        }
    }

    // Periodic progress save (every ~3s) to survive abrupt app kills.
    LaunchedEffect(player) {
        while (true) {
            delay(3_000)
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            // Don't persist a 0/0 snapshot before the first chapter starts loading.
            if (dur > 0L) onProgress(currentChapter, pos, dur)
        }
    }

    // Cross-pane focus requesters
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val chaptersFocus = remember { FocusRequester() }

    // Trap focus in player & auto-focus play button
    LaunchedEffect(book.id) {
        try { rootFocus.requestFocus() } catch (_: Exception) {}
        delay(140)
        try { playFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PlayerBg)
            .focusRequester(rootFocus)
            .focusable()
            .focusGroup()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Back) {
                    onClose(); return@onPreviewKeyEvent true
                }
                // Swallow any unhandled key so it can't reach the screen behind us
                false
            }
            // Block any pointer / focus from the layer behind
            .clickable(enabled = false, onClick = {})
    ) {
        // Blurred poster backdrop
        if (!book.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = book.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.22f },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(PlayerBg.copy(alpha = 0.85f), PlayerBg.copy(alpha = 0.95f))
                    )
                )
        )

        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        val availH = maxHeight
        // Compact mode for short screens (e.g., phones in landscape ~360dp tall)
        val compact = availH < 480.dp
        val veryCompact = availH < 380.dp
        val posterH = when {
            veryCompact -> (availH * 0.46f)
            compact -> (availH * 0.42f)
            else -> 260.dp
        }.coerceAtMost(260.dp).coerceAtLeast(120.dp)
        val posterW = posterH * (2f / 3f)
        val controlsW = posterW.coerceAtLeast(220.dp)
        val playSize = if (compact) 56.dp else 64.dp
        val sideBtnSize = if (compact) 40.dp else 46.dp
        val titleSize = if (compact) 14.sp else 16.sp
        val gapSm = if (compact) 6.dp else 10.dp
        val gapMd = if (compact) 10.dp else 16.dp

        Row(Modifier.fillMaxSize()) {
            // ═════════════ LEFT HALF ═════════════
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                        colors = CardDefaults.colors(containerColor = PlayerPanel),
                        shape = CardDefaults.shape(CircleShape),
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(Icons.Filled.ArrowBack, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Now playing", color = PlayerDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(gapMd))

                // Poster + save overlay
                Box(
                    modifier = Modifier
                        .height(posterH)
                        .width(posterW)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlayerPanelLight),
                ) {
                    if (!book.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = book.posterUrl,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(
                                Icons.Filled.Headphones, null,
                                tint = PlayerAccent.copy(alpha = 0.6f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }

                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        SaveButton(
                            isLiked = isLiked,
                            onClick = onToggleLike,
                            focusRequester = saveFocus,
                            onDpadDown = { try { seekFocus.requestFocus() } catch (_: Exception) {} },
                            onDpadRight = { try { chaptersFocus.requestFocus() } catch (_: Exception) {} },
                        )
                    }
                }

                Spacer(Modifier.height(gapMd))

                Text(
                    book.title,
                    color = Color.White,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Chapter ${currentChapter + 1} / ${book.chapters.size}",
                    color = PlayerDim,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(gapMd))

                FocusableSeekBar(
                    width = controlsW,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    focusRequester = seekFocus,
                    onSeekRelative = { delta ->
                        val target = (player.currentPosition + delta)
                            .coerceIn(0, player.duration.coerceAtLeast(0))
                        player.seekTo(target)
                        positionMs = target
                    },
                    onDpadUp = { try { saveFocus.requestFocus() } catch (_: Exception) {} },
                    onDpadDown = { try { playFocus.requestFocus() } catch (_: Exception) {} },
                    onDpadRight = { try { chaptersFocus.requestFocus() } catch (_: Exception) {} },
                )

                Spacer(Modifier.height(4.dp))
                Row(Modifier.width(controlsW), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(positionMs), color = PlayerDim, fontSize = 10.sp)
                    Text(formatTime(durationMs), color = PlayerDim, fontSize = 10.sp)
                }

                Spacer(Modifier.height(gapMd))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(gapSm, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton(
                        icon = Icons.Filled.SkipPrevious,
                        label = "Prev chapter",
                        size = sideBtnSize,
                        onClick = { if (currentChapter > 0) loadChapter(currentChapter - 1) },
                        onDpadUp = { try { seekFocus.requestFocus() } catch (_: Exception) {} },
                    )
                    TransportButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (isPlaying) "Pause" else "Play",
                        size = playSize,
                        isPrimary = true,
                        focusRequester = playFocus,
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        onDpadUp = { try { seekFocus.requestFocus() } catch (_: Exception) {} },
                    )
                    TransportButton(
                        icon = Icons.Filled.SkipNext,
                        label = "Next chapter",
                        size = sideBtnSize,
                        onClick = { if (currentChapter < book.chapters.lastIndex) loadChapter(currentChapter + 1) },
                        onDpadUp = { try { seekFocus.requestFocus() } catch (_: Exception) {} },
                        onDpadRight = { try { chaptersFocus.requestFocus() } catch (_: Exception) {} },
                    )
                }
            }

            Spacer(Modifier.width(24.dp))

            // ═════════════ RIGHT HALF ═════════════
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text("Chapters", color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("${book.chapters.size} total", color = PlayerDim, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))

                val listState = rememberLazyListState()
                LaunchedEffect(currentChapter) {
                    listState.animateScrollToItem(max(0, currentChapter - 1))
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(book.chapters, key = { _, c -> c.mp3Url }) { idx, ch ->
                        ChapterRow(
                            index = idx + 1,
                            title = ch.title,
                            isCurrent = idx == currentChapter,
                            isPlaying = idx == currentChapter && isPlaying,
                            focusRequester = if (idx == 0) chaptersFocus else null,
                            onClick = { loadChapter(idx) },
                            onDpadLeft = { try { playFocus.requestFocus() } catch (_: Exception) {} },
                        )
                    }
                }
            }
        }
        } // BoxWithConstraints
    }
}

// ─── Components ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onDpadUp: (() -> Unit)? = null,
    onDpadRight: (() -> Unit)? = null,
    onDpadLeft: (() -> Unit)? = null,
) {
    val bg = if (isPrimary) PlayerAccent else PlayerPanel
    val tint = if (isPrimary) Color.Black else Color.White

    Card(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> onDpadUp?.let { it(); true } ?: false
                    Key.DirectionRight -> onDpadRight?.let { it(); true } ?: false
                    Key.DirectionLeft -> onDpadLeft?.let { it(); true } ?: false
                    else -> false
                }
            },
        colors = CardDefaults.colors(containerColor = bg),
        shape = CardDefaults.shape(CircleShape),
        scale = CardDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
private fun SaveButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onDpadDown: () -> Unit,
    onDpadRight: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> PlayerAccent
        isLiked -> PlayerAccent.copy(alpha = 0.85f)
        else -> Color.Black.copy(alpha = 0.55f)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionDown -> { onDpadDown(); true }
                    Key.DirectionRight -> { onDpadRight(); true }
                    else -> false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isLiked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            null,
            tint = if (isLiked || focused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (isLiked) "Saved" else "Save",
            color = if (isLiked || focused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FocusableSeekBar(
    width: androidx.compose.ui.unit.Dp,
    positionMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    onSeekRelative: (Long) -> Unit,
    onDpadUp: () -> Unit,
    onDpadDown: () -> Unit,
    onDpadRight: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ratio = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier
            .width(width)
            .height(28.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeekRelative(-SEEK_STEP_MS); true }
                    Key.DirectionRight -> {
                        if (durationMs > 0 && positionMs >= durationMs - 500) {
                            onDpadRight(); true
                        } else { onSeekRelative(SEEK_STEP_MS); true }
                    }
                    Key.DirectionUp -> { onDpadUp(); true }
                    Key.DirectionDown -> { onDpadDown(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (focused) PlayerPanelLight else Color.White.copy(alpha = 0.15f))
                .align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PlayerAccent)
            )
        }
        if (focused) {
            val thumbOffset = (maxWidth - 14.dp) * ratio
            Box(
                Modifier
                    .padding(start = thumbOffset)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, PlayerAccent, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChapterRow(
    index: Int,
    title: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onDpadLeft: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isCurrent -> PlayerAccent.copy(alpha = 0.18f)
        else -> PlayerPanel
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) PlayerAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                    Key.DirectionLeft -> { onDpadLeft(); true }
                    else -> false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isCurrent) PlayerAccent else PlayerPanelLight),
            contentAlignment = Alignment.Center,
        ) {
            if (isCurrent && isPlaying) {
                Icon(Icons.Filled.Pause, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            } else if (isCurrent) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            } else {
                Text("$index", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = if (isCurrent || focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
