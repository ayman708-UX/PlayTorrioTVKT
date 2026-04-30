package com.playtorrio.tv.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playtorrio.tv.data.reader.ComicImageLoader
import com.playtorrio.tv.data.reader.ReadingProgressStore

private val SurfaceDarkLib = Color(0xFF0A0A0F)
private val PanelLib = Color(0xFF14141F)
private val PanelLightLib = Color(0xFF1E1E2E)
private val TextDimLib = Color.White.copy(alpha = 0.55f)

// ─────────────────────────── HEADER + SEARCH ───────────────────────────

@Composable
fun ReaderHeader(
    title: String,
    subtitle: String,
    accent: Color,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    page: Int,
    pagedMode: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Text(text = subtitle, color = TextDimLib, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        ReaderSearchField(
            query = query,
            placeholder = "Search $title…",
            accent = accent,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onClear = onClear,
        )
        if (pagedMode) {
            Spacer(Modifier.width(12.dp))
            if (page > 1) {
                ReaderPagerButton("◀ Prev", accent) { onPrevPage() }
                Spacer(Modifier.width(8.dp))
            }
            ReaderPagerButton("Page $page", accent, dim = true) {}
            Spacer(Modifier.width(8.dp))
            ReaderPagerButton("Next ▶", accent) { onNextPage() }
        }
    }
}

@Composable
fun ReaderSearchField(
    query: String,
    placeholder: String,
    accent: Color,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(220.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (focused) accent.copy(alpha = 0.18f) else PanelLib)
            .border(
                1.dp,
                if (focused) accent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 12.dp)
            .onFocusChanged { focused = it.isFocused }
            // Intercept D-pad navigation BEFORE the text field consumes it so
            // the user can leave the search box without hitting Back.
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionDown -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down); true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); true
                    }
                    Key.DirectionLeft -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left); true
                    }
                    // Right only escapes when the field is empty (otherwise the
                    // user is editing and Right should move the cursor).
                    Key.DirectionRight -> {
                        if (query.isEmpty()) {
                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right); true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        Icon(Icons.Filled.Search, null, tint = if (focused) accent else TextDimLib, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(placeholder, color = TextDimLib, fontSize = 13.sp)
            }
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .focusable()
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown &&
                            (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                            onClear(); true
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun ReaderPagerButton(label: String, accent: Color, dim: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                    onClick(); true
                } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) accent.copy(alpha = 0.35f) else PanelLib)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) accent else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (dim) TextDimLib else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────── RESPONSIVE GRID ───────────────────────────

@Composable
fun <T : Any> ResponsivePosterGrid(
    items: List<T>,
    keyOf: (T) -> Any,
    coverUrlOf: (T) -> String,
    titleOf: (T) -> String,
    accent: Color,
    useReferer: Boolean,
    onClicked: (T) -> Unit,
) {
    val ctx = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Responsive column count: target poster width ~120dp.
        val cols = (maxWidth.value / 120f).toInt().coerceIn(4, 10)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, it -> keyOf(it) }) { _, item ->
                PosterCard(
                    title = titleOf(item),
                    coverUrl = coverUrlOf(item),
                    accent = accent,
                    useReferer = useReferer,
                    onClick = { onClicked(item) },
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    coverUrl: String,
    accent: Color,
    useReferer: Boolean,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier = Modifier.graphicsLayer {
            val s = if (focused) 1.08f else 1f; scaleX = s; scaleY = s
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .onFocusChanged { focused = it.isFocused }
                .focusable(interactionSource = interactionSource)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown &&
                        (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                        onClick(); true
                    } else false
                }
                .clip(RoundedCornerShape(8.dp))
                .background(PanelLib)
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) accent else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(8.dp)
                )
        ) {
            if (useReferer) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(coverUrl).crossfade(true).build(),
                    imageLoader = ComicImageLoader.get(ctx),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────── CHAPTER LIST ───────────────────────────

data class ChapterListEntry(
    val id: String,
    val label: String,
    val sub: String,
    val index: Int,
)

@Composable
fun ChapterList(
    chapters: List<ChapterListEntry>,
    onClicked: (ChapterListEntry) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(chapters.size) {
        if (chapters.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            runCatching { firstFocus.requestFocus() }
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listItemsIndexed(chapters, key = { _, c -> c.id }) { idx, ch ->
            ChapterRowItem(
                label = ch.label,
                sub = ch.sub,
                focusRequester = if (idx == 0) firstFocus else null,
                onClick = { onClicked(ch) },
            )
        }
    }
}

@Composable
private fun ChapterRowItem(
    label: String,
    sub: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val accent = Color(0xFF818CF8)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                    onClick(); true
                } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) accent.copy(alpha = 0.28f) else PanelLib)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) accent else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) {
                Text(sub, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────── CONTINUE READING ───────────────────────────

@Composable
fun ContinueReadingRow(
    entries: List<ReadingProgressStore.Entry>,
    accent: Color,
    onOpen: (ReadingProgressStore.Entry) -> Unit,
    onRemove: (ReadingProgressStore.Entry) -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    // Auto-exit edit mode if list becomes empty.
    LaunchedEffect(entries.size) { if (entries.isEmpty()) editMode = false }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                "Continue Reading",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (editMode) "Tap a card to remove" else "\u00B7 ${entries.size}",
                color = if (editMode) Color(0xFFEF4444) else TextDimLib,
                fontSize = 10.sp,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "__edit_toggle__") {
                ContinueEditToggle(
                    active = editMode,
                    accent = accent,
                    onClick = { editMode = !editMode },
                )
            }
            listItemsIndexed(entries, key = { _, e -> "${e.source}_${e.workKey}" }) { _, e ->
                ContinueCard(
                    entry = e,
                    accent = accent,
                    editMode = editMode,
                    onActivate = {
                        if (editMode) onRemove(e) else onOpen(e)
                    },
                )
            }
        }
    }
}

