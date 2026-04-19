package com.playtorrio.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.data.trailer.YoutubeChunkedDataSourceFactory

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrailerFullScreen(
    videoUrl: String,
    audioUrl: String?,
    clientUserAgent: String?,
    logoUrl: String?,
    title: String,
    onExit: () -> Unit,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasRenderedFirstFrame by remember(videoUrl) { mutableStateOf(false) }
    val playerAlpha by animateFloatAsState(
        targetValue = if (hasRenderedFirstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "trailerAlpha"
    )

    val player = remember(videoUrl, audioUrl) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 5_000, 10_000)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onExit()
            }
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TrailerFullScreen", "Player error: ${error.message}")
                onError()
            }
        }
        player.addListener(listener)

        val dsFactory = YoutubeChunkedDataSourceFactory(clientUserAgent = clientUserAgent)
        val isHls = videoUrl.contains("manifest/hls") || videoUrl.contains("/api/manifest/")
        if (!audioUrl.isNullOrBlank()) {
            val factory = DefaultMediaSourceFactory(dsFactory)
            val videoSource = factory.createMediaSource(MediaItem.fromUri(videoUrl))
            val audioSource = factory.createMediaSource(MediaItem.fromUri(audioUrl))
            player.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else if (isHls) {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
        } else {
            val factory = DefaultMediaSourceFactory(dsFactory)
            player.setMediaSource(factory.createMediaSource(MediaItem.fromUri(videoUrl)))
        }
        player.prepare()
        player.playWhenReady = true

        onDispose {
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    // System back button exits the trailer overlay (not the activity)
    BackHandler { onExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onExit()
                    true
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = playerAlpha }
                .clipToBounds()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .fillMaxHeight(0.3f)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset.Zero,
                        radius = 600f
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 32.dp)
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .height(56.dp)
                        .width(200.dp)
                        .graphicsLayer { alpha = playerAlpha },
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.graphicsLayer { alpha = playerAlpha }
                )
            }
        }
    }
}
