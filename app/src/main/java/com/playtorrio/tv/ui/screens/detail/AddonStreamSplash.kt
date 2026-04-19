package com.playtorrio.tv.ui.screens.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.PlayerActivity
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.stremio.StreamRoute
import com.playtorrio.tv.data.stremio.StremioStream
import com.playtorrio.tv.data.watch.WatchProgress

/**
 * Splash that re-fetches a Stremio addon's /stream endpoint and tries to find
 * the same stream the user was previously watching (or a similar one — these
 * lists update over time). Used by the home-screen continue-watching slider
 * for [com.playtorrio.tv.data.watch.WatchKind.ADDON_STREAM] entries.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddonStreamSplash(
    visible: Boolean,
    progress: WatchProgress?,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible && progress != null,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200))
    ) {
        val context = LocalContext.current
        val entry = progress ?: return@AnimatedVisibility
        var statusText by remember { mutableStateOf("Re-fetching addon streams…") }
        var isError by remember { mutableStateOf(false) }

        LaunchedEffect(visible, entry.key) {
            if (!visible) return@LaunchedEffect
            isError = false
            statusText = "Re-fetching addon streams…"

            val addonId = entry.addonId
            val type = entry.stremioType
            val id = entry.stremioId
            if (addonId.isNullOrBlank() || type.isNullOrBlank() || id.isNullOrBlank()) {
                statusText = "Missing addon info."
                isError = true
                return@LaunchedEffect
            }

            val addons = StremioAddonRepository.getAddons()
            if (addons.isEmpty()) {
                statusText = "No Stremio addons installed."
                isError = true
                return@LaunchedEffect
            }

            val streams: List<StremioStream> = try {
                StremioService.getStreams(addons, type, id, preferredAddonId = addonId)
            } catch (_: Exception) {
                emptyList()
            }

            if (streams.isEmpty()) {
                statusText = "Couldn't fetch streams. Try again later."
                isError = true
                return@LaunchedEffect
            }

            // Try to find the same stream by url or infoHash. If the addon's
            // list rotates, fall back to (a) same name/title from same addon
            // then (b) first stream from the same addon then (c) first overall.
            val pickKey = entry.streamPickKey
            val pickName = entry.streamPickName
            val sameAddon = streams.filter { it.addonId == addonId }
            val chosen: StremioStream =
                streams.firstOrNull { pickKey != null && (it.url == pickKey || it.infoHash == pickKey) }
                    ?: sameAddon.firstOrNull { pickName != null && (it.name == pickName || it.title == pickName) }
                    ?: sameAddon.firstOrNull()
                    ?: streams.first()

            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra("title", entry.title)
                putExtra("logoUrl", entry.logoUrl)
                putExtra("backdropUrl", entry.backdropUrl)
                putExtra("posterUrl", entry.posterUrl)
                putExtra("year", entry.year)
                putExtra("rating", entry.rating)
                putExtra("overview", entry.overview)
                putExtra("isMovie", entry.isMovie)
                entry.seasonNumber?.let { putExtra("seasonNumber", it) }
                entry.episodeNumber?.let { putExtra("episodeNumber", it) }
                putExtra("episodeTitle", entry.episodeTitle)
                if (entry.tmdbId > 0) putExtra("tmdbId", entry.tmdbId)
                entry.imdbId?.let { putExtra("imdbId", it) }
                // Re-stamp addon resume context so saves land on the same key.
                putExtra("addonId", addonId)
                putExtra("stremioType", type)
                putExtra("stremioId", id)
                (chosen.url ?: chosen.infoHash)?.let { putExtra("streamPickKey", it) }
                (chosen.name ?: chosen.title)?.let { putExtra("streamPickName", it) }
                putExtra("resumePositionMs", entry.positionMs)
            }

            when (val route = StremioService.routeStream(chosen)) {
                is StreamRoute.Torrent -> {
                    intent.putExtra("magnetUri", route.magnet)
                    route.fileIdx?.let { intent.putExtra("fileIdx", it) }
                    context.startActivity(intent)
                    onDismiss()
                }
                is StreamRoute.DirectUrl -> {
                    intent.putExtra("streamUrl", route.url)
                    intent.putExtra("streamReferer", route.headers?.get("Referer") ?: "")
                    context.startActivity(intent)
                    onDismiss()
                }
                else -> {
                    statusText = "This stream type can't be resumed automatically."
                    isError = true
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        onDismiss(); true
                    } else false
                }
        ) {
            entry.backdropUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.6f))
                        },
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (entry.logoUrl != null) {
                    AsyncImage(
                        model = entry.logoUrl,
                        contentDescription = entry.title,
                        modifier = Modifier
                            .height(72.dp)
                            .padding(horizontal = 24.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = entry.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(36.dp))

                if (!isError) {
                    CircularProgressIndicator(
                        color = Color(0xFF818CF8),
                        modifier = Modifier.size(42.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = if (isError) Color(0xFFF87171) else Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
