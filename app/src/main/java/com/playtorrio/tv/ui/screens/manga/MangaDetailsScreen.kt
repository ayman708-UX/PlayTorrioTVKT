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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.core.manga.model.MangaChapter
import com.playtorrio.tv.ui.components.ErrorState
import com.playtorrio.tv.ui.components.LoadingIndicator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
    onBackPress: () -> Unit,
    onNavigateToReader: (seriesId: String, chapterId: String, chapterIndex: Int, pageIndex: Int) -> Unit,
    viewModel: MangaDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showJumpDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        val manga = uiState.manga

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@Box
        }

        if (uiState.error != null || manga == null) {
            ErrorState(
                message = uiState.error ?: "Manga not found",
                onRetry = { viewModel.loadDetails() }
            )
            return@Box
        }

        // Ambient blurred backdrop
        if (manga.coverNormal.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(manga.coverNormal)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .blur(26.dp)
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
            item(key = "manga_header_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Back button & brand badge
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
                            text = "PLAYTORRIO MANGA",
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
                        text = manga.title,
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

                    // Meta Chips: Type, Status, Year, Author
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (manga.type.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = manga.type.uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (manga.status.isNotBlank()) {
                            Text(
                                text = manga.status,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        if (manga.year.isNotBlank()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = manga.year,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }

                        if (manga.author.isNotBlank()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.4f))
                            )
                            Text(
                                text = manga.author,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tags
                    if (manga.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(0.75f)
                        ) {
                            manga.tags.take(6).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Synopsis
                    if (manga.synopsis.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = manga.synopsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                lineHeight = 21.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.68f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Read / Resume Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstChapter = uiState.chapters.firstOrNull()
                        val resumeProgress = uiState.readingProgress

                        Button(
                            onClick = {
                                if (resumeProgress != null) {
                                    val resumeIdx = uiState.chapters.indexOfFirst { it.id == resumeProgress.chapterId }
                                        .takeIf { it >= 0 } ?: 0
                                    onNavigateToReader(
                                        manga.id,
                                        resumeProgress.chapterId,
                                        resumeIdx,
                                        resumeProgress.pageIndex
                                    )
                                } else if (firstChapter != null) {
                                    onNavigateToReader(
                                        manga.id,
                                        firstChapter.id,
                                        0,
                                        0
                                    )
                                }
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF0284C7),
                                focusedContainerColor = Color(0xFF38BDF8)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
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
                                    text = if (resumeProgress != null) "Resume: ${resumeProgress.chapterName}"
                                    else "Read Chapter 1",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFF070A13),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Bookmark / Like Button
                        Button(
                            onClick = { viewModel.toggleLike() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF131E35),
                                focusedContainerColor = Color(0xFF1E293B)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.isLiked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (uiState.isLiked) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isLiked) "Saved" else "Bookmark",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Chapters Section with 25-Chapter Chunking & Range Buttons
            if (uiState.chapters.isNotEmpty()) {
                item(key = "manga_chapters_section") {
                    val totalChapters = uiState.chapters.size
                    val totalBatches = uiState.totalBatches
                    val currentBatch = uiState.selectedBatchIndex.coerceIn(0, (totalBatches - 1).coerceAtLeast(0))
                    val currentBatchChapters = uiState.currentBatchChapters
                    val batchListState = rememberLazyListState()
                    val chaptersListState = rememberLazyListState()

                    LaunchedEffect(currentBatch) {
                        if (totalBatches > 1) {
                            batchListState.animateScrollToItem(currentBatch)
                        }
                        chaptersListState.scrollToItem(0)
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
                                    text = "Chapters ($totalChapters total)",
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

                        // Range Buttons Row (1-25, 26-50, etc.)
                        if (totalBatches > 1) {
                            LazyRow(
                                state = batchListState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                items(totalBatches) { idx ->
                                    val startEp = idx * MangaDetailsUiState.CHAPTERS_PER_PAGE + 1
                                    val endEp = minOf((idx + 1) * MangaDetailsUiState.CHAPTERS_PER_PAGE, totalChapters)
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
                                                border = BorderStroke(
                                                    if (isSelected) 1.5.dp else 1.dp,
                                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, Color.White),
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

                        // Chapter Cards Row (25 chapters)
                        LazyRow(
                            state = chaptersListState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                items = currentBatchChapters,
                                key = { _, chapter -> "chapter_${chapter.id}" }
                            ) { indexInBatch, chapter ->
                                val overallIndex = currentBatch * MangaDetailsUiState.CHAPTERS_PER_PAGE + indexInBatch
                                MangaChapterCard(
                                    chapter = chapter,
                                    isLastRead = uiState.readingProgress?.chapterId == chapter.id,
                                    onClick = {
                                        onNavigateToReader(
                                            manga.id,
                                            chapter.id,
                                            overallIndex,
                                            if (uiState.readingProgress?.chapterId == chapter.id) uiState.readingProgress?.pageIndex ?: 0 else 0
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Jump to Chapter Dialog
        if (showJumpDialog) {
            val totalChapters = uiState.chapters.size
            val currentCh = uiState.chapters.getOrNull(
                uiState.selectedBatchIndex * MangaDetailsUiState.CHAPTERS_PER_PAGE
            )?.number ?: 1.0

            MangaJumpChapterDialog(
                totalChapters = totalChapters,
                currentChapterNumber = currentCh,
                onDismiss = { showJumpDialog = false },
                onJump = { targetNumber ->
                    showJumpDialog = false
                    viewModel.jumpToChapter(targetNumber)
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MangaChapterCard(
    chapter: MangaChapter,
    isLastRead: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .height(90.dp),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF131E35),
            focusedContainerColor = Color(0xFF1E293B)
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isLastRead) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLastRead) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0284C7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Last Read",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (chapter.date.isNotBlank()) {
                Text(
                    text = chapter.date.take(10),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                )
            } else {
                Text(
                    text = "Tap to read",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF38BDF8).copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MangaJumpChapterDialog(
    totalChapters: Int,
    currentChapterNumber: Double,
    onDismiss: () -> Unit,
    onJump: (Double) -> Unit
) {
    var targetChapter by remember { mutableDoubleStateOf(currentChapterNumber) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .widthIn(min = 340.dp, max = 480.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Jump to Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )

                Text(
                    text = "Total Chapters: $totalChapters",
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
                        text = "CHAPTER ${if (targetChapter % 1.0 == 0.0) targetChapter.toInt() else targetChapter}",
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
                                targetChapter = (targetChapter + step).coerceAtLeast(1.0)
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

                // Action buttons: Jump and Cancel
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            focusedContainerColor = Color(0xFF334155)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onJump(targetChapter) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF0284C7),
                            focusedContainerColor = Color(0xFF38BDF8)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Jump Now",
                            color = Color(0xFF070A13),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
