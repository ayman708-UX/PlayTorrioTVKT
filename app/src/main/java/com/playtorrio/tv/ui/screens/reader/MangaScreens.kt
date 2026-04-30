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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playtorrio.tv.data.reader.ComicImageLoader
import com.playtorrio.tv.data.reader.Manga
import com.playtorrio.tv.data.reader.MangaChapter
import com.playtorrio.tv.data.reader.MangaService
import com.playtorrio.tv.data.reader.ReadingProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFF818CF8)
private val SurfaceDark = Color(0xFF0A0A0F)
private val Panel = Color(0xFF14141F)
private val PanelLight = Color(0xFF1E1E2E)
private val TextDim = Color.White.copy(alpha = 0.55f)

// ════════════════════════════════════════════════════════════════════
//                          BROWSE
// ════════════════════════════════════════════════════════════════════

@Composable
fun MangaScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: MangaBrowseViewModel = viewModel()
    val state by vm.state.collectAsState()
    val resumeState by vm.resumeState.collectAsState()

    LaunchedEffect(Unit) { if (state.items.isEmpty() && state.query.isEmpty()) vm.load(1) }

    // Refresh continue-reading every time the screen comes back to the foreground.
    var continueEntries by remember { mutableStateOf(emptyList<ReadingProgressStore.Entry>()) }
    LaunchedEffect(Unit) {
        continueEntries = ReadingProgressStore.list(ctx)
            .filter { it.source == ReadingProgressStore.Source.MANGA }
    }

    // When resume is ready, jump straight to the reader.
    LaunchedEffect(resumeState.ready) {
        if (resumeState.ready) {
            navController.navigate("reader")
            vm.consumeResumeReady()
        }
    }

    Box(
        Modifier.fillMaxSize().background(SurfaceDark)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                    navController.popBackStack(); true
                } else false
            }
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            ReaderHeader(
                title = "Manga",
                subtitle = "WeebCentral",
                accent = Accent,
                query = state.query,
                onQueryChange = { vm.updateQuery(it) },
                onSubmit = { vm.submitSearch() },
                onClear = { vm.clearSearch() },
                page = state.page,
                pagedMode = state.query.isEmpty(),
                onPrevPage = { vm.load(state.page - 1) },
                onNextPage = { vm.load(state.page + 1) },
            )
            Spacer(Modifier.height(10.dp))

            if (continueEntries.isNotEmpty() && state.query.isEmpty()) {
                ContinueReadingRow(
                    entries = continueEntries,
                    accent = Accent,
                    onOpen = { entry -> vm.resume(entry) },
                    onRemove = { entry ->
                        ReadingProgressStore.remove(ctx, entry.source, entry.workKey)
                        continueEntries = continueEntries.filterNot { it.workKey == entry.workKey }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (state.isLoading && state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isNotEmpty()) "No results" else "Empty",
                        color = TextDim, fontSize = 14.sp
                    )
                }
            } else {
                ResponsivePosterGrid(
                    items = state.items,
                    keyOf = { it.id },
                    coverUrlOf = { it.coverNormal.ifEmpty { it.coverSmall } },
                    titleOf = { it.title },
                    accent = Accent,
                    useReferer = false,
                    onClicked = { manga -> navController.navigate("manga_detail/${manga.id}") },
                )
            }
        }

        // Resume overlay (Continue Reading → opens reader directly).
        if (resumeState.isResuming || resumeState.error != null) {
            ResumeOverlay(
                label = resumeState.label,
                error = resumeState.error,
                accent = Accent,
                onDismiss = { vm.consumeResumeReady() },
            )
        }
    }
}

private fun resumeManga(navController: NavController, entry: ReadingProgressStore.Entry) {
    // Fallback: just open the detail screen if VM-driven resume isn't available.
    navController.navigate("manga_detail/${entry.workKey}")
}

@Composable
fun MangaDetailsScreen(seriesId: String, navController: NavController) {
    val vm: MangaDetailsViewModel = viewModel()
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(SurfaceDark)
        .onPreviewKeyEvent { e ->
            if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                navController.popBackStack(); true
            } else false
        }) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Box
        }
        val manga = state.manga ?: return@Box

        Row(Modifier.fillMaxSize().padding(20.dp)) {
            Column(Modifier.width(280.dp).fillMaxHeight()) {
                AsyncImage(
                    model = manga.coverNormal,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Panel)
                )
                Spacer(Modifier.height(14.dp))
                Text(manga.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (manga.author.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(manga.author, color = TextDim, fontSize = 12.sp)
                }
                if (manga.type.isNotEmpty() || manga.status.isNotEmpty() || manga.year.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(manga.type, manga.status, manga.year)
                            .filter { it.isNotEmpty() }.joinToString(" • "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                if (manga.synopsis.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        manga.synopsis,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmark, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.chapters.size} chapter${if (state.chapters.size != 1) "s" else ""}",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                ChapterList(
                    chapters = state.chapters.mapIndexed { i, c ->
                        ChapterListEntry(
                            id = c.id,
                            label = c.rawName.ifEmpty { "Chapter ${c.number}" },
                            sub = "",
                            index = i,
                        )
                    },
                    onClicked = { entry ->
                        ReaderSession.set(
                            source = ReaderSession.Source.MANGA,
                            workKey = manga.id,
                            workTitle = manga.title,
                            workCoverUrl = manga.coverNormal.ifEmpty { manga.coverSmall },
                            chapters = state.chapters.map {
                                ReaderSession.ChapterRef(
                                    it.rawName.ifEmpty { "Chapter ${it.number}" },
                                    it.id
                                )
                            },
                            startIndex = entry.index,
                        )
                        navController.navigate("reader")
                    },
                )
            }
        }
    }
}

