package com.playtorrio.tv.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.playtorrio.tv.data.streaming.HttpStreamResult
import com.playtorrio.tv.data.stremio.StremioStream
import com.playtorrio.tv.data.torrent.TorrentResult
import kotlinx.coroutines.delay

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val AccentTertiary = Color(0xFF38BDF8)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)
private val GreenSeed = Color(0xFF4ADE80)
private val RedLeech = Color(0xFFF87171)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TorrentOverlay(
    visible: Boolean,
    searchLabel: String,
    results: List<TorrentResult>,
    isLoading: Boolean,
    httpStreams: List<HttpStreamResult> = emptyList(),
    isLoadingHttpStreams: Boolean = false,
    stremioStreams: List<StremioStream> = emptyList(),
    isLoadingStremioStreams: Boolean = false,
    onDismiss: () -> Unit,
    onTorrentSelected: (TorrentResult) -> Unit,
    onHttpStreamSelected: (HttpStreamResult) -> Unit = {},
    onStremioStreamSelected: (StremioStream) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val emptyFocusRequester = remember { FocusRequester() }
    val panelFocusRequester = remember { FocusRequester() }

    val tabNames = remember(stremioStreams) {
        val addonNames = stremioStreams.mapNotNull { it.addonName }.distinct()
        listOf("PlayTorrio", "PlayTorrioHTTP") + addonNames
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(tabNames.size) {
        if (selectedTabIndex >= tabNames.size) selectedTabIndex = 0
    }

    // Auto-focus panel when opened
    LaunchedEffect(visible) {
        if (visible) {
            delay(50)
            runCatching { panelFocusRequester.requestFocus() }
        }
    }

    // Manage focus between active items and empty/loading state
    LaunchedEffect(visible, selectedTabIndex, results.size, httpStreams.size, stremioStreams.size, isLoading, isLoadingHttpStreams, isLoadingStremioStreams) {
        if (visible) {
            val hasItems = when (selectedTabIndex) {
                0 -> results.isNotEmpty()
                1 -> httpStreams.isNotEmpty()
                else -> stremioStreams.any { it.addonName == tabNames.getOrNull(selectedTabIndex) }
            }
            if (hasItems) {
                runCatching { focusRequester.requestFocus() }
            } else {
                runCatching { emptyFocusRequester.requestFocus() }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        ) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 3 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss()
                        true
                    } else false
                }
        ) {
            // Right-side panel
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0A0A0F).copy(alpha = 0.95f),
                                Color(0xFF0F0F18)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SurfaceGlassBorder,
                                Color.Transparent,
                                SurfaceGlassBorder
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .clickable(enabled = false) {}
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(panelFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (selectedTabIndex > 0) {
                                            selectedTabIndex--
                                            true
                                        } else false
                                    }
                                    Key.DirectionRight -> {
                                        if (selectedTabIndex < tabNames.lastIndex) {
                                            selectedTabIndex++
                                            true
                                        } else false
                                    }
                                    Key.Back -> {
                                        onDismiss()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = searchLabel,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    selectedTabIndex == 0 && isLoading && results.isEmpty() -> "Searching torrents…"
                                    selectedTabIndex == 0 -> "${results.size} torrent${if (results.size != 1) "s" else ""} found"
                                    selectedTabIndex == 1 && isLoadingHttpStreams && httpStreams.isEmpty() -> "Extracting HTTP streams…"
                                    selectedTabIndex == 1 && isLoadingHttpStreams -> "${httpStreams.size} stream${if (httpStreams.size != 1) "s" else ""} found (extracting…)"
                                    selectedTabIndex == 1 -> "${httpStreams.size} stream${if (httpStreams.size != 1) "s" else ""} found"
                                    isLoadingStremioStreams -> "Searching add-ons…"
                                    else -> {
                                        val count = stremioStreams.count { it.addonName == tabNames.getOrNull(selectedTabIndex) }
                                        "$count stream${if (count != 1) "s" else ""} found"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Source tabs (interactive buttons)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabNames.forEachIndexed { index, name ->
                            SourceTab(
                                name = name,
                                isActive = index == selectedTabIndex,
                                onClick = { selectedTabIndex = index }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when (selectedTabIndex) {
                        0 -> {
                            // ── Tab 0: PlayTorrio (Torrents) ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NAME",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "SIZE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    text = "S",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp
                                    ),
                                    color = GreenSeed.copy(alpha = 0.5f),
                                    modifier = Modifier.width(45.dp)
                                )
                                Text(
                                    text = "L",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp
                                    ),
                                    color = RedLeech.copy(alpha = 0.5f),
                                    modifier = Modifier.width(45.dp)
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            if (isLoading && results.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = null,
                                            tint = AccentPrimary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Searching torrents…",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else if (results.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No torrents found",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).focusGroup(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(results, key = { _, r -> r.magnetLink.hashCode() }) { index, result ->
                                        TorrentRow(
                                            result = result,
                                            onClick = { onTorrentSelected(result) },
                                            focusRequester = if (index == 0) focusRequester else null
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ── Tab 1: PlayTorrioHTTP (Real-Time HTTP Streams) ──
                            if (isLoadingHttpStreams && httpStreams.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = AccentSecondary,
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            text = "Extracting HTTP streams in real time…",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else if (httpStreams.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No HTTP streams found",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).focusGroup(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(httpStreams, key = { idx, s -> "http_${s.sourceName}_${s.url}_$idx" }) { index, stream ->
                                        HttpStreamRow(
                                            stream = stream,
                                            onClick = { onHttpStreamSelected(stream) },
                                            focusRequester = if (index == 0) focusRequester else null
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            // ── Stremio Addon Tabs ──
                            val filteredStreams = remember(stremioStreams, selectedTabIndex, tabNames) {
                                stremioStreams.filter { it.addonName == tabNames.getOrNull(selectedTabIndex) }
                            }

                            if (isLoadingStremioStreams && filteredStreams.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = null,
                                            tint = AccentSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Searching add-ons…",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else if (filteredStreams.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .focusRequester(emptyFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No streams found",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).focusGroup(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(filteredStreams, key = { idx, _ -> "stremio_${selectedTabIndex}_$idx" }) { index, stream ->
                                        StremioStreamRow(
                                            stream = stream,
                                            onClick = { onStremioStreamSelected(stream) },
                                            focusRequester = if (index == 0) focusRequester else null
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
private fun SourceTab(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = when {
                isFocused -> AccentPrimary.copy(alpha = 0.25f)
                isActive -> AccentPrimary.copy(alpha = 0.15f)
                else -> SurfaceGlass
            }
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Row(
            modifier = Modifier
                .border(
                    1.dp,
                    if (isFocused || isActive) AccentPrimary.copy(alpha = 0.5f) else SurfaceGlassBorder,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive || isFocused) AccentPrimary else Color.White.copy(alpha = 0.3f))
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontSize = 11.sp
                ),
                color = if (isActive || isFocused) AccentPrimary else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HttpStreamRow(
    stream: HttpStreamResult,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "s"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentSecondary.copy(alpha = 0.12f)
            else Color.Transparent
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentTertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = AccentTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stream.sourceName.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = AccentSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentSecondary.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    if (!stream.quality.isNullOrBlank()) {
                        Text(
                            text = stream.quality.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSeed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GreenSeed.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stream.title,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                if (!stream.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stream.description,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TorrentRow(
    result: TorrentResult,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "s"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentPrimary.copy(alpha = 0.12f)
            else Color.Transparent
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (result.isSeasonPack) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentSecondary.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = AccentSecondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "PACK",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = AccentSecondary
                            )
                        }
                    }
                    Text(
                        text = result.source.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = AccentTertiary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentTertiary.copy(alpha = 0.1f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = result.name,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = result.size,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.width(80.dp)
            )

            Row(
                modifier = Modifier.width(45.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = GreenSeed,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "${result.seeders}",
                    color = GreenSeed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${result.leechers}",
                color = RedLeech.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.width(45.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StremioStreamRow(
    stream: StremioStream,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "s"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentSecondary.copy(alpha = 0.12f)
            else Color.Transparent
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val typeBadge = when {
                        !stream.infoHash.isNullOrBlank() -> Pair("TORRENT", AccentPrimary)
                        stream.url?.startsWith("magnet:", ignoreCase = true) == true -> Pair("TORRENT", AccentPrimary)
                        !stream.ytId.isNullOrBlank() -> Pair("YOUTUBE", Color(0xFFFF4444))
                        !stream.externalUrl.isNullOrBlank() -> Pair("EXTERNAL", AccentTertiary)
                        !stream.playerFrameUrl.isNullOrBlank() -> Pair("EMBED", AccentTertiary)
                        !stream.url.isNullOrBlank() -> Pair("DIRECT", AccentSecondary)
                        else -> Pair("STREAM", AccentSecondary)
                    }
                    Text(
                        text = typeBadge.first,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = typeBadge.second,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeBadge.second.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    if (!stream.addonName.isNullOrBlank()) {
                        Text(
                            text = stream.addonName.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stream.name ?: stream.title ?: stream.addonName ?: "Stream",
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 17.sp
                )
                if (!stream.description.isNullOrBlank() || !stream.title.isNullOrBlank()) {
                    val subtitle = stream.description ?: stream.title
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