@Composable
private fun ContinueEditToggle(
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val red = Color(0xFFEF4444)
    val border = when {
        active -> red
        focused -> accent
        else -> Color.White.copy(alpha = 0.06f)
    }
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                    onClick(); true
                } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(when {
                active -> red.copy(alpha = 0.18f)
                focused -> PanelLightLib
                else -> PanelLib
            })
            .border(
                if (focused || active) 2.dp else 1.dp,
                border,
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (active) Icons.Filled.Close else Icons.Filled.Edit,
                contentDescription = if (active) "Done" else "Edit",
                tint = if (active) red else Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (active) "Done" else "Edit",
                color = if (active) red else TextDimLib,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ContinueCard(
    entry: ReadingProgressStore.Entry,
    accent: Color,
    editMode: Boolean,
    onActivate: () -> Unit,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val isComic = entry.source == ReadingProgressStore.Source.COMIC
    val pct = if (entry.totalPages > 0)
        ((entry.pageIndex + 1).toFloat() / entry.totalPages).coerceIn(0f, 1f)
    else 0f
    val red = Color(0xFFEF4444)
    val borderColor = when {
        editMode && focused -> red
        focused -> accent
        editMode -> red.copy(alpha = 0.4f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.DirectionCenter)) {
                    onActivate(); true
                } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(when {
                editMode && focused -> red.copy(alpha = 0.18f)
                focused -> PanelLightLib
                else -> PanelLib
            })
            .border(
                if (focused || editMode) 2.dp else 1.dp,
                borderColor,
                RoundedCornerShape(8.dp)
            )
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp)) {
            // Cover thumb
            Box(
                Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceDarkLib)
            ) {
                if (isComic) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(entry.coverUrl).build(),
                        imageLoader = ComicImageLoader.get(ctx),
                        contentDescription = entry.workTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = entry.coverUrl,
                        contentDescription = entry.workTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    entry.workTitle,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.chapterTitle,
                    color = TextDimLib,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${entry.pageIndex + 1}/${entry.totalPages}",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                // Progress bar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct)
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            }
        }
    }
}

// ─────────────────────────── RESUME OVERLAY ───────────────────────────

@Composable
fun ResumeOverlay(
    label: String,
    error: String?,
    accent: Color,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Swallow all key events while overlay is up so background doesn't react.
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && error != null &&
                    (it.key == Key.Back || it.key == Key.Escape ||
                     it.key == Key.Enter || it.key == Key.NumPadEnter ||
                     it.key == Key.DirectionCenter)) {
                    onDismiss(); true
                } else true
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                androidx.compose.material3.CircularProgressIndicator(color = accent)
                Spacer(Modifier.height(14.dp))
                Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(error, color = Color(0xFFF87171), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Press OK to dismiss", color = TextDimLib, fontSize = 11.sp)
            }
        }
    }
}