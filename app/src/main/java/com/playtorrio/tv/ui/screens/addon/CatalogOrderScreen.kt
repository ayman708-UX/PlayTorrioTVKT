@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.playtorrio.tv.ui.screens.addon

import com.playtorrio.tv.ui.theme.PlayTorrioTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.playtorrio.tv.ui.components.LoadingIndicator
import androidx.compose.ui.res.stringResource
import com.playtorrio.tv.R
import kotlinx.coroutines.launch

@Composable
fun CatalogOrderScreen(
    viewModel: CatalogOrderViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BackHandler { onBackPress() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PlayTorrioTheme.spacing.xxxl, vertical = PlayTorrioTheme.spacing.xl)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = PlayTorrioTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.lg)
        ) {
            item {
                Text(
                    text = stringResource(R.string.catalog_order_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PlayTorrioTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.catalog_order_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayTorrioTheme.colors.TextSecondary
                )
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.catalog_order_follow_addons),
                            style = MaterialTheme.typography.titleMedium,
                            color = PlayTorrioTheme.colors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.catalog_order_follow_addons_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayTorrioTheme.colors.TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.followAddonsOrder,
                        onCheckedChange = { viewModel.toggleFollowAddonsOrder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PlayTorrioTheme.colors.Primary,
                            checkedTrackColor = PlayTorrioTheme.colors.Primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PlayTorrioTheme.spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.catalog_order_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PlayTorrioTheme.colors.TextSecondary
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = uiState.items,
                        key = { _, item -> item.key }
                    ) { index, item ->
                        CatalogOrderCard(
                            item = item,
                            onMoveUp = {
                                viewModel.moveUp(item.key)
                                scope.launch {
                                    listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                                }
                            },
                            onMoveDown = {
                                viewModel.moveDown(item.key)
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (index + 1).coerceAtMost(uiState.items.lastIndex)
                                    )
                                }
                            },
                            onToggleEnabled = { viewModel.toggleCatalogEnabled(item.disableKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogOrderCard(
    item: CatalogOrderItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PlayTorrioTheme.colors.BackgroundCard),
        shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.catalogName} - ${item.typeLabel.toDisplayTypeLabel()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (item.isDisabled) PlayTorrioTheme.colors.TextSecondary else PlayTorrioTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                Text(
                    text = item.addonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayTorrioTheme.colors.TextSecondary
                )
                if (item.isDisabled) {
                    Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                    Text(
                        text = stringResource(R.string.catalog_order_disabled_on_home),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayTorrioTheme.colors.Error
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onMoveUp,
                    enabled = item.canMoveUp,
                    colors = ButtonDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        contentColor = PlayTorrioTheme.colors.TextSecondary,
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground,
                        focusedContentColor = PlayTorrioTheme.colors.Primary
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.cd_move_up)
                    )
                }

                Button(
                    onClick = onMoveDown,
                    enabled = item.canMoveDown,
                    colors = ButtonDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        contentColor = PlayTorrioTheme.colors.TextSecondary,
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground,
                        focusedContentColor = PlayTorrioTheme.colors.Primary
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.cd_move_down)
                    )
                }

                Button(
                    onClick = onToggleEnabled,
                    colors = ButtonDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        contentColor = if (item.isDisabled) PlayTorrioTheme.colors.Success else PlayTorrioTheme.colors.TextSecondary,
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground,
                        focusedContentColor = if (item.isDisabled) PlayTorrioTheme.colors.Success else PlayTorrioTheme.colors.Error
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        )
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md))
                ) {
                    Text(text = if (item.isDisabled) stringResource(R.string.catalog_order_enable) else stringResource(R.string.catalog_order_disable))
                }
            }
        }
    }
}

private fun String.toDisplayTypeLabel(): String {
    return replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}
