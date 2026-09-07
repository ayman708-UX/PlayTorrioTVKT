package com.playtorrio.tv.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PlayTorrioRadiusTokens(
    val none: Dp,
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val panel: Dp,
    val full: Dp
)

@Immutable
data class PlayTorrioShapeTokens(
    val posterCard: Shape,
    val backdropCard: Shape,
    val collectionCard: Shape,
    val button: Shape,
    val iconButton: Shape,
    val chip: Shape,
    val badge: Shape,
    val dialog: Shape,
    val sidePanel: Shape,
    val sidebar: Shape,
    val navItem: Shape,
    val progress: Shape,
    val slider: Shape,
    val field: Shape,
    val menu: Shape,
    val circle: Shape
)

object PlayTorrioRadii {
    val tokens = PlayTorrioRadiusTokens(
        none = 0.dp,
        xxs = 2.dp,
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 14.dp,
        xl = 16.dp,
        xxl = 20.dp,
        panel = 28.dp,
        full = 999.dp
    )
}

object PlayTorrioShapes {
    val tokens = PlayTorrioShapeTokens(
        posterCard = RoundedCornerShape(PlayTorrioRadii.tokens.md),
        backdropCard = RoundedCornerShape(PlayTorrioRadii.tokens.xl),
        collectionCard = RoundedCornerShape(PlayTorrioRadii.tokens.xl),
        button = RoundedCornerShape(PlayTorrioRadii.tokens.md),
        iconButton = RoundedCornerShape(PlayTorrioRadii.tokens.md),
        chip = RoundedCornerShape(PlayTorrioRadii.tokens.full),
        badge = RoundedCornerShape(PlayTorrioRadii.tokens.xs),
        dialog = RoundedCornerShape(PlayTorrioRadii.tokens.xl),
        sidePanel = RoundedCornerShape(PlayTorrioRadii.tokens.xxl),
        sidebar = RoundedCornerShape(30.dp),
        navItem = RoundedCornerShape(PlayTorrioRadii.tokens.full),
        progress = RoundedCornerShape(PlayTorrioRadii.tokens.xxs),
        slider = RoundedCornerShape(PlayTorrioRadii.tokens.full),
        field = RoundedCornerShape(PlayTorrioRadii.tokens.md),
        menu = RoundedCornerShape(PlayTorrioRadii.tokens.lg),
        circle = CircleShape
    )
}
