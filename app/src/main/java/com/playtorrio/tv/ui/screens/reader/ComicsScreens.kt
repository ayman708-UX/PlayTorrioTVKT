package com.playtorrio.tv.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.playtorrio.tv.data.reader.Comic
import com.playtorrio.tv.data.reader.ComicDetails
import com.playtorrio.tv.data.reader.ComicImageLoader
import com.playtorrio.tv.data.reader.ComicsService
import com.playtorrio.tv.data.reader.ReadingProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFC084FC)
private val Surface = Color(0xFF0A0A0F)
private val Panel = Color(0xFF14141F)
private val TextDim = Color.White.copy(alpha = 0.55f)

// ════════════════════════════════════════════════════════════════════
//                          BROWSE
// ════════════════════════════════════════════════════════════════════

@Composable
fun ComicsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: ComicsBrowseViewModel = viewModel()
    val state by vm.state.collectAsState()
    val resumeState by vm.resumeState.collectAsState()

    LaunchedEffect(Unit) { if (state.items.isEmpty() && state.query.isEmpty()) vm.load(1) }

    var continueEntries by remember { mutableStateOf(emptyList<ReadingProgressStore.Entry>()) }
    LaunchedEffect(Unit) {
        continueEntries = ReadingProgressStore.list(ctx)
            .filter { it.source == ReadingProgressStore.Source.COMIC }
    }

    LaunchedEffect(resumeState.ready) {
        if (resumeState.ready) {
            navController.navigate("reader")
            vm.consumeResumeReady()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Surface)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                    navController.popBackStack(); true
                } else false
            }
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            ReaderHeader(
                title = "Comics",
                subtitle = "ReadComicOnline",
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
                    keyOf = { c -> c.url },
                    coverUrlOf = { c -> c.poster },
                    titleOf = { c -> c.title },
                    accent = Accent,
                    useReferer = true,
                    onClicked = { comic ->
                        ReaderNav.pendingComic = comic
                        navController.navigate("comic_detail")
                    }
                )
            }
        }

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

private fun resumeComic(navController: NavController, entry: ReadingProgressStore.Entry) {
    // Fallback path (unused now that ComicsBrowseViewModel.resume handles it directly).
    ReaderNav.pendingComic = Comic(
        title = entry.workTitle,
        url = entry.workKey,
        poster = entry.coverUrl,
        summary = entry.comicSummary,
        source = entry.comicSourceTag,
    )
    navController.navigate("comic_detail")
}

@Composable
fun ComicDetailsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vm: ComicDetailsViewModel = viewModel()
    val pending = ReaderNav.pendingComic
    LaunchedEffect(pending?.url) {
        if (pending != null) vm.load(pending)
    }
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Surface)
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
        val details = state.details ?: return@Box

        Row(Modifier.fillMaxSize().padding(20.dp)) {
            Column(Modifier.width(280.dp).fillMaxHeight()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(details.comic.poster).build(),
                    imageLoader = ComicImageLoader.get(ctx),
                    contentDescription = details.comic.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Panel)
                )
                Spacer(Modifier.height(14.dp))
                Text(details.comic.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (details.writer != "Unknown") {
                    Spacer(Modifier.height(4.dp))
                    Text("Writer: ${details.writer}", color = TextDim, fontSize = 11.sp)
                }
                if (details.artist != "Unknown") {
                    Text("Artist: ${details.artist}", color = TextDim, fontSize = 11.sp)
                }
                if (details.publisher != "Unknown") {
                    Text("Publisher: ${details.publisher}", color = TextDim, fontSize = 11.sp)
                }
                if (details.publicationDate != "Unknown") {
                    Text(details.publicationDate, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                if (details.genres.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        details.genres.joinToString(" • "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (details.comic.summary.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        details.comic.summary,
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
                        "${details.chapters.size} chapter${if (details.chapters.size != 1) "s" else ""}",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                ChapterList(
                    chapters = details.chapters.mapIndexed { i, c ->
                        ChapterListEntry(
                            id = "${c.url}_$i",
                            label = c.title,
                            sub = c.dateAdded,
                            index = i,
                        )
                    },
                    onClicked = { entry ->
                        ReaderSession.set(
                            source = ReaderSession.Source.COMIC,
                            workKey = details.comic.url,
                            workTitle = details.comic.title,
                            workCoverUrl = details.comic.poster,
                            chapters = details.chapters.map {
                                ReaderSession.ChapterRef(it.title, it.url)
                            },
                            startIndex = entry.index,
                            comicSourceTag = details.comic.source,
                            comicSummary = details.comic.summary,
                        )
                        navController.navigate("reader")
                    }
                )
            }
        }
    }
}

// ───────────────────────── ViewModels ─────────────────────────

class ComicsBrowseViewModel : ViewModel() {
    data class State(
        val items: List<Comic> = emptyList(),
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
            val list = withContext(Dispatchers.IO) { ComicsService.getComics(page) }
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
            delay(450)
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
        val list = withContext(Dispatchers.IO) { ComicsService.searchComics(q) }
        _s.value = _s.value.copy(items = list, isLoading = false)
    }

    fun resume(entry: ReadingProgressStore.Entry) {
        resumeJob?.cancel()
        _resume.value = ResumeState(isResuming = true, label = "Opening ${entry.workTitle}\u2026")
        resumeJob = viewModelScope.launch {
            // Reconstruct stub so ComicsService can refetch full details.
            val stub = Comic(
                title = entry.workTitle,
                url = entry.workKey,
                poster = entry.coverUrl,
                summary = entry.comicSummary,
                source = entry.comicSourceTag,
            )
            val details = runCatching {
                withContext(Dispatchers.IO) { ComicsService.getComicDetails(stub) }
            }.getOrElse {
                _resume.value = ResumeState(isResuming = false, error = "Failed to load: ${it.message}")
                return@launch
            }
            if (details == null || details.chapters.isEmpty()) {
                _resume.value = ResumeState(isResuming = false, error = "No chapters found.")
                return@launch
            }
            val foundIdx = details.chapters.indexOfFirst { it.url == entry.chapterKey }
            val chapterIndex = if (foundIdx >= 0) foundIdx
                else entry.chapterIndex.coerceIn(0, details.chapters.size - 1)
            ReaderSession.set(
                source = ReaderSession.Source.COMIC,
                workKey = details.comic.url,
                workTitle = details.comic.title,
                workCoverUrl = details.comic.poster,
                chapters = details.chapters.map { ReaderSession.ChapterRef(it.title, it.url) },
                startIndex = chapterIndex,
                comicSourceTag = details.comic.source,
                comicSummary = details.comic.summary,
                startPageIndex = entry.pageIndex,
            )
            _resume.value = ResumeState(isResuming = false, ready = true)
        }
    }

    fun consumeResumeReady() {
        _resume.value = ResumeState()
    }
}

class ComicDetailsViewModel : ViewModel() {
    data class State(
        val details: ComicDetails? = null,
        val isLoading: Boolean = true,
    )
    private val _s = MutableStateFlow(State())
    val state = _s.asStateFlow()

    fun load(comic: Comic) {
        if (_s.value.details?.comic?.url == comic.url) return
        _s.value = State(isLoading = true)
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { ComicsService.getComicDetails(comic) }
            _s.value = State(details = d, isLoading = false)
        }
    }
}

object ReaderNav {
    @Volatile var pendingComic: Comic? = null
}
