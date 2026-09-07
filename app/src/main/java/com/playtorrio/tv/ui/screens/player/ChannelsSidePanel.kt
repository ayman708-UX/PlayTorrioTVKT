package com.playtorrio.tv.ui.screens.player

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import coil3.compose.AsyncImage
import com.playtorrio.tv.core.iptv.channels.HardcodedChannel
import com.playtorrio.tv.core.iptv.channels.HardcodedChannels
import com.playtorrio.tv.core.iptv.context.IptvChannelContextHolder
import com.playtorrio.tv.core.iptv.context.IptvLiveContext
import com.playtorrio.tv.core.iptv.model.IptvStream
import com.playtorrio.tv.core.iptv.network.IptvAliveChecker
import com.playtorrio.tv.core.iptv.network.IptvClient
import com.playtorrio.tv.core.iptv.storage.IptvStorage
import com.playtorrio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChannelsSidePanel(
    onClose: () -> Unit,
    onSwitchChannel: (streamUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveContext by IptvChannelContextHolder.currentContext.collectAsState()
    val context = LocalContext.current
    val storage = remember { IptvStorage(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedHardcodedCategory by remember { mutableStateOf("All") }
    var resolvingChannelId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .width(480.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF090D16))
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        val title = when (val ctx = liveContext) {
                            is IptvLiveContext.Portal -> ctx.categoryName.ifEmpty { ctx.portal.name }
                            is IptvLiveContext.Hardcoded -> "Live Channels"
                            null -> "Channels"
                        }
                        val subtitle = when (val ctx = liveContext) {
                            is IptvLiveContext.Portal -> "${ctx.portal.name} · ${ctx.channels.size} channels"
                            is IptvLiveContext.Hardcoded -> "${ctx.allChannels.size} channels available"
                            null -> ""
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                color = Color(0x88FFFFFF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter channels…", color = Color(0x66FFFFFF), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF111827),
                    focusedIndicatorColor = Color(0xFF38BDF8),
                    unfocusedIndicatorColor = Color(0x22FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Category Filter for Hardcoded Channels
            if (liveContext is IptvLiveContext.Hardcoded) {
                Spacer(modifier = Modifier.height(8.dp))
                val categories = remember {
                    listOf("All", "Combat", "Premier", "US Sports", "Soccer", "Racing", "Movies", "News", "Arabic", "Discovery", "Kids")
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = cat == selectedHardcodedCategory
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedHardcodedCategory = cat },
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else Color(0xAAFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Channels List
            when (val ctx = liveContext) {
                is IptvLiveContext.Portal -> {
                    val filtered = remember(ctx.channels, searchQuery) {
                        if (searchQuery.isBlank()) ctx.channels else {
                            ctx.channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    LaunchedEffect(Unit) {
                        val activeIdx = filtered.indexOfFirst { it.streamId == ctx.currentStreamId }
                        if (activeIdx >= 0) {
                            listState.scrollToItem(activeIdx)
                            initialFocusRequester.requestFocusAfterFrames(2)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.streamId }) { stream ->
                            val isCurrent = stream.streamId == ctx.currentStreamId
                            PortalChannelRow(
                                stream = stream,
                                isCurrent = isCurrent,
                                focusRequester = if (isCurrent) initialFocusRequester else null,
                                onClick = {
                                    IptvChannelContextHolder.updateCurrentStreamId(stream.streamId)
                                    val streamUrl = IptvClient.streamUrl(ctx.portal.portal, stream)
                                    onSwitchChannel(streamUrl, stream.name)
                                }
                            )
                        }
                    }
                }

                is IptvLiveContext.Hardcoded -> {
                    val filtered = remember(ctx.allChannels, searchQuery, selectedHardcodedCategory) {
                        var list = ctx.allChannels
                        if (selectedHardcodedCategory != "All") {
                            list = list.filter { it.category.equals(selectedHardcodedCategory, ignoreCase = true) }
                        }
                        if (searchQuery.isNotBlank()) {
                            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        list
                    }

                    LaunchedEffect(Unit) {
                        val activeIdx = filtered.indexOfFirst { it.id == ctx.currentChannelId }
                        if (activeIdx >= 0) {
                            listState.scrollToItem(activeIdx)
                            initialFocusRequester.requestFocusAfterFrames(2)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { channel ->
                            val isCurrent = channel.id == ctx.currentChannelId
                            val isResolving = resolvingChannelId == channel.id
                            HardcodedChannelRow(
                                channel = channel,
                                isCurrent = isCurrent,
                                isResolving = isResolving,
                                focusRequester = if (isCurrent) initialFocusRequester else null,
                                onClick = {
                                    if (isResolving) return@HardcodedChannelRow
                                    scope.launch {
                                        resolvingChannelId = channel.id
                                        val cachedHits = storage.loadChannelHits(channel.id)
                                        if (cachedHits.isNotEmpty()) {
                                            val hit = cachedHits.first()
                                            IptvChannelContextHolder.updateCurrentHardcodedId(channel.id)
                                            onSwitchChannel(hit.streamUrl, "${channel.name} (${hit.portal.name})")
                                            resolvingChannelId = null
                                            return@launch
                                        }

                                        // Fallback: quickly check first 5 verified portals
                                        val portals = storage.loadVerifiedPortals().take(5)
                                        var foundUrl: String? = null
                                        var portalName: String = ""
                                        withContext(Dispatchers.IO) {
                                            for (p in portals) {
                                                try {
                                                    val matches = IptvClient.searchChannelInPortal(p.portal, channel)
                                                    for (m in matches) {
                                                        val u = IptvClient.streamUrl(p.portal, m)
                                                        if (u.isNotEmpty() && IptvAliveChecker.isAlive(u)) {
                                                            foundUrl = u
                                                            portalName = p.name
                                                            break
                                                        }
                                                    }
                                                    if (foundUrl != null) break
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        resolvingChannelId = null
                                        if (foundUrl != null) {
                                            IptvChannelContextHolder.updateCurrentHardcodedId(channel.id)
                                            onSwitchChannel(foundUrl!!, "${channel.name} ($portalName)")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No channels loaded", color = Color(0x66FFFFFF), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalChannelRow(
    stream: IptvStream,
    isCurrent: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF1E293B) else if (isCurrent) Color(0xFF0F2236) else Color(0xFF111827)
        ),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF38BDF8)) else if (isCurrent) BorderStroke(1.dp, Color(0xFF0284C7)) else BorderStroke(1.dp, Color(0x11FFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stream.icon.isNotBlank()) {
                AsyncImage(
                    model = stream.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x2238BDF8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stream.containerExt.isNotBlank()) {
                    Text(
                        text = stream.containerExt.uppercase(),
                        color = Color(0x66FFFFFF),
                        fontSize = 10.sp
                    )
                }
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0284C7))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PLAYING",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HardcodedChannelRow(
    channel: HardcodedChannel,
    isCurrent: Boolean,
    isResolving: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF1E293B) else if (isCurrent) Color(0xFF0F2236) else Color(0xFF111827)
        ),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF38BDF8)) else if (isCurrent) BorderStroke(1.dp, Color(0xFF0284C7)) else BorderStroke(1.dp, Color(0x11FFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x2238BDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.category,
                    color = Color(0x66FFFFFF),
                    fontSize = 10.sp
                )
            }

            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.dp
                )
            } else if (isCurrent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0284C7))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PLAYING",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
