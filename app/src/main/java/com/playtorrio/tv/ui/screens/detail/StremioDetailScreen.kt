package com.playtorrio.tv.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.playtorrio.tv.R
import com.playtorrio.tv.PlayerActivity
import com.playtorrio.tv.data.stremio.StremioMeta
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.stremio.StremioStream
import com.playtorrio.tv.data.stremio.StremioVideo
import com.playtorrio.tv.data.stremio.StreamRoute

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val AccentTertiary = Color(0xFF38BDF8)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StremioDetailScreen(
    addonId: String,
    type: String,
    stremioId: String,
    navController: NavController,
    viewModel: StremioDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(addonId, type, stremioId) {
        viewModel.load(addonId, type, stremioId)
    }

    // Handle TMDB redirect — when addon meta fails but IMDB→TMDB resolves
    LaunchedEffect(state.tmdbRedirectRoute) {
        val route = state.tmdbRedirectRoute
        if (route != null) {
            navController.navigate(route) {
                popUpTo(navController.currentDestination?.route ?: "home") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    if (state.showStreamOverlay) {
                        viewModel.dismissStreamOverlay()
                    } else {
                        navController.popBackStack()
                    }
                    true
                } else false
            }
    ) {
        when {
            state.isLoading -> StremioDetailLoading()
            state.error != null -> StremioDetailError(state.error!!)
            state.meta != null -> StremioDetailContent(
                meta = state.meta!!,
                type = type,
                selectedSeason = state.selectedSeason,
                onSeasonSelected = { viewModel.selectSeason(it) },
                onPlayMovie = { viewModel.loadStreams(stremioId, null) },
                onEpisodeSelected = { video ->
                    viewModel.loadStreamsForVideo(video)
                },
                navController = navController,
                addonId = addonId
            )
        }

        // Stream overlay
        if (state.showStreamOverlay) {
            StreamSelectionOverlay(
                title = state.selectedVideoTitle ?: state.meta?.name ?: "",
                streams = state.streams,
                isLoading = state.isLoadingStreams,
                onDismiss = { viewModel.dismissStreamOverlay() },
                onStreamSelected = { stream ->
                    viewModel.dismissStreamOverlay()
                    val meta = state.meta
                    when (val route = StremioService.routeStream(stream)) {
                        is StreamRoute.Torrent -> {
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("magnetUri", route.magnet)
                                route.fileIdx?.let { putExtra("fileIdx", it) }
                                putExtra("title", meta?.name ?: "")
                                putExtra("backdropUrl", meta?.background)
                                putExtra("posterUrl", meta?.poster)
                                putExtra("overview", meta?.description)
                                putExtra("isMovie", type == "movie")
                                // Addon-stream resume context — used so the
                                // home-screen continue-watching slider can re-fetch.
                                stream.addonId?.let { putExtra("addonId", it) }
                                putExtra("stremioType", type)
                                putExtra("stremioId", stremioId)
                                (stream.url ?: stream.infoHash)?.let { putExtra("streamPickKey", it) }
                                (stream.name ?: stream.title)?.let { putExtra("streamPickName", it) }
                            }
                            context.startActivity(intent)
                        }
                        is StreamRoute.DirectUrl -> {
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("streamUrl", route.url)
                                putExtra("streamReferer", route.headers?.get("Referer") ?: "")
                                putExtra("title", meta?.name ?: "")
                                putExtra("backdropUrl", meta?.background)
                                putExtra("posterUrl", meta?.poster)
                                putExtra("overview", meta?.description)
                                putExtra("isMovie", type == "movie")
                                stream.addonId?.let { putExtra("addonId", it) }
                                putExtra("stremioType", type)
                                putExtra("stremioId", stremioId)
                                (stream.url ?: stream.infoHash)?.let { putExtra("streamPickKey", it) }
                                (stream.name ?: stream.title)?.let { putExtra("streamPickName", it) }
                            }
                            context.startActivity(intent)
                        }
                        is StreamRoute.YouTube -> {
                            val ytIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=${route.ytId}")
                            )
                            context.startActivity(ytIntent)
                        }
                        is StreamRoute.External -> {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(route.url)))
                        }
                        is StreamRoute.IFrame -> {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(route.url)))
                        }
                        is StreamRoute.StremioDeepLink -> {
                            handleStremioDeepLink(route, navController)
                        }
                        StreamRoute.Unsupported -> { /* no-op */ }
                    }
                }
            )
        }
    }
}

