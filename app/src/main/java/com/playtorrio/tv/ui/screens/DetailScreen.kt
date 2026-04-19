package com.playtorrio.tv.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import com.playtorrio.tv.data.model.*
import com.playtorrio.tv.ui.screens.detail.CastInfo
import com.playtorrio.tv.ui.screens.detail.CrewInfo
import kotlinx.coroutines.launch
import com.playtorrio.tv.ui.screens.detail.DetailUiState
import com.playtorrio.tv.ui.screens.detail.DetailViewModel
import com.playtorrio.tv.ui.screens.detail.TorrentOverlay
import android.content.Intent
import android.net.Uri
import com.playtorrio.tv.PlayerActivity
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.stremio.StreamRoute
import com.playtorrio.tv.ui.screens.detail.StreamingSplash

private val AccentPrimary = Color(0xFF818CF8)
private val AccentSecondary = Color(0xFFC084FC)
private val AccentTertiary = Color(0xFF38BDF8)
private val GoldStar = Color(0xFFFFD700)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)
private val GreenSuccess = Color(0xFF4ADE80)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    mediaId: Int,
    isMovie: Boolean,
    navController: NavController,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(mediaId, isMovie) {
        viewModel.load(mediaId, isMovie)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    if (state.isTrailerPlaying) {
                        viewModel.onTrailerEnded()
                    } else if (state.showTorrentOverlay) {
                        viewModel.dismissTorrentOverlay()
                    } else {
                        navController.popBackStack()
                    }
                    true
                } else false
            }
    ) {
        when {
            state.isLoading -> DetailLoading()
            state.error != null -> DetailError(state.error!!)
            else -> DetailContent(state, navController, viewModel, mediaId)
        }

        // Torrent overlay on top of everything
        val context = androidx.compose.ui.platform.LocalContext.current
        TorrentOverlay(
            visible = state.showTorrentOverlay,
            searchLabel = state.torrentSearchLabel,
            results = state.torrentResults,
            isLoading = state.isLoadingTorrents,
            stremioStreams = state.stremioStreams,
            isLoadingStremioStreams = state.isLoadingStremioStreams,
            onDismiss = { viewModel.dismissTorrentOverlay() },
            onTorrentSelected = { torrent ->
                viewModel.dismissTorrentOverlay()
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("magnetUri", torrent.magnetLink)
                    putExtra("title", state.title)
                    putExtra("logoUrl", state.logoUrl)
                    putExtra("backdropUrl", state.backdropUrl)
                    putExtra("posterUrl", state.posterUrl)
                    putExtra("year", state.year)
                    putExtra("rating", state.voteAverage?.let { String.format("%.1f", it) })
                    putExtra("overview", state.overview)
                    putExtra("isMovie", state.isMovie)
                    state.torrentSeasonNumber?.let { putExtra("seasonNumber", it) }
                    state.torrentEpisodeNumber?.let { putExtra("episodeNumber", it) }
                    putExtra("episodeTitle", state.torrentEpisodeTitle)
                    putExtra("tmdbId", mediaId)
                    state.imdbId?.let { putExtra("imdbId", it) }
                }
                context.startActivity(intent)
            },
            onStremioStreamSelected = { stream ->
                viewModel.dismissTorrentOverlay()
                when (val route = StremioService.routeStream(stream)) {
                    is StreamRoute.Torrent -> {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("magnetUri", route.magnet)
                            route.fileIdx?.let { putExtra("fileIdx", it) }
                            putExtra("title", state.title)
                            putExtra("logoUrl", state.logoUrl)
                            putExtra("backdropUrl", state.backdropUrl)
                            putExtra("posterUrl", state.posterUrl)
                            putExtra("year", state.year)
                            putExtra("rating", state.voteAverage?.let { String.format("%.1f", it) })
                            putExtra("overview", state.overview)
                            putExtra("isMovie", state.isMovie)
                            state.torrentSeasonNumber?.let { putExtra("seasonNumber", it) }
                            state.torrentEpisodeNumber?.let { putExtra("episodeNumber", it) }
                            putExtra("episodeTitle", state.torrentEpisodeTitle)
                            putExtra("tmdbId", mediaId)
                            state.imdbId?.let { putExtra("imdbId", it) }
                            // Addon-stream resume context
                            stream.addonId?.let { putExtra("addonId", it) }
                            putExtra("stremioType", if (state.isMovie) "movie" else "series")
                            state.imdbId?.let { imdb ->
                                val sid = if (!state.isMovie && state.torrentSeasonNumber != null && state.torrentEpisodeNumber != null) {
                                    "$imdb:${state.torrentSeasonNumber}:${state.torrentEpisodeNumber}"
                                } else imdb
                                putExtra("stremioId", sid)
                            }
                            (stream.url ?: stream.infoHash)?.let { putExtra("streamPickKey", it) }
                            (stream.name ?: stream.title)?.let { putExtra("streamPickName", it) }
                        }
                        context.startActivity(intent)
                    }
                    is StreamRoute.DirectUrl -> {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("streamUrl", route.url)
                            putExtra("streamReferer", route.headers?.get("Referer") ?: "")
                            putExtra("title", state.title)
                            putExtra("logoUrl", state.logoUrl)
                            putExtra("backdropUrl", state.backdropUrl)
                            putExtra("posterUrl", state.posterUrl)
                            putExtra("year", state.year)
                            putExtra("rating", state.voteAverage?.let { String.format("%.1f", it) })
                            putExtra("overview", state.overview)
                            putExtra("isMovie", state.isMovie)
                            state.torrentSeasonNumber?.let { putExtra("seasonNumber", it) }
                            state.torrentEpisodeNumber?.let { putExtra("episodeNumber", it) }
                            putExtra("episodeTitle", state.torrentEpisodeTitle)
                            putExtra("tmdbId", mediaId)
                            state.imdbId?.let { putExtra("imdbId", it) }
                            // Addon-stream resume context
                            stream.addonId?.let { putExtra("addonId", it) }
                            putExtra("stremioType", if (state.isMovie) "movie" else "series")
                            state.imdbId?.let { imdb ->
                                val sid = if (!state.isMovie && state.torrentSeasonNumber != null && state.torrentEpisodeNumber != null) {
                                    "$imdb:${state.torrentSeasonNumber}:${state.torrentEpisodeNumber}"
                                } else imdb
                                putExtra("stremioId", sid)
                            }
                            (stream.url ?: stream.infoHash)?.let { putExtra("streamPickKey", it) }
                            (stream.name ?: stream.title)?.let { putExtra("streamPickName", it) }
                        }
                        context.startActivity(intent)
                    }
                    is StreamRoute.YouTube -> {
                        val ytIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=${route.ytId}"))
                        context.startActivity(ytIntent)
                    }
                    is StreamRoute.External -> {
                        val extIntent = Intent(Intent.ACTION_VIEW, Uri.parse(route.url))
                        context.startActivity(extIntent)
                    }
                    is StreamRoute.IFrame -> {
                        val iframeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(route.url))
                        context.startActivity(iframeIntent)
                    }
                    is StreamRoute.StremioDeepLink -> {
                        handleStremioDeepLinkFromDetail(route, navController)
                    }
                    StreamRoute.Unsupported -> { /* no-op */ }
                }
            }
        )

        // Streaming splash — shown instead of torrent overlay when Streaming Mode is ON
        StreamingSplash(
            visible = state.showStreamingSplash,
            backdropUrl = state.backdropUrl,
            logoUrl = state.logoUrl,
            title = state.title,
            year = state.year,
            rating = state.voteAverage?.let { String.format("%.1f", it) },
            overview = state.overview,
            isMovie = state.isMovie,
            tmdbId = mediaId,
            seasonNumber = state.streamingSeasonNumber,
            episodeNumber = state.streamingEpisodeNumber,
            episodeTitle = state.streamingEpisodeTitle,
            onDismiss = { viewModel.dismissStreamingSplash() },
            posterUrl = state.posterUrl,
            imdbId = state.imdbId
        )

        // Trailer overlay
        if (state.isTrailerPlaying && state.trailerSource != null) {
            com.playtorrio.tv.ui.components.TrailerFullScreen(
                videoUrl = state.trailerSource!!.videoUrl,
                audioUrl = state.trailerSource!!.audioUrl,
                clientUserAgent = state.trailerSource!!.clientUserAgent,
                logoUrl = state.logoUrl,
                title = state.title,
                onExit = viewModel::onTrailerEnded,
                onError = viewModel::onTrailerFailed
            )
        }

        // Cancel trailer when leaving detail screen
        DisposableEffect(Unit) {
            onDispose { viewModel.cancelTrailer() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState,
    navController: NavController,
    viewModel: DetailViewModel,
    mediaId: Int
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val trailerFocusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Backdrop ──
        state.backdropUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .drawWithContent {
                        drawContent()
                        // Bottom fade
                        drawRect(Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.4f to Color.Transparent,
                                0.7f to Color.Black.copy(alpha = 0.8f),
                                1f to Color.Black
                            )
                        ))
                        // Left fade
                        drawRect(Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.7f),
                                0.15f to Color.Black.copy(alpha = 0.3f),
                                0.4f to Color.Transparent
                            )
                        ))
                        // Top subtle darken
                        drawRect(Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.1f
                        ))
                    },
                contentScale = ContentScale.Crop
            )
        }

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Spacer for backdrop
            Spacer(modifier = Modifier.fillMaxWidth().height(180.dp))

            // ── Hero section: poster + info ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Poster
                Box {
                    AsyncImage(
                        model = state.posterUrl,
                        contentDescription = state.title,
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // Info column
                Column(
                    modifier = Modifier.weight(1f).padding(top = 20.dp)
                ) {
                    // Logo or title
                    if (state.logoUrl != null) {
                        AsyncImage(
                            model = state.logoUrl,
                            contentDescription = state.title,
                            modifier = Modifier.height(72.dp).widthIn(max = 350.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart
                        )
                    } else {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Play Now button (movies only) + Play Trailer button
                    if (state.isMovie) {
                        Spacer(Modifier.height(14.dp))
                        var playFocused by remember { mutableStateOf(false) }
                        var trailerFocused by remember { mutableStateOf(false) }
                        LaunchedEffect(playFocused || trailerFocused) {
                            if (playFocused || trailerFocused) scrollState.animateScrollTo(80)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            onClick = {
                                if (AppPreferences.streamingMode) viewModel.showStreamingSplashForMovie()
                                else viewModel.searchTorrentsForMovie()
                            },
                            modifier = Modifier
                                .onFocusChanged { playFocused = it.isFocused }
                                .then(
                                    if (playFocused) Modifier.border(
                                        1.5.dp,
                                        Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary)),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            scale = CardDefaults.scale(focusedScale = 1.05f),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (playFocused) Brush.horizontalGradient(listOf(AccentPrimary, AccentSecondary))
                                        else Brush.horizontalGradient(listOf(AccentPrimary.copy(alpha = 0.3f), AccentSecondary.copy(alpha = 0.3f)))
                                    )
                                    .padding(horizontal = 28.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Play Now",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        // Play Trailer button
                        Card(
                            onClick = { viewModel.playTrailer() },
                            modifier = Modifier
                                .onFocusChanged { trailerFocused = it.isFocused }
                                .then(
                                    if (trailerFocused) Modifier.border(
                                        1.5.dp,
                                        Brush.horizontalGradient(listOf(AccentSecondary, AccentTertiary)),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            scale = CardDefaults.scale(focusedScale = 1.05f),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (trailerFocused) Brush.horizontalGradient(listOf(AccentSecondary, AccentTertiary))
                                        else Brush.horizontalGradient(listOf(AccentSecondary.copy(alpha = 0.3f), AccentTertiary.copy(alpha = 0.3f)))
                                    )
                                    .padding(horizontal = 28.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (state.isLoadingTrailer) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Trailer",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = if (state.isLoadingTrailer) "Loading..." else "Play Trailer",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        } // end Row
                    } else {
                        // TV shows: just the trailer button
                        Spacer(Modifier.height(14.dp))
                        var trailerFocused by remember { mutableStateOf(false) }
                        Card(
                            onClick = { viewModel.playTrailer() },
                            modifier = Modifier
                                .focusRequester(trailerFocusRequester)
                                .onFocusChanged {
                                    trailerFocused = it.isFocused
                                    // When focused, scroll to the very top so the backdrop
                                    // and title/logo are fully visible.
                                    if (it.isFocused) {
                                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                                    }
                                }
                                .then(
                                    if (trailerFocused) Modifier.border(
                                        1.5.dp,
                                        Brush.horizontalGradient(listOf(AccentSecondary, AccentTertiary)),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            scale = CardDefaults.scale(focusedScale = 1.05f),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (trailerFocused) Brush.horizontalGradient(listOf(AccentSecondary, AccentTertiary))
                                        else Brush.horizontalGradient(listOf(AccentSecondary.copy(alpha = 0.3f), AccentTertiary.copy(alpha = 0.3f)))
                                    )
                                    .padding(horizontal = 28.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (state.isLoadingTrailer) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Trailer",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = if (state.isLoadingTrailer) "Loading..." else "Play Trailer",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Tagline
                    state.tagline?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "\"$it\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.3.sp
                            ),
                            color = AccentSecondary.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Metadata pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type badge
                        val typeLabel = if (state.isMovie) "MOVIE" else "SERIES"
                        val badgeColor = if (state.isMovie) AccentPrimary else AccentTertiary
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 10.sp
                            ),
                            color = badgeColor,
                            modifier = Modifier
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )

                        // Year
                        Text(
                            text = if (state.isMovie) state.year ?: "" else state.yearRange ?: state.year ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        // Runtime or seasons
                        if (state.isMovie) {
                            state.runtime?.let {
                                Text("•", color = Color.White.copy(alpha = 0.3f))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            state.numberOfSeasons?.let {
                                Text("•", color = Color.White.copy(alpha = 0.3f))
                                Text(
                                    text = "$it Season${if (it != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Rating
                        state.voteAverage?.let { rating ->
                            Text("•", color = Color.White.copy(alpha = 0.3f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("★", fontSize = 16.sp, color = GoldStar)
                                Text(
                                    text = " %.1f".format(rating),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = GoldStar
                                )
                                state.voteCount?.let { count ->
                                    Text(
                                        text = " (${formatCount(count)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        // Status
                        state.status?.let {
                            val statusColor = when (it) {
                                "Returning Series" -> GreenSuccess
                                "Ended", "Canceled" -> Color(0xFFF87171)
                                "Released" -> GreenSuccess
                                else -> Color.White.copy(alpha = 0.5f)
                            }
                            Text(
                                text = it.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp
                                ),
                                color = statusColor,
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Genre chips
                    if (state.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.genres.take(5).forEach { genre ->
                                Text(
                                    text = genre.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .background(SurfaceGlass, RoundedCornerShape(12.dp))
                                        .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Overview
                    state.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 6, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Budget / Revenue (movies)
                    if (state.isMovie && (state.budget != null || state.revenue != null)) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            state.budget?.let {
                                MetadataStat("Budget", "$${formatMoney(it)}")
                            }
                            state.revenue?.let {
                                MetadataStat("Revenue", "$${formatMoney(it)}")
                            }
                        }
                    }

                    // Directors
                    if (state.directors.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.isMovie) "Director" else "Directed by",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.width(8.dp))
                            state.directors.take(3).forEachIndexed { i, d ->
                                if (i > 0) Text(", ", color = Color.White.copy(alpha = 0.3f))
                                Text(
                                    text = d.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = AccentPrimary.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // Writers
                    if (state.writers.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Written by",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.width(8.dp))
                            state.writers.take(3).forEachIndexed { i, w ->
                                if (i > 0) Text(", ", color = Color.White.copy(alpha = 0.3f))
                                Text(
                                    text = w.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = AccentSecondary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Created By (TV)
                    if (state.createdBy.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Created by",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.width(8.dp))
                            state.createdBy.forEachIndexed { i, c ->
                                if (i > 0) Text(", ", color = Color.White.copy(alpha = 0.3f))
                                Text(
                                    text = c.name ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = AccentTertiary.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Cast row ──
            // Pressing UP from the Cast row redirects focus (and scrolls camera) to the
            // trailer button at the top — same flow as Home page row navigation.
            if (state.cast.isNotEmpty()) {
                Column(
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            try {
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                                trailerFocusRequester.requestFocus()
                                true
                            } catch (_: Exception) { false }
                        } else false
                    }
                ) {
                    SectionDivider("Cast")
                    Spacer(Modifier.height(12.dp))
                    CastRow(state.cast, state.isMovie, navController, onScrollToTop = {
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    })
                    Spacer(Modifier.height(28.dp))
                }
            }

            // ── Season & Episode selector (TV only) ──
            if (!state.isMovie && state.seasons.isNotEmpty()) {
                SectionDivider("Seasons & Episodes")
                Spacer(Modifier.height(12.dp))
                SeasonSelector(
                    seasons = state.seasons,
                    selectedSeason = state.selectedSeason,
                    onSeasonSelected = { viewModel.selectSeason(mediaId, it) }
                )
                Spacer(Modifier.height(16.dp))
                EpisodeList(
                    episodes = state.episodes,
                    isLoading = state.isLoadingEpisodes,
                    onEpisodeClick = { episode ->
                        if (AppPreferences.streamingMode) viewModel.showStreamingSplashForEpisode(episode)
                        else viewModel.searchTorrentsForEpisode(episode)
                    }
                )
                Spacer(Modifier.height(28.dp))
            }

            // ── Production Companies ──
            if (state.productionCompanies.isNotEmpty()) {
                SectionDivider("Studios")
                Spacer(Modifier.height(12.dp))
                StudioRow(state.productionCompanies, navController)
                Spacer(Modifier.height(28.dp))
            }

            // ── Similar ──
            if (state.similar.isNotEmpty()) {
                SectionDivider("More Like This")
                Spacer(Modifier.height(12.dp))
                MediaRow(state.similar, navController)
                Spacer(Modifier.height(28.dp))
            }

            // ── Recommendations ──
            if (state.recommendations.isNotEmpty()) {
                SectionDivider("Recommended")
                Spacer(Modifier.height(12.dp))
                MediaRow(state.recommendations, navController)
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// SECTION DIVIDER
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionDivider(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(
                    colors = listOf(SurfaceGlassBorder, Color.Transparent)
                ))
        )
    }
}

// ════════════════════════════════════════════════════════════
// CAST ROW — clickable circular avatars
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastRow(cast: List<CastInfo>, isMovie: Boolean, navController: NavController, onScrollToTop: () -> Unit = {}) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onScrollToTop()
                    // For movies: let focus system handle Up (to play button)
                    // For TV: consume the event (no focusable above cast)
                    !isMovie
                } else false
            }
            .focusGroup()
    ) {
        items(cast, key = { it.id }) { person ->
            CastCard(person, isMovie, onClick = {
                navController.navigate("person/${person.id}")
            })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastCard(person: CastInfo, isMovie: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isFocused) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
    )
    val borderAlpha by animateFloatAsState(
        if (isFocused) 1f else 0f, tween(250), label = "b"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(80.dp)
                .then(
                    if (borderAlpha > 0f) Modifier.border(
                        2.dp,
                        Brush.sweepGradient(listOf(
                            AccentPrimary.copy(alpha = borderAlpha),
                            AccentSecondary.copy(alpha = borderAlpha),
                            AccentTertiary.copy(alpha = borderAlpha),
                            AccentPrimary.copy(alpha = borderAlpha)
                        )),
                        CircleShape
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(CircleShape)
        ) {
            if (person.profileUrl != null) {
                AsyncImage(
                    model = person.profileUrl,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(SurfaceGlass),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(1),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        person.character?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = AccentSecondary.copy(alpha = if (isFocused) 0.9f else 0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!isMovie && person.episodeCount != null) {
            Text(
                text = "${person.episodeCount} ep",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// SEASON SELECTOR — horizontal pill tabs
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonSelector(
    seasons: List<SeasonSummary>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeason
            var isFocused by remember { mutableStateOf(false) }
            val bgAlpha by animateFloatAsState(
                if (isSelected) 0.25f else if (isFocused) 0.15f else 0.06f,
                tween(200), label = "bg"
            )
            val borderColor = if (isSelected) AccentPrimary else if (isFocused) Color.White.copy(alpha = 0.3f) else SurfaceGlassBorder

            Card(
                onClick = { onSeasonSelected(season.seasonNumber) },
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                scale = CardDefaults.scale(focusedScale = 1f),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) AccentPrimary.copy(alpha = bgAlpha)
                            else Color.White.copy(alpha = bgAlpha)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = season.name ?: "Season ${season.seasonNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.8f)
                        )
                        season.episodeCount?.let {
                            Text(
                                text = "$it episodes",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// EPISODE LIST — horizontal scrollable episode cards
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeList(episodes: List<Episode>, isLoading: Boolean, onEpisodeClick: (Episode) -> Unit = {}) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Loading episodes...",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(episodes, key = { it.id }) { episode ->
            EpisodeCard(episode, onEpisodeClick)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(episode: Episode, onEpisodeClick: (Episode) -> Unit = {}) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isFocused) 1.05f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
    )

    Column(
        modifier = Modifier
            .width(280.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Card(
            onClick = { onEpisodeClick(episode) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(
                    if (isFocused) Modifier.border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(
                            AccentPrimary.copy(alpha = 0.8f),
                            AccentSecondary.copy(alpha = 0.6f),
                            AccentTertiary.copy(alpha = 0.5f),
                            AccentPrimary.copy(alpha = 0.8f)
                        )),
                        RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (episode.stillUrl != null) {
                    AsyncImage(
                        model = episode.stillUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "E${episode.episodeNumber}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }

                // Episode number badge
                Text(
                    text = "E${episode.episodeNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold, fontSize = 10.sp
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                // Runtime badge
                episode.runtimeFormatted?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Rating
                episode.voteAverage?.takeIf { it > 0 }?.let { rating ->
                    Text(
                        text = "★ %.1f".format(rating),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 9.sp
                        ),
                        color = GoldStar,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Bottom gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        ))
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = episode.name ?: "Episode ${episode.episodeNumber}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        episode.overview?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                color = Color.White.copy(alpha = if (isFocused) 0.5f else 0.3f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// STUDIO ROW — clickable company logos/names
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioRow(companies: List<ProductionCompany>, navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(companies, key = { it.id }) { company ->
            var isFocused by remember { mutableStateOf(false) }
            val borderAlpha by animateFloatAsState(
                if (isFocused) 1f else 0f, tween(200), label = "b"
            )

            Card(
                onClick = { navController.navigate("studio/${company.id}") },
                modifier = Modifier
                    .height(60.dp)
                    .widthIn(min = 120.dp)
                    .then(
                        if (borderAlpha > 0f) Modifier.border(
                            1.dp, AccentPrimary.copy(alpha = borderAlpha), RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .onFocusChanged { isFocused = it.isFocused },
                scale = CardDefaults.scale(focusedScale = 1.05f),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceGlass)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (company.logoUrl != null) {
                        AsyncImage(
                            model = company.logoUrl,
                            contentDescription = company.name,
                            modifier = Modifier.height(30.dp).widthIn(max = 100.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = company.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// MEDIA ROW — similar / recommended (reusable card row)
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaRow(items: List<TmdbMedia>, navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.focusGroup()
    ) {
        items(items, key = { it.id }) { media ->
            MediaPosterCard(media, onClick = {
                navController.navigate("detail/${media.id}/${media.isMovie}")
            })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaPosterCard(media: TmdbMedia, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isFocused) 1.08f else 0.95f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "s"
    )
    val cardAlpha by animateFloatAsState(
        if (isFocused) 1f else 0.65f, tween(300), label = "a"
    )

    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = cardAlpha }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (isFocused) Modifier.border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(
                            Color.White.copy(alpha = 0.6f),
                            AccentPrimary.copy(alpha = 0.8f),
                            AccentSecondary.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.4f)
                        )),
                        RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = media.posterUrl ?: media.cardBackdropUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                // Bottom gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        ))
                )
                // Rating
                media.voteAverage?.let { rating ->
                    Text(
                        text = "★ %.1f".format(rating),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, fontSize = 9.sp
                        ),
                        color = GoldStar,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        media.year?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// METADATA STAT — small label+value
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetadataStat(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp, letterSpacing = 1.sp
            ),
            color = Color.White.copy(alpha = 0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

// ════════════════════════════════════════════════════════════
// LOADING + ERROR
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "load")
            val progress by infiniteTransition.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
                label = "p"
            )
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .drawWithContent {
                        drawContent()
                        val barW = size.width * 0.3f
                        val x = -barW + (size.width + barW) * progress
                        drawRect(Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, AccentPrimary, AccentSecondary, Color.Transparent),
                            startX = x - barW, endX = x + barW
                        ))
                    }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "LOADING", style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 4.sp, fontWeight = FontWeight.Light
                ), color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailError(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.Red.copy(alpha = 0.8f))
    }
}

// ════════════════════════════════════════════════════════════
// UTILS
// ════════════════════════════════════════════════════════════

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun formatMoney(amount: Long): String = when {
    amount >= 1_000_000_000 -> "%.1fB".format(amount / 1_000_000_000.0)
    amount >= 1_000_000 -> "%.0fM".format(amount / 1_000_000.0)
    amount >= 1_000 -> "%.0fK".format(amount / 1_000.0)
    else -> amount.toString()
}

private fun handleStremioDeepLinkFromDetail(route: StreamRoute.StremioDeepLink, navController: NavController) {
    when (route.action) {
        "detail" -> {
            val id = route.id ?: return
            val type = route.type ?: "movie"
            val encodedId = android.net.Uri.encode(id)
            navController.navigate("stremio_detail/_auto_/$type/$encodedId")
        }
        "search" -> {
            navController.navigate("search")
        }
        else -> { /* no-op */ }
    }
}
