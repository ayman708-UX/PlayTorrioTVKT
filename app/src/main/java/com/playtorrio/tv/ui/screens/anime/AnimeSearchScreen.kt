package com.playtorrio.tv.ui.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.playtorrio.tv.ui.components.ContentCard
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.ui.components.PosterCardDefaults
import com.playtorrio.tv.ui.components.PosterCardStyle
import kotlin.math.roundToInt

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
    "Hentai", "Horror", "Mahou Shoujo", "Mecha", "Mystery",
    "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

private val FORMATS = listOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeSearchScreen(
    viewModel: AnimeSearchViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val posterCardStyle = remember(uiState.posterCardWidthDp, uiState.posterCardCornerRadiusDp) {
        val computedHeight = (uiState.posterCardWidthDp * 1.5f).roundToInt()
        PosterCardStyle(
            width = uiState.posterCardWidthDp.dp,
            height = computedHeight.dp,
            cornerRadius = uiState.posterCardCornerRadiusDp.dp,
            focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
            focusedScale = PosterCardDefaults.Style.focusedScale
        )
    }

    val hasActiveSearchOrFilter = uiState.query.isNotBlank() ||
        uiState.selectedGenre != null ||
        uiState.selectedFormat != null ||
        uiState.selectedStatus != null ||
        uiState.isAdult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))

            BasicTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontSize = 18.sp
                ),
                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                decorationBox = { innerTextField ->
                    if (uiState.query.isEmpty()) {
                        Text(
                            text = "Search anime by title, studio, or character...",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 18.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )

            if (uiState.query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { viewModel.onQueryChanged("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Bar (18+ Toggle + Genres + Formats)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🔞 18+ Adult Content Toggle Button
            item {
                Button(
                    onClick = { viewModel.toggleAdult() },
                    colors = ButtonDefaults.colors(
                        containerColor = if (uiState.isAdult) Color(0xFFDC2626) else Color(0xFF1E293B),
                        focusedContainerColor = if (uiState.isAdult) Color(0xFFEF4444) else Color(0xFFDC2626)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (uiState.isAdult) "🔞 18+ ON" else "🔞 18+ OFF",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Genre Chips
            items(GENRES) { genre ->
                val isSelected = uiState.selectedGenre.equals(genre, ignoreCase = true)
                Button(
                    onClick = { viewModel.selectGenre(genre) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF131E35),
                        focusedContainerColor = Color(0xFF38BDF8)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }

            // Format Chips
            items(FORMATS) { format ->
                val isSelected = uiState.selectedFormat.equals(format, ignoreCase = true)
                Button(
                    onClick = { viewModel.selectFormat(format) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF131E35),
                        focusedContainerColor = Color(0xFF38BDF8)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Results or Discovery Section
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (hasActiveSearchOrFilter) {
            if (uiState.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No anime found matching your criteria",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = uiState.posterCardWidthDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = uiState.searchResults,
                        key = { "search_${it.id}" }
                    ) { anime ->
                        ContentCard(
                            item = anime.toMetaPreview(),
                            onClick = { onNavigateToDetail(anime.id) },
                            posterCardStyle = posterCardStyle,
                            focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled
                        )
                    }
                }
            }
        } else {
            // Initial Discovery Sliders
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (uiState.discoveryTrending.isNotEmpty()) {
                    item {
                        Text(
                            text = "Trending Now",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(uiState.discoveryTrending, key = { "disc_trend_${it.id}" }) { anime ->
                                ContentCard(
                                    item = anime.toMetaPreview(),
                                    onClick = { onNavigateToDetail(anime.id) },
                                    posterCardStyle = posterCardStyle,
                                    focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled
                                )
                            }
                        }
                    }
                }

                if (uiState.discoveryPopular.isNotEmpty()) {
                    item {
                        Text(
                            text = "Popular This Season",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(uiState.discoveryPopular, key = { "disc_pop_${it.id}" }) { anime ->
                                ContentCard(
                                    item = anime.toMetaPreview(),
                                    onClick = { onNavigateToDetail(anime.id) },
                                    posterCardStyle = posterCardStyle,
                                    focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled
                                )
                            }
                        }
                    }
                }

                if (uiState.discoveryTopRated.isNotEmpty()) {
                    item {
                        Text(
                            text = "All-Time Top Rated",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(uiState.discoveryTopRated, key = { "disc_top_${it.id}" }) { anime ->
                                ContentCard(
                                    item = anime.toMetaPreview(),
                                    onClick = { onNavigateToDetail(anime.id) },
                                    posterCardStyle = posterCardStyle,
                                    focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
