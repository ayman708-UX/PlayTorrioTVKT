package com.playtorrio.tv.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playtorrio.tv.data.reader.ComicImageLoader
import com.playtorrio.tv.data.reader.ComicsService
import com.playtorrio.tv.data.reader.MangaService
import com.playtorrio.tv.data.reader.ReadingProgressStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ReaderMode { PAGE, ZOOM, DRAG }

/**
 * Shared D-pad reader. Three OK-toggled modes:
 *   PAGE  → Left/Right = prev/next page.
 *   ZOOM  → Up = zoom in, Down = zoom out (current zoom is preserved on exit).
 *   DRAG  → D-pad pans the image at the current zoom.
 * Back exits the reader.
 */
@Composable
fun ReaderScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: ReaderViewModel = viewModel()

    LaunchedEffect(Unit) { vm.start(ReaderSession.startIndex, ReaderSession.startPageIndex) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Persist progress whenever chapter or page changes.
    val readerState = vm.state.value
    LaunchedEffect(readerState.chapterIndex, readerState.pageIndex, readerState.pageUrls.size) {
        if (readerState.pageUrls.isNotEmpty()) {
            ReadingProgressStore.upsert(
                ctx,
                ReadingProgressStore.Entry(
                    source = if (ReaderSession.source == ReaderSession.Source.MANGA)
                        ReadingProgressStore.Source.MANGA
                    else ReadingProgressStore.Source.COMIC,
                    workKey = ReaderSession.workKey,
                    workTitle = ReaderSession.workTitle,
                    coverUrl = ReaderSession.workCoverUrl,
                    chapterTitle = readerState.chapterTitle,
                    chapterKey = ReaderSession.chapters.getOrNull(readerState.chapterIndex)?.key.orEmpty(),
                    chapterIndex = readerState.chapterIndex,
                    pageIndex = readerState.pageIndex,
                    totalPages = readerState.pageUrls.size,
                    updatedAt = System.currentTimeMillis(),
                    comicSourceTag = ReaderSession.comicSourceTag,
                    comicSummary = ReaderSession.comicSummary,
                )
            )
        }
    }

    var mode by remember { mutableStateOf(ReaderMode.PAGE) }
    var modeBannerKey by remember { mutableIntStateOf(0) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun resetPan() { offsetX = 0f; offsetY = 0f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Back, Key.Escape -> {
                        navController.popBackStack(); true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        mode = when (mode) {
                            ReaderMode.PAGE -> ReaderMode.ZOOM
                            ReaderMode.ZOOM -> ReaderMode.DRAG
                            ReaderMode.DRAG -> {
                                // Returning to PAGE — reset zoom & pan so the next page fits.
                                scale = 1f; resetPan()
                                ReaderMode.PAGE
                            }
                        }
                        modeBannerKey++
                        true
                    }
                    Key.DirectionLeft -> when (mode) {
                        ReaderMode.PAGE -> { vm.prevPage(); resetPan(); scale = 1f; true }
                        ReaderMode.ZOOM -> true
                        ReaderMode.DRAG -> { offsetX += panStep(scale); true }
                    }
                    Key.DirectionRight -> when (mode) {
                        ReaderMode.PAGE -> { vm.nextPage(); resetPan(); scale = 1f; true }
                        ReaderMode.ZOOM -> true
                        ReaderMode.DRAG -> { offsetX -= panStep(scale); true }
                    }
                    Key.DirectionUp -> when (mode) {
                        ReaderMode.PAGE -> true
                        ReaderMode.ZOOM -> {
                            scale = (scale * 1.25f).coerceAtMost(5f); true
                        }
                        ReaderMode.DRAG -> { offsetY += panStep(scale); true }
                    }
                    Key.DirectionDown -> when (mode) {
                        ReaderMode.PAGE -> true
                        ReaderMode.ZOOM -> {
                            scale = (scale / 1.25f).coerceAtLeast(1f)
                            if (scale <= 1.0001f) resetPan()
                            true
                        }
                        ReaderMode.DRAG -> { offsetY -= panStep(scale); true }
                    }
                    else -> false
                }
            }
    ) {
        // ── Page image ──
        val state = vm.state.value
        val url = state.pageUrls.getOrNull(state.pageIndex)
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = ComicImageLoader.get(ctx),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }

        // ── Loading overlay ──
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF818CF8))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.loadingLabel,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error,
                    color = Color(0xFFF87171),
                    fontSize = 14.sp
                )
            }
        }

        // ── HUD: top bar + mode pill ──
        Column(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(20.dp)) {
            Text(
                text = ReaderSession.workTitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.chapterTitle.ifEmpty { "—" } +
                    if (state.pageUrls.isNotEmpty())
                        "   •   Page ${state.pageIndex + 1} / ${state.pageUrls.size}"
                    else "",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )
        }

        ModePill(
            mode = mode,
            visible = true,
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
        )

        // Bottom hint, only fades in when mode changes.
        ModeBanner(mode = mode, key = modeBannerKey,
            modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp))
    }
}

