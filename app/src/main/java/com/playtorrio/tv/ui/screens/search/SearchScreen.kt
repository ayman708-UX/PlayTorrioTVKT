package com.playtorrio.tv.ui.screens.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import android.net.Uri
import com.playtorrio.tv.data.model.TmdbMedia
import com.playtorrio.tv.data.stremio.BoardRow
import com.playtorrio.tv.data.stremio.StremioMetaPreview

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    navController.popBackStack(); true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(48.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold, fontSize = 30.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(Modifier.height(20.dp))

            SearchInputField(
                query = state.query,
                onQueryChanged = viewModel::onQueryChanged,
                focusRequester = searchFocusRequester
            )

            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Searching…", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                }
            } else if (state.query.isBlank()) {
                if (state.trendingMovies.isNotEmpty()) {
                    SearchResultRow(
                        title = "Popular Movies", items = state.trendingMovies,
                        onItemClicked = { navController.navigate("detail/${it.id}/${it.isMovie}") }
                    )
                    Spacer(Modifier.height(24.dp))
                }
                if (state.trendingTv.isNotEmpty()) {
                    SearchResultRow(
                        title = "Popular TV Shows", items = state.trendingTv,
                        onItemClicked = { navController.navigate("detail/${it.id}/${it.isMovie}") }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                if (state.movies.isEmpty() && state.tvShows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.SearchOff, contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(text = "No results found", color = Color.White.copy(alpha = 0.5f),
                                fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Try a different search term", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                        }
                    }
                }
                if (state.movies.isNotEmpty()) {
                    SearchResultRow(
                        title = "Movies", items = state.movies,
                        onItemClicked = { navController.navigate("detail/${it.id}/true") }
                    )
                    Spacer(Modifier.height(24.dp))
                }
                if (state.tvShows.isNotEmpty()) {
                    SearchResultRow(
                        title = "TV Shows", items = state.tvShows,
                        onItemClicked = { navController.navigate("detail/${it.id}/false") }
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // Addon search results
                if (state.isLoadingAddonSearch && state.addonRows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Searching addons…",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 13.sp
                        )
                    }
                }
                state.addonRows.forEach { addonRow ->
                    AddonSearchRow(
                        row = addonRow,
                        onItemClicked = { item ->
                            val resolvedType = item.type.trim().ifBlank { addonRow.catalogType.trim() }
                                .ifBlank { "movie" }
                            val encodedId = Uri.encode(item.id)
                            navController.navigate(
                                "stremio_detail/${addonRow.addonId}/$resolvedType/$encodedId"
                            )
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            } // end else (query not blank)

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChanged: (String) -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(AccentPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .border(
                width = 1.dp,
                color = if (isFocused) AccentPrimary.copy(alpha = 0.6f) else SurfaceGlassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 20.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down); true
                } else false
            },
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(text = "Search movies, TV shows…", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp)
                }
                innerTextField()
            }
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultRow(
    title: String,
    items: List<TmdbMedia>,
    onItemClicked: (TmdbMedia) -> Unit
) {
    Column(modifier = Modifier.focusGroup()) {
        Row(
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp, end = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.45f)
            )
            Box(
                modifier = Modifier.weight(1f).height(1.dp)
                    .background(Brush.horizontalGradient(colors = listOf(SurfaceGlassBorder, Color.Transparent)))
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { media ->
                SearchMediaCard(media = media, onClicked = { onItemClicked(media) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchMediaCard(media: TmdbMedia, onClicked: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale"
    )

    Card(
        onClick = onClicked,
        modifier = Modifier
            .width(260.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isFocused) Modifier.border(1.5.dp, AccentPrimary, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, SurfaceGlassBorder, RoundedCornerShape(10.dp))
            )
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp))
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = media.cardBackdropUrl ?: media.posterUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                    )
                )
            )
            Text(
                text = media.displayTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (media.isMovie) AccentPrimary.copy(alpha = 0.85f) else AccentSecondary.copy(alpha = 0.85f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (media.isMovie) "MOVIE" else "TV",
                    color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─── Addon search row ────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonSearchRow(
    row: BoardRow,
    onItemClicked: (StremioMetaPreview) -> Unit
) {
    Column(modifier = Modifier.focusGroup()) {
        Row(
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp, end = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = row.title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 11.sp
                ),
                color = AccentSecondary.copy(alpha = 0.8f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(colors = listOf(SurfaceGlassBorder, Color.Transparent)))
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(row.items.size, key = { idx -> "${row.items[idx].id}_$idx" }) { idx ->
                val item = row.items[idx]
                AddonSearchCard(item = item, onClicked = { onItemClicked(item) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonSearchCard(item: StremioMetaPreview, onClicked: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale"
    )
    val isPortrait = item.posterShape == "poster" || item.posterShape == null
    val cardWidth = if (isPortrait) 160.dp else 260.dp
    val cardAspect = if (isPortrait) (2f / 3f) else (16f / 9f)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(cardAspect)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Card(
            onClick = onClicked,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFocused) Modifier.border(1.5.dp, AccentSecondary, RoundedCornerShape(10.dp))
                    else Modifier.border(1.dp, SurfaceGlassBorder, RoundedCornerShape(10.dp))
                )
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
