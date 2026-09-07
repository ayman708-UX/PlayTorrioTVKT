package com.playtorrio.tv.ui.screens.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.playtorrio.tv.core.iptv.context.IptvChannelContextHolder
import com.playtorrio.tv.ui.screens.detail.requestFocusAfterFrames
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.playtorrio.tv.core.iptv.model.AliveProgress
import com.playtorrio.tv.core.iptv.model.IptvCategory
import com.playtorrio.tv.core.iptv.model.IptvSection
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.model.VerifiedPortal

@Composable
fun IptvPortalBrowserScreen(
    viewModel: IptvViewModel,
    portal: VerifiedPortal,
    onBack: () -> Unit,
    onPlayStream: (streamUrl: String, title: String) -> Unit
) {
    var activeSection by remember { mutableStateOf(IptvSection.LIVE) }
    var searchQuery by remember { mutableStateOf("") }

    val categories by viewModel.browserCategories.collectAsState()
    val streams by viewModel.browserStreams.collectAsState()
    val isLoading by viewModel.isBrowserLoading.collectAsState()
    val selectedCatId by viewModel.browserSelectedCategoryId.collectAsState()
    val lastPlayedId by viewModel.lastPlayedStreamId.collectAsState()
    val aliveIds by viewModel.browserAliveIds.collectAsState()
    val isAliveChecking by viewModel.isAliveChecking.collectAsState()
    val aliveProgress by viewModel.aliveProgress.collectAsState()

    val gridState = rememberLazyGridState()
    val categoryListState = rememberLazyListState()

    LaunchedEffect(portal, activeSection) {
        viewModel.loadPortalCategories(portal, activeSection)
    }

    val filteredStreams = remember(streams, searchQuery) {
        if (searchQuery.isBlank()) streams else {
            streams.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val focusRequesters = remember(filteredStreams) {
        filteredStreams.associate { it.streamId to FocusRequester() }
    }

    LaunchedEffect(filteredStreams, lastPlayedId) {
        val targetId = lastPlayedId ?: return@LaunchedEffect
        val index = filteredStreams.indexOfFirst { it.streamId == targetId }
        if (index >= 0) {
            gridState.scrollToItem(index)
            focusRequesters[targetId]?.requestFocusAfterFrames(2)
            viewModel.setLastPlayedStreamId(null)
        }
    }

    val selectedCatIndex = remember(categories, selectedCatId) {
        categories.indexOfFirst { it.id == selectedCatId }
    }
    LaunchedEffect(selectedCatIndex) {
        if (selectedCatIndex >= 0) {
            categoryListState.animateScrollToItem(selectedCatIndex)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Left Categories Sidebar
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xFF0F172A))
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = portal.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Expiry: ${portal.expiry}",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Picker (Live / VOD / Series)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionTabButton(
                    label = "Live",
                    icon = Icons.Default.LiveTv,
                    selected = activeSection == IptvSection.LIVE,
                    onClick = { activeSection = IptvSection.LIVE },
                    modifier = Modifier.weight(1f)
                )
                SectionTabButton(
                    label = "VOD",
                    icon = Icons.Default.Movie,
                    selected = activeSection == IptvSection.VOD,
                    onClick = { activeSection = IptvSection.VOD },
                    modifier = Modifier.weight(1f)
                )
                SectionTabButton(
                    label = "Series",
                    icon = Icons.Default.Tv,
                    selected = activeSection == IptvSection.SERIES,
                    onClick = { activeSection = IptvSection.SERIES },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CATEGORIES",
                color = Color(0x66FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            // Categories List
            LazyColumn(
                state = categoryListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    CategoryItemRow(
                        category = cat,
                        isSelected = cat.id == selectedCatId,
                        onClick = {
                            viewModel.loadPortalStreams(portal, activeSection, cat.id)
                        }
                    )
                }
            }
        }

        // Right Content Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(20.dp)
        ) {
            // Toolbar (Search & Health Check)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TvIptvSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search channel or title in this category...",
                    modifier = Modifier.width(360.dp)
                )

                if (activeSection == IptvSection.LIVE) {
                    Button(
                        onClick = { viewModel.checkAliveForCategory(portal) },
                        enabled = !isAliveChecking && streams.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAliveChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking: ${aliveProgress.checked}/${aliveProgress.total}", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Alive Feeds", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Streams Grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (filteredStreams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No channels match '$searchQuery'" else "No streams in this category",
                        color = Color(0x88FFFFFF),
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredStreams, key = { it.streamId }) { stream ->
                        val isAlive = aliveIds.contains(stream.streamId)
                        val focusRequester = focusRequesters[stream.streamId]
                        IptvStreamCard(
                            stream = stream,
                            isAlive = isAlive,
                            focusRequester = focusRequester,
                            onClick = {
                                val activeCatName = categories.firstOrNull { it.id == selectedCatId }?.name ?: "Channels"
                                IptvChannelContextHolder.setPortalContext(
                                    portal = portal,
                                    categoryName = activeCatName,
                                    currentStreamId = stream.streamId,
                                    channels = filteredStreams
                                )
                                viewModel.setLastPlayedStreamId(stream.streamId)
                                val url = viewModel.getStreamUrl(portal, stream)
                                onPlayStream(url, stream.name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFF0284C7) else Color(0xFF1E293B)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0x88FFFFFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color(0x88FFFFFF),
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun CategoryItemRow(
    category: IptvCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        color = if (isFocused || isSelected) Color(0xFF1E293B) else Color.Transparent,
        border = if (isFocused) BorderStroke(1.5.dp, Color(0xFF38BDF8)) else null
    ) {
        Text(
            text = category.name,
            color = if (isSelected) Color(0xFF38BDF8) else Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun IptvStreamCard(
    stream: IptvStream,
    isAlive: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF1E293B) else Color(0xFF111827)),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF38BDF8)) else BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stream.icon.isNotBlank()) {
                    AsyncImage(
                        model = stream.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x2238BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stream.containerExt.uppercase(),
                        color = Color(0x88FFFFFF),
                        fontSize = 10.sp
                    )
                }
            }

            if (isAlive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3310B981))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ALIVE",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
