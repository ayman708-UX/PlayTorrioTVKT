package com.playtorrio.tv.ui.screens.audiobook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.core.audiobook.model.Audiobook
import com.playtorrio.tv.core.audiobook.model.AudiobookProgress
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudiobookCard(
    book: Audiobook,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    progress: AudiobookProgress? = null,
    focusRequester: FocusRequester? = null,
    onFocus: (Audiobook) -> Unit = {},
    onClick: (Audiobook) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(180)
            onFocus(book)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "audiobookCardScale"
    )

    Column(
        modifier = modifier
            .width(160.dp)
            .zIndex(if (isFocused) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            onClick = { onClick(book) },
            modifier = Modifier
                .size(160.dp)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF131722),
                focusedContainerColor = Color(0xFF1E2435)
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border.None
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Artwork
                if (book.coverImage.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1F30)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Bottom subtle gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Playing indicator / Equalizer
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.9f))
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Source badge on top-left
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = book.source.take(12),
                        color = Color(0xFFC084FC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Hover play overlay when focused
                if (isFocused && !isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Continue Listening Progress Bar
                if (progress != null && progress.durationMs > 0L) {
                    val progressFraction = (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFF8B5CF6),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }

                // Focus Border Overlay on top of artwork
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                BorderStroke(3.dp, Color(0xFFC084FC)),
                                RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = book.title,
            color = if (isFocused) Color(0xFFC084FC) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Author / Chapter subtitle
        val subtitle = if (progress != null) {
            progress.chapterTitle.ifBlank { "Chapter ${progress.chapterIndex + 1}" }
        } else {
            book.author.ifBlank { book.source.replaceFirstChar { it.uppercase() } }
        }

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
