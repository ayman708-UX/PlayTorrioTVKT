package com.playtorrio.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.navigation.NavController
import com.playtorrio.tv.data.model.TmdbMedia
import android.net.Uri
import com.playtorrio.tv.data.stremio.BoardRow
import com.playtorrio.tv.data.stremio.StremioMetaPreview
import com.playtorrio.tv.data.trailer.YoutubeChunkedDataSourceFactory
import kotlinx.coroutines.launch

private val AccentPrimary = Color(0xFF818CF8)   // nav active dot
private val GoldStar = Color(0xFFFFD700)

// ── Network data ─────────────────────────────────────────────
private data class NetworkInfo(
    val id: Int,
    val name: String,
    val brandColor: Color,
    val accentColor: Color = brandColor,
    val gifUrl: String? = null
)

private val NETWORKS = listOf(
    NetworkInfo(213,  "NETFLIX",      Color(0xFFE50914), Color(0xFFFF1A1A),  "https://gifsfornetworks.pages.dev/netflix-intro.gif"),
    NetworkInfo(2739, "DISNEY+",      Color(0xFF1136C0), Color(0xFF3A6FFF),  "https://gifsfornetworks.pages.dev/disney-star.gif"),
    NetworkInfo(49,   "HBO",          Color(0xFF6226CC), Color(0xFF9B5AFF),  "https://gifsfornetworks.pages.dev/hbo.gif"),
    NetworkInfo(1024, "PRIME VIDEO",  Color(0xFF00A8E0), Color(0xFF33CCFF),  "https://gifsfornetworks.pages.dev/Prime%20Video.gif"),
    NetworkInfo(2552, "APPLE TV+",    Color(0xFF1C1C1E), Color(0xFF8E8E93),  "https://gifsfornetworks.pages.dev/Apple%20TV+%20Intro.gif"),
    NetworkInfo(453,  "HULU",         Color(0xFF0D7A47), Color(0xFF1CE783),  "https://gifsfornetworks.pages.dev/Hulu%20Originals%20ID.gif"),
    NetworkInfo(174,  "AMC+",         Color(0xFF1A1A1A), Color(0xFFFFBB00),  "https://gifsfornetworks.pages.dev/AMC+%20Original%20(2021).gif"),
    NetworkInfo(4330, "PARAMOUNT+",   Color(0xFF0050C8), Color(0xFF4488FF),  "https://gifsfornetworks.pages.dev/paramount%20plus%20original%20logo.gif"),
    NetworkInfo(67,   "SHOWTIME",     Color(0xFFCC0000), Color(0xFFFF3333),  "https://gifsfornetworks.pages.dev/Showtime%20Intro.gif"),
    NetworkInfo(88,   "FX",           Color(0xFF111111), Color(0xFFAAAAAA),  "https://gifsfornetworks.pages.dev/FX%20Networks%20-%20Fearless%20logo%20-%202022.gif"),
    NetworkInfo(3353, "PEACOCK",      Color(0xFF0A0A16), Color(0xFF8888FF),  "https://gifsfornetworks.pages.dev/peacock.gif"),
    NetworkInfo(318,  "STARZ",        Color(0xFF0A0028), Color(0xFF6644CC),  "https://gifsfornetworks.pages.dev/Starz%20Logo%20Animation.gif"),
    NetworkInfo(4,    "BBC",          Color(0xFF111111), Color(0xFFDDDDDD),  "https://gifsfornetworks.pages.dev/BBC%20corporate%20ident%20(2022).gif"),
    NetworkInfo(1399, "CRUNCHYROLL",  Color(0xFFCC3300), Color(0xFFFF6633),  "https://gifsfornetworks.pages.dev/Crunchyroll%20Logo.gif"),
    NetworkInfo(2222, "NAT GEO",      Color(0xFF996600), Color(0xFFFFCC00),  "https://gifsfornetworks.pages.dev/Logo%20Animation%202a%20National%20Geographic.gif"),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Cancel trailer when leaving this screen
    DisposableEffect(Unit) {
        onDispose { viewModel.forceStopTrailer() }
    }

    // Observe BOTH the back-stack entry lifecycle (covers nav back from Settings)
    // AND the hosting Activity lifecycle (covers return from PlayerActivity, which
    // does not move the NavBackStackEntry through PAUSED).
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAddonRows()
                viewModel.refreshContinueWatching()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val activity = context as? androidx.activity.ComponentActivity
        activity?.lifecycle?.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.lifecycle?.removeObserver(observer)
        }
    }



    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val navFocusRequester = remember { FocusRequester() }
        // Set from inside the content branch; called by NavPill to drop focus into the active row.
        val exitNavToContent = remember { mutableStateOf<(() -> Unit)?>(null) }

        // === IN-APP UPDATE CHECK (popup is rendered at the end of this Box so it is on top) ===
        var updateInfo by remember { mutableStateOf<com.playtorrio.tv.data.update.UpdateService.UpdateInfo?>(null) }
        var updateDismissed by remember { mutableStateOf(false) }
        var updateBusy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (com.playtorrio.tv.ui.screens.UpdatePromptShownThisSession.value) return@LaunchedEffect
            val info = com.playtorrio.tv.data.update.UpdateService.check()
            if (info != null) {
                updateInfo = info
                com.playtorrio.tv.ui.screens.UpdatePromptShownThisSession.value = true
            }
        }

        // === BACKDROP ===
        AnimatedContent(
            targetState = state.featured?.backdropUrl,
            transitionSpec = {
                fadeIn(tween(1000, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(700, easing = FastOutSlowInEasing))
            },
            label = "backdrop"
        ) { backdropUrl ->
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.3f to Color.Transparent,
                                        0.72f to Color.Black.copy(alpha = 0.75f),
                                        1.0f to Color.Black
                                    )
                                )
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> ErrorMessage(state.error!!)
            else -> {
                var activeRowIndex by remember { mutableIntStateOf(viewModel.lastFocusedRowIndex) }
                var pendingStreamingResume by remember { mutableStateOf<com.playtorrio.tv.data.watch.WatchProgress?>(null) }
                var pendingAddonResume by remember { mutableStateOf<com.playtorrio.tv.data.watch.WatchProgress?>(null) }
                val hasContinue = state.continueWatching.isNotEmpty()
                val cwOffset = if (hasContinue) 1 else 0
                val totalRows = 1 + cwOffset + state.rows.size + state.addonRows.size
                val rowFocusRequesters = remember(totalRows) {
                    List(totalRows.coerceAtLeast(1)) { FocusRequester() }
                }
                val lazyListState = rememberLazyListState()

                // Expose a focus-restore callback to the NavPill (sibling in the outer Box).
                exitNavToContent.value = {
                    try { rowFocusRequesters[activeRowIndex].requestFocus() } catch (_: Exception) {}
                }

                // Animate scroll, then wait until the target row is actually composed
                // (visible in layoutInfo) before requesting focus. This makes the focus
                // appear right when the row arrives, instead of having to press again.
                LaunchedEffect(activeRowIndex) {
                    val targetIdx = activeRowIndex
                    coroutineScope.launch {
                        try { lazyListState.animateScrollToItem(targetIdx) } catch (_: Exception) {}
                    }
                    // Wait (up to ~600ms) for the row to be composed in the LazyColumn
                    val deadline = System.currentTimeMillis() + 600
                    while (System.currentTimeMillis() < deadline) {
                        val visible = lazyListState.layoutInfo.visibleItemsInfo
                        if (visible.any { it.index == targetIdx }) break
                        kotlinx.coroutines.delay(16)
                    }
                    try { rowFocusRequesters[targetIdx].requestFocus() } catch (_: Exception) {}
                }
                LaunchedEffect(state.rows.isNotEmpty()) {
                    if (state.rows.isNotEmpty()) {
                        kotlinx.coroutines.delay(80)
                        try { rowFocusRequesters[activeRowIndex].requestFocus() } catch (_: Exception) {}
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (activeRowIndex < totalRows - 1) { activeRowIndex++; true }
                                        else false
                                    }
                                    Key.DirectionUp -> {
                                        if (activeRowIndex > 0) { activeRowIndex--; true }
                                        else { navFocusRequester.requestFocus(); true }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    // ── HERO — 43% ──
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(0.43f),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        FeaturedInfo(
                            media = state.featured,
                            logoUrl = state.featuredLogoUrl,
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .padding(start = 48.dp, bottom = 16.dp)
                        )
                    }

                    // ── ALL ROWS — 57%, scrollable ──
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxWidth().weight(0.57f),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                    ) {
                        // Networks row
                        item(key = "networks_row") {
                            NetworkRow(
                                networks = NETWORKS,
                                logoUrlsById = state.networkLogos,
                                initialFocusIndex = if (viewModel.lastFocusedRowIndex == 0) viewModel.lastFocusedItemIndex else 0,
                                onNetworkClicked = { network, itemIndex ->
                                    viewModel.saveFocusPosition(0, itemIndex)
                                    navController.navigate("network/${network.id}")
                                },
                                onItemFocused = { itemIndex ->
                                    viewModel.saveFocusPosition(0, itemIndex)
                                },
                                focusRequester = rowFocusRequesters[0],
                                navFocusRequester = navFocusRequester
                            )
                        }

                        // Continue Watching row (hidden when empty)
                        if (hasContinue) {
                            item(key = "continue_watching_row") {
                                ContinueWatchingRow(
                                    items = state.continueWatching,
                                    editMode = state.continueWatchingEditMode,
                                    initialFocusIndex = if (viewModel.lastFocusedRowIndex == 1) viewModel.lastFocusedItemIndex else 0,
                                    focusRequester = rowFocusRequesters.getOrElse(1) { FocusRequester() },
                                    navFocusRequester = navFocusRequester,
                                    onItemFocused = { itemIdx -> viewModel.saveFocusPosition(1, itemIdx) },
                                    onItemClicked = { entry ->
                                        viewModel.saveFocusPosition(1, state.continueWatching.indexOfFirst { it.key == entry.key } + 1)
                                        when (entry.kind) {
                                            com.playtorrio.tv.data.watch.WatchKind.MAGNET -> {
                                                val intent = android.content.Intent(navController.context, com.playtorrio.tv.PlayerActivity::class.java).apply {
                                                    putExtra("magnetUri", entry.magnetUri)
                                                    entry.fileIdx?.let { putExtra("fileIdx", it) }
                                                    putExtra("title", entry.title)
                                                    putExtra("logoUrl", entry.logoUrl)
                                                    putExtra("backdropUrl", entry.backdropUrl)
                                                    putExtra("posterUrl", entry.posterUrl)
                                                    putExtra("year", entry.year)
                                                    putExtra("rating", entry.rating)
                                                    putExtra("overview", entry.overview)
                                                    putExtra("isMovie", entry.isMovie)
                                                    entry.seasonNumber?.let { putExtra("seasonNumber", it) }
                                                    entry.episodeNumber?.let { putExtra("episodeNumber", it) }
                                                    putExtra("episodeTitle", entry.episodeTitle)
                                                    if (entry.tmdbId > 0) putExtra("tmdbId", entry.tmdbId)
                                                    entry.imdbId?.let { putExtra("imdbId", it) }
                                                    putExtra("resumePositionMs", entry.positionMs)
                                                }
                                                navController.context.startActivity(intent)
                                            }
                                            com.playtorrio.tv.data.watch.WatchKind.STREAMING -> {
                                                pendingStreamingResume = entry
                                            }
                                            com.playtorrio.tv.data.watch.WatchKind.ADDON_STREAM -> {
                                                pendingAddonResume = entry
                                            }
                                        }
                                    },
                                    onItemRemoved = { entry -> viewModel.removeContinueWatching(entry.key) },
                                    onLongPress = { viewModel.toggleContinueWatchingEditMode() }
                                )
                            }
                        }

                        // Content rows
                        itemsIndexed(state.rows, key = { _, row -> row.title }) { index, row ->
                            val rowIdx = 1 + cwOffset + index
                            ContentRow(
                                title = row.title,
                                items = row.items,
                                initialFocusIndex = if (rowIdx == viewModel.lastFocusedRowIndex) viewModel.lastFocusedItemIndex else 0,
                                onItemFocused = { media, itemIdx ->
                                    viewModel.onItemFocused(media)
                                    viewModel.saveFocusPosition(rowIdx, itemIdx)
                                },
                                onItemUnfocused = { viewModel.cancelTrailer() },
                                onItemClicked = { media ->
                                    navController.navigate("detail/${media.id}/${media.isMovie}")
                                },
                                focusRequester = rowFocusRequesters.getOrElse(rowIdx) { FocusRequester() },
                                navFocusRequester = navFocusRequester
                            )
                        }

                        // Stremio addon catalog rows
                        itemsIndexed(
                            state.addonRows,
                            key = { _, row -> "${row.addonId}_${row.catalogType}_${row.catalogId}" }
                        ) { addonIndex, addonRow ->
                            val rowIndex = 1 + cwOffset + state.rows.size + addonIndex
                            val itemType = addonRow.items.firstOrNull()?.type ?: "movie"
                            StremioAddonRow(
                                row = addonRow,
                                focusRequester = rowFocusRequesters.getOrElse(rowIndex) { FocusRequester() },
                                navFocusRequester = navFocusRequester,
                                initialFocusIndex = if (rowIndex == viewModel.lastFocusedRowIndex) {
                                    viewModel.lastFocusedItemIndex
                                } else 0,
                                onItemFocused = { focusedItemIndex ->
                                    viewModel.saveFocusPosition(rowIndex, focusedItemIndex)
                                },
                                onItemClicked = { item ->
                                    coroutineScope.launch {
                                        val focusedItemIndex = addonRow.items.indexOfFirst { it.id == item.id }
                                            .takeIf { it >= 0 }?.plus(1) ?: 1
                                        viewModel.saveFocusPosition(rowIndex, focusedItemIndex)
                                        val tmdbRoute = viewModel.resolveImdbToTmdbRoute(item)
                                        if (tmdbRoute != null) {
                                            navController.navigate(tmdbRoute)
                                        } else {
                                            val resolvedType = item.type.trim().ifBlank { addonRow.catalogType.trim() }
                                                .ifBlank { "movie" }
                                            val encodedId = Uri.encode(item.id)
                                            navController.navigate(
                                                "stremio_detail/${addonRow.addonId}/$resolvedType/$encodedId"
                                            )
                                        }
                                    }
                                },
                                onShowAllClicked = {
                                    viewModel.saveFocusPosition(rowIndex, 0)
                                    val encodedAddon = Uri.encode(addonRow.addonId)
                                    val encodedCatalog = Uri.encode(addonRow.catalogId)
                                    val encodedTitle = Uri.encode(addonRow.title)
                                    val encodedType = Uri.encode(itemType)
                                    navController.navigate(
                                        "stremio_catalog/$encodedAddon/$encodedType/$encodedCatalog/$encodedTitle"
                                    )
                                }
                            )
                        }
                    }
                }

                // ── Continue-Watching resume splashes ──
                com.playtorrio.tv.ui.screens.detail.StreamingSplash(
                    visible = pendingStreamingResume != null,
                    backdropUrl = pendingStreamingResume?.backdropUrl,
                    logoUrl = pendingStreamingResume?.logoUrl,
                    title = pendingStreamingResume?.title ?: "",
                    year = pendingStreamingResume?.year,
                    rating = pendingStreamingResume?.rating,
                    overview = pendingStreamingResume?.overview,
                    isMovie = pendingStreamingResume?.isMovie ?: true,
                    tmdbId = pendingStreamingResume?.tmdbId ?: 0,
                    seasonNumber = pendingStreamingResume?.seasonNumber,
                    episodeNumber = pendingStreamingResume?.episodeNumber,
                    episodeTitle = pendingStreamingResume?.episodeTitle,
                    onDismiss = { pendingStreamingResume = null },
                    posterUrl = pendingStreamingResume?.posterUrl,
                    imdbId = pendingStreamingResume?.imdbId,
                    forceSourceIndex = pendingStreamingResume?.sourceIndex,
                    resumePositionMs = pendingStreamingResume?.positionMs,
                )

                com.playtorrio.tv.ui.screens.detail.AddonStreamSplash(
                    visible = pendingAddonResume != null,
                    progress = pendingAddonResume,
                    onDismiss = { pendingAddonResume = null },
                )
            }
        }

        // === FLOATING NAV BUTTON (top-left) ===
        NavPill(
            navController = navController,
            navFocusRequester = navFocusRequester,
            onExitToContent = { exitNavToContent.value?.invoke() },
        )

        // === TRAILER OVERLAY (on top of everything, keeps content rows composed underneath) ===
        if (state.isTrailerPlaying && state.trailerSource != null) {
            com.playtorrio.tv.ui.components.TrailerFullScreen(
                videoUrl = state.trailerSource!!.videoUrl,
                audioUrl = state.trailerSource!!.audioUrl,
                clientUserAgent = state.trailerSource!!.clientUserAgent,
                logoUrl = state.featuredLogoUrl,
                title = state.featured?.displayTitle ?: "",
                onExit = viewModel::onTrailerEnded,
                onError = viewModel::onTrailerFailed
            )
        }

        // === UPDATE POPUP (must be the LAST child so it renders on top of every overlay) ===
        val info = updateInfo
        if (info != null && !updateDismissed) {
            com.playtorrio.tv.ui.components.UpdatePopup(
                versionName = info.versionName,
                releaseNotes = info.releaseNotes,
                onLater = { updateDismissed = true },
                onUpdateNow = {
                    if (updateBusy) return@UpdatePopup
                    updateBusy = true
                    coroutineScope.launch {
                        val apk = com.playtorrio.tv.data.update.UpdateService.download(context, info)
                        if (apk != null) {
                            val launched = com.playtorrio.tv.data.update.UpdateService.installApk(context, apk)
                            if (!launched) {
                                // User must grant "Install unknown apps". Settings already opened.
                                // Keep popup so they can retry after returning.
                            } else {
                                updateDismissed = true
                            }
                        }
                        updateBusy = false
                    }
                }
            )
        }
    }
}

