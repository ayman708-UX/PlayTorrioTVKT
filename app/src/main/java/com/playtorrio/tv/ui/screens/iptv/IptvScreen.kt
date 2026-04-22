package com.playtorrio.tv.ui.screens.iptv

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.PlayerActivity
import com.playtorrio.tv.data.iptv.IptvClient
import com.playtorrio.tv.data.iptv.IptvSection
import com.playtorrio.tv.data.iptv.IptvStream
import com.playtorrio.tv.data.iptv.VerifiedPortal
import com.playtorrio.tv.data.iptv.HardcodedChannel
import com.playtorrio.tv.data.iptv.HardcodedChannels

// ───────────────────────────────────────────────────────────────────────
// Responsive sizing
// ───────────────────────────────────────────────────────────────────────
// All hardcoded card dimensions multiply by LocalCardScale.current. This
// scales the UI to the actual TV screen width — small TVs / Chromecast
// configurations get smaller cards, big-density TVs stay roughly the same.
private val LocalCardScale = androidx.compose.runtime.compositionLocalOf { 1f }

@Composable
private fun rememberCardScale(): Float {
    val sw = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    // 960dp = baseline (typical Android TV 1080p). Clamp so cards never blow
    // up on giant tablets and never collapse on tiny screens.
    return (sw / 960f).coerceIn(0.65f, 1.0f)
}

// ───────────────────────────────────────────────────────────────────────
// Theme
// ───────────────────────────────────────────────────────────────────────

private val BgTop = Color(0xFF0B0B16)
private val BgBottom = Color(0xFF050509)
private val Surface1 = Color(0xFF15151F)
private val Surface2 = Color(0xFF1F1F2E)
private val Stroke = Color(0xFF2A2A3A)
private val Accent = Color(0xFF8B5CF6)
private val AccentSoft = Color(0xFF22D3EE)
private val Success = Color(0xFF34D399)
private val Danger = Color(0xFFEF4444)
private val TextDim = Color.White.copy(alpha = 0.55f)
private val TextDim2 = Color.White.copy(alpha = 0.38f)

private val LiveGrad = listOf(Color(0xFFEF4444), Color(0xFFF59E0B))
private val MovieGrad = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
private val SeriesGrad = listOf(Color(0xFF14B8A6), Color(0xFF22D3EE))

private fun Modifier.bgGradient() = this.background(
    Brush.verticalGradient(listOf(BgTop, BgBottom))
)

private fun gradFor(s: IptvSection?): List<Color> = when (s) {
    IptvSection.LIVE -> LiveGrad
    IptvSection.VOD -> MovieGrad
    IptvSection.SERIES -> SeriesGrad
    null -> MovieGrad
}

private fun iconFor(s: IptvSection?): ImageVector = when (s) {
    IptvSection.LIVE -> Icons.Filled.LiveTv
    IptvSection.VOD -> Icons.Filled.Movie
    IptvSection.SERIES -> Icons.Filled.Tv
    null -> Icons.Filled.Movie
}

private fun labelFor(s: IptvSection?): String = when (s) {
    IptvSection.LIVE -> "Live TV"
    IptvSection.VOD -> "Movies"
    IptvSection.SERIES -> "Series"
    null -> ""
}

