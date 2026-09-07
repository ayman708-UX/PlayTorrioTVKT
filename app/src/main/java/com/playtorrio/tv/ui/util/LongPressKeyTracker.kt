package com.playtorrio.tv.ui.util

import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberLongPressKeyTracker(): LongPressKeyTracker = remember { LongPressKeyTracker() }

fun isSelectKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
}

class LongPressKeyTracker(
    private val timeoutMillis: Long = ViewConfiguration.getLongPressTimeout().toLong()
) {
    private var pressedKeyCode: Int? = null
    private var pressedAtMillis: Long = 0L
    private var handledLongPress = false

    fun handle(
        event: KeyEvent,
        isLongPressKey: (Int) -> Boolean,
        onLongPress: () -> Unit
    ): Boolean {
        if (!isLongPressKey(event.keyCode)) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(event, onLongPress)
            KeyEvent.ACTION_UP -> handleUp(event, onLongPress)
            else -> false
        }
    }

    private fun handleDown(event: KeyEvent, onLongPress: () -> Unit): Boolean {
        if (event.repeatCount == 0 || pressedKeyCode != event.keyCode) {
            pressedKeyCode = event.keyCode
            pressedAtMillis = event.eventTime
            handledLongPress = false
        }

        if (event.isLongPress || event.repeatCount > 0 || heldDurationMillis(event) >= timeoutMillis) {
            if (!handledLongPress) {
                handledLongPress = true
                onLongPress()
            }
            return true
        }

        return false
    }

    private fun handleUp(event: KeyEvent, onLongPress: () -> Unit): Boolean {
        val wasHandled = handledLongPress && pressedKeyCode == event.keyCode
        val isLongPress = !wasHandled && heldDurationMillis(event) >= timeoutMillis

        reset()

        if (isLongPress) {
            onLongPress()
            return true
        }

        return wasHandled
    }

    private fun heldDurationMillis(event: KeyEvent): Long {
        val startedAt = if (pressedKeyCode == event.keyCode) pressedAtMillis else event.downTime
        return event.eventTime - startedAt
    }

    private fun reset() {
        pressedKeyCode = null
        pressedAtMillis = 0L
        handledLongPress = false
    }
}
