package com.playtorrio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Modal update prompt that fully steals focus from the home screen.
 *
 * Implementation notes:
 *  - Uses [Dialog] (creates its own Window, so DPAD events cannot reach the
 *    HomeScreen behind it).
 *  - Two buttons only: "Update Now" and "Later".
 *  - Initial focus lands on "Update Now". DPAD left/right toggles between them,
 *    DPAD up/down is consumed (no focus escape).
 *  - Back button = Later.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdatePopup(
    versionName: String,
    releaseNotes: String,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    isDownloading: Boolean = false,
    downloadedBytes: Long = 0L,
    totalBytes: Long = 0L,
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // Full-screen scrim so nothing behind is visible / interactable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            val updateFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                runCatching { updateFocus.requestFocus() }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 720.dp)
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Update Available",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Version $versionName is ready to install.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp
                )

                // Scrollable middle area so buttons stay pinned at the bottom.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 14.dp, bottom = 14.dp)
                ) {
                    if (releaseNotes.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = releaseNotes.trim(),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (isDownloading) {
                    val pct = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .progressSemantics(),
                                color = Color(0xFFE50914),
                                trackColor = Color.White.copy(alpha = 0.10f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Downloading… ${(pct * 100).toInt()}%  (${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)})",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .progressSemantics(),
                                color = Color(0xFFE50914),
                                trackColor = Color.White.copy(alpha = 0.10f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Downloading… ${formatBytes(downloadedBytes)}",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        UpdateDialogButton(
                            text = "Later",
                            primary = false,
                            modifier = Modifier,
                            onClick = onLater
                        )
                        UpdateDialogButton(
                            text = "Update Now",
                            primary = true,
                            modifier = Modifier.focusRequester(updateFocus),
                            onClick = onUpdateNow
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateDialogButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (primary) Color(0xFFE50914) else Color.White.copy(alpha = 0.14f)
    val focusedContainer = if (primary) Color(0xFFFF1F2E) else Color.White.copy(alpha = 0.30f)
    Button(
        onClick = onClick,
        modifier = modifier.widthIn(min = 130.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = Color.White,
            focusedContainerColor = focusedContainer,
            focusedContentColor = Color.White,
        ),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

/** Caps the release-notes scroll area to a sensible TV-friendly height. */
@Composable
private fun Modifier.heightInScrollable(): Modifier =
    this.then(Modifier.height(140.dp))

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}