private fun panStep(scale: Float): Float = (120f * scale).coerceAtMost(420f)

@Composable
private fun ModePill(mode: ReaderMode, visible: Boolean, modifier: Modifier = Modifier) {
    val bg = when (mode) {
        ReaderMode.PAGE -> Color(0xFF818CF8)
        ReaderMode.ZOOM -> Color(0xFFC084FC)
        ReaderMode.DRAG -> Color(0xFF38BDF8)
    }
    val icon = when (mode) {
        ReaderMode.PAGE -> Icons.Filled.SwapHoriz
        ReaderMode.ZOOM -> Icons.Filled.ZoomIn
        ReaderMode.DRAG -> Icons.Filled.OpenWith
    }
    val label = when (mode) {
        ReaderMode.PAGE -> "PAGE"
        ReaderMode.ZOOM -> "ZOOM"
        ReaderMode.DRAG -> "DRAG"
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(bg.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = bg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(text = label, color = bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeBanner(mode: ReaderMode, key: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        visible = true
        kotlinx.coroutines.delay(1600)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "ba")
    val text = when (mode) {
        ReaderMode.PAGE -> "Press OK to enter Zoom mode  •  ◀ ▶ change pages"
        ReaderMode.ZOOM -> "▲ zoom in  •  ▼ zoom out  •  OK → Drag mode"
        ReaderMode.DRAG -> "D-pad to pan  •  OK → back to Page mode"
    }
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mouse,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────
//                         VIEW MODEL
// ────────────────────────────────────────────────────────────────────

class ReaderViewModel : androidx.lifecycle.ViewModel() {
    data class State(
        val chapterIndex: Int = 0,
        val chapterTitle: String = "",
        val pageUrls: List<String> = emptyList(),
        val pageIndex: Int = 0,
        val isLoading: Boolean = false,
        val loadingLabel: String = "Loading…",
        val error: String? = null,
    )

    val state = androidx.compose.runtime.mutableStateOf(State())

    fun start(initial: Int, initialPage: Int = 0) {
        if (state.value.pageUrls.isNotEmpty() && state.value.chapterIndex == initial) return
        loadChapter(initial, initialPage)
    }

    private fun loadChapter(index: Int, initialPage: Int = 0) {
        val chapters = ReaderSession.chapters
        if (chapters.isEmpty()) {
            state.value = State(error = "No chapters loaded.")
            return
        }
        val safe = index.coerceIn(0, chapters.size - 1)
        val ch = chapters[safe]
        state.value = state.value.copy(
            chapterIndex = safe,
            chapterTitle = ch.title,
            pageUrls = emptyList(),
            pageIndex = 0,
            isLoading = true,
            loadingLabel = "Loading ${ch.title}…",
            error = null,
        )
        viewModelScope.launch {
            val urls = runCatching {
                withContext(Dispatchers.IO) {
                    when (ReaderSession.source) {
                        ReaderSession.Source.MANGA -> MangaService.getChapterImages(ch.key)
                        ReaderSession.Source.COMIC -> ComicsService.getChapterPages(ch.key)
                    }
                }
            }.getOrElse {
                state.value = state.value.copy(isLoading = false, error = "Failed: ${it.message}")
                return@launch
            }
            val seek = initialPage.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
            state.value = state.value.copy(
                pageUrls = urls,
                pageIndex = seek,
                isLoading = false,
                error = if (urls.isEmpty()) "No pages found." else null
            )
            // One-shot: subsequent chapter switches start at page 0.
            ReaderSession.startPageIndex = 0
        }
    }

    fun nextPage() {
        val s = state.value
        if (s.pageIndex < s.pageUrls.size - 1) {
            state.value = s.copy(pageIndex = s.pageIndex + 1)
        } else if (s.chapterIndex < ReaderSession.chapters.size - 1) {
            loadChapter(s.chapterIndex + 1)
        }
    }

    fun prevPage() {
        val s = state.value
        if (s.pageIndex > 0) {
            state.value = s.copy(pageIndex = s.pageIndex - 1)
        } else if (s.chapterIndex > 0) {
            loadChapter(s.chapterIndex - 1)
        }
    }
}
