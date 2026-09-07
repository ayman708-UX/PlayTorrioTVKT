package com.playtorrio.tv.ui.screens.audiobook

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.ui.components.P2pConsentDialog

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudiobookHomeScreen(
    onNavigateToPlayer: () -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continueListening by viewModel.continueListening.collectAsStateWithLifecycle()
    val currentPlayingBook by viewModel.currentPlayingBook.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val needsP2pConsent by viewModel.needsP2pConsent.collectAsStateWithLifecycle()

    val activeBook = uiState.focusedBook ?: uiState.spotlightBook ?: currentPlayingBook

    // P2P Consent dialog if required for AudiobookBay
    if (needsP2pConsent) {
        P2pConsentDialog(
            onEnableP2p = { viewModel.onP2pConsentGranted() },
            onDismiss = { viewModel.onP2pConsentDismissed() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
    ) {
        // --- Ambient Dynamic Background Artwork ---
        if (activeBook != null && activeBook.coverImage.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(activeBook.coverImage)
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
            uiState.isLoading && uiState.rows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                }
            }

            uiState.error != null && uiState.rows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "Unable to load audiobooks",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAudiobookHome() },
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF8B5CF6)
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
                    // Item 1: Top Hero / Spotlight Section
                    item(key = "audiobook_spotlight") {
                        AudiobookSpotlightSection(
                            book = activeBook,
                            isPlayingThisBook = isPlaying && currentPlayingBook?.uuid == activeBook?.uuid,
                            onPlayClick = {
                                if (activeBook != null) {
                                    if (currentPlayingBook?.uuid == activeBook.uuid && isPlaying) {
                                        onNavigateToPlayer()
                                    } else {
                                        viewModel.playBook(activeBook)
                                        onNavigateToPlayer()
                                    }
                                }
                            },
                            searchQuery = uiState.searchQuery,
                            isSearchMode = uiState.isSearchMode,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onToggleSearch = { viewModel.setSearchMode(!uiState.isSearchMode) }
                        )
                    }

                    // Item 2: Search Results
                    if (uiState.isSearchMode) {
                        item(key = "audiobook_search_section") {
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
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                                    }
                                } else if (uiState.searchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No audiobooks found for \"${uiState.searchQuery}\"",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) {
                                        items(uiState.searchResults, key = { "search_${it.uuid}" }) { book ->
                                            AudiobookCard(
                                                book = book,
                                                isPlaying = isPlaying && currentPlayingBook?.uuid == book.uuid,
                                                onFocus = { viewModel.setFocusedBook(it) },
                                                onClick = {
                                                    viewModel.playBook(it)
                                                    onNavigateToPlayer()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Item 3: Continue Listening (Only in Audiobooks section!)
                        if (continueListening.isNotEmpty()) {
                            item(key = "audiobook_continue_listening") {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.audiobook_continue_listening),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)
                                    ) {
                                        items(continueListening, key = { "continue_${it.audiobook.uuid}" }) { progress ->
                                            AudiobookCard(
                                                book = progress.audiobook,
                                                progress = progress,
                                                isPlaying = isPlaying && currentPlayingBook?.uuid == progress.audiobook.uuid,
                                                onFocus = { viewModel.setFocusedBook(it) },
                                                onClick = {
                                                    viewModel.playBook(progress.audiobook, progress)
                                                    onNavigateToPlayer()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Regular Shelves
                        items(uiState.rows, key = { "row_${it.title}" }) { row ->
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    text = row.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)
                                ) {
                                    items(row.books, key = { "${row.title}_${it.uuid}" }) { book ->
                                        AudiobookCard(
                                            book = book,
                                            isPlaying = isPlaying && currentPlayingBook?.uuid == book.uuid,
                                            onFocus = { viewModel.setFocusedBook(it) },
                                            onClick = {
                                                viewModel.playBook(it)
                                                onNavigateToPlayer()
                                            }
                                        )
                                    }
                                }
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
private fun AudiobookSpotlightSection(
    book: Audiobook?,
    isPlayingThisBook: Boolean,
    onPlayClick: () -> Unit,
    searchQuery: String,
    isSearchMode: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Top Row: Category tag + Search button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.25f))
                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "PLAYTORRIO AUDIOBOOKS",
                    color = Color(0xFFC084FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Search Bar & Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    var isSearchFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1E2435))
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (isSearchFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.2f)
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.audiobook_search_hint),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp
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
                    modifier = Modifier.size(40.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSearchMode) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color(0xFFA78BFA)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Spotlight Audiobook Information
        if (book != null) {
            Text(
                text = book.title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = (book.author.ifBlank { book.source.replaceFirstChar { it.uppercase() } }) +
                    "  •  Source: ${book.source}",
                color = Color(0xFFA78BFA),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.65f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Listen Now Action Button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF8B5CF6),
                    focusedContainerColor = Color(0xFFA78BFA)
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
                        imageVector = if (isPlayingThisBook) Icons.Default.Pause else Icons.Default.Headphones,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlayingThisBook) "Pause" else stringResource(R.string.audiobook_spotlight_play),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Placeholder when no book is selected
            Text(
                text = "Discover Audiobooks",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Stream thousands of full-length audiobooks across 9 sources",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
        }
    }
}
