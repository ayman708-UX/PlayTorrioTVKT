package com.playtorrio.tv.ui.util

import android.view.KeyEvent

object RtlKeyUtils {
    /**
     * Returns the appropriate DPAD key to navigate from the search text field
     * to the "Clear search history" button.
     * In LTR (Left-To-Right) the clear button is on the right -> KEYCODE_DPAD_RIGHT.
     * In RTL (Right-To-Left) the clear button is on the left -> KEYCODE_DPAD_LEFT.
     */
    fun getClearHistoryDpadKey(isRtl: Boolean): Int {
        return if (isRtl) {
            KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            KeyEvent.KEYCODE_DPAD_RIGHT
        }
    }
}