// ───────────────────────── ViewModels ─────────────────────────

class MangaBrowseViewModel : ViewModel() {
    data class State(
        val items: List<Manga> = emptyList(),
        val page: Int = 1,
        val isLoading: Boolean = false,
        val query: String = "",
    )
    data class ResumeState(
        val isResuming: Boolean = false,
        val label: String = "",
        val error: String? = null,
        val ready: Boolean = false,
    )
    private val _s = MutableStateFlow(State())
    val state = _s.asStateFlow()
    private val _resume = MutableStateFlow(ResumeState())
    val resumeState = _resume.asStateFlow()
    private var searchJob: Job? = null
    private var resumeJob: Job? = null

    fun load(page: Int) {
        if (page < 1) return
        _s.value = _s.value.copy(isLoading = true, page = page, items = emptyList(), query = "")
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MangaService.getManga(page) }
            _s.value = _s.value.copy(items = list, isLoading = false)
        }
    }

    fun updateQuery(q: String) {
        _s.value = _s.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            if (_s.value.items.isEmpty()) load(1)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(q)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        if (_s.value.query.isBlank()) return
        viewModelScope.launch { runSearch(_s.value.query) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _s.value = _s.value.copy(query = "")
        load(1)
    }

    private suspend fun runSearch(q: String) {
        _s.value = _s.value.copy(isLoading = true, items = emptyList())
        val list = withContext(Dispatchers.IO) { MangaService.searchManga(q) }
        _s.value = _s.value.copy(items = list, isLoading = false)
    }

    fun resume(entry: ReadingProgressStore.Entry) {
        resumeJob?.cancel()
        _resume.value = ResumeState(isResuming = true, label = "Opening ${entry.workTitle}\u2026")
        resumeJob = viewModelScope.launch {
            val (manga, chapters) = runCatching {
                withContext(Dispatchers.IO) {
                    MangaService.getSeriesDetail(entry.workKey) to MangaService.getChapters(entry.workKey)
                }
            }.getOrElse {
                _resume.value = ResumeState(isResuming = false, error = "Failed to load: ${it.message}")
                return@launch
            }
            if (chapters.isEmpty()) {
                _resume.value = ResumeState(isResuming = false, error = "No chapters found.")
                return@launch
            }
            // Find chapter by saved key first, fall back to saved index.
            val foundIdx = chapters.indexOfFirst { it.id == entry.chapterKey }
            val chapterIndex = if (foundIdx >= 0) foundIdx
                else entry.chapterIndex.coerceIn(0, chapters.size - 1)
            ReaderSession.set(
                source = ReaderSession.Source.MANGA,
                workKey = manga.id,
                workTitle = manga.title,
                workCoverUrl = manga.coverNormal.ifEmpty { manga.coverSmall },
                chapters = chapters.map { ReaderSession.ChapterRef(
                    it.rawName.ifEmpty { "Chapter ${it.number}" }, it.id
                ) },
                startIndex = chapterIndex,
                startPageIndex = entry.pageIndex,
            )
            _resume.value = ResumeState(isResuming = false, ready = true)
        }
    }

    fun consumeResumeReady() {
        _resume.value = ResumeState()
    }
}

class MangaDetailsViewModel : ViewModel() {
    data class State(
        val manga: Manga? = null,
        val chapters: List<MangaChapter> = emptyList(),
        val isLoading: Boolean = true,
    )
    private val _s = MutableStateFlow(State())
    val state = _s.asStateFlow()

    fun load(seriesId: String) {
        if (_s.value.manga?.id == seriesId && _s.value.chapters.isNotEmpty()) return
        _s.value = State(isLoading = true)
        viewModelScope.launch {
            val (m, ch) = withContext(Dispatchers.IO) {
                MangaService.getSeriesDetail(seriesId) to MangaService.getChapters(seriesId)
            }
            _s.value = State(manga = m, chapters = ch, isLoading = false)
        }
    }
}
