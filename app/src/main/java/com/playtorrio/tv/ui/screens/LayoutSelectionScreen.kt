@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.playtorrio.tv.ui.screens

import com.playtorrio.tv.ui.theme.PlayTorrioTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.playtorrio.tv.domain.model.HomeLayout
import com.playtorrio.tv.ui.components.ClassicLayoutPreview
import com.playtorrio.tv.ui.components.GridLayoutPreview
import com.playtorrio.tv.ui.components.ModernLayoutPreview
import com.playtorrio.tv.ui.screens.settings.LayoutSettingsEvent
import com.playtorrio.tv.ui.screens.settings.LayoutSettingsViewModel
import androidx.compose.ui.res.stringResource
import com.playtorrio.tv.R

@Composable
fun LayoutSelectionScreen(
    viewModel: LayoutSettingsViewModel = hiltViewModel(),
    onContinue: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedLayout by remember { mutableStateOf(HomeLayout.MODERN) }
    val continueFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.selectedLayout) {
        selectedLayout = uiState.selectedLayout
    }

    LaunchedEffect(Unit) {
        continueFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 80.dp, vertical = PlayTorrioTheme.spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = stringResource(R.string.layout_selection_welcome),
                style = MaterialTheme.typography.headlineLarge,
                color = PlayTorrioTheme.colors.TextPrimary
            )

            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))

            Text(
                text = stringResource(R.string.layout_selection_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = PlayTorrioTheme.colors.TextSecondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Layout cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.xxl, Alignment.CenterHorizontally)
            ) {
                LayoutOptionCard(
                    layout = HomeLayout.MODERN,
                    isSelected = selectedLayout == HomeLayout.MODERN,
                    onSelect = { selectedLayout = HomeLayout.MODERN },
                    modifier = Modifier.weight(1f)
                )

                LayoutOptionCard(
                    layout = HomeLayout.GRID,
                    isSelected = selectedLayout == HomeLayout.GRID,
                    onSelect = { selectedLayout = HomeLayout.GRID },
                    modifier = Modifier.weight(1f)
                )

                LayoutOptionCard(
                    layout = HomeLayout.CLASSIC,
                    isSelected = selectedLayout == HomeLayout.CLASSIC,
                    onSelect = { selectedLayout = HomeLayout.CLASSIC },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xl))

            // Continue button
            Button(
                onClick = {
                    viewModel.onEvent(LayoutSettingsEvent.SelectLayout(selectedLayout))
                    onContinue()
                },
                modifier = Modifier
                    .width(200.dp)
                    .height(PlayTorrioTheme.spacing.xxxl)
                    .focusRequester(continueFocusRequester),
                shape = ButtonDefaults.shape(
                    shape = RoundedCornerShape(PlayTorrioTheme.spacing.xl)
                ),
                colors = ButtonDefaults.colors(
                    containerColor = PlayTorrioTheme.colors.Primary,
                    focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                ),
                border = ButtonDefaults.border(
                    focusedBorder = Border(
                        border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                        shape = RoundedCornerShape(PlayTorrioTheme.spacing.xl)
                    )
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.layout_selection_continue),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutOptionCard(
    layout: HomeLayout,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = PlayTorrioTheme.colors.BackgroundCard,
            focusedContainerColor = PlayTorrioTheme.colors.BackgroundCard
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                shape = RoundedCornerShape(PlayTorrioTheme.radii.xl)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.xl)),
        scale = CardDefaults.scale(focusedScale = 1.03f)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PlayTorrioTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    when (layout) {
                        HomeLayout.CLASSIC -> ClassicLayoutPreview(
                            modifier = Modifier.fillMaxSize()
                        )
                        HomeLayout.GRID -> GridLayoutPreview(
                            modifier = Modifier.fillMaxSize()
                        )
                        HomeLayout.MODERN -> ModernLayoutPreview(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))

                Text(
                    text = when (layout) {
                        HomeLayout.CLASSIC -> stringResource(R.string.layout_classic)
                        HomeLayout.GRID -> stringResource(R.string.layout_grid)
                        HomeLayout.MODERN -> stringResource(R.string.layout_modern)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected || isFocused) PlayTorrioTheme.colors.TextPrimary else PlayTorrioTheme.colors.TextSecondary
                )

            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))

            Text(
                text = when (layout) {
                    HomeLayout.CLASSIC -> stringResource(R.string.layout_classic_desc)
                    HomeLayout.GRID -> stringResource(R.string.layout_grid_desc)
                    HomeLayout.MODERN -> stringResource(R.string.layout_modern_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = PlayTorrioTheme.colors.TextTertiary
            )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = PlayTorrioTheme.colors.FocusRing,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PlayTorrioTheme.spacing.sm)
                        .size(PlayTorrioTheme.spacing.xl)
                )
            }
        }
    }
}
