package com.playtorrio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.playtorrio.tv.R
import com.playtorrio.tv.domain.model.MemberTier
import com.playtorrio.tv.ui.theme.PlayTorrioTheme

@Composable
internal fun DebugMemberTierCard(
    selectedTier: MemberTier?,
    onTierSelected: (MemberTier?) -> Unit
) {
    val options = listOf(
        null to stringResource(R.string.debug_member_tier_none),
        MemberTier.SUPPORTER to stringResource(R.string.debug_member_tier_one),
        MemberTier.SUPPORTER_PLUS to stringResource(R.string.debug_member_tier_two)
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.xs)) {
            Text(
                text = stringResource(R.string.debug_member_tier_title),
                style = MaterialTheme.typography.titleMedium,
                color = PlayTorrioTheme.colors.TextPrimary
            )
            Text(
                text = stringResource(R.string.debug_member_tier_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = PlayTorrioTheme.colors.TextSecondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.sm)
        ) {
            options.forEach { (tier, label) ->
                val selected = tier == selectedTier
                val shape = RoundedCornerShape(PlayTorrioTheme.radii.sm)
                Card(
                    onClick = { onTierSelected(tier) },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.colors(
                        containerColor = if (selected) {
                            PlayTorrioTheme.colors.Secondary.copy(alpha = 0.2f)
                        } else {
                            PlayTorrioTheme.colors.BackgroundCard
                        },
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                    ),
                    border = CardDefaults.border(
                        border = if (selected) {
                            Border(
                                border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.hairline),
                                shape = shape
                            )
                        } else {
                            Border.None
                        },
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = shape
                        )
                    ),
                    shape = CardDefaults.shape(shape),
                    scale = CardDefaults.scale(focusedScale = 1.04f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = PlayTorrioTheme.colors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayTorrioTheme.spacing.md, vertical = PlayTorrioTheme.spacing.lg),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
