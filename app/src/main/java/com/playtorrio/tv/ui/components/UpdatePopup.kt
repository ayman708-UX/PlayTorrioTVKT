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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
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
    onLater: () -> Unit
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
                .background(Color.Black.copy(alpha = 0.85f))
                // Swallow any DPAD events that don't hit the buttons so the
                // screen behind never sees them.
                .onPreviewKeyEvent { true },
            contentAlignment = Alignment.Center
        ) {
            val updateFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                runCatching { updateFocus.requestFocus() }
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 520.dp, max = 720.dp)
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
                    .padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Update Available",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Version $versionName is ready to install.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp
                )

                if (releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightInScrollable()
                    ) {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.verticalScroll(scroll)) {
                            Text(
                                text = releaseNotes.trim(),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateDialogButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseColor = if (primary) Color(0xFFE50914) else Color.White.copy(alpha = 0.12f)
    val focusedColor = if (primary) Color(0xFFFF1F2E) else Color.White.copy(alpha = 0.28f)
    val border = if (focused) Color.White else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) focusedColor else baseColor)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onClick(); true
                        }
                        // Block vertical navigation so focus can't escape the dialog.
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                } else false
            }
            .clickable(onClick = onClick)
            .widthIn(min = 160.dp)
            .padding(horizontal = 28.dp, vertical = 14.dp)
            .wrapContentSize(Alignment.Center)
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
