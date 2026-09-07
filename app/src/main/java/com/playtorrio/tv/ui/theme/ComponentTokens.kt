package com.playtorrio.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PlayTorrioCardComponentTokens(
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
    val contentPadding: Dp,
    val focusedBorderWidth: Dp,
    val focusedScale: Float
)

@Immutable
data class PlayTorrioRowComponentTokens(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val itemSpacing: Dp,
    val titleBottomSpacing: Dp
)

@Immutable
data class PlayTorrioSidebarComponentTokens(
    val legacyCollapsedWidth: Dp,
    val legacyExpandedWidth: Dp,
    val collapsedWidth: Dp,
    val expandedWidth: Dp,
    val itemHeight: Dp,
    val itemWidth: Dp,
    val iconSize: Dp,
    val leadingVisual: Dp,
    val panelRadius: Dp,
    val contentGap: Dp
)

@Immutable
data class PlayTorrioDialogComponentTokens(
    val maxWidth: Dp,
    val contentPadding: Dp,
    val cornerRadius: Dp,
    val actionSpacing: Dp
)

@Immutable
data class PlayTorrioPlayerComponentTokens(
    val overlayHorizontalPadding: Dp,
    val overlayVerticalPadding: Dp,
    val controlSize: Dp,
    val sidePanelWidth: Dp,
    val railWidth: Dp,
    val progressHeight: Dp
)

@Immutable
data class PlayTorrioSettingsComponentTokens(
    val containerRadius: Dp,
    val secondaryCardRadius: Dp,
    val railItemHeight: Dp,
    val workspacePadding: Dp,
    val rowGap: Dp
)

@Immutable
data class PlayTorrioComponentTokens(
    val posterCard: PlayTorrioCardComponentTokens,
    val backdropCard: PlayTorrioCardComponentTokens,
    val collectionCard: PlayTorrioCardComponentTokens,
    val continueWatchingCard: PlayTorrioCardComponentTokens,
    val episodeCard: PlayTorrioCardComponentTokens,
    val row: PlayTorrioRowComponentTokens,
    val sidebar: PlayTorrioSidebarComponentTokens,
    val dialog: PlayTorrioDialogComponentTokens,
    val sidePanel: PlayTorrioDialogComponentTokens,
    val settings: PlayTorrioSettingsComponentTokens,
    val player: PlayTorrioPlayerComponentTokens,
    val buttonHeight: Dp,
    val chipHeight: Dp,
    val badgeHeight: Dp,
    val skeletonCornerRadius: Dp
)

object PlayTorrioComponents {
    val tokens = PlayTorrioComponentTokens(
        posterCard = PlayTorrioCardComponentTokens(
            width = 126.dp,
            height = 189.dp,
            cornerRadius = 12.dp,
            contentPadding = 8.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        backdropCard = PlayTorrioCardComponentTokens(
            width = 320.dp,
            height = 180.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        collectionCard = PlayTorrioCardComponentTokens(
            width = 320.dp,
            height = 180.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        continueWatchingCard = PlayTorrioCardComponentTokens(
            width = 260.dp,
            height = 146.dp,
            cornerRadius = 12.dp,
            contentPadding = 12.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        episodeCard = PlayTorrioCardComponentTokens(
            width = 320.dp,
            height = 207.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        row = PlayTorrioRowComponentTokens(
            horizontalPadding = 48.dp,
            verticalPadding = 6.dp,
            itemSpacing = 12.dp,
            titleBottomSpacing = 14.dp
        ),
        sidebar = PlayTorrioSidebarComponentTokens(
            legacyCollapsedWidth = 72.dp,
            legacyExpandedWidth = 196.dp,
            collapsedWidth = 184.dp,
            expandedWidth = 262.dp,
            itemHeight = 52.dp,
            itemWidth = 148.dp,
            iconSize = 22.dp,
            leadingVisual = 34.dp,
            panelRadius = 30.dp,
            contentGap = 14.dp
        ),
        dialog = PlayTorrioDialogComponentTokens(
            maxWidth = 720.dp,
            contentPadding = 24.dp,
            cornerRadius = 16.dp,
            actionSpacing = 12.dp
        ),
        sidePanel = PlayTorrioDialogComponentTokens(
            maxWidth = 420.dp,
            contentPadding = 20.dp,
            cornerRadius = 20.dp,
            actionSpacing = 12.dp
        ),
        settings = PlayTorrioSettingsComponentTokens(
            containerRadius = 28.dp,
            secondaryCardRadius = 18.dp,
            railItemHeight = 56.dp,
            workspacePadding = 20.dp,
            rowGap = 16.dp
        ),
        player = PlayTorrioPlayerComponentTokens(
            overlayHorizontalPadding = 52.dp,
            overlayVerticalPadding = 36.dp,
            controlSize = 44.dp,
            sidePanelWidth = 360.dp,
            railWidth = 280.dp,
            progressHeight = 4.dp
        ),
        buttonHeight = 52.dp,
        chipHeight = 32.dp,
        badgeHeight = 20.dp,
        skeletonCornerRadius = 10.dp
    )
}
