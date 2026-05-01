package com.playtorrio.tv.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.playtorrio.tv.R
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.torrent.TorrServerService
import com.playtorrio.tv.i18n.AppLocaleManager
import com.playtorrio.tv.data.stremio.InstalledAddon
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.server.DeviceIpAddress
import com.playtorrio.tv.server.QrCodeGenerator
import com.playtorrio.tv.server.SettingsConfigServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var appLanguage by remember { mutableStateOf(AppLocaleManager.getAppLanguage(context)) }

    // ── Addon state ──────────────────────────────────────────────────────────
    var addons by remember { mutableStateOf(StremioAddonRepository.getAddons()) }
    var addonUrl by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf<String?>(null) }

    // ── Phone-remote server state ─────────────────────────────────────────────
    var server by remember { mutableStateOf<SettingsConfigServer?>(null) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingChange by remember { mutableStateOf<SettingsConfigServer.PendingChange?>(null) }
    var isApplyingRemote by remember { mutableStateOf(false) }
    val confirmFocusRequester = remember { FocusRequester() }

    // Start server when screen opens, stop on dispose
    DisposableEffect(Unit) {
        val ip = DeviceIpAddress.get(context)
        val srv = SettingsConfigServer.startOnAvailablePort(
            stateProvider = {
                SettingsConfigServer.SettingsState(
                    addons = StremioAddonRepository.getAddons().map { installed ->
                        SettingsConfigServer.AddonInfo(
                            url = installed.transportUrl,
                            name = installed.manifest.name,
                            description = installed.manifest.description
                        )
                    },
                    streamingMode = AppPreferences.streamingMode,
                    debridEnabled = AppPreferences.debridEnabled,
                    debridProvider = AppPreferences.debridProvider,
                    realDebridApiKey = AppPreferences.realDebridApiKey,
                    torboxApiKey = AppPreferences.torboxApiKey,
                    torrentPreset = AppPreferences.torrentPreset,
                    torrentCacheSizeMb = AppPreferences.torrentCacheSizeMb,
                    torrentPreloadPercent = AppPreferences.torrentPreloadPercent,
                    torrentReadAheadPercent = AppPreferences.torrentReadAheadPercent,
                    torrentConnectionsLimit = AppPreferences.torrentConnectionsLimit,
                    torrentResponsiveMode = AppPreferences.torrentResponsiveMode,
                    torrentDisableUpload = AppPreferences.torrentDisableUpload,
                    torrentDisableIpv6 = AppPreferences.torrentDisableIpv6,
                    trailerAutoplay = AppPreferences.trailerAutoplay,
                    trailerDelaySec = AppPreferences.trailerDelaySec,
                    streamingSourceOrder = AppPreferences.streamingSourceOrder,
                    streamingExtractTimeoutSec = AppPreferences.streamingExtractTimeoutSec,
                    availableSources = com.playtorrio.tv.data.streaming.StreamExtractorService.SOURCES.map {
                        SettingsConfigServer.SourceInfo(index = it.index, name = it.name)
                    }
                )
            },
            onChangeProposed = { change ->
                pendingChange = change
            }
        )
        server = srv
        if (srv != null && ip != null) {
            val listeningPort = srv.listeningPort
            val url = "http://$ip:$listeningPort"
            serverUrl = url
            scope.launch(Dispatchers.Default) {
                val bmp = QrCodeGenerator.generate(url, 400)
                withContext(Dispatchers.Main) { qrBitmap = bmp }
            }
        }
        onDispose { srv?.stop() }
    }

    // When a pending change arrives, request focus on the confirm button
    LaunchedEffect(pendingChange) {
        if (pendingChange != null) {
            kotlinx.coroutines.delay(80)
            try { confirmFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    if (pendingChange != null) {
                        server?.rejectChange(pendingChange!!.id)
                        pendingChange = null
                        true
                    } else {
                        navController.popBackStack()
                        true
                    }
                } else false
            }
    ) {
        // ── Main content: settings left, QR right ────────────────────────────
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT — scrollable settings
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 64.dp, vertical = 48.dp)
            ) {
            // Title
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 2.sp
                ),
                color = Color.White
            )

            Spacer(Modifier.height(6.dp))

            // Accent divider
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AccentPrimary, AccentPrimary.copy(alpha = 0f))
                        )
                    )
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_section_language),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_language_title),
                description = stringResource(R.string.settings_language_description),
                options = listOf(
                    AppLocaleManager.SYSTEM to stringResource(R.string.settings_language_system),
                    AppLocaleManager.ENGLISH to stringResource(R.string.settings_language_english),
                    AppLocaleManager.ITALIAN to stringResource(R.string.settings_language_italian)
                ),
                selectedValue = appLanguage,
                onSelected = { language ->
                    appLanguage = language
                    activity?.let { AppLocaleManager.setAppLanguage(it, language) }
                }
            )

            Spacer(Modifier.height(40.dp))

            // Section header
            Text(
                text = stringResource(R.string.settings_section_streaming),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            // Streaming Mode toggle row
            var streamingMode by remember {
                mutableStateOf(AppPreferences.streamingMode)
            }
            var torrentPreset by remember { mutableStateOf(AppPreferences.torrentPreset) }
            var torrentCacheMb by remember { mutableStateOf(AppPreferences.torrentCacheSizeMb) }
            var torrentPreload by remember { mutableStateOf(AppPreferences.torrentPreloadPercent) }
            var torrentReadAhead by remember { mutableStateOf(AppPreferences.torrentReadAheadPercent) }
            var torrentConnections by remember { mutableStateOf(AppPreferences.torrentConnectionsLimit) }
            var torrentResponsive by remember { mutableStateOf(AppPreferences.torrentResponsiveMode) }
            var torrentDisableUpload by remember { mutableStateOf(AppPreferences.torrentDisableUpload) }
            var torrentDisableIpv6 by remember { mutableStateOf(AppPreferences.torrentDisableIpv6) }
            val applyTorrentSettings = {
                scope.launch {
                    runCatching { TorrServerService.ensureInitialized(context) }
                }
            }

            SettingsToggleRow(
                title = stringResource(R.string.settings_streaming_mode_title),
                description = stringResource(R.string.settings_streaming_mode_description),
                checked = streamingMode,
                onCheckedChange = {
                    streamingMode = it
                    AppPreferences.streamingMode = it
                }
            )

            // ── Source priority + extraction timeout ─────────────────────────
            Spacer(Modifier.height(20.dp))

            var sourceOrder by remember { mutableStateOf(AppPreferences.streamingSourceOrder) }
            var extractTimeoutSec by remember { mutableStateOf(AppPreferences.streamingExtractTimeoutSec) }
            // Make sure all known sources appear in the editable list.
            val mergedOrder = remember(sourceOrder) {
                val seen = sourceOrder.toMutableSet()
                val merged = sourceOrder.toMutableList()
                com.playtorrio.tv.data.streaming.StreamExtractorService.SOURCES.forEach { s ->
                    if (s.index !in seen) {
                        merged.add(s.index)
                        seen.add(s.index)
                    }
                }
                merged
            }

            Text(
                text = stringResource(R.string.settings_source_priority_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_source_priority_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.6f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mergedOrder.forEachIndexed { idx, srcIndex ->
                    val srcName = com.playtorrio.tv.data.streaming.StreamExtractorService.SOURCES
                        .firstOrNull { it.index == srcIndex }?.name ?: stringResource(R.string.settings_source_number, srcIndex)
                    var rowFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (rowFocused) SurfaceGlass.copy(alpha = 0.14f) else SurfaceGlass)
                            .border(
                                1.dp,
                                if (rowFocused) AccentPrimary.copy(alpha = 0.55f) else SurfaceGlassBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .onFocusChanged { rowFocused = it.hasFocus }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (idx > 0) {
                                                val newList = mergedOrder.toMutableList()
                                                val tmp = newList[idx]
                                                newList[idx] = newList[idx - 1]
                                                newList[idx - 1] = tmp
                                                sourceOrder = newList
                                                AppPreferences.streamingSourceOrder = newList
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight -> {
                                            if (idx < mergedOrder.lastIndex) {
                                                val newList = mergedOrder.toMutableList()
                                                val tmp = newList[idx]
                                                newList[idx] = newList[idx + 1]
                                                newList[idx + 1] = tmp
                                                sourceOrder = newList
                                                AppPreferences.streamingSourceOrder = newList
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AccentPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = srcName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "◀ ▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rowFocused) AccentPrimary else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSliderRow(
                title = stringResource(R.string.settings_extraction_timeout_title),
                description = stringResource(R.string.settings_extraction_timeout_description),
                value = extractTimeoutSec,
                range = 5..60,
                suffix = "s",
                onValueChange = {
                    extractTimeoutSec = it
                    AppPreferences.streamingExtractTimeoutSec = it
                }
            )

            // ── TORRENT ENGINE SECTION ───────────────────────────────────────
            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_section_torrent_engine),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_preset_title),
                description = stringResource(R.string.settings_preset_description),
                options = listOf(
                    "safe" to stringResource(R.string.settings_preset_safe),
                    "balanced" to stringResource(R.string.settings_preset_balanced),
                    "turbo" to stringResource(R.string.settings_preset_turbo),
                    "extreme" to stringResource(R.string.settings_preset_extreme)
                ),
                selectedValue = torrentPreset,
                onSelected = { preset ->
                    AppPreferences.applyTorrentPreset(preset)
                    torrentPreset = AppPreferences.torrentPreset
                    torrentCacheMb = AppPreferences.torrentCacheSizeMb
                    torrentPreload = AppPreferences.torrentPreloadPercent
                    torrentReadAhead = AppPreferences.torrentReadAheadPercent
                    torrentConnections = AppPreferences.torrentConnectionsLimit
                    torrentResponsive = AppPreferences.torrentResponsiveMode
                    torrentDisableUpload = AppPreferences.torrentDisableUpload
                    torrentDisableIpv6 = AppPreferences.torrentDisableIpv6
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_cache_size_title),
                description = stringResource(R.string.settings_cache_size_description),
                options = listOf("128" to "128 MB", "256" to "256 MB", "384" to "384 MB", "512" to "512 MB"),
                selectedValue = torrentCacheMb.toString(),
                onSelected = { value ->
                    torrentPreset = "custom"
                    torrentCacheMb = value.toInt()
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentCacheSizeMb = torrentCacheMb
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_preload_title),
                description = stringResource(R.string.settings_preload_description),
                options = listOf("1" to "1%", "2" to "2%", "4" to "4%", "8" to "8%"),
                selectedValue = torrentPreload.toString(),
                onSelected = { value ->
                    torrentPreset = "custom"
                    torrentPreload = value.toInt()
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentPreloadPercent = torrentPreload
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_read_ahead_title),
                description = stringResource(R.string.settings_read_ahead_description),
                options = listOf("85" to "85%", "90" to "90%", "95" to "95%", "99" to "99%"),
                selectedValue = torrentReadAhead.toString(),
                onSelected = { value ->
                    torrentPreset = "custom"
                    torrentReadAhead = value.toInt()
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentReadAheadPercent = torrentReadAhead
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsChoiceRow(
                title = stringResource(R.string.settings_connections_title),
                description = stringResource(R.string.settings_connections_description),
                options = listOf("80" to "80", "120" to "120", "200" to "200", "300" to "300"),
                selectedValue = torrentConnections.toString(),
                onSelected = { value ->
                    torrentPreset = "custom"
                    torrentConnections = value.toInt()
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentConnectionsLimit = torrentConnections
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_responsive_mode_title),
                description = stringResource(R.string.settings_responsive_mode_description),
                checked = torrentResponsive,
                onCheckedChange = {
                    torrentPreset = "custom"
                    torrentResponsive = it
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentResponsiveMode = it
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_disable_upload_title),
                description = stringResource(R.string.settings_disable_upload_description),
                checked = torrentDisableUpload,
                onCheckedChange = {
                    torrentPreset = "custom"
                    torrentDisableUpload = it
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentDisableUpload = it
                    applyTorrentSettings()
                }
            )

            Spacer(Modifier.height(12.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_disable_ipv6_title),
                description = stringResource(R.string.settings_disable_ipv6_description),
                checked = torrentDisableIpv6,
                onCheckedChange = {
                    torrentPreset = "custom"
                    torrentDisableIpv6 = it
                    AppPreferences.torrentPreset = "custom"
                    AppPreferences.torrentDisableIpv6 = it
                    applyTorrentSettings()
                }
            )

            // ── DEBRID SECTION ────────────────────────────────────────────────
            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_section_debrid),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            var debridEnabled by remember { mutableStateOf(AppPreferences.debridEnabled) }
            var debridProvider by remember { mutableStateOf(AppPreferences.debridProvider) }
            var rdApiKey by remember { mutableStateOf(AppPreferences.realDebridApiKey) }
            var tbApiKey by remember { mutableStateOf(AppPreferences.torboxApiKey) }

            SettingsToggleRow(
                title = stringResource(R.string.settings_use_debrid_title),
                description = stringResource(R.string.settings_use_debrid_description),
                checked = debridEnabled,
                onCheckedChange = {
                    debridEnabled = it
                    AppPreferences.debridEnabled = it
                }
            )

            if (debridEnabled) {
                Spacer(Modifier.height(16.dp))

                // Provider picker
                Text(
                    text = stringResource(R.string.settings_provider_label),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("realdebrid" to "Real-Debrid", "torbox" to "TorBox").forEach { (id, label) ->
                        var pFocused by remember { mutableStateOf(false) }
                        val selected = debridProvider == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        selected -> AccentPrimary.copy(alpha = 0.25f)
                                        pFocused -> Color.White.copy(alpha = 0.08f)
                                        else -> Color.White.copy(alpha = 0.04f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (selected) AccentPrimary.copy(alpha = 0.7f)
                                    else if (pFocused) Color.White.copy(alpha = 0.3f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { pFocused = it.hasFocus }
                                .focusable()
                                .onKeyEvent { evt ->
                                    if (evt.type == KeyEventType.KeyDown &&
                                        (evt.key == Key.DirectionCenter || evt.key == Key.Enter)
                                    ) {
                                        debridProvider = id
                                        AppPreferences.debridProvider = id
                                        true
                                    } else false
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) AccentPrimary else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // API key field for the selected provider
                val (keyValue, keyLabelRes) = if (debridProvider == "realdebrid")
                    rdApiKey to R.string.settings_realdebrid_api_key
                else
                    tbApiKey to R.string.settings_torbox_api_key

                Text(
                    text = stringResource(keyLabelRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))

                var apiKeyFocused by remember { mutableStateOf(false) }
                BasicTextField(
                    value = keyValue,
                    onValueChange = { v ->
                        if (debridProvider == "realdebrid") {
                            rdApiKey = v
                            AppPreferences.realDebridApiKey = v
                        } else {
                            tbApiKey = v
                            AppPreferences.torboxApiKey = v
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(AccentPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                    }),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (apiKeyFocused) Color.White.copy(alpha = 0.08f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            1.dp,
                            if (apiKeyFocused) AccentPrimary.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { apiKeyFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        keyboard?.hide()
                                        focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        keyboard?.hide()
                                        focusManager.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    Key.Back, Key.Escape -> {
                                        keyboard?.hide()
                                        focusManager.clearFocus(force = true)
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (keyValue.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_api_key_placeholder),
                                style = TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                            )
                        }
                        inner()
                    }
                )
            }

            // ── TRAILER SECTION ──────────────────────────────────────────────
            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_section_trailers),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            var trailerAutoplay by remember { mutableStateOf(AppPreferences.trailerAutoplay) }
            var trailerDelaySec by remember { mutableStateOf(AppPreferences.trailerDelaySec) }

            SettingsToggleRow(
                title = stringResource(R.string.settings_autoplay_trailers_title),
                description = stringResource(R.string.settings_autoplay_trailers_description),
                checked = trailerAutoplay,
                onCheckedChange = {
                    trailerAutoplay = it
                    AppPreferences.trailerAutoplay = it
                }
            )

            if (trailerAutoplay) {
                Spacer(Modifier.height(12.dp))

                SettingsSliderRow(
                    title = stringResource(R.string.settings_trailer_delay_title),
                    description = stringResource(R.string.settings_trailer_delay_description),
                    value = trailerDelaySec,
                    range = 3..10,
                    suffix = "s",
                    onValueChange = {
                        trailerDelaySec = it
                        AppPreferences.trailerDelaySec = it
                    }
                )
            }

            // ── ADDONS SECTION ────────────────────────────────────────────────
            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_section_addons),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(12.dp))

            // URL input row
            var urlFieldFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(0.75f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicTextField(
                    value = addonUrl,
                    onValueChange = {
                        addonUrl = it
                        installError = null
                        installSuccess = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(AccentPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Right)
                        if (addonUrl.isNotBlank() && !isInstalling) {
                            scope.launch {
                                isInstalling = true
                                installError = null
                                installSuccess = null
                                val result = StremioAddonRepository.installAddon(addonUrl.trim())
                                result.fold(
                                    onSuccess = { addon ->
                                        addonUrl = ""
                                        installSuccess = context.getString(R.string.settings_install_success, addon.manifest.name)
                                        addons = StremioAddonRepository.getAddons()
                                    },
                                    onFailure = { e ->
                                        installError = e.message ?: context.getString(R.string.settings_unknown_error)
                                    }
                                )
                                isInstalling = false
                            }
                        }
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (urlFieldFocused) Color.White.copy(alpha = 0.08f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (urlFieldFocused) AccentPrimary.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { urlFieldFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        keyboard?.hide()
                                        focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        keyboard?.hide()
                                        focusManager.moveFocus(FocusDirection.Up)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        keyboard?.hide()
                                        focusManager.moveFocus(FocusDirection.Right)
                                        true
                                    }
                                    Key.Back, Key.Escape -> {
                                        keyboard?.hide()
                                        focusManager.clearFocus(force = true)
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (addonUrl.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_addon_url_placeholder),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 14.sp
                                )
                            )
                        }
                        inner()
                    }
                )

                // Add button
                var addBtnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (addBtnFocused) AccentPrimary else AccentPrimary.copy(alpha = 0.7f)
                        )
                        .onFocusChanged { addBtnFocused = it.hasFocus }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) {
                                if (addonUrl.isNotBlank() && !isInstalling) {
                                    scope.launch {
                                        isInstalling = true
                                        installError = null
                                        installSuccess = null
                                        val result = StremioAddonRepository.installAddon(addonUrl.trim())
                                        result.fold(
                                            onSuccess = { addon ->
                                                addonUrl = ""
                                                installSuccess = context.getString(R.string.settings_install_success, addon.manifest.name)
                                                addons = StremioAddonRepository.getAddons()
                                            },
                                            onFailure = { e ->
                                                installError = e.message ?: context.getString(R.string.settings_unknown_error)
                                            }
                                        )
                                        isInstalling = false
                                    }
                                }
                                true
                            } else false
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_add_addon),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // Feedback messages
            installError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF87171)
                )
            }
            installSuccess?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4ADE80)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Installed addons list
            if (addons.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_addons_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    addons.forEach { addon ->
                        AddonRow(
                            addon = addon,
                            onRemove = {
                                scope.launch {
                                    StremioAddonRepository.removeAddon(addon.manifest.id)
                                    addons = StremioAddonRepository.getAddons()
                                }
                            },
                            onConfigure = {
                                // Open configure page in browser
                                val configUrl = "${addon.transportUrl}/configure"
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(configUrl)
                                )
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
            } // end left Column

            // RIGHT — fixed QR panel
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(end = 40.dp, top = 48.dp, bottom = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0C0C1E).copy(alpha = 0.85f))
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                                    endY = size.height * 0.40f
                                ),
                                cornerRadius = CornerRadius(20.dp.toPx())
                            )
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_phone_remote_title),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 10.sp
                        ),
                        color = AccentPrimary.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_phone_remote_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.settings_cd_qr_code),
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (serverUrl == null) stringResource(R.string.settings_no_network) else stringResource(R.string.settings_generating),
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    serverUrl?.let { url ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = AccentPrimary.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } // end Row

        // ── Confirmation dialog overlay ───────────────────────────────────────
        AnimatedVisibility(
            visible = pendingChange != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                val change = pendingChange
                if (change != null) {
                    Column(
                        modifier = Modifier
                            .width(480.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F0F1F))
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                                        endY = size.height * 0.35f
                                    ),
                                    cornerRadius = CornerRadius(20.dp.toPx())
                                )
                            }
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.settings_apply_changes_from_phone),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 14.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))

                        // Summary of what's changing
                        val currentAddonUrls = StremioAddonRepository.getAddons().map { it.transportUrl }.toSet()
                        val proposed = change.proposedAddons.toSet()
                        val toAdd = proposed - currentAddonUrls
                        val toRemove = currentAddonUrls - proposed
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (toAdd.isNotEmpty()) {
                                Text(
                                    text = pluralStringResource(R.plurals.settings_pending_addons_install, toAdd.size, toAdd.size),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF4ADE80)
                                )
                            }
                            if (toRemove.isNotEmpty()) {
                                Text(
                                    text = pluralStringResource(R.plurals.settings_pending_addons_remove, toRemove.size, toRemove.size),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFFF87171)
                                )
                            }
                            if (change.proposedStreamingMode != AppPreferences.streamingMode) {
                                Text(
                                    text = stringResource(R.string.settings_streaming_mode_status, if (change.proposedStreamingMode) stringResource(R.string.common_on) else stringResource(R.string.common_off)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (change.proposedDebridEnabled != AppPreferences.debridEnabled) {
                                Text(
                                    text = stringResource(R.string.settings_debrid_status, if (change.proposedDebridEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (change.proposedDebridProvider != AppPreferences.debridProvider) {
                                Text(
                                    text = stringResource(R.string.settings_debrid_provider_status, change.proposedDebridProvider),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            val debridKeyChanged = (change.proposedRealDebridApiKey != AppPreferences.realDebridApiKey) ||
                                    (change.proposedTorboxApiKey != AppPreferences.torboxApiKey)
                            val torrentChanged = change.proposedTorrentPreset != AppPreferences.torrentPreset ||
                                change.proposedTorrentCacheSizeMb != AppPreferences.torrentCacheSizeMb ||
                                change.proposedTorrentPreloadPercent != AppPreferences.torrentPreloadPercent ||
                                change.proposedTorrentReadAheadPercent != AppPreferences.torrentReadAheadPercent ||
                                change.proposedTorrentConnectionsLimit != AppPreferences.torrentConnectionsLimit ||
                                change.proposedTorrentResponsiveMode != AppPreferences.torrentResponsiveMode ||
                                change.proposedTorrentDisableUpload != AppPreferences.torrentDisableUpload ||
                                change.proposedTorrentDisableIpv6 != AppPreferences.torrentDisableIpv6
                            val trailerChanged = change.proposedTrailerAutoplay != AppPreferences.trailerAutoplay ||
                                change.proposedTrailerDelaySec != AppPreferences.trailerDelaySec
                            val sourceOrderChanged = change.proposedStreamingSourceOrder.isNotEmpty() &&
                                change.proposedStreamingSourceOrder != AppPreferences.streamingSourceOrder
                            val timeoutChanged = change.proposedStreamingExtractTimeoutSec != AppPreferences.streamingExtractTimeoutSec
                            if (debridKeyChanged) {
                                Text(
                                    text = stringResource(R.string.settings_debrid_api_key_updated),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (torrentChanged) {
                                Text(
                                    text = stringResource(R.string.settings_torrent_updated),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (trailerChanged) {
                                Text(
                                    text = stringResource(R.string.settings_trailer_updated),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (sourceOrderChanged) {
                                Text(
                                    text = stringResource(R.string.settings_source_priority_updated),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (timeoutChanged) {
                                Text(
                                    text = stringResource(R.string.settings_extraction_timeout_status, change.proposedStreamingExtractTimeoutSec),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            if (toAdd.isEmpty() && toRemove.isEmpty() && change.proposedStreamingMode == AppPreferences.streamingMode
                                && change.proposedDebridEnabled == AppPreferences.debridEnabled
                                && change.proposedDebridProvider == AppPreferences.debridProvider
                                && !debridKeyChanged && !torrentChanged && !trailerChanged
                                && !sourceOrderChanged && !timeoutChanged) {
                                Text(
                                    text = stringResource(R.string.settings_no_changes_detected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        if (isApplyingRemote) {
                            Spacer(Modifier.height(20.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = AccentPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_applying),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        } else {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Reject button
                                var rejectFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (rejectFocused) Color(0xFFF87171).copy(alpha = 0.18f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            1.dp,
                                            if (rejectFocused) Color(0xFFF87171).copy(alpha = 0.6f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .onFocusChanged { rejectFocused = it.hasFocus }
                                        .focusable()
                                        .onKeyEvent { evt ->
                                            if (evt.type == KeyEventType.KeyDown &&
                                                (evt.key == Key.DirectionCenter || evt.key == Key.Enter)
                                            ) {
                                                server?.rejectChange(change.id)
                                                pendingChange = null
                                                true
                                            } else false
                                        }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.common_reject),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (rejectFocused) Color(0xFFF87171) else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Confirm button
                                var confirmFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (confirmFocused) AccentPrimary.copy(alpha = 0.3f)
                                            else AccentPrimary.copy(alpha = 0.18f)
                                        )
                                        .border(
                                            1.dp,
                                            if (confirmFocused) AccentPrimary.copy(alpha = 0.8f)
                                            else AccentPrimary.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .onFocusChanged { confirmFocused = it.hasFocus }
                                        .focusRequester(confirmFocusRequester)
                                        .focusable()
                                        .onKeyEvent { evt ->
                                            if (evt.type == KeyEventType.KeyDown &&
                                                (evt.key == Key.DirectionCenter || evt.key == Key.Enter)
                                            ) {
                                                val captured = change
                                                scope.launch {
                                                    isApplyingRemote = true
                                                    // Apply streaming mode
                                                    AppPreferences.streamingMode = captured.proposedStreamingMode
                                                    // Apply debrid settings
                                                    AppPreferences.debridEnabled = captured.proposedDebridEnabled
                                                    AppPreferences.debridProvider = captured.proposedDebridProvider
                                                    AppPreferences.realDebridApiKey = captured.proposedRealDebridApiKey
                                                    AppPreferences.torboxApiKey = captured.proposedTorboxApiKey
                                                    AppPreferences.torrentPreset = captured.proposedTorrentPreset
                                                    AppPreferences.torrentCacheSizeMb = captured.proposedTorrentCacheSizeMb
                                                    AppPreferences.torrentPreloadPercent = captured.proposedTorrentPreloadPercent
                                                    AppPreferences.torrentReadAheadPercent = captured.proposedTorrentReadAheadPercent
                                                    AppPreferences.torrentConnectionsLimit = captured.proposedTorrentConnectionsLimit
                                                    AppPreferences.torrentResponsiveMode = captured.proposedTorrentResponsiveMode
                                                    AppPreferences.torrentDisableUpload = captured.proposedTorrentDisableUpload
                                                    AppPreferences.torrentDisableIpv6 = captured.proposedTorrentDisableIpv6
                                                    AppPreferences.trailerAutoplay = captured.proposedTrailerAutoplay
                                                    AppPreferences.trailerDelaySec = captured.proposedTrailerDelaySec
                                                    if (captured.proposedStreamingSourceOrder.isNotEmpty()) {
                                                        AppPreferences.streamingSourceOrder = captured.proposedStreamingSourceOrder
                                                    }
                                                    AppPreferences.streamingExtractTimeoutSec = captured.proposedStreamingExtractTimeoutSec
                                                    runCatching { TorrServerService.ensureInitialized(context) }

                                                    val currentUrls = StremioAddonRepository.getAddons()
                                                        .map { it.transportUrl }.toSet()
                                                    val proposedUrls = captured.proposedAddons.toSet()

                                                    // Remove addons not in the proposed list
                                                    val toRemoveIds = StremioAddonRepository.getAddons()
                                                        .filter { it.transportUrl !in proposedUrls }
                                                        .map { it.manifest.id }
                                                    for (id in toRemoveIds) {
                                                        StremioAddonRepository.removeAddon(id)
                                                    }

                                                    // Install new addons
                                                    for (url in proposedUrls - currentUrls) {
                                                        StremioAddonRepository.installAddon(url)
                                                    }

                                                    addons = StremioAddonRepository.getAddons()
                                                    server?.confirmChange(captured.id)
                                                    pendingChange = null
                                                    isApplyingRemote = false
                                                }
                                                true
                                            } else false
                                        }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.common_apply),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } // end outer Box
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonRow(
    addon: InstalledAddon,
    onRemove: () -> Unit,
    onConfigure: () -> Unit
) {
        val manifest = addon.manifest
        var isFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceGlass)
                .border(
                    width = 1.dp,
                    color = if (isFocused) AccentPrimary.copy(alpha = 0.4f) else SurfaceGlassBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon / Logo placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = manifest.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentPrimary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = manifest.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Badges row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (manifest.behaviorHints?.p2p == true) AddonBadge("P2P", Color(0xFF38BDF8))
                        if (manifest.behaviorHints?.adult == true) AddonBadge("18+", Color(0xFFF87171))
                        if (manifest.behaviorHints?.hasAds == true) AddonBadge("Ads", Color(0xFFFBBF24))
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Configure button (only for configurable addons)
                if (manifest.behaviorHints?.configurable == true ||
                    manifest.behaviorHints?.configurationRequired == true
                ) {
                    var configFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (configFocused) AccentSecondary.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (configFocused) AccentSecondary.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { configFocused = it.hasFocus; isFocused = it.hasFocus }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) { onConfigure(); true } else false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.settings_cd_configure),
                            tint = if (configFocused) AccentSecondary else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Remove button
                var removeFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (removeFocused) Color(0xFFF87171).copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (removeFocused) Color(0xFFF87171).copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { removeFocused = it.hasFocus; isFocused = it.hasFocus }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter)
                            ) { onRemove(); true } else false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.settings_cd_remove),
                        tint = if (removeFocused) Color(0xFFF87171) else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
}

@Composable
private fun AddonBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                var focused by remember { mutableStateOf(false) }
                val selected = selectedValue == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                selected -> AccentPrimary.copy(alpha = 0.25f)
                                focused -> Color.White.copy(alpha = 0.08f)
                                else -> Color.White.copy(alpha = 0.04f)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                selected -> AccentPrimary.copy(alpha = 0.7f)
                                focused -> Color.White.copy(alpha = 0.3f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { focused = it.hasFocus }
                        .focusable()
                        .onKeyEvent { evt ->
                            if (evt.type == KeyEventType.KeyDown &&
                                (evt.key == Key.DirectionCenter || evt.key == Key.Enter)
                            ) {
                                onSelected(value)
                                true
                            } else false
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) AccentPrimary else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) SurfaceGlass.copy(alpha = 0.12f) else SurfaceGlass)
            .border(
                width = 1.dp,
                color = if (isFocused) AccentPrimary.copy(alpha = 0.5f) else SurfaceGlassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onCheckedChange(!checked)
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.width(24.dp))

            // Toggle indicator
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (checked) AccentPrimary else Color.White.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 3.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsSliderRow(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onValueChange: (Int) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) SurfaceGlass.copy(alpha = 0.12f) else SurfaceGlass)
            .border(
                width = 1.dp,
                color = if (isFocused) AccentPrimary.copy(alpha = 0.5f) else SurfaceGlassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            if (value < range.last) onValueChange(value + 1)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (value > range.first) onValueChange(value - 1)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AccentPrimary
            )
        }
        Spacer(Modifier.height(12.dp))
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            val fraction = (value - range.first).toFloat() / (range.last - range.first).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AccentPrimary)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${range.first}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
            Text(
                text = "${range.last}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
