package com.playtorrio.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.playtorrio.tv.core.metadata.BSItem
import com.playtorrio.tv.ui.theme.PlayTorrioTheme

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SimilarContentSection(
    items: List<BSItem>,
    isLoading: Boolean = false,
    posterCardCornerRadius: Dp = PlayTorrioTheme.spacing.md,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    sectionFocusRequester: FocusRequester? = null,
    onItemClick: (BSItem) -> Unit
) {
    if (items.isEmpty() && !isLoading) return

    val firstItemFocusRequester = remember { FocusRequester() }
    val cardWidth = 140.dp
    val cardHeight = 210.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PlayTorrioTheme.spacing.sm, bottom = PlayTorrioTheme.spacing.sm)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (sectionFocusRequester != null) Modifier.focusRequester(sectionFocusRequester) else Modifier)
                .focusRestorer { firstItemFocusRequester },
            contentPadding = PaddingValues(horizontal = PlayTorrioTheme.spacing.xxxl, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${item.id}_${item.slug}_$index" }
            ) { index, item ->
                val isFirstItem = index == 0
                val focusRequester = if (isFirstItem) firstItemFocusRequester else remember { FocusRequester() }
                var isFocused by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.width(cardWidth)
                ) {
                    Card(
                        onClick = { onItemClick(item) },
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        shape = CardDefaults.shape(RoundedCornerShape(posterCardCornerRadius)),
                        border = CardDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                inset = 0.dp,
                                shape = RoundedCornerShape(posterCardCornerRadius)
                            ),
                            focusedBorder = androidx.tv.material3.Border(
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                inset = 0.dp,
                                shape = RoundedCornerShape(posterCardCornerRadius)
                            )
                        ),
                        colors = CardDefaults.colors(
                            containerColor = Color.DarkGray.copy(alpha = 0.3f),
                            focusedContainerColor = Color.DarkGray.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.thumbUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Similarity badge top-end
                            item.similarityPercent?.let { pct ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(PlayTorrioTheme.spacing.xxs)
                                        .clip(RoundedCornerShape(PlayTorrioTheme.spacing.xs))
                                        .background(
                                            if (pct >= 80) Color(0xFF2E7D32).copy(alpha = 0.9f)
                                            else if (pct >= 60) Color(0xFF1565C0).copy(alpha = 0.9f)
                                            else Color.Black.copy(alpha = 0.75f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isFocused) MaterialTheme.colorScheme.primary else PlayTorrioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val year = item.year
                    if (year != null) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayTorrioTheme.colors.TextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Attribution footer
        Text(
            text = "Powered by BestSimilar",
            style = MaterialTheme.typography.bodySmall,
            color = PlayTorrioTheme.colors.TextTertiary,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = PlayTorrioTheme.spacing.xxxl, top = PlayTorrioTheme.spacing.xxs)
        )
    }
}
