package com.playtorrio.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PlayTorrioIconSizes(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp
)

@Immutable
data class PlayTorrioButtonSizes(
    val compactHeight: Dp,
    val defaultHeight: Dp,
    val largeHeight: Dp,
    val minWidth: Dp
)

@Immutable
data class PlayTorrioSidebarSizes(
    val hiddenWidth: Dp,
    val compactWidth: Dp,
    val closedWidth: Dp,
    val expandedWidth: Dp,
    val expandedItemWidth: Dp,
    val railItemHeight: Dp,
    val leadingVisual: Dp
)

@Immutable
data class PlayTorrioCardSizes(
    val posterWidth: Dp,
    val posterHeight: Dp,
    val posterCompactWidth: Dp,
    val posterCompactHeight: Dp,
    val backdropWidth: Dp,
    val backdropHeight: Dp,
    val episodeWidth: Dp,
    val episodeHeight: Dp
)

@Immutable
data class PlayTorrioAvatarSizes(
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val profile: Dp,
    val profileCompact: Dp
)

@Immutable
data class PlayTorrioPlayerSizes(
    val control: Dp,
    val compactControl: Dp,
    val timelineHeight: Dp,
    val sidePanelWidth: Dp,
    val railWidth: Dp
)

@Immutable
data class PlayTorrioSettingsSizes(
    val railWidth: Dp,
    val railItemHeight: Dp,
    val workspaceMinWidth: Dp,
    val rowMinHeight: Dp
)

@Immutable
data class PlayTorrioSizeTokens(
    val icons: PlayTorrioIconSizes,
    val buttons: PlayTorrioButtonSizes,
    val sidebar: PlayTorrioSidebarSizes,
    val cards: PlayTorrioCardSizes,
    val avatars: PlayTorrioAvatarSizes,
    val player: PlayTorrioPlayerSizes,
    val settings: PlayTorrioSettingsSizes,
    val logoWidth: Dp,
    val logoHeight: Dp,
    val menuItemHeight: Dp
)

object PlayTorrioSizes {
    val tokens = PlayTorrioSizeTokens(
        icons = PlayTorrioIconSizes(
            xs = 14.dp,
            sm = 18.dp,
            md = 22.dp,
            lg = 28.dp,
            xl = 36.dp
        ),
        buttons = PlayTorrioButtonSizes(
            compactHeight = 40.dp,
            defaultHeight = 52.dp,
            largeHeight = 64.dp,
            minWidth = 96.dp
        ),
        sidebar = PlayTorrioSidebarSizes(
            hiddenWidth = 0.dp,
            compactWidth = 72.dp,
            closedWidth = 184.dp,
            expandedWidth = 262.dp,
            expandedItemWidth = 148.dp,
            railItemHeight = 52.dp,
            leadingVisual = 34.dp
        ),
        cards = PlayTorrioCardSizes(
            posterWidth = 126.dp,
            posterHeight = 189.dp,
            posterCompactWidth = 112.dp,
            posterCompactHeight = 168.dp,
            backdropWidth = 320.dp,
            backdropHeight = 180.dp,
            episodeWidth = 320.dp,
            episodeHeight = 207.dp
        ),
        avatars = PlayTorrioAvatarSizes(
            sm = 34.dp,
            md = 48.dp,
            lg = 82.dp,
            xl = 112.dp,
            profile = 126.dp,
            profileCompact = 104.dp
        ),
        player = PlayTorrioPlayerSizes(
            control = 44.dp,
            compactControl = 40.dp,
            timelineHeight = 4.dp,
            sidePanelWidth = 360.dp,
            railWidth = 280.dp
        ),
        settings = PlayTorrioSettingsSizes(
            railWidth = 260.dp,
            railItemHeight = 56.dp,
            workspaceMinWidth = 720.dp,
            rowMinHeight = 64.dp
        ),
        logoWidth = 190.dp,
        logoHeight = 44.dp,
        menuItemHeight = 48.dp
    )
}
