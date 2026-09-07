package com.playtorrio.tv.ui.screens.detail

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos

suspend fun FocusRequester.requestFocusAfterFrames(frames: Int = 2): Boolean {
    repeat(frames.coerceAtLeast(0)) {
        withFrameNanos { }
    }
    repeat(4) { attempt ->
        val requested = runCatching { requestFocus() }.getOrDefault(false)
        if (requested) return true
        if (attempt < 3) {
            withFrameNanos { }
        }
    }
    return false
}
