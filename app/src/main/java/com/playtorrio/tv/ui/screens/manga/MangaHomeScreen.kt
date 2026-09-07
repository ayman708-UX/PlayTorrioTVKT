package com.playtorrio.tv.ui.screens.manga

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.playtorrio.tv.core.manga.MangaReadingProgress
import com.playtorrio.tv.ui.components.PlayTorrioDialog
import com.playtorrio.tv.ui.theme.PlayTorrioTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.R
import com.playtorrio.tv.core.manga.model.Manga
import com.playtorrio.tv.ui.components.LoadingIndicator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MangaHomeScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToReader: (seriesId: String, chapterId: String, chapterIndex: Int, pageIndex: Int) -> Unit = { _, _, _, _ -> },
    viewModel: MangaHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeManga = uiState.focusedManga ?: uiState.spotlightManga
    var selectedMangaForOptions by remember { mutableStateOf<MangaReadingProgress?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        // --- Ambient Dynamic Background Artwork ---
        if (activeManga != null && activeManga.coverNormal.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(activeManga.coverNormal)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .blur(30.dp)
            )

            // Vertical gradient fade into background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF070A13).copy(alpha = 0.3f),
                                Color(0xFF070A13).copy(alpha = 0.75f),
                                Color(0xFF070A13)
                            )
                        )
                    )
            )

            // Horizontal gradient fade for text contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF070A13).copy(alpha = 0.95f),
                                Color(0xFF070A13).copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // --- Main Content ---
        when {
            uiState.isLoading && uiState.shelves.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                }
            }

            uiState.error != null && uiState.shelves.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "Unable to load manga",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadHomeData() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF0284C7)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    // Item 1: Top Hero / Spotlight Section with Search & Genres
                    item(key = "manga_spotlight") {
                        MangaSpotlightSection(
                            manga = activeManga,
                            onReadClick = {
                                if (activeManga != null) {
                                    onNavigateToDetails(activeManga.id)
                                }
                            },
                            searchQuery = uiState.searchQuery,
                            isSearchMode = uiState.isSearchMode,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onToggleSearch = { viewModel.setSearchMode(!uiState.isSearchMode) },
                            genres = uiState.genres,
                            selectedGenre = uiState.selectedGenre,
                            onSelectGenre = { viewModel.selectGenre(it) }
                        )
                    }

                    // Item 2: Search Results Section
                    if (uiState.isSearchMode) {
                        item(key = "manga_search_section") {
                            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                                Text(
                                    text = if (uiState.isSearching) "Searching..."
                                    else "Search Results for \"${uiState.searchQuery}\"",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (uiState.isSearching) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                                    }
                                } else if (uiState.searchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No manga found for \"${uiState.searchQuery}\"",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) {
                                        items(uiState.searchResults, key = { "search_${it.id}" }) { manga ->
                                            MangaCard(
                                                manga = manga,
                                                onFocus = { viewModel.setFocusedManga(it) },
                                                onClick = { onNavigateToDetails(it.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (uiState.selectedGenre != "All") {
                        // Genre Section
                        item(key = "manga_genre_section") {
                            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                                Text(
                                    text = "${uiState.selectedGenre} Manga",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (uiState.isLoadingGenre) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) {
                                        items(uiState.genreItems, key = { "genre_${it.id}" }) { manga ->
                                            MangaCard(
                                                manga = manga,
                                                onFocus = { viewModel.setFocusedManga(it) },
                                                onClick = { onNavigateToDetails(it.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Continue Reading Shelf
                        if (uiState.continueReading.isNotEmpty()) {
                            item(key = "manga_continue_reading_section") {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Continue Reading",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${uiState.continueReading.size}",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)
                                    ) {
                                        items(uiState.continueReading, key = { "reading_${it.mangaId}" }) { progress ->
                                            MangaContinueReadingCard(
                                                progress = progress,
                                                onClick = {
                                                    onNavigateToReader(
                                                        progress.mangaId,
                                                        progress.chapterId,
                                                        0,
                                                        progress.pageIndex
                                                    )
                                                },
                                                onLongClick = {
                                                    selectedMangaForOptions = it
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Regular Shelves
                        items(uiState.shelves, key = { "shelf_${it.title}" }) { shelf ->
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = shelf.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)
                                ) {
                                    items(shelf.items, key = { "${shelf.title}_${it.id}" }) { manga ->
                                        MangaCard(
                                            manga = manga,
                                            onFocus = { viewModel.setFocusedManga(it) },
                                            onClick = { onNavigateToDetails(it.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val optionsProgress = selectedMangaForOptions
        if (optionsProgress != null) {
            val removeFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                removeFocusRequester.requestFocus()
            }

            PlayTorrioDialog(
                onDismiss = { selectedMangaForOptions = null },
                title = optionsProgress.title,
                subtitle = "Continue Reading • ${optionsProgress.chapterName}"
            ) {
                Button(
                    onClick = {
                        viewModel.removeFromContinueReading(optionsProgress.mangaId)
                        selectedMangaForOptions = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(removeFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFFDC2626).copy(alpha = 0.2f),
                        contentColor = Color(0xFFEF4444),
                        focusedContainerColor = Color(0xFFDC2626),
                        focusedContentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove from Continue Reading", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        val id = optionsProgress.mangaId
                        selectedMangaForOptions = null
                        onNavigateToDetails(id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        contentColor = PlayTorrioTheme.colors.TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Details")
                }

                Button(
                    onClick = { selectedMangaForOptions = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        contentColor = PlayTorrioTheme.colors.TextSecondary
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MangaSpotlightSection(
    manga: Manga?,
    onReadClick: () -> Unit,
    searchQuery: String,
    isSearchMode: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    genres: List<String>,
    selectedGenre: String,
    onSelectGenre: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        // Top Row: Category badge + Genre Pills + Search Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "PLAYTORRIO MANGA",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Genre Pills
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(genres.take(12)) { genre ->
                        val isSelected = genre == selectedGenre
                        Surface(
                            onClick = { onSelectGenre(genre) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF0284C7) else Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF38BDF8)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, Color.White))
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = genre,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar & Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    var isSearchFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E2435))
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (isSearchFocused) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.2f)
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.manga_search_hint),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Search / Close Button
                Surface(
                    onClick = onToggleSearch,
                    modifier = Modifier.size(38.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSearchMode) Color(0xFF0284C7) else Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color(0xFF38BDF8)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSearchMode) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchMode) "Close Search" else "Search",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Spotlight Information
        if (manga != null) {
            Text(
                text = manga.title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val metaText = buildString {
                if (manga.author.isNotBlank()) append(manga.author)
                if (manga.year.isNotBlank()) {
                    if (isNotEmpty()) append("  •  ")
                    append(manga.year)
                }
                if (manga.status.isNotBlank()) {
                    if (isNotEmpty()) append("  •  ")
                    append(manga.status)
                }
                if (manga.type.isNotBlank()) {
                    if (isNotEmpty()) append("  •  ")
                    append(manga.type)
                }
            }

            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    color = Color(0xFF38BDF8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.65f)
                )
            }

            if (manga.synopsis.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = manga.synopsis,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(0.65f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button
            Button(
                onClick = onReadClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF0284C7),
                    focusedContainerColor = Color(0xFF38BDF8)
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                border = ButtonDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                scale = ButtonDefaults.scale(focusedScale = 1.08f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFF070A13),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.manga_spotlight_read),
                        color = Color(0xFF070A13),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Placeholder when no manga is focused
            Text(
                text = "Discover Manga",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Read trending manga, manhwa, and manhua on your TV",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
        }
    }
}
