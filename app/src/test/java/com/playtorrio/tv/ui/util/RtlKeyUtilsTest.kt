package com.playtorrio.tv.ui.util

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RtlKeyUtilsTest {
    @Test
    fun `getClearHistoryDpadKey returns LEFT when RTL is true`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, RtlKeyUtils.getClearHistoryDpadKey(isRtl = true))
    }

    @Test
    fun `getClearHistoryDpadKey returns RIGHT when RTL is false`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, RtlKeyUtils.getClearHistoryDpadKey(isRtl = false))
    }
}
