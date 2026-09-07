package com.playtorrio.tv.ui.screens.manga

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.ui.components.ErrorState
import com.playtorrio.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class MangaReaderMode {
    PAGE_TURN,   // Mode 1: Left / Right flips page
    ZOOM,        // Mode 2: Up / Down zooms in / out
    PAN_CURSOR   // Mode 3: DPAD drags virtual dot, camera follows keeping zoom
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    onBackPress: () -> Unit,
    viewModel: MangaReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current

    val focusRequester = remember { FocusRequester() }

    // Reader Mode State: Starts in Mode 1
    var currentMode by remember { mutableStateOf(MangaReaderMode.PAGE_TURN) }

    // Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Virtual cursor position for Mode 3 (in pixels)
    var cursorX by remember { mutableFloatStateOf(0f) }
    var cursorY by remember { mutableFloatStateOf(0f) }
    var cursorInitialized by remember { mutableStateOf(false) }

    // HUD banner visibility (auto-hide after idle)
    var isHudVisible by remember { mutableStateOf(true) }
    var hudRevision by remember { mutableStateOf(0) }

    // End-of-chapter dialog prompt
    var showNextChapterPrompt by remember { mutableStateOf(false) }
    var showPrevChapterPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-hide HUD after 4 seconds of inactivity
    LaunchedEffect(hudRevision) {
        isHudVisible = true
        delay(4000)
        isHudVisible = false
    }

    // Preload next 2 pages with Coil
    LaunchedEffect(uiState.currentPageIndex, uiState.pages) {
        val nextIdx1 = uiState.currentPageIndex + 1
        val nextIdx2 = uiState.currentPageIndex + 2
        val imageLoader = ImageLoader(context)

        if (nextIdx1 < uiState.pages.size) {
            val req1 = ImageRequest.Builder(context)
                .data(uiState.pages[nextIdx1])
                .build()
            imageLoader.enqueue(req1)
        }
        if (nextIdx2 < uiState.pages.size) {
            val req2 = ImageRequest.Builder(context)
                .data(uiState.pages[nextIdx2])
                .build()
            imageLoader.enqueue(req2)
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    fun resetZoomAndPan() {
        zoomScale = 1.0f
        panOffsetX = 0f
        panOffsetY = 0f
        cursorX = screenWidthPx / 2f
        cursorY = screenHeightPx / 2f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (currentMode != MangaReaderMode.PAN_CURSOR) {
                    hudRevision++ // Wake up HUD on key press in Mode 1 and 2
                }

                if (!cursorInitialized) {
                    cursorX = screenWidthPx / 2f
                    cursorY = screenHeightPx / 2f
                    cursorInitialized = true
                }

                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        onBackPress()
                        return@onPreviewKeyEvent true
                    }

                    // --- OK BUTTON (DPAD_CENTER / ENTER / BUTTON_A): Mode Switcher ---
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        currentMode = when (currentMode) {
                            MangaReaderMode.PAGE_TURN -> {
                                isHudVisible = true
                                MangaReaderMode.ZOOM
                            }
                            MangaReaderMode.ZOOM -> {
                                // Initialize cursor at center when entering Mode 3
                                cursorX = screenWidthPx / 2f
                                cursorY = screenHeightPx / 2f
                                isHudVisible = false
                                MangaReaderMode.PAN_CURSOR
                            }
                            MangaReaderMode.PAN_CURSOR -> {
                                isHudVisible = true
                                MangaReaderMode.PAGE_TURN
                            }
                        }
                        return@onPreviewKeyEvent true
                    }

                    // --- MODE 1: PAGE FLIP ---
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (currentMode == MangaReaderMode.PAGE_TURN) {
                            val moved = viewModel.nextPage()
                            if (moved) {
                                resetZoomAndPan()
                            } else {
                                showNextChapterPrompt = true
                            }
                            return@onPreviewKeyEvent true
                        } else if (currentMode == MangaReaderMode.PAN_CURSOR) {
                            // Move cursor right
                            val step = with(density) { 36.dp.toPx() }
                            cursorX = (cursorX + step).coerceIn(0f, screenWidthPx)
                            panOffsetX = ((screenWidthPx / 2f) - cursorX) * (zoomScale - 1f)
                            return@onPreviewKeyEvent true
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (currentMode == MangaReaderMode.PAGE_TURN) {
                            val moved = viewModel.prevPage()
                            if (moved) {
                                resetZoomAndPan()
                            } else {
                                showPrevChapterPrompt = true
                            }
                            return@onPreviewKeyEvent true
                        } else if (currentMode == MangaReaderMode.PAN_CURSOR) {
                            // Move cursor left
                            val step = with(density) { 36.dp.toPx() }
                            cursorX = (cursorX - step).coerceIn(0f, screenWidthPx)
                            panOffsetX = ((screenWidthPx / 2f) - cursorX) * (zoomScale - 1f)
                            return@onPreviewKeyEvent true
                        }
                    }

                    // --- MODE 2: ZOOM & MODE 3: VERTICAL PAN ---
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (currentMode) {
                            MangaReaderMode.ZOOM -> {
                                zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f)
                                return@onPreviewKeyEvent true
                            }
                            MangaReaderMode.PAN_CURSOR -> {
                                val step = with(density) { 36.dp.toPx() }
                                cursorY = (cursorY - step).coerceIn(0f, screenHeightPx)
                                panOffsetY = ((screenHeightPx / 2f) - cursorY) * (zoomScale - 1f)
                                return@onPreviewKeyEvent true
                            }
                            MangaReaderMode.PAGE_TURN -> {
                                isHudVisible = !isHudVisible
                                return@onPreviewKeyEvent true
                            }
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (currentMode) {
                            MangaReaderMode.ZOOM -> {
                                zoomScale = (zoomScale - 0.25f).coerceAtLeast(1.0f)
                                if (zoomScale == 1.0f) {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                                return@onPreviewKeyEvent true
                            }
                            MangaReaderMode.PAN_CURSOR -> {
                                val step = with(density) { 36.dp.toPx() }
                                cursorY = (cursorY + step).coerceIn(0f, screenHeightPx)
                                panOffsetY = ((screenHeightPx / 2f) - cursorY) * (zoomScale - 1f)
                                return@onPreviewKeyEvent true
                            }
                            MangaReaderMode.PAGE_TURN -> {
                                isHudVisible = !isHudVisible
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }

                false
            }
    ) {

        // --- Main Content ---
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            uiState.error != null -> {
                ErrorState(
                    message = uiState.error ?: "Unable to load chapter",
                    onRetry = { viewModel.loadChapterAndMetadata(uiState.chapterId, uiState.currentPageIndex) }
                )
            }

            uiState.pages.isNotEmpty() -> {
                val currentPageUrl = uiState.pages.getOrNull(uiState.currentPageIndex).orEmpty()

                // Display Image with Zoom and Pan Transformations
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffsetX
                            translationY = panOffsetY
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentPageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page ${uiState.currentPageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Mode 3: Glowing Virtual Cursor Dot Overlay
                if (currentMode == MangaReaderMode.PAN_CURSOR) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pos = Offset(cursorX, cursorY)
                        // Glowing outer halo
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                            radius = 18.dp.toPx(),
                            center = pos
                        )
                        // Middle ring
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = 8.dp.toPx(),
                            center = pos
                        )
                        // Solid core dot
                        drawCircle(
                            color = Color(0xFF0284C7),
                            radius = 5.dp.toPx(),
                            center = pos
                        )
                    }
                }

                // --- HUD Indicator Bar ---
                AnimatedVisibility(
                    visible = isHudVisible && currentMode != MangaReaderMode.PAN_CURSOR,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ReaderHudBar(
                        mode = currentMode,
                        chapterName = uiState.currentChapter?.name ?: "Chapter",
                        pageIndex = uiState.currentPageIndex,
                        totalPages = uiState.pages.size,
                        zoomPercent = (zoomScale * 100).roundToInt()
                    )
                }
            }
        }

        // --- Next Chapter Prompt Dialog ---
        if (showNextChapterPrompt) {
            ChapterPromptDialog(
                title = "End of Chapter",
                message = "You have reached the end of this chapter. Load next chapter?",
                confirmLabel = "Next Chapter ▶",
                onConfirm = {
                    showNextChapterPrompt = false
                    val loaded = viewModel.loadNextChapter()
                    if (loaded) {
                        resetZoomAndPan()
                    }
                },
                onDismiss = { showNextChapterPrompt = false }
            )
        }

        // --- Prev Chapter Prompt Dialog ---
        if (showPrevChapterPrompt) {
            ChapterPromptDialog(
                title = "Start of Chapter",
                message = "You are on the first page. Load previous chapter?",
                confirmLabel = "◀ Previous Chapter",
                onConfirm = {
                    showPrevChapterPrompt = false
                    val loaded = viewModel.loadPrevChapter()
                    if (loaded) {
                        resetZoomAndPan()
                    }
                },
                onDismiss = { showPrevChapterPrompt = false }
            )
        }
    }
}

