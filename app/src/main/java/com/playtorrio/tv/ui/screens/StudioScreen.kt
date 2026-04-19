package com.playtorrio.tv.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.playtorrio.tv.data.model.TmdbMedia
import com.playtorrio.tv.ui.screens.studio.NetworkFilter
import com.playtorrio.tv.ui.screens.studio.StudioUiState
import com.playtorrio.tv.ui.screens.studio.StudioViewModel

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val AccentTertiary = Color(0xFF38BDF8)
private val GoldStar = Color(0xFFFFD700)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StudioScreen(
    companyId: Int,
    isNetwork: Boolean = false,
    navController: NavController,
    viewModel: StudioViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(companyId) {
        if (isNetwork) viewModel.loadNetwork(companyId)
        else viewModel.load(companyId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    navController.popBackStack()
                    true
                } else false
            }
    ) {
        when {
            state.isLoading -> StudioLoading()
            state.error != null -> StudioError(state.error!!)
            state.company != null -> StudioContent(state, viewModel, navController)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioContent(
    state: StudioUiState,
    viewModel: StudioViewModel,
    navController: NavController
) {
    val company = state.company!!
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val items = remember(state.isNetworkMode, state.displayedItems, state.shows, state.movies) {
        val rawItems = if (state.isNetworkMode) state.displayedItems else state.shows + state.movies
        rawItems.distinctBy { "${if (it.isMovie) "mv" else "tv"}_${it.id}" }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 135.dp),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Header ──
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (company.logoUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = company.logoUrl,
                            contentDescription = company.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = company.name ?: "Unknown",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        company.originCountry?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = AccentTertiary,
                                modifier = Modifier
                                    .border(1.dp, AccentTertiary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        company.headquarters?.let {
                            Text(text = "📍 $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // ── Filter chips (network mode only) ──
        if (state.isNetworkMode) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NetworkFilter.entries.forEach { f ->
                        val isSelected = state.filter == f
                        var focused by remember { mutableStateOf(false) }
                        val chipLabel = when (f) {
                            NetworkFilter.ALL -> "All"
                            NetworkFilter.SHOWS -> "Series"
                            NetworkFilter.MOVIES -> "Movies"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    when {
                                        isSelected -> AccentPrimary.copy(alpha = 0.3f)
                                        focused -> Color.White.copy(alpha = 0.12f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    }
                                )
                                .border(
                                    width = if (isSelected || focused) 1.5.dp else 1.dp,
                                    color = when {
                                        isSelected -> AccentPrimary
                                        focused -> Color.White.copy(alpha = 0.6f)
                                        else -> Color.White.copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                onClick = {
                                    viewModel.setFilter(f)
                                    scope.launch { gridState.animateScrollToItem(0) }
                                },
                                modifier = Modifier
                                    .onFocusChanged { focused = it.isFocused },
                                scale = CardDefaults.scale(focusedScale = 1f),
                                colors = CardDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                shape = CardDefaults.shape(RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = chipLabel,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = if (isSelected) AccentPrimary else Color.White.copy(alpha = if (focused) 1f else 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Content ──
        if (state.isNetworkMode) {
            // Unified filtered grid
            if (items.isEmpty() && !state.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No ${if (state.filter == NetworkFilter.MOVIES) "movies" else "series"} found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            } else {
                itemsIndexed(items, key = { index, m -> "${if (m.isMovie) "mv" else "tv"}_${m.id}_$index" }) { _, media ->
                    StudioPosterCard(
                        media = media,
                        onClick = { navController.navigate("detail/${media.id}/${media.isMovie}") }
                    )
                }
            }
        } else {
            // Studio mode: shows section then movies section
            if (state.shows.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { StudioSectionTitle("TV Shows") }
                itemsIndexed(items.filter { !it.isMovie }, key = { index, m -> "tv_${m.id}_$index" }) { _, media ->
                    StudioPosterCard(
                        media = media,
                        onClick = { navController.navigate("detail/${media.id}/false") }
                    )
                }
            }
            if (state.movies.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { StudioSectionTitle("Movies") }
                itemsIndexed(items.filter { it.isMovie }, key = { index, m -> "mv_${m.id}_$index" }) { _, media ->
                    StudioPosterCard(
                        media = media,
                        onClick = { navController.navigate("detail/${media.id}/true") }
                    )
                }
            }
        }

        // ── Show More button ──
        if (state.isNetworkMode && (state.hasMore || state.isLoadingMore)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoadingMore) {
                        Text(
                            "LOADING  •  •  •",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    } else {
                        var btnFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (btnFocused) AccentPrimary.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = if (btnFocused) 1.5.dp else 1.dp,
                                    color = if (btnFocused) AccentPrimary else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.onFocusChanged { btnFocused = it.isFocused },
                                scale = CardDefaults.scale(focusedScale = 1f),
                                colors = CardDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                shape = CardDefaults.shape(RoundedCornerShape(24.dp))
                            ) {
                                Text(
                                    text = "Show More",
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                                    ),
                                    color = if (btnFocused) AccentPrimary else Color.White.copy(alpha = 0.7f)
                                )
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
private fun StudioPosterCard(
    media: TmdbMedia,
    isLastItem: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Trigger load-more once when the last item gets focused
    LaunchedEffect(isLastItem, isFocused) {
        if (isLastItem && isFocused) onLoadMore?.invoke()
    }

    val scale by animateFloatAsState(
        if (isFocused) 1.08f else 0.95f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
    )
    val cardAlpha by animateFloatAsState(
        if (isFocused) 1f else 0.65f, tween(300), label = "a"
    )

    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = cardAlpha }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (isFocused) Modifier.border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(
                            Color.White.copy(alpha = 0.6f),
                            AccentPrimary.copy(alpha = 0.8f),
                            AccentSecondary.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.4f)
                        )),
                        RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = media.posterUrl ?: media.cardBackdropUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        ))
                )
                media.voteAverage?.let { rating ->
                    Text(
                        text = "★ %.1f".format(rating),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 9.sp
                        ),
                        color = GoldStar,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        media.year?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
            color = Color.White.copy(alpha = 0.35f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioSectionTitle(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(
                    colors = listOf(SurfaceGlassBorder, Color.Transparent)
                ))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("LOADING", style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 4.sp, fontWeight = FontWeight.Light
        ), color = Color.White.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioError(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.Red.copy(alpha = 0.8f))
    }
}
