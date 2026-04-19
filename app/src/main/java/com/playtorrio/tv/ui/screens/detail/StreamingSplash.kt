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
import com.playtorrio.tv.data.streaming.StreamExtractorService
import com.playtorrio.tv.data.streaming.StreamResult
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamingSplash(
    visible: Boolean,
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    year: String?,
    rating: String?,
    overview: String?,
    isMovie: Boolean,
    tmdbId: Int,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    onDismiss: () -> Unit,
    posterUrl: String? = null,
    imdbId: String? = null,
    forceSourceIndex: Int? = null,
    resumePositionMs: Long? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200))
    ) {
        val context = LocalContext.current
        var statusText by remember { mutableStateOf("Getting sources for you…") }
        var isError by remember { mutableStateOf(false) }

        LaunchedEffect(visible, tmdbId, seasonNumber, episodeNumber) {
            if (!visible) return@LaunchedEffect
            isError = false
            statusText = "Finding best source…"

            // Sequential extraction to avoid TV lag from concurrent WebView work.
            var winner: Pair<StreamResult, StreamExtractorService.Source>? = null
            val priorityOrder = buildList {
                // If resuming from a remembered source, try it first.
                forceSourceIndex?.let { add(it) }
                addAll(listOf(8, 3, 2))
            }.distinct() // VidFun, Vidlink, Videasy (or remembered first)
            val orderedSources = buildList {
                priorityOrder.forEach { idx ->
                    StreamExtractorService.SOURCES.find { it.index == idx }?.let { add(it) }
                }
                StreamExtractorService.SOURCES
                    .filterNot { src -> priorityOrder.contains(src.index) }
                    .forEach { add(it) }
            }

            val total = orderedSources.size
            for ((idx, source) in orderedSources.withIndex()) {
                statusText = "Trying ${source.name} (${idx + 1}/$total)…"

                val result = StreamExtractorService.extract(
                    context = context,
                    sourceIdx = source.index,
                    tmdbId = tmdbId,
                    season = seasonNumber,
                    episode = episodeNumber
                )

                if (result != null) {
                    winner = result to source
                    break
                }

                // Small breathing room for UI/frame scheduling between heavy attempts.
                delay(120)
            }

            if (winner != null) {
                val (streamResult, source) = winner
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("streamUrl", streamResult.url)
                    putExtra("streamReferer", streamResult.referer)
                    putExtra("sourceIndex", source.index)
                    putExtra("title", title)
                    putExtra("logoUrl", logoUrl)
                    putExtra("backdropUrl", backdropUrl)
                    putExtra("year", year)
                    putExtra("rating", rating)
                    putExtra("overview", overview)
                    putExtra("isMovie", isMovie)
                    seasonNumber?.let { putExtra("seasonNumber", it) }
                    episodeNumber?.let { putExtra("episodeNumber", it) }
                    putExtra("episodeTitle", episodeTitle)
                    putExtra("tmdbId", tmdbId)
                    posterUrl?.let { putExtra("posterUrl", it) }
                    imdbId?.let { putExtra("imdbId", it) }
                    resumePositionMs?.let { putExtra("resumePositionMs", it) }
                }
                context.startActivity(intent)
                onDismiss()
            } else {
                statusText = "Could not find a stream. Please try again."
                isError = true
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
            // Dimmed backdrop
            backdropUrl?.let {
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

            // Center content: logo / title → spinner → status
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .height(72.dp)
                            .padding(horizontal = 24.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = title,
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