// ───────────────────────────────────────────────────────────────────────
// Root
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IptvScreen(navController: NavController) {
    val vm: IptvViewModel = viewModel()
    val state by vm.ui.collectAsState()
    val cardScale = rememberCardScale()

    androidx.compose.runtime.CompositionLocalProvider(LocalCardScale provides cardScale) {
        Box(
            Modifier
                .fillMaxSize()
                .bgGradient()
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                        if (!vm.back()) navController.popBackStack()
                        true
                    } else false
                }
        ) {
            when (state.view) {
                IptvView.PORTAL_LIST -> PortalListView(state, vm) { navController.popBackStack() }
                IptvView.SECTION_PICK -> SectionPickView(state, vm)
                IptvView.BROWSER -> BrowserView(state, vm)
                IptvView.EPISODE_LIST -> EpisodeListView(state, vm)
                IptvView.CHANNELS_HUB -> ChannelsHubView(state, vm)
                IptvView.CHANNEL_RESULTS -> ChannelResultsView(state, vm)
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Portal list
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PortalListView(state: IptvUiState, vm: IptvViewModel, onBack: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackPill(onBack)
            Spacer(Modifier.width(20.dp))
            Text(
                "IPTV Portals",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Edit toggle (only when we have results)
            if (state.verified.isNotEmpty()) {
                IconPill(
                    icon = if (state.editMode) Icons.Filled.Done else Icons.Filled.Edit,
                    selected = state.editMode,
                    onClick = { vm.toggleEditMode() },
                )
                Spacer(Modifier.width(10.dp))
            }
            // Manual add
            IconPill(
                icon = Icons.Filled.Add,
                selected = false,
                onClick = { vm.openAddDialog() },
            )
            Spacer(Modifier.width(10.dp))
            // Channels hub (hardcoded sports channels across portals)
            IconPill(
                icon = Icons.Filled.Tv,
                selected = false,
                onClick = { vm.openChannelsHub() },
            )
            Spacer(Modifier.width(10.dp))
            ScrapeButton(
                isScraping = state.isScraping,
                onClick = { vm.scrape() },
                modifier = Modifier.focusRequester(firstFocus),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Status / edit-mode bar
        when {
            state.editMode -> EditModeBar(
                count = state.selected.size,
                total = state.verified.size,
                onDelete = { vm.deleteSelected() },
                onSelectAll = { vm.toggleSelectAll() },
                onCancel = { vm.toggleEditMode() },
            )
            state.statusText.isNotEmpty() -> StatusBar(state)
        }

        if (state.editMode || state.statusText.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
        }

        if (state.verified.isEmpty() && !state.isScraping && state.statusText.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.LiveTv,
                title = "No portals yet",
                hint = "Press Scrape to discover free IPTV portals",
            )
        } else if (state.verified.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.verified) { portal ->
                    val key = "${portal.portal.url}|${portal.portal.username}|${portal.portal.password}".lowercase()
                    PortalCard(
                        portal = portal,
                        editMode = state.editMode,
                        selected = key in state.selected,
                        onClick = {
                            if (state.editMode) vm.toggleSelect(portal)
                            else vm.openPortal(portal)
                        },
                    )
                }
                if (!state.editMode) {
                    item {
                        GetMoreCard(
                            isLoading = state.isScraping,
                            onClick = { vm.getMore() },
                        )
                    }
                }
            }
        }
    }

    if (state.showAddDialog) {
        AddPortalDialog(
            isAdding = state.isAdding,
            error = state.addError,
            onDismiss = { vm.dismissAddDialog() },
            onSubmit = { url, user, pass -> vm.addManual(url, user, pass) },
        )
    }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusBar(state: IptvUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isScraping) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.verified.isEmpty()) TextDim2 else Success),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(state.statusText, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EditModeBar(
    count: Int,
    total: Int,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
) {
    val allSelected = count > 0 && count >= total
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Edit,
            contentDescription = null,
            tint = Danger,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (count == 0) "Select portals to delete"
            else "$count selected",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (total > 0) {
            GhostButton(
                if (allSelected) "Deselect all" else "Select all",
                onClick = onSelectAll,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (count > 0) {
            DangerButton("Delete", onClick = onDelete)
            Spacer(Modifier.width(8.dp))
        }
        GhostButton("Cancel", onClick = onCancel)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddPortalDialog(
    isAdding: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val urlFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
    ) {
        Box(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, Stroke, RoundedCornerShape(20.dp))
                .padding(28.dp)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown &&
                        (ev.key == Key.Back || ev.key == Key.Escape)
                    ) {
                        if (!isAdding) onDismiss()
                        true
                    } else false
                }
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Accent, AccentSoft))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Add IPTV Portal",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Enter your Xtream-Codes credentials",
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                DialogField(
                    label = "Portal URL",
                    placeholder = "http://example.com:8080",
                    value = url,
                    onChange = { url = it },
                    enabled = !isAdding,
                    focusRequester = urlFocus,
                    onDpadDown = { runCatching { userFocus.requestFocus() } },
                    onDpadUp = null,
                )
                Spacer(Modifier.height(12.dp))
                DialogField(
                    label = "Username",
                    placeholder = "username",
                    value = username,
                    onChange = { username = it },
                    enabled = !isAdding,
                    focusRequester = userFocus,
                    onDpadDown = { runCatching { passFocus.requestFocus() } },
                    onDpadUp = { runCatching { urlFocus.requestFocus() } },
                )
                Spacer(Modifier.height(12.dp))
                DialogField(
                    label = "Password",
                    placeholder = "password",
                    value = password,
                    onChange = { password = it },
                    enabled = !isAdding,
                    isPassword = true,
                    focusRequester = passFocus,
                    onDpadDown = { runCatching { addFocus.requestFocus() } },
                    onDpadUp = { runCatching { userFocus.requestFocus() } },
                )

                if (!error.isNullOrEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Danger.copy(alpha = 0.12f))
                            .border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = Danger,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            color = Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Verifying…",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    GhostButton(
                        "Cancel",
                        onClick = { if (!isAdding) onDismiss() },
                        modifier = Modifier
                            .focusRequester(cancelFocus)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                    runCatching { passFocus.requestFocus() }; true
                                } else false
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(
                        label = if (isAdding) "Adding…" else "Add",
                        enabled = !isAdding,
                        onClick = { onSubmit(url, username, password) },
                        modifier = Modifier
                            .focusRequester(addFocus)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                    runCatching { passFocus.requestFocus() }; true
                                } else false
                            },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null,
    onDpadDown: (() -> Unit)? = null,
    onDpadUp: (() -> Unit)? = null,
) {
    Column {
        Text(
            label.uppercase(),
            color = TextDim2,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        var focused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            enabled = enabled,
            placeholder = {
                androidx.compose.material3.Text(placeholder, color = TextDim2, fontSize = 13.sp)
            },
            visualTransformation = if (isPassword)
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = if (isPassword)
                    androidx.compose.ui.text.input.KeyboardType.Password
                else androidx.compose.ui.text.input.KeyboardType.Text,
            ),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Stroke,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                cursorColor = Accent,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> {
                            if (onDpadDown != null) { onDpadDown(); true } else false
                        }
                        Key.DirectionUp -> {
                            if (onDpadUp != null) { onDpadUp(); true } else false
                        }
                        else -> false
                    }
                },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(140), label = "s")
    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Box(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        if (focused && enabled) listOf(Color(0xFFA855F7), Color(0xFF22D3EE))
                        else if (enabled) listOf(Accent, Accent.copy(alpha = 0.85f))
                        else listOf(Surface2, Surface2)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                label,
                color = if (enabled) Color.White else TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScrapeButton(
    isScraping: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(160), label = "s")
    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        if (focused) listOf(Color(0xFFA855F7), Color(0xFF22D3EE))
                        else listOf(Accent, Accent.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isScraping) "Scraping…" else "Scrape",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconPill(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(140), label = "s")
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = when {
                focused -> Accent
                selected -> Accent.copy(alpha = 0.4f)
                else -> Surface2
            },
        ),
        shape = CardDefaults.shape(CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (focused) Danger else Danger.copy(alpha = 0.85f),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (focused) Surface2 else Color.Transparent,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Box(
            Modifier
                .border(1.dp, Stroke, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PortalCard(
    portal: VerifiedPortal,
    editMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(160), label = "s")
    val host = portal.portal.url.removePrefix("http://").removePrefix("https://").trimEnd('/')
    val initial = (portal.name.firstOrNull() ?: host.firstOrNull() ?: '?').uppercaseChar()

    val borderColor = when {
        editMode && selected -> Danger
        focused -> Accent
        else -> Stroke
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        when {
                            editMode && selected -> listOf(
                                Color(0xFF3F1D1D), Color(0xFF1F1F2E)
                            )
                            focused -> listOf(Color(0xFF2A1F4F), Color(0xFF1F1F2E))
                            else -> listOf(Surface2, Surface1)
                        }
                    )
                )
                .border(
                    width = if (focused || (editMode && selected)) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editMode) {
                        SelectionDot(selected = selected)
                        Spacer(Modifier.width(14.dp))
                    }
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Accent, AccentSoft))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initial.toString(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            portal.name.ifBlank { "User" },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            host,
                            color = TextDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!editMode) StatusPill()
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(Icons.Filled.Schedule, portal.expiry.ifBlank { "—" })
                    Spacer(Modifier.width(8.dp))
                    InfoChip(
                        Icons.Filled.LiveTv,
                        "${portal.activeConnections}/${portal.maxConnections}",
                    )
                    Spacer(Modifier.weight(1f))
                    if (!editMode) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (focused) Accent else TextDim,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) Danger else Color.Transparent)
            .border(2.dp, if (selected) Danger else TextDim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Done,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GetMoreCard(isLoading: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(160), label = "s")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    if (focused) Brush.linearGradient(listOf(Color(0xFF2A1F4F), Surface1))
                    else Brush.linearGradient(listOf(Surface1, Surface1))
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Accent else Stroke,
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Accent, strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isLoading) "Testing more…" else "Get More",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Test up to 5 more portals",
                    color = TextDim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusPill() {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Success.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("ACTIVE", color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoChip(icon: ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextDim, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

// ───────────────────────────────────────────────────────────────────────
// Section pick
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionPickView(state: IptvUiState, vm: IptvViewModel) {
    val portal = state.activePortal ?: return
    val firstFocus = remember { FocusRequester() }
    val host = portal.portal.url.removePrefix("http://").removePrefix("https://").trimEnd('/')
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp)
    ) {
        Header(title = portal.name.ifBlank { "Portal" }, subtitle = host, onBack = { vm.back() })
        Spacer(Modifier.height(40.dp))
        Text(
            "Choose a category",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeroTile(
                icon = Icons.Filled.LiveTv,
                label = "LIVE TV",
                hint = "Channels",
                gradient = LiveGrad,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(firstFocus),
                onClick = { vm.openSection(IptvSection.LIVE) },
            )
            HeroTile(
                icon = Icons.Filled.Movie,
                label = "MOVIES",
                hint = "VOD library",
                gradient = MovieGrad,
                modifier = Modifier.weight(1f),
                onClick = { vm.openSection(IptvSection.VOD) },
            )
            HeroTile(
                icon = Icons.Filled.Tv,
                label = "SERIES",
                hint = "TV shows",
                gradient = SeriesGrad,
                modifier = Modifier.weight(1f),
                onClick = { vm.openSection(IptvSection.SERIES) },
            )
        }
    }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroTile(
    icon: ImageVector,
    label: String,
    hint: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(180), label = "s")
    Card(
        onClick = onClick,
        modifier = modifier
            .height(220.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(24.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    hint,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Browser — sidebar (categories + search) + content pane (live/vod/series)
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveOnlyBar(
    state: IptvUiState,
    vm: IptvViewModel,
    toggleFocus: FocusRequester? = null,
) {
    val hasCache = state.aliveCheckedAt > 0L
    val verifying = state.isVerifyingAlive
    val cachedAgo = if (hasCache) formatAgo(state.aliveCheckedAt) else ""

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Toggle pill
        ToggleChip(
            checked = state.liveOnly,
            label = "Alive only",
            onChange = { vm.setLiveOnly(it) },
            focusRequester = toggleFocus,
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            val title = when {
                verifying -> "Verifying channels…"
                state.liveOnly && hasCache ->
                    "${state.aliveCount} alive of ${state.browserAllStreams.size}"
                state.liveOnly && !hasCache ->
                    "Tap Verify to scan channels"
                else -> "Show only alive channels"
            }
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val sub = when {
                verifying -> {
                    val n = state.aliveChecked
                    val t = state.aliveTotal.coerceAtLeast(1)
                    "Checked $n / $t · ${state.aliveCount} alive · this may take a few minutes"
                }
                state.liveOnly && hasCache -> "Cached $cachedAgo · tap Recheck to refresh"
                state.liveOnly && !hasCache ->
                    "Probes each channel’s stream — may take a few minutes the first time."
                else -> "Toggle to filter out dead channels (cached per portal)."
            }
            Text(sub, color = TextDim, fontSize = 11.sp)
        }

        Spacer(Modifier.width(12.dp))

        if (verifying) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            GhostButton(
                label = "Stop",
                onClick = { vm.stopAliveCheck() },
            )
        } else if (state.liveOnly) {
            GhostButton(
                label = if (hasCache) "Recheck" else "Verify",
                onClick = { vm.recheckAlive() },
            )
        }
    }
}

private fun formatAgo(ts: Long): String {
    if (ts <= 0L) return "never"
    val mins = ((System.currentTimeMillis() - ts) / 60_000L).coerceAtLeast(0L)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleChip(
    checked: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = { onChange(!checked) },
        modifier = Modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
    ) {
        Row(
            Modifier
                .background(
                    if (checked) Brush.horizontalGradient(listOf(Accent, AccentSoft))
                    else Brush.horizontalGradient(listOf(Surface2, Surface2))
                )
                .border(
                    1.dp,
                    if (focused) Color.White.copy(alpha = 0.7f) else Stroke,
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowserView(state: IptvUiState, vm: IptvViewModel) {
    val portal = state.activePortal ?: return
    val ctx = LocalContext.current
    val section = state.activeSection
    val accent = gradFor(section).first()
    val sidebarFirstFocus = remember { FocusRequester() }
    val liveOnlyFocus = remember { FocusRequester() }

    val filtered = remember(
        state.browserAllStreams, state.browserSelectedCategoryId, state.browserSearch,
        state.liveOnly, state.aliveStreamIds, state.isVerifyingAlive,
    ) {
        val q = state.browserSearch.trim().lowercase()
        val base = if (q.isNotEmpty()) {
            // Search overrides category filter — search across all.
            state.browserAllStreams.filter { it.name.lowercase().contains(q) }
        } else if (state.browserSelectedCategoryId == LIVE_ALL_CATEGORY_ID) {
            state.browserAllStreams
        } else {
            state.browserAllStreams.filter { it.categoryId == state.browserSelectedCategoryId }
        }
        // Live-only filter (LIVE section). While verifying we progressively
        // reveal the streams that have been confirmed alive.
        if (section == IptvSection.LIVE && state.liveOnly &&
            (state.aliveCheckedAt > 0L || state.isVerifyingAlive)
        ) {
            base.filter { it.streamId in state.aliveStreamIds }
        } else base
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 32.dp)
    ) {
        SectionHeader(
            title = labelFor(section),
            subtitle = "${state.browserAllStreams.size} items in ${state.categories.size} categories",
            gradient = gradFor(section),
            icon = iconFor(section),
            onBack = { vm.back() },
        )
        if (section == IptvSection.LIVE) {
            Spacer(Modifier.height(14.dp))
            LiveOnlyBar(state = state, vm = vm, toggleFocus = liveOnlyFocus)
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxSize()) {
            // ── Sidebar ─────────────────────────────────────────────
            Column(
                Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Stroke, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                SearchField(
                    value = state.browserSearch,
                    onChange = { vm.setBrowserSearch(it) },
                    accent = accent,
                    placeholder = when (section) {
                        IptvSection.LIVE -> "Search channels…"
                        IptvSection.VOD -> "Search movies…"
                        IptvSection.SERIES -> "Search shows…"
                        null -> "Search…"
                    },
                    onEscape = { runCatching { sidebarFirstFocus.requestFocus() } },
                    onUp = if (section == IptvSection.LIVE) {
                        { runCatching { liveOnlyFocus.requestFocus() } }
                    } else null,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "CATEGORIES",
                    color = TextDim2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    val aliveActive = section == IptvSection.LIVE && state.liveOnly &&
                        (state.aliveCheckedAt > 0L || state.isVerifyingAlive)
                    val countingStreams = if (aliveActive) {
                        state.browserAllStreams.filter { it.streamId in state.aliveStreamIds }
                    } else state.browserAllStreams
                    item {
                        SidebarCategory(
                            name = "All",
                            count = countingStreams.size,
                            selected = state.browserSelectedCategoryId == LIVE_ALL_CATEGORY_ID,
                            accent = accent,
                            onClick = { vm.selectBrowserCategory(LIVE_ALL_CATEGORY_ID) },
                            modifier = Modifier.focusRequester(sidebarFirstFocus),
                        )
                    }
                    items(state.categories) { c ->
                        val count = remember(c.id, countingStreams) {
                            countingStreams.count { it.categoryId == c.id }
                        }
                        SidebarCategory(
                            name = c.name,
                            count = count,
                            selected = state.browserSelectedCategoryId == c.id,
                            accent = accent,
                            onClick = { vm.selectBrowserCategory(c.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.width(20.dp))

            // ── Content pane ──────────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (section == IptvSection.LIVE) {
                            Modifier.focusProperties { up = liveOnlyFocus }
                        } else Modifier
                    )
            ) {
                if (state.browserSearch.isNotEmpty()) {
                    Text(
                        "Search results · ${filtered.size}",
                        color = TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                    )
                }
                when {
                    state.isLoading && state.browserAllStreams.isEmpty() -> CenterSpinner()
                    filtered.isEmpty() -> EmptyState(
                        iconFor(section),
                        if (state.browserSearch.isNotEmpty()) "No matches" else "Empty",
                        if (state.browserSearch.isNotEmpty())
                            "Nothing matches \"${state.browserSearch}\"."
                        else state.error ?: "Nothing here.",
                    )
                    section == IptvSection.LIVE -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        itemsIndexed(filtered) { index, s ->
                            LiveChannelRow(s, index + 1, accent) {
                                playStream(ctx, portal, s)
                            }
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive((140.dp * LocalCardScale.current).coerceAtLeast(110.dp)),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(end = 4.dp, bottom = 8.dp),
                    ) {
                        items(filtered) { s ->
                            PosterTile(s, accent) {
                                if (section == IptvSection.SERIES) vm.openSeries(s)
                                else playStream(ctx, portal, s)
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
private fun SidebarCategory(
    name: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = focused || selected
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    when {
                        focused -> accent.copy(alpha = 0.18f)
                        selected -> Color.White.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (highlight) accent else Color.Transparent),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                color = if (highlight) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                count.toString(),
                color = if (highlight) accent else TextDim2,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    accent: Color,
    placeholder: String,
    onEscape: () -> Unit,
    onUp: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = {
            androidx.compose.material3.Text(
                placeholder,
                color = TextDim2,
                fontSize = 13.sp,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) accent else TextDim,
                modifier = Modifier.size(18.dp),
            )
        },
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onEscape()
        }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Stroke,
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            cursorColor = accent,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> {
                        if (onUp != null) {
                            focusManager.clearFocus()
                            onUp(); true
                        } else false
                    }
                    Key.DirectionDown,
                    Key.Tab,
                    Key.Back,
                    Key.Escape,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.DirectionCenter -> {
                        focusManager.clearFocus()
                        onEscape()
                        true
                    }
                    else -> false
                }
            },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveChannelRow(
    s: IptvStream,
    number: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Surface2 else Surface1)
                .border(
                    width = 1.dp,
                    color = if (focused) accent else Stroke,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    number.toString().padStart(3, '0'),
                    color = if (focused) accent else TextDim2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (s.icon.isNotEmpty()) {
                    AsyncImage(
                        model = s.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        Icons.Filled.LiveTv,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                s.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (focused) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterTile(s: IptvStream, accent: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val cardScale = LocalCardScale.current
    val w = (160.dp * cardScale).coerceAtLeast(120.dp)
    val h = (230.dp * cardScale).coerceAtLeast(170.dp)
    Column(
        Modifier.width(w)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .onFocusChanged { focused = it.isFocused },
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Surface1)
                    .border(
                        width = if (focused) 3.dp else 1.dp,
                        color = if (focused) accent else Stroke,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (s.icon.isNotEmpty()) {
                    AsyncImage(
                        model = s.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        tint = TextDim2,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            s.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

// ───────────────────────────────────────────────────────────────────────
// Episode list
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeListView(state: IptvUiState, vm: IptvViewModel) {
    val portal = state.activePortal ?: return
    val series = state.activeSeries ?: return
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp)
    ) {
        SectionHeader(
            title = series.name,
            subtitle = "${state.episodes.size} episodes",
            gradient = SeriesGrad,
            icon = Icons.Filled.Tv,
            onBack = { vm.back() },
        )
        Spacer(Modifier.height(24.dp))
        when {
            state.isLoading -> CenterSpinner()
            state.episodes.isEmpty() ->
                EmptyState(Icons.Filled.Tv, "No episodes", state.error ?: "")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.episodes) { e ->
                    EpisodeRow(
                        season = e.season,
                        episode = e.episode,
                        title = e.title,
                        accent = SeriesGrad.first(),
                        onClick = {
                            val url = IptvClient.episodeUrl(portal.portal, e)
                            launchPlayer(ctx, url, "${series.name} – ${e.title}")
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeRow(
    season: Int,
    episode: Int,
    title: String,
    accent: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.01f else 1f, tween(140), label = "s")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) Surface2 else Surface1)
                .border(
                    width = 1.dp,
                    color = if (focused) accent else Stroke,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "S${season.toString().padStart(2, '0')}·E${episode.toString().padStart(2, '0')}",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (focused) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Reusable bits
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackPill(onBack)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    icon: ImageVector,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackPill(onBack)
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, color = TextDim, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BackPill(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "s")
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (focused) Accent else Surface2,
        ),
        shape = CardDefaults.shape(CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CenterSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Accent, strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, Stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = TextDim, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (hint.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(hint, color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Channels hub (curated sports/news channels across all portals)
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelsHubView(state: IptvUiState, vm: IptvViewModel) {
    val firstFocus = remember { FocusRequester() }
    var hasFocusedOnce by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val accent = Color(0xFF7C3AED)

    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) HardcodedChannels.all
        else HardcodedChannels.all.filter {
            it.name.lowercase().contains(q) || it.short.lowercase().contains(q)
        }
    }

    // Focus the first tile only on first composition. Re-running this on every
    // keystroke (which used to be the case via filtered.firstOrNull()?.id) was
    // stealing focus away from the search field and dismissing the keyboard.
    LaunchedEffect(Unit) {
        if (!hasFocusedOnce && filtered.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
            hasFocusedOnce = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackPill(onClick = { vm.back() })
            Spacer(Modifier.width(20.dp))
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF22D3EE))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Channels",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Pick a channel — we’ll search across your portals and verify alive streams.",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SearchField(
            value = query,
            onChange = { query = it },
            accent = accent,
            placeholder = "Search channels (e.g. boxing, espn, mbc)",
            onEscape = { runCatching { firstFocus.requestFocus() } },
        )

        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Filled.Search,
                "No channels match",
                "Try a different keyword.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive((150.dp * LocalCardScale.current).coerceAtLeast(120.dp)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 4.dp, bottom = 8.dp),
            ) {
                items(filtered, key = { it.id }) { channel ->
                    val isFirst = channel.id == filtered.first().id
                    ChannelTile(
                        channel = channel,
                        modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { vm.openHardcodedChannel(channel) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelTile(
    channel: HardcodedChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardScale = LocalCardScale.current
    Card(
        onClick = onClick,
        modifier = modifier
            .height((96.dp * cardScale).coerceAtLeast(72.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(
                    2.dp,
                    Color.White,
                    RoundedCornerShape(16.dp),
                ) else Modifier
            ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(channel.gradient)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (focused) 0.10f else 0.25f))
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    channel.short,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    channel.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Channel results (alive streams across all portals)
// ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelResultsView(state: IptvUiState, vm: IptvViewModel) {
    val ctx = LocalContext.current
    val channel = state.activeHardcoded ?: return
    val firstFocus = remember { FocusRequester() }
    var hasFocusedOnce by remember(channel.id) { mutableStateOf(false) }

    var query by rememberSaveable(channel.id) { mutableStateOf("") }
    var selectMode by remember(channel.id) { mutableStateOf(false) }
    var selected by remember(channel.id) { mutableStateOf(emptySet<String>()) }
    val actionFocus = remember(channel.id) { FocusRequester() }

    val filteredResults = remember(state.channelResults, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.channelResults
        else state.channelResults.filter { it.stream.name.lowercase().contains(q) }
    }

    LaunchedEffect(channel.id, filteredResults.isNotEmpty()) {
        if (!hasFocusedOnce && filteredResults.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
            hasFocusedOnce = true
        }
    }

    val accent = channel.gradient.first()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp)
    ) {
        SectionHeader(
            title = channel.name,
            subtitle = state.channelStatus,
            gradient = channel.gradient,
            icon = Icons.Filled.Tv,
            onBack = { vm.back() },
        )

        Spacer(Modifier.height(16.dp))

        // Status bar with selection / Stop / Search again
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor = if (state.channelIsRunning) Color(0xFF22C55E) else Color(0xFF64748B)
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    selectMode -> "${selected.size} selected"
                    state.channelIsRunning ->
                        "Searching · ${state.channelResults.size} alive"
                    state.channelResults.isEmpty() -> "Done · 0 alive"
                    else -> "${state.channelResults.size} alive"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (selectMode) {
                val allUrls = filteredResults.map { it.streamUrl }.toSet()
                val allSelected = allUrls.isNotEmpty() && selected.containsAll(allUrls)
                GhostButton(
                    label = if (allSelected) "Clear all" else "Select all",
                    onClick = {
                        selected = if (allSelected) selected - allUrls else selected + allUrls
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (selected.isNotEmpty()) {
                    DangerButton(label = "Delete (${selected.size})") {
                        vm.deleteChannelHits(channel, selected)
                        selected = emptySet()
                        selectMode = false
                    }
                    Spacer(Modifier.width(8.dp))
                }
                GhostButton(label = "Cancel", onClick = {
                    selectMode = false
                    selected = emptySet()
                })
            } else {
                if (state.channelResults.isNotEmpty()) {
                    GhostButton(label = "Select", onClick = { selectMode = true })
                    Spacer(Modifier.width(8.dp))
                }
                if (state.channelIsRunning) {
                    GhostButton(
                        label = "Stop",
                        onClick = { vm.stopChannelSearch() },
                        modifier = Modifier.focusRequester(actionFocus),
                    )
                } else {
                    GhostButton(
                        label = "Search again",
                        onClick = { vm.searchAgainChannel(channel) },
                        modifier = Modifier.focusRequester(actionFocus),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.channelResults.isNotEmpty()) {
            SearchField(
                value = query,
                onChange = { query = it },
                accent = accent,
                placeholder = "Search results (e.g. hd, 1080, sport 2)",
                onEscape = { runCatching { firstFocus.requestFocus() } },
                onUp = { runCatching { actionFocus.requestFocus() } },
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.channelResults.isEmpty() && state.channelIsRunning -> CenterSpinner()
            state.channelResults.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        Icons.Filled.Tv,
                        "No alive streams",
                        state.channelStatus.ifEmpty { "Try Get more channels below." },
                    )
                    Spacer(Modifier.height(16.dp))
                    GetMoreChannelsButton(
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { vm.getMoreChannels(channel) },
                    )
                }
            }
            filteredResults.isEmpty() -> {
                EmptyState(
                    Icons.Filled.Search,
                    "No results match",
                    "Try a different keyword.",
                )
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 4.dp, bottom = 8.dp),
            ) {
                itemsIndexed(
                    filteredResults,
                    key = { _, h -> h.streamUrl },
                ) { index, hit ->
                    val isSelected = hit.streamUrl in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            LiveChannelRow(
                                s = hit.stream,
                                number = index + 1,
                                accent = if (selectMode && isSelected) Danger else accent,
                                modifier = if (index == 0)
                                    Modifier.focusRequester(firstFocus)
                                else Modifier,
                                onClick = {
                                    if (selectMode) {
                                        selected = if (isSelected) selected - hit.streamUrl
                                        else selected + hit.streamUrl
                                    } else {
                                        launchPlayer(ctx, hit.streamUrl, hit.stream.name)
                                    }
                                },
                            )
                        }
                        if (selectMode) {
                            IconPill(
                                icon = if (isSelected) Icons.Filled.CheckCircle
                                else Icons.Filled.Add,
                                selected = isSelected,
                                onClick = {
                                    selected = if (isSelected) selected - hit.streamUrl
                                    else selected + hit.streamUrl
                                },
                            )
                        } else {
                            IconPill(
                                icon = Icons.Filled.Delete,
                                selected = false,
                                onClick = { vm.deleteChannelHit(channel, hit) },
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    GetMoreChannelsButton(
                        onClick = { vm.getMoreChannels(channel) },
                        enabled = !state.channelIsRunning,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GetMoreChannelsButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(140), label = "s")
    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (enabled) Color(0xFF1F2937) else Color(0xFF111827),
            focusedContainerColor = Color(0xFF7C3AED),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (enabled) "Get more channels" else "Searching…",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// Playback
// ───────────────────────────────────────────────────────────────────────

private fun playStream(
    ctx: android.content.Context,
    portal: VerifiedPortal,
    s: IptvStream,
) {
    val url = IptvClient.streamUrl(portal.portal, s)
    if (url.isNotEmpty()) launchPlayer(ctx, url, s.name)
}

private fun launchPlayer(ctx: android.content.Context, streamUrl: String, title: String) {
    val intent = Intent(ctx, PlayerActivity::class.java).apply {
        putExtra("streamUrl", streamUrl)
        putExtra("title", title)
        putExtra("isMovie", true)
        putExtra("isIptv", true)
    }
    ctx.startActivity(intent)
}
