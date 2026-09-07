package com.playtorrio.tv.ui.screens.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.ui.components.ContentCard
import com.playtorrio.tv.ui.components.ErrorState
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.ui.components.PosterCardDefaults
import com.playtorrio.tv.ui.components.PosterCardStyle
import com.playtorrio.tv.ui.theme.PlayTorrioTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeHomeScreen(
    viewModel: AnimeHomeViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    val activeAnime = uiState.focusedAnime ?: uiState.heroAnime

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        // Ambient Dynamic Hero Backdrop Background
        if (activeAnime != null && activeAnime.backdropUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(activeAnime.backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            )

            // Gradients over the backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF070A13).copy(alpha = 0.6f),
                                Color(0xFF070A13)
                            )
                        )
                    )
            )
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

        when {
            uiState.isLoading && uiState.rows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            uiState.error != null && uiState.rows.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "Error loading Anime catalogs",
                    onRetry = { viewModel.loadAnimeCatalogs() }
                )
            }

            else -> {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 64.dp)
                ) {
                    // Top Hero / Spotlight Section
                    item(key = "anime_hero_section") {
                        AnimeHeroSpotlight(
                            anime = activeAnime,
                            isArabicAnime = uiState.isArabicAnime,
                            onToggleArabic = { viewModel.toggleArabicAnime() },
                            onPlayClick = {
                                activeAnime?.let { onNavigateToDetail(it.slug.ifBlank { it.id.toString() }) }
                            },
                            onSearchClick = onNavigateToSearch
                        )
                    }

                    // Catalog Rows
                    items(
                        items = uiState.rows,
                        key = { it.title }
                    ) { row ->
                        AnimeCatalogRowSection(
                            row = row,
                            cardStyle = posterCardStyle,
                            expandBackdropEnabled = uiState.focusedPosterBackdropExpandEnabled,
                            onAnimeClick = { onNavigateToDetail(it.slug.ifBlank { it.id.toString() }) },
                            onAnimeFocus = { viewModel.setFocusedAnime(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeHeroSpotlight(
    anime: AnimeMedia?,
    isArabicAnime: Boolean,
    onToggleArabic: () -> Unit,
    onPlayClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    if (anime == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 20.dp)
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PLAYTORRIO",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabicAnime) "ARABIC ANIME" else "ANIME",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isArabicAnime) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleArabic,
                    colors = ButtonDefaults.colors(
                        containerColor = if (isArabicAnime) Color(0xFF0C4A6E) else Color(0xFF131E35),
                        focusedContainerColor = Color(0xFF0284C7)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isArabicAnime) "🌙 Arabic Anime" else "🌐 Normal Anime",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isArabicAnime) Color(0xFF38BDF8) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Button(
                    onClick = onSearchClick,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF131E35),
                        focusedContainerColor = Color(0xFF0284C7)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Anime",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Search Anime",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Title
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.68f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Metadata badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (anime.averageScore > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${anime.formattedScore}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Text(
                text = anime.formattedFormat,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
            )

            if (anime.formattedSeasonYear.isNotBlank()) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                )
                Text(
                    text = anime.formattedSeasonYear,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
            }

            if (anime.genres.isNotEmpty()) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                )
                Text(
                    text = anime.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Synopsis
        if (anime.description.isNotBlank()) {
            Text(
                text = anime.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.72f),
                    lineHeight = 20.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.60f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Play / Details Action
        Button(
            onClick = onPlayClick,
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF0284C7),
                focusedContainerColor = Color(0xFF38BDF8)
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF070A13),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch Now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color(0xFF070A13),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeCatalogRowSection(
    row: AnimeCatalogRow,
    cardStyle: PosterCardStyle,
    expandBackdropEnabled: Boolean,
    onAnimeClick: (AnimeMedia) -> Unit,
    onAnimeFocus: (AnimeMedia) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = row.items,
                key = { "${row.title}_${it.id}" }
            ) { anime ->
                ContentCard(
                    item = anime.toMetaPreview(),
                    onClick = { onAnimeClick(anime) },
                    posterCardStyle = cardStyle,
                    focusedPosterBackdropExpandEnabled = expandBackdropEnabled,
                    onFocus = { onAnimeFocus(anime) }
                )
            }
        }
    }
}
