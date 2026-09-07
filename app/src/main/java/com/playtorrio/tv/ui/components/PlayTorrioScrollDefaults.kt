package com.playtorrio.tv.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import com.playtorrio.tv.ui.theme.PlayTorrioFocus
import com.playtorrio.tv.ui.theme.PlayTorrioMotion

@OptIn(ExperimentalFoundationApi::class)
object PlayTorrioScrollDefaults {
    val smoothScrollSpec = object : BringIntoViewSpec {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override val scrollAnimationSpec: AnimationSpec<Float> = spring(
            dampingRatio = 0.95f,
            stiffness = PlayTorrioMotion.tokens.durations.fast.toFloat()
        )

        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float
        ): Float {
            if (containerSize <= 0f || size <= 0f) return 0f
            val itemCenter = offset + size / 2f
            val viewportTarget = containerSize * PlayTorrioFocus.tokens.scrollViewportTarget
            return itemCenter - viewportTarget
        }
    }
}
