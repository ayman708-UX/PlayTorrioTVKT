package com.playtorrio.tv.ui.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
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
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.ui.components.LoadingIndicator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeStreamScreen(
    viewModel: AnimeStreamViewModel = hiltViewModel(),
    onBackPress: () -> Unit,
    onStreamSelected: (AnimeStreamResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        // Ambient background
        if (!uiState.backdrop.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.backdrop)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A13))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A13).copy(alpha = 0.88f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 32.dp)
        ) {
            // Left Pane: Artwork & Metadata
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .padding(end = 36.dp)
            ) {
                // Back Button
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

                Spacer(modifier = Modifier.height(20.dp))

                // Poster
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uiState.poster ?: uiState.backdrop)
                            .crossfade(true)
                            .build(),
                        contentDescription = uiState.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Episode label
                Text(
                    text = uiState.episodeTitle ?: "Episode ${uiState.episodeNumber}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )

                if (uiState.totalEpisodes > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total Episodes: ${uiState.totalEpisodes}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // Right Pane: Filter Chips & Streams List
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                // Category Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            null to "ALL",
                            "sub" to "SUB",
                            "dub" to "DUB"
                        ).forEach { (catKey, catLabel) ->
                            val isSelected = uiState.selectedCategoryFilter == catKey
                            Button(
                                onClick = { viewModel.setCategoryFilter(catKey) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF131E35),
                                    focusedContainerColor = Color(0xFF38BDF8)
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = catLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    if (uiState.isScraping) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Scraping sources...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LoadingIndicator(modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Text(
                            text = "${uiState.streams.size} sources found",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stream List
                if (uiState.streams.isEmpty() && uiState.isScraping) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Extracting video streams from built-in sources...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        )
                    }
                } else if (uiState.streams.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No streams available for this episode.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.streams, key = { it.streamUrl }) { stream ->
                            AnimeStreamItemCard(
                                stream = stream,
                                onClick = { onStreamSelected(stream) }
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
private fun AnimeStreamItemCard(
    stream: AnimeStreamResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Server icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = stream.serverName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Category (SUB / DUB)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stream.category,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (stream.category == "DUB") Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        // Quality
                        Text(
                            text = stream.quality,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )

                        // Subtitle track count
                        if (stream.tracks.isNotEmpty()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = "${stream.tracks.size} Subtitles",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Intro/Outro skip badge
                        if (stream.introStart != null && stream.introEnd != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = "Skip Intro",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            // Play icon
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