@Composable
private fun StremioDetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.stremio_loading), color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun StremioDetailError(error: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(error, color = Color(0xFFF87171))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StremioDetailContent(
    meta: StremioMeta,
    type: String,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onPlayMovie: () -> Unit,
    onEpisodeSelected: (StremioVideo) -> Unit,
    navController: NavController,
    addonId: String
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        AsyncImage(
            model = meta.background ?: meta.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Left: poster
            AsyncImage(
                model = meta.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(12.dp))
            )

            // Right: info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Release + genres
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!meta.releaseInfo.isNullOrBlank()) {
                        Text(
                            text = meta.releaseInfo,
                            color = AccentSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!meta.runtime.isNullOrBlank()) {
                        Text(
                            text = "· ${meta.runtime}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                    if (!meta.imdbRating.isNullOrBlank()) {
                        Text(
                            text = "★ ${meta.imdbRating}",
                            color = Color(0xFFFFD700),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!meta.genres.isNullOrEmpty()) {
                    Text(
                        text = meta.genres.take(4).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }

                if (!meta.description.isNullOrBlank()) {
                    Text(
                        text = meta.description,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (type == "movie") {
                    val videos = meta.videos ?: emptyList()
                    if (videos.isEmpty()) {
                        WatchButton(onClick = onPlayMovie)
                    } else {
                        // Collection: check if videos have IMDB IDs → show as movie cards
                        val isCollection = videos.any { it.id.startsWith("tt") }
                        Text(
                            text = pluralStringResource(R.plurals.stremio_collection_movies, videos.size, videos.size),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        if (isCollection) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(videos) { video ->
                                    CollectionMovieCard(
                                        video = video,
                                        onClick = {
                                            navController.navigate(
                                                "stremio_detail/${android.net.Uri.encode(addonId)}/movie/${android.net.Uri.encode(video.id)}"
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                itemsIndexed(videos) { _, video ->
                                    EpisodeRow(video = video, onClick = { onEpisodeSelected(video) })
                                }
                            }
                        }
                    }
                } else {
                    // Series: season selector + episode list
                    val videos = meta.videos ?: emptyList()
                    val seasons = videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted()

                    if (seasons.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { season ->
                                SeasonChip(
                                    season = season,
                                    isSelected = season == selectedSeason,
                                    onClick = { onSeasonSelected(season) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    val episodesForSeason = videos.filter {
                        it.season == selectedSeason || (seasons.isEmpty())
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(episodesForSeason) { _, video ->
                            EpisodeRow(video = video, onClick = { onEpisodeSelected(video) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "ws"
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentPrimary else AccentPrimary.copy(alpha = 0.25f),
            focusedContainerColor = AccentPrimary
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.common_watch),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonChip(season: Int, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = when {
                isSelected -> AccentPrimary.copy(alpha = 0.3f)
                isFocused -> SurfaceGlass
                else -> Color.Transparent
            }
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) AccentPrimary.copy(alpha = 0.6f) else SurfaceGlassBorder
                )
            )
        )
    ) {
        Text(
            text = stringResource(R.string.stremio_season, season),
            color = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeRow(video: StremioVideo, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.12f else 0f,
        animationSpec = tween(150),
        label = "bg"
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = AccentPrimary.copy(alpha = bgAlpha)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (video.episode != null) {
                Text(
                    text = String.format("E%02d", video.episode),
                    color = AccentSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(36.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title ?: video.id,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                    fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!video.overview.isNullOrBlank()) {
                    Text(
                        text = video.overview,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!video.released.isNullOrBlank()) {
                Text(
                    text = video.released.take(10),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CollectionMovieCard(video: StremioVideo, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) AccentPrimary else SurfaceGlassBorder
    val title = video.title ?: video.id
    val year = video.released?.take(4)
    val rating = video.overview?.let {
        Regex("""IMDB:\s*([\d.]+)""").find(it)?.groupValues?.getOrNull(1)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A2E),
            focusedContainerColor = Color(0xFF1E1E3A)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, AccentPrimary))
        )
    ) {
        Column {
            // Poster area — if no thumbnail, show gradient placeholder with title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            ) {
                if (video.thumbnail != null) {
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Gradient placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(AccentPrimary.copy(alpha = 0.3f), Color(0xFF1A1A2E))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                // Bottom gradient over poster for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xFF1A1A2E))
                            )
                        )
                )
            }
            // Info row
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (year != null) {
                        Text(text = year, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (rating != null) {
                        Text(
                            text = "★ $rating",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamSelectionOverlay(
    title: String,
    streams: List<StremioStream>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onStreamSelected: (StremioStream) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss(); true
                } else false
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0A0A0F).copy(alpha = 0.97f), Color(0xFF0F0F18))
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(SurfaceGlassBorder, Color.Transparent, SurfaceGlassBorder)
                    ),
                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                )
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isLoading) stringResource(R.string.stremio_searching_streams)
                    else pluralStringResource(R.plurals.stremio_streams_found, streams.size, streams.size),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))

                if (isLoading && streams.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.stremio_searching_addons), color = Color.White.copy(alpha = 0.4f))
                    }
                } else if (streams.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.stremio_no_streams), color = Color.White.copy(alpha = 0.4f))
                    }
                } else {
                    LaunchedEffect(streams.size) {
                        if (streams.isNotEmpty()) {
                            runCatching { focusRequester.requestFocus() }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(streams, key = { idx, _ -> "stream_$idx" }) { index, stream ->
                            OverlayStreamRow(
                                stream = stream,
                                onClick = { onStreamSelected(stream) },
                                focusRequester = if (index == 0) focusRequester else null
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
private fun OverlayStreamRow(
    stream: StremioStream,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "sc"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) AccentSecondary.copy(alpha = 0.12f) else Color.Transparent
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
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.06f))
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = stream.description ?: if (stream.title != stream.name) stream.title else null
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Handles stremio:// deep link navigation within the app.
 * Routes detail links to the appropriate in-app screen.
 */
private fun handleStremioDeepLink(route: StreamRoute.StremioDeepLink, navController: NavController) {
    when (route.action) {
        "detail" -> {
            val id = route.id ?: return
            val type = route.type ?: "movie"
            // For IMDB IDs, go to stremio_detail (which will resolve meta)
            val encodedId = Uri.encode(id)
            navController.navigate("stremio_detail/_auto_/$type/$encodedId")
        }
        "search" -> {
            val query = route.query ?: return
            navController.navigate("search")
        }
        else -> { /* no-op for unsupported deep link actions */ }
    }
}
