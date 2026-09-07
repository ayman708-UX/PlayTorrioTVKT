package com.playtorrio.tv.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.playtorrio.tv.ui.theme.PlayTorrioComponents

@Immutable
data class PosterCardStyle(
    val width: Dp = PlayTorrioComponents.tokens.posterCard.width,
    val height: Dp = PlayTorrioComponents.tokens.posterCard.height,
    val cornerRadius: Dp = PlayTorrioComponents.tokens.posterCard.cornerRadius,
    val focusedBorderWidth: Dp = PlayTorrioComponents.tokens.posterCard.focusedBorderWidth,
    val focusedScale: Float = PlayTorrioComponents.tokens.posterCard.focusedScale
) {
    val aspectRatio: Float
        get() = width.value / height.value
}

object PosterCardDefaults {
    val Style = PosterCardStyle()
}
