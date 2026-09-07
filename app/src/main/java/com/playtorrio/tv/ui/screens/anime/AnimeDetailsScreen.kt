package com.playtorrio.tv.ui.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.playtorrio.tv.core.anime.model.AnimeCharacter
import com.playtorrio.tv.core.anime.model.AnimeEpisode
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.ui.components.ContentCard
import com.playtorrio.tv.ui.components.ErrorState
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.ui.components.PosterCardDefaults
import com.playtorrio.tv.ui.components.PosterCardStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeDetailsScreen(
    viewModel: AnimeDetailsViewModel = hiltViewModel(),
    onBackPress: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToStream: (
        animeId: Int,
        episodeNumber: Int,
        title: String,
        poster: String?,
        backdrop: String?,
        episodeTitle: String?,
        totalEpisodes: Int,
        isAdult: Boolean
    ) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showJumpDialog by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        val anime = uiState.anime

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@Box
        }

        if (uiState.error != null || anime == null) {
            ErrorState(
                message = uiState.error ?: "Anime not found",
                onRetry = { viewModel.loadDetails() }
            )
            return@Box
        }

        // Full-bleed Backdrop Image
        if (anime.backdropUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            )

            // Gradients
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF070A13).copy(alpha = 0.65f),
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
                                Color(0xFF070A13).copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 64.dp)
        ) {
            // Header / Metadata Block
            item(key = "anime_header_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Back button & format badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Button(
                            onClick = onBackPress,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF131E35),
                                focusedContainerColor = Color(0xFF0284C7)
                            ),
                            shape = ButtonDefaults.shape(CircleShape),
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "PLAYTORRIO ANIME",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title
                    Text(
                        text = anime.displayTitle,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.75f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Meta chips
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
                                color = Color.White.copy(alpha = 0.85f),
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
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }

                        if (anime.studioName.isNotBlank()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = anime.studioName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }

                        if (anime.totalEpisodes > 0) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = "${anime.totalEpisodes} Episodes",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Genres
                    if (anime.genres.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            anime.genres.take(5).forEach { g ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF131E35))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = g,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Synopsis
                    if (anime.description.isNotBlank()) {
                        Text(
                            text = anime.description,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 21.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.68f)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // Play / Resume Ep 1 Action
                    Button(
                        onClick = {
                            val firstEp = uiState.episodes.firstOrNull()?.number ?: 1
                            onNavigateToStream(
                                anime.id,
                                firstEp,
                                anime.displayTitle,
                                anime.coverUrl,
                                anime.backdropUrl,
                                "Episode $firstEp",
                                anime.totalEpisodes,
                                anime.isAdult
                            )
                        },
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
                                text = "Play Episode 1",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color(0xFF070A13),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Episodes Section with 25-Episode Chunking & Range Buttons
            if (uiState.episodes.isNotEmpty()) {
                item(key = "anime_episodes_section") {
                    val totalEps = uiState.episodes.size
                    val totalBatches = uiState.totalBatches
                    val currentBatch = uiState.selectedBatchIndex.coerceIn(0, (totalBatches - 1).coerceAtLeast(0))
                    val currentEpisodes = uiState.currentBatchEpisodes
                    val batchListState = rememberLazyListState()
                    val episodesListState = rememberLazyListState()

                    LaunchedEffect(currentBatch) {
                        if (totalBatches > 1) {
                            batchListState.animateScrollToItem(currentBatch)
                        }
                        episodesListState.scrollToItem(0)
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header row: Title, Page indicator, and Navigation buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Episodes ($totalEps total)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                if (totalBatches > 1) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Page ${currentBatch + 1} of $totalBatches",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }

                            if (totalBatches > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Jump Button
                                    Button(
                                        onClick = { showJumpDialog = true },
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF131E35),
                                            focusedContainerColor = Color(0xFF0284C7)
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Jump to...",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    // Prev Page Button
                                    Button(
                                        onClick = { viewModel.prevPage() },
                                        enabled = currentBatch > 0,
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF131E35),
                                            focusedContainerColor = Color(0xFF0284C7)
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "◀ Prev",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (currentBatch > 0) Color.White else Color.White.copy(alpha = 0.35f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    // Next Page Button
                                    Button(
                                        onClick = { viewModel.nextPage() },
                                        enabled = currentBatch < totalBatches - 1,
                                        colors = ButtonDefaults.colors(
                                            containerColor = Color(0xFF131E35),
                                            focusedContainerColor = Color(0xFF0284C7)
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Next ▶",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (currentBatch < totalBatches - 1) Color.White else Color.White.copy(alpha = 0.35f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Range Buttons Row (1-25, 26-50, 51-75, etc.)
                        if (totalBatches > 1) {
                            LazyRow(
                                state = batchListState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                items(totalBatches) { idx ->
                                    val startEp = idx * AnimeDetailsUiState.EPISODES_PER_PAGE + 1
                                    val endEp = minOf((idx + 1) * AnimeDetailsUiState.EPISODES_PER_PAGE, totalEps)
                                    val isSelected = idx == currentBatch
                                    val label = "$startEp - $endEp"

                                    Button(
                                        onClick = { viewModel.selectBatch(idx) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF131E35),
                                            focusedContainerColor = Color(0xFF38BDF8)
                                        ),
                                        border = ButtonDefaults.border(
                                            border = Border(
                                                border = androidx.compose.foundation.BorderStroke(
                                                    if (isSelected) 1.5.dp else 1.dp,
                                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                            focusedBorder = Border(
                                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Episode Cards Row (25 episodes)
                        LazyRow(
                            state = episodesListState,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = currentEpisodes,
                                key = { "ep_${it.number}" }
                            ) { ep ->
                                AnimeEpisodeCard(
                                    episode = ep,
                                    fallbackBackdrop = anime.backdropUrl,
                                    onClick = {
                                        onNavigateToStream(
                                            anime.id,
                                            ep.number,
                                            anime.displayTitle,
                                            anime.coverUrl,
                                            anime.backdropUrl,
                                            ep.displayTitle,
                                            anime.totalEpisodes,
                                            anime.isAdult
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // Characters / Cast Section
            if (anime.characters.isNotEmpty()) {
                item(key = "anime_characters_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Characters & Voice Actors",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(anime.characters, key = { "char_${it.id}" }) { c ->
                                AnimeCharacterCard(character = c)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // Relations Section
            if (anime.relations.isNotEmpty()) {
                item(key = "anime_relations_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Related Anime",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(anime.relations, key = { "rel_${it.id}" }) { rel ->
                                AnimeRelationCard(
                                    relation = rel,
                                    onClick = { onNavigateToDetail(rel.id) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // Recommendations Section ("More Like This")
            if (anime.recommendations.isNotEmpty()) {
                item(key = "anime_recommendations_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "More Like This",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(anime.recommendations, key = { "rec_${it.id}" }) { rec ->
                                ContentCard(
                                    item = rec.toMetaPreview(),
                                    onClick = { onNavigateToDetail(rec.id) },
                                    posterCardStyle = posterCardStyle,
                                    focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showJumpDialog && uiState.episodes.isNotEmpty()) {
            val currentFirstEp = uiState.currentBatchEpisodes.firstOrNull()?.number ?: 1
            AnimeJumpEpisodeDialog(
                totalEpisodes = uiState.episodes.size,
                currentEpisode = currentFirstEp,
                onDismiss = { showJumpDialog = false },
                onJump = { ep ->
                    viewModel.jumpToEpisode(ep)
                    showJumpDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeEpisodeCard(
    episode: AnimeEpisode,
    fallbackBackdrop: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF0F172A),
            focusedContainerColor = Color(0xFF1E293B)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(Color(0xFF131E35))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(episode.thumbnail.ifBlank { fallbackBackdrop })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Episode badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = episode.displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AnimeCharacterCard(character: AnimeCharacter) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFF131E35))
        ) {
            if (character.imageLarge.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(character.imageLarge)
                        .crossfade(true)
                        .build(),
                    contentDescription = character.nameFull,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = character.nameFull,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (character.role.isNotBlank()) {
            Text(
                text = character.role,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeRelationCard(
    relation: com.playtorrio.tv.core.anime.model.AnimeRelation,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(130.dp),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(containerColor = Color(0xFF0F172A)),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF131E35))
            ) {
                if (relation.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(relation.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = relation.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = relation.relationType.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Text(
                text = relation.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeJumpEpisodeDialog(
    totalEpisodes: Int,
    currentEpisode: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var targetEp by remember { mutableStateOf(currentEpisode.coerceIn(1, totalEpisodes)) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .widthIn(min = 320.dp, max = 460.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Jump to Episode",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )

                Text(
                    text = "Total Available: 1 – $totalEpisodes",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EPISODE $targetEp",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Remote-friendly step buttons (-100, -10, -1, +1, +10, +100)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val steps = listOf(-100, -10, -1, 1, 10, 100)
                    steps.forEach { step ->
                        Button(
                            onClick = {
                                targetEp = (targetEp + step).coerceIn(1, totalEpisodes)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF131E35),
                                focusedContainerColor = Color(0xFF0284C7)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (step > 0) "+$step" else "$step",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Quick preset chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = mutableListOf(1 to "First (Ep 1)")
                    if (totalEpisodes > 50) {
                        presets.add(totalEpisodes / 2 to "Mid (Ep ${totalEpisodes / 2})")
                    }
                    presets.add(totalEpisodes to "Latest (Ep $totalEpisodes)")

                    presets.forEach { (ep, label) ->
                        Button(
                            onClick = { targetEp = ep },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF1E293B),
                                focusedContainerColor = Color(0xFF38BDF8)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF334155)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                        )
                    }

                    Button(
                        onClick = { onJump(targetEp) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF0284C7),
                            focusedContainerColor = Color(0xFF38BDF8)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Jump",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