// ============================================================
// FLOATING NAV PILL — expands on focus, top-left overlay
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavPill(
    navController: NavController,
    navFocusRequester: FocusRequester,
    onExitToContent: () -> Unit,
) {
    var groupHasFocus by remember { mutableStateOf(false) }
    // expanded follows groupHasFocus but with LaunchedEffect to re-grab focus on expand
    var expanded by remember { mutableStateOf(false) }

    // When group loses focus, collapse after a brief grace period
    LaunchedEffect(groupHasFocus) {
        if (!groupHasFocus) {
            kotlinx.coroutines.delay(150)
            expanded = false
        }
    }

    // When expanding, re-request focus so it lands on the Home item
    LaunchedEffect(expanded) {
        if (expanded) {
            kotlinx.coroutines.delay(50)
            navFocusRequester.requestFocus()
        }
    }

    val pillWidth by animateDpAsState(
        targetValue = if (expanded) 200.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pillWidth"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.4f,
        animationSpec = tween(300),
        label = "pillAlpha"
    )

    Box(
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .alpha(pillAlpha)
                .onFocusChanged { groupHasFocus = it.hasFocus }
                .focusGroup()
        ) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NavPillItem(
                            icon = Icons.Filled.Home,
                            label = "Home",
                            isActive = true,
                            onClicked = { /* already on home */ },
                            focusRequester = navFocusRequester,
                            onExitRight = onExitToContent,
                        )
                        NavPillItem(
                            icon = Icons.Filled.Search,
                            label = "Search",
                            isActive = false,
                            onClicked = { navController.navigate("search") },
                            onExitRight = onExitToContent,
                        )
                        NavPillItem(
                            icon = Icons.Filled.MusicNote,
                            label = "Music",
                            isActive = false,
                            onClicked = { navController.navigate("music") },
                            onExitRight = onExitToContent,
                        )
                        NavPillItem(
                            icon = Icons.Filled.Headphones,
                            label = "Audiobooks",
                            isActive = false,
                            onClicked = { navController.navigate("audiobooks") },
                            onExitRight = onExitToContent,
                        )
                        NavPillItem(
                            icon = Icons.Filled.Settings,
                            label = "Settings",
                            isActive = false,
                            onClicked = { navController.navigate("settings") },
                            onExitRight = onExitToContent,
                        )
                        ProfilePillItem(
                            onClicked = {
                                navController.navigate("profile_select") {
                                    popUpTo("home") { inclusive = false }
                                }
                            },
                            onExitRight = onExitToContent,
                            onExitDown = onExitToContent,
                        )
                    }
                } else {
                    Card(
                        onClick = {},
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(navFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) expanded = true
                            },
                        colors = CardDefaults.colors(containerColor = Color.Transparent),
                        shape = CardDefaults.shape(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavPillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClicked: () -> Unit,
    focusRequester: FocusRequester? = null,
    onExitRight: (() -> Unit)? = null,
    onExitDown: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.2f else if (isActive) 0.1f else 0f,
        animationSpec = tween(200),
        label = "navBg"
    )

    Card(
        onClick = onClicked,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        onExitRight?.let { it(); true } ?: false
                    }
                    Key.DirectionDown -> {
                        onExitDown?.let { it(); true } ?: false
                    }
                    Key.Back, Key.Escape -> {
                        // Drop focus into the content rows instead of letting
                        // the system pop the activity.
                        onExitRight?.let { it(); true } ?: false
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                isFocused = it.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentPrimary.copy(alpha = 0.25f)
            else Color.White.copy(alpha = bgAlpha)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            if (isActive) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfilePillItem(
    onClicked: () -> Unit,
    onExitRight: (() -> Unit)? = null,
    onExitDown: (() -> Unit)? = null,
) {
    val profile = remember { com.playtorrio.tv.data.profile.ProfileManager.activeProfile() }
    var isFocused by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.2f else 0f,
        animationSpec = tween(200),
        label = "profileBg"
    )

    Card(
        onClick = onClicked,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { onExitRight?.let { it(); true } ?: false }
                    Key.DirectionDown -> { onExitDown?.let { it(); true } ?: false }
                    Key.Back, Key.Escape -> { onExitRight?.let { it(); true } ?: false }
                    else -> false
                }
            }
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentPrimary.copy(alpha = 0.25f)
            else Color.White.copy(alpha = bgAlpha)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isFocused) AccentPrimary else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!profile.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.imageUrl,
                        contentDescription = profile.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = profile.name,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "Switch profile",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ============================================================