@Composable
private fun ReaderHudBar(
    mode: MangaReaderMode,
    chapterName: String,
    pageIndex: Int,
    totalPages: Int,
    zoomPercent: Int
) {
    val (modeBadgeText, modeBadgeBg, modeInstruction) = when (mode) {
        MangaReaderMode.PAGE_TURN -> Triple(
            "MODE 1: PAGE",
            Color(0xFF0284C7),
            "◄ Left: Prev Page  |  Right: Next Page ►  •  Press OK for Zoom Mode"
        )
        MangaReaderMode.ZOOM -> Triple(
            "MODE 2: ZOOM ($zoomPercent%)",
            Color(0xFFD97706),
            "▲ Up: Zoom In  |  Down: Zoom Out ▼  •  Press OK for Pan/Drag Mode"
        )
        MangaReaderMode.PAN_CURSOR -> Triple(
            "MODE 3: PAN / DRAG",
            Color(0xFF059669),
            "◄ ▲ ▼ ► Move Cursor to Pan Page  •  Press OK to Return to Page Mode"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070A13).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Mode badge & instructions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(modeBadgeBg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = modeBadgeText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = modeInstruction,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Right: Chapter Name & Page Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = chapterName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pageIndex + 1} / $totalPages",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChapterPromptDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .widthIn(min = 320.dp, max = 440.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF334155)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Stay on Page",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF0284C7),
                            focusedContainerColor = Color(0xFF38BDF8)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = confirmLabel,
                            color = Color(0xFF070A13),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
