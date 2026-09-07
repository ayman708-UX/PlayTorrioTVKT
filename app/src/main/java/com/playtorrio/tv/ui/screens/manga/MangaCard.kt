package com.playtorrio.tv.ui.screens.manga

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.Text
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.playtorrio.tv.ui.util.rememberLongPressKeyTracker
import com.playtorrio.tv.ui.util.isSelectKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.playtorrio.tv.core.manga.model.Manga
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MangaCard(
    manga: Manga,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: (Manga) -> Unit = {},
    onClick: (Manga) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(180)
            onFocus(manga)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "mangaCardScale"
    )

    Column(
        modifier = modifier
            .width(150.dp)
            .zIndex(if (isFocused) 10f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            onClick = { onClick(manga) },
            modifier = Modifier
                .width(150.dp)
                .height(220.dp)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF131722),
                focusedContainerColor = Color(0xFF1E2435)
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Cover Poster
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.coverNormal.ifBlank { manga.coverSmall })
                        .crossfade(true)
                        .build(),
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )

                // Top Type Badge
                if (manga.type.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = manga.type.uppercase(),
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF070A13).copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Status or Year pill at bottom
                if (manga.status.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = manga.status,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = manga.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subtext = when {
            manga.author.isNotBlank() -> manga.author
            manga.year.isNotBlank() -> manga.year
            manga.tags.isNotEmpty() -> manga.tags.first()
            else -> "Manga"
        }
        Text(
            text = subtext,
            color = if (isFocused) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MangaContinueReadingCard(
    progress: com.playtorrio.tv.core.manga.MangaReadingProgress,
    modifier: Modifier = Modifier,
    onClick: (com.playtorrio.tv.core.manga.MangaReadingProgress) -> Unit = {},
    onLongClick: ((com.playtorrio.tv.core.manga.MangaReadingProgress) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "mangaContinueScale"
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
            onClick = {
                if (longPressTriggered) {
                    longPressTriggered = false
                } else {
                    onClick(progress)
                }
            },
            modifier = Modifier
                .width(160.dp)
                .height(230.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .pointerInput(progress.mangaId) {
                    detectTapGestures(
                        onLongPress = {
                            longPressTriggered = true
                            onLongClick?.invoke(progress)
                        }
                    )
                }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == AndroidKeyEvent.ACTION_DOWN && onLongClick != null) {
                        if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                            longPressTriggered = true
                            onLongClick(progress)
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (onLongClick != null &&
                        longPressKeyTracker.handle(native, ::isSelectKey) {
                            longPressTriggered = true
                            onLongClick(progress)
                        }
                    ) {
                        if (native.action == AndroidKeyEvent.ACTION_UP) {
                            longPressTriggered = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (native.action == AndroidKeyEvent.ACTION_UP &&
                        longPressTriggered &&
                        (isSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU)
                    ) {
                        longPressTriggered = false
                        return@onPreviewKeyEvent true
                    }
                    false
                },
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF131722),
                focusedContainerColor = Color(0xFF1E2435)
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Cover
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(progress.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )

                // Top Resume Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0284C7))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "RESUME",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                // Bottom Gradient & Progress info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF070A13).copy(alpha = 0.7f),
                                    Color(0xFF070A13).copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Text(
                        text = progress.chapterName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (progress.totalPages > 0) "Page ${progress.pageIndex + 1} of ${progress.totalPages}"
                        else "Page ${progress.pageIndex + 1}",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Bar
                    val fraction = if (progress.totalPages > 0) {
                        ((progress.pageIndex + 1).toFloat() / progress.totalPages.toFloat()).coerceIn(0.05f, 1.0f)
                    } else 0.5f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF38BDF8))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = progress.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Continue Reading",
            color = if (isFocused) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