// FEATURED INFO — glass card with logo, metadata, overview
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedInfo(
    media: TmdbMedia?,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    media ?: return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C0C1E).copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            // Logo or title
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier
                        .height(68.dp)
                        .width(260.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = media.displayTitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (media.isMovie) "MOVIE" else "SERIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 10.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )

                media.year?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                media.voteAverage?.let { rating ->
                    Text(
                        text = "★ %.1f".format(rating),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = GoldStar
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overview
            media.overview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================
// NETWORK ROW — animated brand cards
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NetworkRow(
    networks: List<NetworkInfo>,
    logoUrlsById: Map<Int, String?>,
    initialFocusIndex: Int = 0,
    onNetworkClicked: (NetworkInfo, Int) -> Unit,
    onItemFocused: (Int) -> Unit = {},
    focusRequester: FocusRequester,
    navFocusRequester: FocusRequester
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialFocusIndex)
    var focusedIndex by remember { mutableIntStateOf(initialFocusIndex) }
    val restoreFocusRequester = remember { FocusRequester() }

    // No manual horizontal scroll — focus-driven bringIntoView handles it smoothly.

    // Restore focus to the previously focused network card on back-nav
    LaunchedEffect(Unit) {
        if (initialFocusIndex > 0) {
            kotlinx.coroutines.delay(100)
            try { restoreFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.focusGroup()) {
        Text(
            text = "NETWORKS",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                fontSize = 11.sp
            ),
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp, top = 4.dp)
        )

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusRequester(focusRequester)
        ) {
            itemsIndexed(networks, key = { _, n -> n.id }) { index, network ->
                AnimatedNetworkCard(
                    network = network,
                    logoUrl = logoUrlsById[network.id],
                    onClicked = { onNetworkClicked(network, index) },
                    onFocused = {
                        focusedIndex = index
                        onItemFocused(index)
                    },
                    onLeftAtStart = if (index == 0) ({ navFocusRequester.requestFocus() }) else null,
                    modifier = if (index == initialFocusIndex && initialFocusIndex > 0)
                        Modifier.focusRequester(restoreFocusRequester) else Modifier
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimatedNetworkCard(
    network: NetworkInfo,
    logoUrl: String?,
    onClicked: () -> Unit,
    onFocused: () -> Unit,
    onLeftAtStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(190.dp)
            .height(105.dp)
    ) {
        Card(
            onClick = onClicked,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFocused) Modifier.border(
                        2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp)
                    ) else Modifier
                )
                .onFocusChanged { state ->
                    val was = isFocused
                    isFocused = state.isFocused
                    if (!was && state.isFocused) onFocused()
                }
                .then(
                    if (onLeftAtStart != null) Modifier.onPreviewKeyEvent { evt ->
                        if (evt.type == KeyEventType.KeyDown && evt.key == Key.DirectionLeft && isFocused) {
                            onLeftAtStart(); true
                        } else false
                    } else Modifier
                ),
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            scale = CardDefaults.scale(focusedScale = 1f),
            colors = CardDefaults.colors(containerColor = Color(0xFF111111))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // When focused: show the GIF. When idle: show TMDB logo on a brand-colored bg.
                val displayUrl = if (isFocused) (network.gifUrl ?: logoUrl) else logoUrl
                if (isFocused && displayUrl != null) {
                    // Focused: full-bleed GIF
                    AsyncImage(
                        model = displayUrl,
                        contentDescription = network.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (!isFocused && displayUrl != null) {
                    // Idle: logo on brand-color background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(network.brandColor.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = displayUrl,
                            contentDescription = network.name,
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .wrapContentHeight(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    // Fallback: styled brand name text
                    Text(
                        text = network.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontSize = 14.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
// CONTENT ROW — title with shimmer + horizontal card slider
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentRow(
    title: String,
    items: List<TmdbMedia>,
    onItemFocused: (TmdbMedia, Int) -> Unit,
    onItemUnfocused: () -> Unit,
    onItemClicked: (TmdbMedia) -> Unit,
    focusRequester: FocusRequester,
    navFocusRequester: FocusRequester? = null,
    initialFocusIndex: Int = 0
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialFocusIndex)
    var focusedIndex by remember { mutableIntStateOf(initialFocusIndex) }
    val restoreFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (initialFocusIndex > 0) {
            kotlinx.coroutines.delay(200)
            try { restoreFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // No manual horizontal scroll — focus-driven bringIntoView handles it smoothly.

    Column(
        modifier = Modifier.focusGroup()
    ) {
        // Row title with subtle accent
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
        )

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusRequester(focusRequester)
        ) {
            itemsIndexed(items, key = { _, media -> media.id }) { index, media ->
                MediaCard(
                    media = media,
                    index = index,
                    onFocused = {
                        focusedIndex = index
                        onItemFocused(media, index)
                    },
                    onUnfocused = onItemUnfocused,
                    onClicked = { onItemClicked(media) },
                    onLeftAtStart = if (index == 0 && navFocusRequester != null) {
                        { navFocusRequester.requestFocus() }
                    } else null,
                    focusRequester = if (index == initialFocusIndex && initialFocusIndex > 0) restoreFocusRequester else null
                )
            }
        }
    }
}

// ============================================================
// MEDIA CARD — glow, reflection, border, scale, dimming
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaCard(
    media: TmdbMedia,
    index: Int,
    onFocused: () -> Unit,
    onUnfocused: () -> Unit,
    onClicked: () -> Unit = {},
    onLeftAtStart: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(250.dp)
            .aspectRatio(16f / 9f)
    ) {
        Card(
            onClick = onClicked,
            modifier = Modifier
                .fillMaxSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else Modifier
                )
                    .onFocusChanged { focusState ->
                        val wasFocused = isFocused
                        isFocused = focusState.isFocused
                        if (!wasFocused && focusState.isFocused) {
                            onFocused()
                        } else if (wasFocused && !focusState.isFocused) {
                            onUnfocused()
                        }
                    }
                    .then(
                        if (onLeftAtStart != null) {
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && isFocused) {
                                    onLeftAtStart()
                                    true
                                } else false
                            }
                        } else Modifier
                    ),
                scale = CardDefaults.scale(focusedScale = 1f),
                shape = CardDefaults.shape(RoundedCornerShape(10.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = media.cardBackdropUrl ?: media.posterUrl,
                        contentDescription = media.displayTitle,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Title + year overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.3f to Color.Black.copy(alpha = 0.2f),
                                        1f to Color.Black.copy(alpha = 0.92f)
                                    )
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = media.displayTitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            media.voteAverage?.let { rating ->
                                Text(
                                    text = "★ %.1f".format(rating),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = GoldStar.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// ============================================================
// LOADING + ERROR
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Red.copy(alpha = 0.8f)
        )
    }
}

// ============================================================
// FULL-SCREEN TRAILER — ExoPlayer with logo overlay + back exit
// ============================================================

// ============================================================
// STREMIO ADDON ROW — horizontal slider for addon catalog items
// ============================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StremioAddonRow(
    row: BoardRow,
    focusRequester: FocusRequester,
    navFocusRequester: FocusRequester? = null,
    initialFocusIndex: Int = 0,
    onItemFocused: (Int) -> Unit = {},
    onItemClicked: (StremioMetaPreview) -> Unit,
    onShowAllClicked: () -> Unit
) {
    val rowState = rememberLazyListState()
    val firstItem = row.items.firstOrNull()
    val rowIsPortrait = firstItem?.let { it.posterShape == "poster" || it.posterShape == null } ?: true
    val showAllWidth = if (rowIsPortrait) 92.dp else 84.dp
    val showAllHeight = if (rowIsPortrait) 240.dp else 140.dp
    val restoreFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocusIndex) {
        if (initialFocusIndex > 0) {
            kotlinx.coroutines.delay(200)
            try { restoreFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.focusGroup()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.title.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 13.sp
                ),
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f)
            )
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusRequester(focusRequester)
        ) {
            item(key = "show_all_btn") {
                ShowAllButton(
                    onClick = onShowAllClicked,
                    width = showAllWidth,
                    height = showAllHeight,
                    onLeftAtStart = navFocusRequester?.let { { it.requestFocus() } },
                    focusRequester = if (initialFocusIndex == 0) restoreFocusRequester else null,
                    onFocused = { onItemFocused(0) }
                )
            }
            itemsIndexed(row.items, key = { idx, item -> "${item.id}_$idx" }) { index, item ->
                StremioMetaCard(
                    item = item,
                    onClicked = { onItemClicked(item) },
                    onFocused = { onItemFocused(index + 1) },
                    onLeftAtStart = null,
                    focusRequester = if (index + 1 == initialFocusIndex) restoreFocusRequester else null
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowAllButton(
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onLeftAtStart: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(height)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val wasFocused = isFocused
                isFocused = it.isFocused
                if (!wasFocused && it.isFocused) onFocused()
            }
            .then(
                if (onLeftAtStart != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && isFocused) {
                            onLeftAtStart()
                            true
                        } else false
                    }
                } else Modifier
            ),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f)
            else Color.White.copy(alpha = 0.07f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SHOW\nALL →",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 13.sp
                ),
                color = Color.White.copy(alpha = if (isFocused) 1f else 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StremioMetaCard(
    item: StremioMetaPreview,
    onClicked: () -> Unit,
    onLeftAtStart: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    val isPortrait = item.posterShape == "poster" || item.posterShape == null
    val cardWidth = if (isPortrait) 160.dp else 250.dp
    val cardAspect = if (isPortrait) (2f / 3f) else (16f / 9f)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(cardAspect)
    ) {
        Card(
            onClick = onClicked,
            modifier = Modifier
                .fillMaxSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (isFocused) Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .onFocusChanged {
                    val wasFocused = isFocused
                    isFocused = it.isFocused
                    if (!wasFocused && it.isFocused) onFocused()
                }
                .then(
                    if (onLeftAtStart != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && isFocused) {
                                onLeftAtStart()
                                true
                            } else false
                        }
                    } else Modifier
                ),
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                // Bottom overlay with title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.4f to Color.Black.copy(alpha = 0.3f),
                                    1f to Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ============================================================
// CONTINUE WATCHING ROW
// ============================================================

private val ContinueAccent = Color(0xFF818CF8)
private val ContinuePanel = Color(0xFF1F2233)
private val ContinuePanelLight = Color(0xFF2A2F45)
private val ContinueTextDim = Color(0xFF94A3B8)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingRow(
    items: List<com.playtorrio.tv.data.watch.WatchProgress>,
    editMode: Boolean,
    initialFocusIndex: Int,
    focusRequester: FocusRequester,
    navFocusRequester: FocusRequester,
    onItemFocused: (Int) -> Unit,
    onItemClicked: (com.playtorrio.tv.data.watch.WatchProgress) -> Unit,
    onItemRemoved: (com.playtorrio.tv.data.watch.WatchProgress) -> Unit,
    onLongPress: () -> Unit,
) {
    // Index 0 = edit toggle card, 1..N = watch entries
    val cardRequesters = remember(items.size) { List(items.size + 1) { FocusRequester() } }

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 48.dp, bottom = 6.dp)
        ) {
            Text(
                "Continue Watching",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (editMode) "Tap a card to remove" else "\u00B7 ${items.size}",
                color = if (editMode) Color(0xFFEF4444) else ContinueTextDim,
                fontSize = 11.sp,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        navFocusRequester.requestFocus(); true
                    } else false
                }
                .focusGroup(),
        ) {
            // First card: edit-mode toggle
            item(key = "__edit_toggle__") {
                ContinueEditCard(
                    active = editMode,
                    focusRequester = cardRequesters[0],
                    onClick = onLongPress,
                    onFocused = { onItemFocused(0) },
                )
            }
            itemsIndexed(items, key = { _, it -> it.key }) { idx, entry ->
                ContinueWatchingCard(
                    entry = entry,
                    editMode = editMode,
                    focusRequester = cardRequesters.getOrElse(idx + 1) { FocusRequester() },
                    onClick = {
                        if (editMode) onItemRemoved(entry) else onItemClicked(entry)
                    },
                    onFocused = { onItemFocused(idx + 1) }
                )
            }
        }

        // Auto-focus restore on row arrival
        LaunchedEffect(initialFocusIndex, items.size) {
            try {
                val target = initialFocusIndex.coerceIn(0, items.size)
                cardRequesters.getOrNull(target)?.requestFocus()
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueEditCard(
    active: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = when {
        active -> Color(0xFFEF4444)
        focused -> ContinueAccent
        else -> Color.Transparent
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(72.dp)
            .height(82.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(
                width = if (focused || active) 2.dp else 0.dp,
                color = border,
                shape = RoundedCornerShape(8.dp),
            ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = when {
                active -> Color(0xFFEF4444).copy(alpha = 0.18f)
                focused -> ContinuePanelLight
                else -> ContinuePanel
            },
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Close else Icons.Filled.Edit,
                contentDescription = if (active) "Done" else "Edit",
                tint = if (active) Color(0xFFEF4444) else Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (active) "Done" else "Edit",
                color = if (active) Color(0xFFEF4444) else ContinueTextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueWatchingCard(
    entry: com.playtorrio.tv.data.watch.WatchProgress,
    editMode: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ratio = if (entry.durationMs > 0L)
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f

    val episodeBadge = if (!entry.isMovie && entry.seasonNumber != null && entry.episodeNumber != null) {
        "S%d \u00B7 E%d".format(entry.seasonNumber, entry.episodeNumber)
    } else entry.year ?: ""

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(280.dp)
            .height(82.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (editMode) Color(0xFFEF4444) else ContinueAccent,
                shape = RoundedCornerShape(8.dp),
            ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        colors = CardDefaults.colors(
            containerColor = if (focused) ContinuePanelLight else ContinuePanel,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(6.dp)
                    .size(width = 48.dp, height = 70.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ContinuePanelLight),
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
                            Icons.Filled.PlayArrow, null,
                            tint = ContinueAccent.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
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
                            modifier = Modifier.size(18.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        episodeBadge.isNotBlank() && entry.episodeTitle != null ->
                            "$episodeBadge \u00B7 ${entry.episodeTitle}"
                        episodeBadge.isNotBlank() -> episodeBadge
                        else -> formatWatchTime(entry.positionMs)
                    },
                    color = ContinueTextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatWatchTime(entry.positionMs) + (if (entry.durationMs > 0)
                        " / " + formatWatchTime(entry.durationMs) else ""),
                    color = ContinueTextDim.copy(alpha = 0.75f),
                    fontSize = 9.sp,
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
                            .background(ContinueAccent)
                    )
                }
            }
        }
    }
}

private fun formatWatchTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ============================================================
// CONTINUE WATCHING ROW
// ============================================================

