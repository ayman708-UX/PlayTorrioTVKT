package com.playtorrio.tv.ui.screens.audiobook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.data.audiobook.AudiobookDetail
import com.playtorrio.tv.data.audiobook.AudiobookSearchResult

private val Accent = Color(0xFFF59E0B)
private val SurfaceDark = Color(0xFF0A0A0F)
private val Panel = Color(0xFF14141F)
private val PanelLight = Color(0xFF1E1E2E)
private val TextDim = Color.White.copy(alpha = 0.55f)

// ═══════════════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudiobookScreen(navController: NavController) {
    val vm: AudiobookViewModel = viewModel()
    val state by vm.ui.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                    when {
                        state.openedBook != null -> { vm.closeBook(); true }
                        else -> { navController.popBackStack(); true }
                    }
                } else false
            }
    ) {
        // Subtle gradient backdrop
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1208).copy(alpha = 0.6f),
                            SurfaceDark,
                        ),
                        radius = 1400f
                    )
                )
        )

        Row(Modifier.fillMaxSize().padding(36.dp)) {
            // ── Left rail ────────────────────────────────────────
            LeftRail(
                state = state,
                onQueryChange = { vm.updateQuery(it) },
                onSearchSubmit = { vm.submitQuery() },
                onPickBrowse = { vm.setView(AudiobookView.BROWSE) },
                onPickSaved = { vm.setView(AudiobookView.LIKED) },
                onBack = { navController.popBackStack() },
            )

            Spacer(Modifier.width(28.dp))

            // ── Right content ────────────────────────────────────
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Continue-listening slider — only on the browse view, only when there
                // are entries. Hidden during search and on the "Saved" view.
                val showContinue = state.view == AudiobookView.BROWSE &&
                    state.query.isBlank() &&
                    state.continueListening.isNotEmpty()
                if (showContinue) {
                    ContinueListeningRow(
                        items = state.continueListening,
                        editMode = state.continueEditMode,
                        onToggleEdit = { vm.toggleContinueEditMode() },
                        onOpen = { vm.openProgress(it) },
                        onRemove = { vm.removeProgress(it.sourceId, it.id) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Header(state)
                Spacer(Modifier.height(16.dp))

                // First-poster focus requester so we can move focus into the
                // freshly-loaded grid after pagination instead of letting it
                // bounce to the back button on the left rail.
                val firstPosterFocusRequester = remember { FocusRequester() }
                var pendingFocusFirstPoster by remember { mutableStateOf(false) }
                var prevBrowsePage by remember { mutableStateOf(state.browsePage) }
                LaunchedEffect(state.browsePage) {
                    if (state.browsePage != prevBrowsePage) {
                        pendingFocusFirstPoster = true
                        prevBrowsePage = state.browsePage
                    }
                }
                LaunchedEffect(state.isLoadingResults, state.featured.size) {
                    if (pendingFocusFirstPoster &&
                        !state.isLoadingResults &&
                        state.featured.isNotEmpty()
                    ) {
                        // Wait one frame so the LazyVerticalGrid items are
                        // composed and the FocusRequester is attached.
                        kotlinx.coroutines.delay(40)
                        try { firstPosterFocusRequester.requestFocus() } catch (_: Exception) {}
                        pendingFocusFirstPoster = false
                    }
                }

                PostersGrid(
                    state = state,
                    onOpen = { vm.openBook(it) },
                    onOpenSaved = { vm.openSaved(it) },
                    onEnsurePoster = { vm.ensurePoster(it) },
                    onPrevPage = { vm.prevBrowsePage() },
                    onNextPage = { vm.nextBrowsePage() },
                    firstPosterFocusRequester = firstPosterFocusRequester,
                )
            }
        }

        // ── Loading overlay while fetching detail ────────────────
        if (state.isOpeningBook) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading audiobook…", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // ── Error toast ──────────────────────────────────────────
        state.openError?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 36.dp), Alignment.BottomCenter) {
                Card(
                    onClick = { vm.dismissOpenError() },
                    colors = CardDefaults.colors(containerColor = Color(0xFF7F1D1D)),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                ) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(msg, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Fullscreen player ────────────────────────────────────
        AnimatedVisibility(
            visible = state.openedBook != null,
            enter = slideInVertically(tween(320)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(),
        ) {
            state.openedBook?.let { book ->
                // Consume any pending resume info exactly once when the player composes.
                val resume = remember(book.sourceId, book.id) { vm.consumePendingResume() }
                AudiobookPlayerScreen(
                    book = book,
                    isLiked = vm.isSaved(book),
                    onToggleLike = { vm.toggleSave(book) },
                    onClose = { vm.closeBook() },
                    initialChapter = resume?.first ?: 0,
                    initialPositionMs = resume?.second ?: 0L,
                    onProgress = { ch, pos, dur -> vm.saveProgress(book, ch, pos, dur) },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LEFT RAIL
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeftRail(
    state: AudiobookUiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onPickBrowse: () -> Unit,
    onPickSaved: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
    ) {
        // Title row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
                colors = CardDefaults.colors(containerColor = Panel),
                shape = CardDefaults.shape(CircleShape),
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.Headphones, null, tint = Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Audiobooks", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(28.dp))

        // Search bar
        SearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearchSubmit = { onSearchSubmit() },
        )

        Spacer(Modifier.height(20.dp))

        // Browse button
        RailButton(
            icon = Icons.Filled.MenuBook,
            label = "Browse",
            isActive = state.view == AudiobookView.BROWSE,
            onClick = onPickBrowse,
        )

        Spacer(Modifier.height(8.dp))

        // Saved button
        RailButton(
            icon = if (state.view == AudiobookView.LIKED) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            label = "Saved (${state.saved.size})",
            isActive = state.view == AudiobookView.LIKED,
            onClick = onPickSaved,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Accent else Color.White.copy(alpha = 0.08f)
    val focusManager = LocalFocusManager.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search, null,
            tint = if (isFocused) Accent else TextDim,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onSearchSubmit(); true
                        }
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down); true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up); true
                        }
                        Key.DirectionRight -> {
                            focusManager.moveFocus(FocusDirection.Right); true
                        }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search audiobooks…",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isActive -> Accent.copy(alpha = 0.22f)
        else -> Panel
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (isActive || focused) Accent else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (isActive || focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HEADER + GRID
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun Header(state: AudiobookUiState) {
    val title = when {
        state.view == AudiobookView.LIKED -> "Saved audiobooks"
        state.query.isNotBlank() -> "Results for \"${state.query}\""
        else -> "Featured"
    }
    val subtitle = when {
        state.view == AudiobookView.LIKED -> "${state.saved.size} books"
        state.query.isNotBlank() -> "${state.results.size} found"
        else -> "${state.featured.size} books"
    }

    Column {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TextDim, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PostersGrid(
    state: AudiobookUiState,
    onOpen: (AudiobookSearchResult) -> Unit,
    onOpenSaved: (AudiobookDetail) -> Unit,
    onEnsurePoster: (AudiobookSearchResult) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    firstPosterFocusRequester: FocusRequester? = null,
) {
    val isLiked = state.view == AudiobookView.LIKED

    if (state.isLoadingResults && !isLiked) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.size(40.dp))
        }
        return
    }

    if (isLiked) {
        if (state.saved.isEmpty()) {
            EmptyState(
                "No saved audiobooks yet",
                "Open a book and tap the like button to keep it here."
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.saved, key = { _, b -> "${b.sourceId}:${b.id}" }) { _, book ->
                PosterCard(
                    title = book.title,
                    posterUrl = book.posterUrl,
                    onClick = { onOpenSaved(book) },
                    onFocus = { /* saved already has poster */ },
                )
            }
        }
        return
    }

    val items = if (state.query.isBlank()) state.featured else state.results
    if (items.isEmpty()) {
        EmptyState(
            if (state.query.isBlank()) "Nothing here yet" else "No results",
            "Try searching for a title or author."
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, r -> "${r.sourceId}:${r.id}" }) { idx, r ->
            // Kick off poster fetch when item enters composition (lazy backfill).
            LaunchedEffect(r.sourceId, r.id, r.posterUrl) {
                if (r.posterUrl.isNullOrBlank()) onEnsurePoster(r)
            }
            PosterCard(
                title = r.title,
                posterUrl = r.posterUrl,
                onClick = { onOpen(r) },
                onFocus = { onEnsurePoster(r) },
                focusRequester = if (idx == 0) firstPosterFocusRequester else null,
            )
        }
        // Pagination footer — only on the browse view (not search results).
        if (state.query.isBlank() && (state.browsePage > 0 || state.browseHasMore)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowsePager(
                    page = state.browsePage,
                    hasPrev = state.browsePage > 0,
                    hasNext = state.browseHasMore,
                    onPrev = onPrevPage,
                    onNext = onNextPage,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowsePager(
    page: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerButton(
            label = "Previous",
            enabled = hasPrev,
            onClick = onPrev,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            "Page ${page + 1}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(20.dp))
        PagerButton(
            label = "Next",
            enabled = hasNext,
            onClick = onNext,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PagerButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        !enabled -> Panel.copy(alpha = 0.4f)
        focused -> Accent
        else -> Panel
    }
    val textColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        focused -> Color.Black
        else -> Color.White
    }
    Card(
        onClick = { if (enabled) onClick() },
        colors = CardDefaults.colors(containerColor = container),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Box(Modifier.padding(horizontal = 24.dp).fillMaxHeight(), Alignment.Center) {
            Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CONTINUE LISTENING
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueListeningRow(
    items: List<com.playtorrio.tv.data.audiobook.AudiobookProgress>,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    onOpen: (com.playtorrio.tv.data.audiobook.AudiobookProgress) -> Unit,
    onRemove: (com.playtorrio.tv.data.audiobook.AudiobookProgress) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditPenButton(active = editMode, onClick = onToggleEdit)
            Spacer(Modifier.width(10.dp))
            Text(
                "Continue listening",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (editMode) "Tap to remove" else "· ${items.size}",
                color = TextDim,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { "${it.sourceId}:${it.id}" }) { entry ->
                ContinueCard(
                    entry = entry,
                    editMode = editMode,
                    onClick = {
                        if (editMode) onRemove(entry) else onOpen(entry)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EditPenButton(active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> Accent
        active -> Accent.copy(alpha = 0.85f)
        else -> Panel
    }
    val tint = if (focused || active) Color.Black else Color.White
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(containerColor = container),
        shape = CardDefaults.shape(CircleShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        modifier = Modifier
            .size(28.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = Color.White,
                shape = CircleShape,
            ),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(
                imageVector = if (active) Icons.Filled.Close else Icons.Filled.Edit,
                contentDescription = if (active) "Done" else "Edit",
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueCard(
    entry: com.playtorrio.tv.data.audiobook.AudiobookProgress,
    editMode: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ratio = if (entry.durationMs > 0L)
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f

    // Compact landscape strip: small poster + title/meta to the right of it.
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(230.dp)
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (editMode) Color(0xFFEF4444) else Accent,
                shape = RoundedCornerShape(8.dp),
            ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        colors = CardDefaults.colors(
            containerColor = if (focused) PanelLight else Panel,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(6.dp)
                    .size(width = 40.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PanelLight),
            ) {
                if (!entry.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(
                            Icons.Filled.Headphones, null,
                            tint = Accent.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (editMode) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close, "Remove",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Ch ${entry.chapterIndex + 1} \u00B7 ${formatProgressTime(entry.positionMs)}",
                    color = TextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio.coerceAtLeast(0.02f))
                            .background(Accent)
                    )
                }
            }
        }
    }
}

private fun formatProgressTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.MenuBook, null, tint = TextDim, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.width(190.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) Accent else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            colors = CardDefaults.colors(containerColor = PanelLight),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(PanelLight, Panel))
                    )) {
                        Column(
                            Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.Headphones, null, tint = Accent.copy(alpha = 0.55f), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                title,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (focused) 6 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
