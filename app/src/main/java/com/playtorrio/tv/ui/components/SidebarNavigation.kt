package com.playtorrio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.playtorrio.tv.R
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.playtorrio.tv.ui.theme.PlayTorrioComponents
import com.playtorrio.tv.ui.theme.PlayTorrioStrokes
import com.playtorrio.tv.ui.theme.PlayTorrioTheme

private val NavItemShape = RoundedCornerShape(PlayTorrioComponents.tokens.sidebar.panelRadius / 2)
private val NavItemIconShape = RoundedCornerShape(PlayTorrioComponents.tokens.sidebar.panelRadius / 3)

data class SidebarItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SidebarNavigation(
    items: List<SidebarItem>,
    selectedRoute: String?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    val sidebarWidth = PlayTorrioTheme.sizes.sidebar.expandedWidth
    val sidebarAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        label = "sidebarAlpha"
    )

    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .graphicsLayer { alpha = sidebarAlpha }
            .background(PlayTorrioTheme.colors.BackgroundElevated)
            .padding(vertical = PlayTorrioTheme.spacing.xl, horizontal = PlayTorrioTheme.spacing.lg)
            .onFocusChanged { state ->
                onFocusChange(state.hasFocus)
                onExpandedChange(state.hasFocus)
            },
        verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = PlayTorrioTheme.colors.Primary
        )

        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.md))

        items.forEach { item ->
            SidebarNavItem(
                item = item,
                isSelected = item.route == selectedRoute,
                focusRequester = if (item.route == selectedRoute) focusRequester else null,
                onNavigate = onNavigate
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarNavItem(
    item: SidebarItem,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onNavigate: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused || isSelected) PlayTorrioTheme.colors.FocusBackground else Color.Transparent,
        label = "navItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) PlayTorrioTheme.colors.FocusRing else Color.Transparent,
        label = "navItemBorder"
    )

    Card(
        onClick = { onNavigate(item.route) },
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayTorrioTheme.sizes.settings.railItemHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.hasFocus
            },
        colors = CardDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor,
        ),
        border = CardDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(PlayTorrioStrokes.tokens.focus, borderColor),
                shape = NavItemShape
            )
        ),
        shape = CardDefaults.shape(shape = NavItemShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayTorrioTheme.sizes.settings.railItemHeight)
                .padding(horizontal = PlayTorrioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
        ) {
        Box(
            modifier = Modifier
                .size(PlayTorrioTheme.sizes.icons.xl - PlayTorrioTheme.spacing.xs)
                .clip(NavItemIconShape)
                .background(PlayTorrioTheme.colors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = PlayTorrioTheme.colors.TextPrimary,
                modifier = Modifier.size(PlayTorrioTheme.sizes.icons.sm)
            )
        }

        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused || isSelected) PlayTorrioTheme.colors.TextPrimary else PlayTorrioTheme.colors.TextSecondary
        )
    }
    }
}
