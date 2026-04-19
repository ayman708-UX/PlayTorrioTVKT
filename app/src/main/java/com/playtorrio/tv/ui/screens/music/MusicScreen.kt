package com.playtorrio.tv.ui.screens.music

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.NavController
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.data.music.DeezerAlbumDetail
import com.playtorrio.tv.data.music.DeezerAlbumRef
import com.playtorrio.tv.data.music.DeezerTrack
import com.playtorrio.tv.data.trailer.YoutubeChunkedDataSourceFactory

private const val TAG = "MusicScreen"
private val Accent = Color(0xFF818CF8)
private val SurfaceDark = Color(0xFF0A0A0F)
private val CardBg = Color(0xFF16162A)
private val CardBgLight = Color(0xFF1E1E38)
private val TextDim = Color.White.copy(alpha = 0.5f)

// ═══════════════════════════════════════════════════════════════════════
// ROOT
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun MusicScreen(navController: NavController) {
    val vm: MusicViewModel = viewModel()
    val state by vm.ui.collectAsState()

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF; volume = 1f }
    }
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var bgImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.currentTrack) {
        state.currentTrack?.let { bgImageUrl = it.album?.coverXl ?: it.album?.coverBig }
    }

    LaunchedEffect(state.currentSource) {
        val src = state.currentSource ?: return@LaunchedEffect
        val audioUrl = src.audioUrl ?: src.videoUrl
        Log.i(TAG, "Setting player source: ${audioUrl.take(80)}...")
        val dsFactory = YoutubeChunkedDataSourceFactory(clientUserAgent = src.clientUserAgent)
        val msFactory = DefaultMediaSourceFactory(dsFactory)
        if (src.audioUrl != null && src.audioUrl != src.videoUrl) {
            player.setMediaSource(msFactory.createMediaSource(MediaItem.fromUri(src.audioUrl)))
        } else {
            player.setMediaItem(MediaItem.fromUri(audioUrl))
        }
        player.prepare(); player.playWhenReady = true; isActuallyPlaying = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED) vm.nextTrack() }
            override fun onIsPlayingChanged(p: Boolean) { isActuallyPlaying = p }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                    if (state.dialog == MusicDialog.PLAYER) { player.stop(); vm.closePlayer(); true }
                    else vm.goBack()
                } else false
            }
    ) {
        // Blurred background
        bgImageUrl?.let { url ->
            AsyncImage(url, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .blur(80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.15f })
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceDark.copy(alpha = 0.85f), SurfaceDark.copy(alpha = 0.95f)))
        ))

        val dialogActive = state.dialog != MusicDialog.NONE

        // ── Screens (hidden when dialog active) ──────────────────────
        if (!dialogActive) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, vm, onBack = { if (!vm.goBack()) navController.popBackStack() })
                when (state.currentView) {
                    MusicView.BROWSE -> BrowseScreen(state, vm) { bgImageUrl = it }
                    MusicView.LIKED_TRACKS -> LikedTracksScreen(state, vm) { bgImageUrl = it }
                    MusicView.LIKED_ALBUMS -> LikedAlbumsScreen(state, vm)
                    MusicView.PLAYLISTS -> PlaylistsScreen(state, vm)
                    MusicView.PLAYLIST_DETAIL -> PlaylistDetailScreen(state, vm) { bgImageUrl = it }
                    MusicView.ALBUM_DETAIL -> AlbumDetailScreen(state, vm) { bgImageUrl = it }
                }
            }
        }

        // ── Player dialog ────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.dialog == MusicDialog.PLAYER,
            enter = slideInVertically(tween(350)) { it } + fadeIn(),
            exit = slideOutVertically(tween(250)) { it } + fadeOut()
        ) {
            PlayerScreen(
                track = state.currentTrack, player = player,
                isExtracting = state.isExtracting, isPlaying = isActuallyPlaying,
                queueIndex = state.queueIndex, queueSize = state.queue.size,
                isSaved = state.currentTrack?.let { vm.isTrackSaved(it.id) } ?: false,
                onToggleSave = { state.currentTrack?.let { vm.toggleSaveTrack(it) } },
                onAddToPlaylist = { state.currentTrack?.let { vm.showAddToPlaylist(it) } },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onNext = { vm.nextTrack() }, onPrev = { vm.prevTrack() },
                onBack = { player.stop(); vm.closePlayer() }
            )
        }

        // ── Add to playlist dialog ───────────────────────────────────
        AnimatedVisibility(
            visible = state.dialog == MusicDialog.ADD_TO_PLAYLIST,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(150))
        ) {
            AddToPlaylistDialog(
                playlists = state.playlists,
                onSelect = { vm.addTrackToPlaylist(it) },
                onCreateNew = { vm.showCreatePlaylist() },
                onBack = { vm.closeDialog() }
            )
        }

        // ── Create playlist dialog ───────────────────────────────────
        AnimatedVisibility(
            visible = state.dialog == MusicDialog.CREATE_PLAYLIST,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(150))
        ) {
            CreatePlaylistDialog(
                onCreate = { vm.createPlaylist(it) },
                onBack = { vm.closeDialog() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(state: MusicUiState, vm: MusicViewModel, onBack: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            Card(onClick = onBack, modifier = Modifier.size(36.dp),
                colors = CardDefaults.colors(containerColor = CardBg),
                shape = CardDefaults.shape(CircleShape)
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            } }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.MusicNote, null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text("Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))

            // Search bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { vm.updateSearchQuery(it) },
                onSearch = { vm.search(it) },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(12.dp))

            // Library button
            val libActive = state.currentView in listOf(
                MusicView.LIKED_TRACKS, MusicView.LIKED_ALBUMS, MusicView.PLAYLISTS, MusicView.PLAYLIST_DETAIL
            ) || state.libraryExpanded
            var libFocused by remember { mutableStateOf(false) }

            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (libActive) Accent.copy(alpha = 0.25f) else CardBg)
                    .border(if (libFocused) 2.dp else 0.dp, if (libFocused) Accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .onFocusChanged { libFocused = it.isFocused }
                    .clickable { vm.toggleLibrary() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LibraryMusic, null,
                    tint = if (libActive || libFocused) Accent else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Library",
                    color = if (libActive || libFocused) Accent else Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Library sub-buttons
        AnimatedVisibility(visible = state.libraryExpanded, enter = fadeIn(tween(150)), exit = fadeOut(tween(100))) {
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                LibSubButton(Icons.Filled.QueueMusic, "Playlists",
                    state.currentView == MusicView.PLAYLISTS || state.currentView == MusicView.PLAYLIST_DETAIL
                ) { vm.navigateTo(MusicView.PLAYLISTS) }
                Spacer(Modifier.width(8.dp))
                LibSubButton(Icons.Filled.Album, "Liked Albums",
                    state.currentView == MusicView.LIKED_ALBUMS
                ) { vm.navigateTo(MusicView.LIKED_ALBUMS) }
                Spacer(Modifier.width(8.dp))
                LibSubButton(Icons.Filled.Favorite, "Liked Tracks",
                    state.currentView == MusicView.LIKED_TRACKS
                ) { vm.navigateTo(MusicView.LIKED_TRACKS) }
            }
        }
    }
}

@Composable
private fun LibSubButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(when {
                active -> Accent.copy(alpha = 0.2f)
                focused -> CardBgLight
                else -> CardBg
            })
            .border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active || focused) Accent else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (active || focused) Accent else Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SEARCH BAR (D-pad friendly)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(
    query: String, onQueryChange: (String) -> Unit, onSearch: (String) -> Unit, modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Accent else Color.White.copy(alpha = 0.1f)

    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = if (isFocused) Accent else TextDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query, onValueChange = onQueryChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            modifier = Modifier.weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.Enter && e.type == KeyEventType.KeyUp) { onSearch(query); true } else false
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search songs, albums...", color = Color.White.copy(alpha = 0.25f), fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BROWSE SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BrowseScreen(state: MusicUiState, vm: MusicViewModel, onBg: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (state.isLoading) {
            item { Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(40.dp))
            } }
        }

        val hasSearch = state.searchQuery.isNotBlank() &&
                (state.searchTracks.isNotEmpty() || state.searchAlbums.isNotEmpty() || state.isSearching)

        if (hasSearch) {
            if (state.isSearching) {
                item { Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
                } }
            }
            if (state.searchTracks.isNotEmpty()) {
                item { SectionTitle("Tracks", Icons.Filled.MusicNote)
                    TrackRow(state.searchTracks, onBg) { t, i -> vm.playTrack(t, state.searchTracks, i) } }
            }
            if (state.searchAlbums.isNotEmpty()) {
                item { SectionTitle("Albums", Icons.Filled.Album)
                    AlbumRow(state.searchAlbums, onBg) { vm.openAlbum(it) } }
            }
        } else if (!state.isLoading) {
            if (state.chartTracks.isNotEmpty()) {
                item { SectionTitle("Trending", Icons.Filled.MusicNote)
                    TrackRow(state.chartTracks, onBg) { t, i -> vm.playTrack(t, state.chartTracks, i) } }
            }
            if (state.chartAlbums.isNotEmpty()) {
                item { SectionTitle("Top Albums", Icons.Filled.Album)
                    AlbumRow(state.chartAlbums, onBg) { vm.openAlbum(it) } }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LIKED TRACKS SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LikedTracksScreen(state: MusicUiState, vm: MusicViewModel, onBg: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Liked Tracks", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("${state.savedTracks.size} songs", color = TextDim, fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            if (state.savedTracks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillBtn(Icons.Filled.PlayArrow, "Play All", Color.White, Accent) {
                        vm.playTrack(state.savedTracks[0], state.savedTracks, 0)
                    }
                    PillBtn(Icons.Filled.Shuffle, "Shuffle", Accent, Accent.copy(alpha = 0.2f)) {
                        val s = state.savedTracks.shuffled()
                        vm.playTrack(s[0], s, 0)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.savedTracks.isEmpty()) {
            item { EmptyMessage("No liked tracks yet. Tap the heart on any track to save it here.") }
        }

        itemsIndexed(state.savedTracks, key = { _, t -> t.id }) { idx, track ->
            TrackListItem(
                index = idx + 1, track = track, onBg = onBg,
                onClick = { vm.playTrack(track, state.savedTracks, idx) },
                onSave = { vm.toggleSaveTrack(track) },
                isSaved = true,
                onAddToPlaylist = { vm.showAddToPlaylist(track) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LIKED ALBUMS SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LikedAlbumsScreen(state: MusicUiState, vm: MusicViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Album, null, tint = Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Liked Albums", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("${state.savedAlbumIds.size} albums", color = TextDim, fontSize = 14.sp)
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.isLoadingSavedAlbums) {
            item { Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
            } }
        } else if (state.savedAlbums.isEmpty()) {
            item { EmptyMessage("No liked albums yet. Tap Save on any album to add it here.") }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.focusGroup()) {
                    itemsIndexed(state.savedAlbums, key = { _, a -> a.id }) { _, album ->
                        AlbumCardDetail(album) { vm.openAlbum(album.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumCardDetail(album: DeezerAlbumDetail, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(200), label = "")
    val bAlpha by animateFloatAsState(if (focused) 0.8f else 0f, tween(200), label = "")
    Card(onClick = onClick, modifier = Modifier.width(170.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.width(170.dp).background(Brush.verticalGradient(listOf(CardBgLight, CardBg)), RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, Accent.copy(alpha = bAlpha), RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                AsyncImage(album.coverXl ?: album.coverBig ?: album.coverMedium, album.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, CardBg.copy(alpha = 0.7f)))))
            }
            Column(Modifier.padding(10.dp)) {
                Text(album.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(album.artist.name, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PLAYLISTS SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistsScreen(state: MusicUiState, vm: MusicViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QueueMusic, null, tint = Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Playlists", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                PillBtn(Icons.Filled.Add, "New Playlist", Accent, Accent.copy(alpha = 0.2f)) { vm.showCreatePlaylist() }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.playlists.isEmpty()) {
            item { EmptyMessage("No playlists yet. Create one to organize your music!") }
        }

        itemsIndexed(state.playlists) { idx, pl ->
            PlaylistListItem(pl, vm) { vm.openPlaylistDetail(idx) }
            if (idx < state.playlists.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistListItem(pl: MusicPlaylist, vm: MusicViewModel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = if (focused) Accent.copy(alpha = 0.12f) else CardBg),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Mosaic thumbnail
            val covers = pl.trackIds.take(4).mapNotNull { id -> vm.trackCache[id.toLongOrNull() ?: 0]?.album?.coverMedium }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(CardBgLight)) {
                if (covers.isNotEmpty()) {
                    AsyncImage(covers[0], null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.QueueMusic, null, tint = Accent.copy(alpha = 0.4f), modifier = Modifier.size(22.dp).align(Alignment.Center))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pl.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pl.trackIds.size} tracks", color = TextDim, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayArrow, null, tint = if (focused) Accent else TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PLAYLIST DETAIL SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistDetailScreen(state: MusicUiState, vm: MusicViewModel, onBg: (String?) -> Unit) {
    val pl = state.playlists.getOrNull(state.viewingPlaylistIndex) ?: return
    val tracks = state.viewingPlaylistTracks

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QueueMusic, null, tint = Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(pl.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size} tracks", color = TextDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tracks.isNotEmpty()) {
                    PillBtn(Icons.Filled.PlayArrow, "Play All", Color.White, Accent) {
                        vm.playAllPlaylist(state.viewingPlaylistIndex)
                    }
                    PillBtn(Icons.Filled.Shuffle, "Shuffle", Accent, Accent.copy(alpha = 0.2f)) {
                        vm.shufflePlaylist(state.viewingPlaylistIndex)
                    }
                }
                PillBtn(Icons.Filled.Delete, "Delete", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f)) {
                    vm.deletePlaylist(state.viewingPlaylistIndex)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (tracks.isEmpty()) {
            item { EmptyMessage("This playlist is empty. Add tracks using the + button on any song.") }
        }

        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
            TrackListItem(
                index = idx + 1, track = track, onBg = onBg,
                onClick = { vm.playTrack(track, tracks, idx) },
                onSave = { vm.toggleSaveTrack(track) },
                isSaved = vm.isTrackSaved(track.id),
                onAddToPlaylist = { vm.showAddToPlaylist(track) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// ALBUM DETAIL SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumDetailScreen(state: MusicUiState, vm: MusicViewModel, onBg: (String?) -> Unit) {
    val album = state.currentAlbum

    Box(Modifier.fillMaxSize()) {
        // Blurred album background
        album?.let { a ->
            AsyncImage(a.coverXl ?: a.coverBig, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .blur(100.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.2f })
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SurfaceDark.copy(alpha = 0.8f), SurfaceDark.copy(alpha = 0.95f)))
        ))

        if (state.isAlbumLoading || album == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(40.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(180.dp).clip(RoundedCornerShape(16.dp))) {
                            AsyncImage(album.coverXl ?: album.coverBig ?: album.coverMedium, album.title,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f).padding(top = 6.dp)) {
                            Text(album.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(album.artist.name, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("${album.tracks?.data?.size ?: 0} tracks", color = TextDim, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val isSaved = vm.isAlbumSaved(album.id)
                                PillBtn(
                                    if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    if (isSaved) "Saved" else "Save",
                                    if (isSaved) Accent else Color.White.copy(alpha = 0.7f),
                                    if (isSaved) Accent.copy(alpha = 0.2f) else CardBg
                                ) { vm.toggleSaveAlbum(album.id) }
                                val tracks = album.tracks?.data ?: emptyList()
                                if (tracks.isNotEmpty()) {
                                    PillBtn(Icons.Filled.PlayArrow, "Play All", Color.White, Accent) {
                                        vm.playTrack(tracks[0], tracks, 0)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                val tracks = album.tracks?.data ?: emptyList()
                itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                    TrackListItem(
                        index = idx + 1, track = track, onBg = onBg,
                        onClick = { vm.playTrack(track, tracks, idx) },
                        onSave = { vm.toggleSaveTrack(track) },
                        isSaved = vm.isTrackSaved(track.id),
                        onAddToPlaylist = { vm.showAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(Modifier.padding(start = 48.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyMessage(msg: String) {
    Box(Modifier.fillMaxWidth().height(100.dp), Alignment.CenterStart) {
        Text(msg, color = TextDim, fontSize = 14.sp)
    }
}

// ── Track row (horizontal scroll) ────────────────────────────────────

@Composable
private fun TrackRow(tracks: List<DeezerTrack>, onBg: (String?) -> Unit, onClick: (DeezerTrack, Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
            TrackCard(track, onBg) { onClick(track, idx) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackCard(track: DeezerTrack, onBg: (String?) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(200), label = "")
    val bAlpha by animateFloatAsState(if (focused) 0.8f else 0f, tween(200), label = "")
    LaunchedEffect(focused) { if (focused) onBg(track.album?.coverXl ?: track.album?.coverBig) }

    Card(onClick = onClick, modifier = Modifier.width(160.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.width(160.dp).background(Brush.verticalGradient(listOf(CardBgLight, CardBg)), RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, Accent.copy(alpha = bAlpha), RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                AsyncImage(track.album?.coverXl ?: track.album?.coverBig ?: track.album?.coverMedium, track.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, CardBg.copy(alpha = 0.7f)))))
            }
            Column(Modifier.padding(10.dp)) {
                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(track.artist.name, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Album row (horizontal scroll) ────────────────────────────────────

@Composable
private fun AlbumRow(albums: List<DeezerAlbumRef>, onBg: (String?) -> Unit, onAlbumClick: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()) {
        itemsIndexed(albums, key = { _, a -> a.id }) { _, album ->
            AlbumCard(album, onBg) { onAlbumClick(album.id) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumCard(album: DeezerAlbumRef, onBg: (String?) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(200), label = "")
    val bAlpha by animateFloatAsState(if (focused) 0.8f else 0f, tween(200), label = "")
    LaunchedEffect(focused) { if (focused) onBg(album.coverXl ?: album.coverBig) }

    Card(onClick = onClick, modifier = Modifier.width(170.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.width(170.dp).background(Brush.verticalGradient(listOf(CardBgLight, CardBg)), RoundedCornerShape(14.dp))
            .border(if (focused) 2.dp else 0.dp, Accent.copy(alpha = bAlpha), RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                AsyncImage(album.coverXl ?: album.coverBig ?: album.coverMedium, album.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, CardBg.copy(alpha = 0.7f)))))
            }
            Column(Modifier.padding(10.dp)) {
                Text(album.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(album.artist?.name ?: "", color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Track list item (vertical list) ──────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackListItem(
    index: Int, track: DeezerTrack, onBg: (String?) -> Unit,
    onClick: () -> Unit, onSave: () -> Unit, isSaved: Boolean, onAddToPlaylist: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) onBg(track.album?.coverXl ?: track.album?.coverBig) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp).onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(containerColor = if (focused) Accent.copy(alpha = 0.1f) else Color.Transparent),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$index", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp, modifier = Modifier.width(28.dp))
            // Mini cover
            val coverUrl = track.album?.coverMedium
            if (coverUrl != null) {
                AsyncImage(coverUrl, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.name, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (focused) {
                // Heart
                Card(onClick = onSave, modifier = Modifier.size(28.dp),
                    colors = CardDefaults.colors(containerColor = Color.Transparent),
                    shape = CardDefaults.shape(CircleShape)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Save",
                            tint = if (isSaved) Accent else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
                // Add to playlist
                Card(onClick = onAddToPlaylist, modifier = Modifier.size(28.dp),
                    colors = CardDefaults.colors(containerColor = Color.Transparent),
                    shape = CardDefaults.shape(CircleShape)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Filled.PlaylistAdd, "Playlist", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            val m = track.duration / 60; val s = track.duration % 60
            Text("%d:%02d".format(m, s), color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PLAYER SCREEN (with seek bar UNDER controls)
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerScreen(
    track: DeezerTrack?, player: ExoPlayer,
    isExtracting: Boolean, isPlaying: Boolean,
    queueIndex: Int, queueSize: Int,
    isSaved: Boolean, onToggleSave: () -> Unit, onAddToPlaylist: () -> Unit,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onBack: () -> Unit
) {
    track ?: return
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); try { playFocus.requestFocus() } catch (_: Exception) {} }

    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, isExtracting) {
        while (true) {
            posMs = player.currentPosition.coerceAtLeast(0L)
            durMs = player.duration.let { if (it > 0) it else 0L }
            kotlinx.coroutines.delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Transparent).focusGroup()) {
        val bgUrl = track.album?.coverXl ?: track.album?.coverBig
        if (bgUrl != null) {
            AsyncImage(bgUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .blur(120.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.3f })
        }
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(SurfaceDark.copy(alpha = 0.7f), SurfaceDark.copy(alpha = 0.92f)))
        ))

        Row(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            // Album art
            val imgUrl = track.album?.coverXl ?: track.album?.coverBig ?: track.album?.coverMedium
            Box(Modifier.size(280.dp).clip(RoundedCornerShape(22.dp))) {
                if (imgUrl != null) {
                    AsyncImage(imgUrl, track.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(CardBg), Alignment.Center) {
                        Icon(Icons.Filled.Album, "Album", tint = Accent, modifier = Modifier.size(72.dp))
                    }
                }
            }

            Spacer(Modifier.width(48.dp))

            Column(Modifier.weight(1f)) {
                // Top row: Back + queue
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Card(onClick = onBack, modifier = Modifier.size(34.dp),
                        colors = CardDefaults.colors(containerColor = CardBg), shape = CardDefaults.shape(CircleShape)) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${queueIndex + 1} / $queueSize", color = TextDim, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))

                Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(track.artist.name, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)

                Spacer(Modifier.height(14.dp))

                // Action pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillBtn(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (isSaved) "Saved" else "Save",
                        if (isSaved) Accent else Color.White.copy(alpha = 0.7f),
                        if (isSaved) Accent.copy(alpha = 0.2f) else CardBg, onToggleSave
                    )
                    PillBtn(Icons.Filled.PlaylistAdd, "Add to Playlist", Color.White.copy(alpha = 0.7f), CardBg, onAddToPlaylist)
                }

                Spacer(Modifier.height(18.dp))

                // Playback controls
                if (isExtracting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Extracting audio...", color = TextDim, fontSize = 13.sp)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusGroup()) {
                        Card(onClick = onPrev, modifier = Modifier.size(48.dp),
                            colors = CardDefaults.colors(containerColor = CardBg), shape = CardDefaults.shape(CircleShape)) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Filled.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Card(onClick = onPlayPause, modifier = Modifier.size(60.dp).focusRequester(playFocus),
                            colors = CardDefaults.colors(containerColor = Accent), shape = CardDefaults.shape(CircleShape)) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Card(onClick = onNext, modifier = Modifier.size(48.dp),
                            colors = CardDefaults.colors(containerColor = CardBg), shape = CardDefaults.shape(CircleShape)) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // ── Seek bar (below controls) ───────────────────────
                Spacer(Modifier.height(16.dp))

                if (!isExtracting && durMs > 0) {
                    val frac = posMs.toFloat() / durMs.toFloat()
                    var seekFocused by remember { mutableStateOf(false) }
                    val seekStep = 5000L

                    // Time labels
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtMs(posMs), color = if (seekFocused) Color.White else TextDim, fontSize = 12.sp)
                        Text(fmtMs(durMs), color = TextDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))

                    // Canvas bar — focusable for D-pad left/right seeking
                    val barH = if (seekFocused) 6.dp else 4.dp
                    val trackCol = if (seekFocused) Accent else Accent.copy(alpha = 0.7f)
                    val thumbCol = if (seekFocused) Color.White else Accent

                    Box(
                        Modifier.fillMaxWidth().height(22.dp)
                            .onFocusChanged { seekFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown) {
                                    when (e.key) {
                                        Key.DirectionRight -> { player.seekTo((posMs + seekStep).coerceAtMost(durMs)); posMs = (posMs + seekStep).coerceAtMost(durMs); true }
                                        Key.DirectionLeft -> { player.seekTo((posMs - seekStep).coerceAtLeast(0L)); posMs = (posMs - seekStep).coerceAtLeast(0L); true }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .border(
                                width = if (seekFocused) 1.dp else 0.dp,
                                color = if (seekFocused) Accent.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(barH)) {
                            val w = size.width; val h = size.height
                            val r = CornerRadius(h / 2f, h / 2f)
                            drawRoundRect(color = Color.White.copy(alpha = 0.12f), size = Size(w, h), cornerRadius = r)
                            val fw = w * frac.coerceIn(0f, 1f)
                            if (fw > 0f) drawRoundRect(color = trackCol, size = Size(fw, h), cornerRadius = r)
                            val tr = if (seekFocused) 7f else 5f
                            drawCircle(color = thumbCol, radius = tr, center = Offset(fw.coerceIn(tr, w - tr), h / 2f))
                        }
                    }
                }
            }
        }
    }
}

private fun fmtMs(ms: Long): String { val t = ms / 1000; return "%d:%02d".format(t / 60, t % 60) }

// ═══════════════════════════════════════════════════════════════════════
// ADD TO PLAYLIST DIALOG
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddToPlaylistDialog(playlists: List<MusicPlaylist>, onSelect: (Int) -> Unit, onCreateNew: () -> Unit, onBack: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); try { fr.requestFocus() } catch (_: Exception) {} }

    Box(Modifier.fillMaxSize().background(SurfaceDark.copy(alpha = 0.92f)).focusGroup(), Alignment.Center) {
        Column(
            Modifier.width(400.dp).background(CardBg, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add to Playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Card(onClick = onCreateNew, modifier = Modifier.fillMaxWidth().height(44.dp).focusRequester(fr),
                colors = CardDefaults.colors(containerColor = Accent.copy(alpha = 0.15f)),
                shape = CardDefaults.shape(RoundedCornerShape(10.dp))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Create New Playlist", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (playlists.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                playlists.forEachIndexed { idx, pl ->
                    Card(onClick = { onSelect(idx) }, modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = CardDefaults.colors(containerColor = CardBgLight),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp))) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QueueMusic, null, tint = TextDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(pl.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${pl.trackIds.size}", color = TextDim, fontSize = 12.sp)
                        }
                    }
                    if (idx < playlists.lastIndex) Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(CardBgLight)
                    .clickable { onBack() }
                    .padding(horizontal = 20.dp),
                Alignment.Center
            ) { Text("Cancel", color = TextDim, fontSize = 13.sp) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CREATE PLAYLIST DIALOG (D-pad friendly text input)
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); try { inputFocus.requestFocus() } catch (_: Exception) {} }

    Box(Modifier.fillMaxSize().background(SurfaceDark.copy(alpha = 0.92f)).focusGroup(), Alignment.Center) {
        Column(
            Modifier.width(400.dp).background(CardBg, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("New Playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            BasicTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp)).background(CardBgLight)
                    .border(1.5.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp)
                    .focusRequester(inputFocus)
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter && e.type == KeyEventType.KeyUp && name.isNotBlank()) { onCreate(name); true } else false
                    },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), Alignment.CenterStart) {
                        if (name.isEmpty()) Text("Playlist name...", color = Color.White.copy(alpha = 0.25f), fontSize = 16.sp)
                        inner()
                    }
                }
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(CardBgLight)
                        .clickable { onBack() }
                        .padding(horizontal = 22.dp),
                    Alignment.Center
                ) { Text("Cancel", color = TextDim, fontSize = 14.sp) }
                Box(
                    Modifier.height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Accent)
                        .clickable { if (name.isNotBlank()) onCreate(name) }
                        .padding(horizontal = 22.dp),
                    Alignment.Center
                ) { Text("Create", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PILL BUTTON (reusable)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun PillBtn(icon: ImageVector, label: String, tint: Color, bg: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(17.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
