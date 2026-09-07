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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.playtorrio.tv.core.iptv.channels.HardcodedChannel
import com.playtorrio.tv.core.iptv.model.ChannelHit

@Composable
fun IptvChannelSourcesDialog(
    viewModel: IptvViewModel,
    channel: HardcodedChannel,
    onDismiss: () -> Unit,
    onPlayHit: (ChannelHit) -> Unit
) {
    val hits by viewModel.channelHits.collectAsState()
    val isScanning by viewModel.isScanningChannel.collectAsState()
    val statusText by viewModel.channelScanStatus.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var isManageMode by remember { mutableStateOf(false) }
    var selectedStreamUrls by remember { mutableStateOf(setOf<String>()) }

    val filteredHits = remember(hits, searchQuery) {
        if (searchQuery.isBlank()) hits else {
            hits.filter {
                it.stream.name.contains(searchQuery, ignoreCase = true) ||
                it.portal.name.contains(searchQuery, ignoreCase = true) ||
                it.stream.containerExt.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val allSelected = filteredHits.isNotEmpty() && selectedStreamUrls.size == filteredHits.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD9000000))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0x3338BDF8))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!channel.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                            Column {
                                Text(
                                    text = if (isManageMode) "Manage ${channel.name} Feeds (${selectedStreamUrls.size} selected)" else channel.name,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isManageMode) "Select feeds to delete from channel cache" else if (isScanning) statusText else "${hits.size} live stream feeds available",
                                    color = if (isScanning) Color(0xFF38BDF8) else Color(0x99FFFFFF),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isManageMode) {
                                Button(
                                    onClick = {
                                        selectedStreamUrls = if (allSelected) {
                                            emptySet()
                                        } else {
                                            filteredHits.map { it.streamUrl }.toSet()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (allSelected) "Deselect All" else "Select All", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.deleteChannelHits(channel.id, selectedStreamUrls)
                                        selectedStreamUrls = emptySet()
                                        isManageMode = false
                                    },
                                    enabled = selectedStreamUrls.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        disabledContainerColor = Color(0x33DC2626)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete (${selectedStreamUrls.size})", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        isManageMode = false
                                        selectedStreamUrls = emptySet()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Done", fontSize = 13.sp)
                                }
                            } else {
                                if (filteredHits.isNotEmpty()) {
                                    Button(
                                        onClick = { isManageMode = true },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Manage", fontSize = 12.sp)
                                    }
                                }

                                if (isScanning) {
                                    Button(
                                        onClick = { viewModel.stopChannelScan() },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.scanChannel(channel, resetAttempted = false) },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan More", fontSize = 12.sp)
                                    }

                                    IconButton(onClick = { viewModel.scanChannel(channel, resetAttempted = true) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Rescan",
                                            tint = Color.White
                                        )
                                    }
                                }

                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // TV-Native Search Bar
                    TvIptvSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search ${channel.name} feeds by quality, portal, or tag...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Scanning Status Banner (Non-blocking)
                    if (isScanning && !isManageMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2238BDF8))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = statusText,
                                color = Color(0xFFBAE6FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Vertically Stacked Hits List (Always Interactive)
                    if (filteredHits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Scanning active portals in parallel...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Feeds will populate below automatically as verified",
                                        color = Color(0x66FFFFFF),
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No feeds match '$searchQuery'" else "No active feeds found. Click refresh to rescrape portals.",
                                    color = Color(0xAAFFFFFF),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredHits, key = { it.streamUrl }) { hit ->
                                val isSelected = selectedStreamUrls.contains(hit.streamUrl)
                                ChannelHitRow(
                                    hit = hit,
                                    isManageMode = isManageMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isManageMode) {
                                            selectedStreamUrls = if (isSelected) {
                                                selectedStreamUrls - hit.streamUrl
                                            } else {
                                                selectedStreamUrls + hit.streamUrl
                                            }
                                        } else {
                                            onPlayHit(hit)
                                            onDismiss()
                                        }
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

@Composable
fun ChannelHitRow(
    hit: ChannelHit,
    isManageMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF1E3A8A) else if (isFocused) Color(0xFF1E293B) else Color(0xFF131C2E),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF38BDF8)) else if (isFocused) BorderStroke(2.dp, Color(0xFF38BDF8)) else BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF38BDF8) else Color(0x3338BDF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Add else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isSelected) Color.Black else Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = hit.stream.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Portal: ${hit.portal.name} • ${hit.stream.containerExt.uppercase()}",
                        color = Color(0x88FFFFFF),
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isManageMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF38BDF8) else Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x3310B981))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ALIVE",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
