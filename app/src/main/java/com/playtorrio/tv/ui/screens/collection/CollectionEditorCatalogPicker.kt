package com.playtorrio.tv.ui.screens.collection

import com.playtorrio.tv.ui.theme.PlayTorrioTheme

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.playtorrio.tv.domain.model.AddonCatalogCollectionSource
import com.playtorrio.tv.domain.model.CollectionFolder
import com.playtorrio.tv.domain.model.CollectionSource
import com.playtorrio.tv.domain.model.FolderViewMode
import com.playtorrio.tv.domain.model.PosterShape
import com.playtorrio.tv.domain.model.TmdbCollectionFilters
import com.playtorrio.tv.domain.model.TmdbCollectionMediaType
import com.playtorrio.tv.domain.model.TmdbCollectionSort
import com.playtorrio.tv.domain.model.TmdbCollectionSource
import com.playtorrio.tv.domain.model.TmdbCollectionSourceType
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogPickerContent(
    catalogs: List<AvailableCatalog>,
    alreadyAdded: List<CollectionSource>,
    onToggle: (AvailableCatalog) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = PlayTorrioTheme.spacing.xxxl, start = PlayTorrioTheme.spacing.xxxl, end = PlayTorrioTheme.spacing.xxxl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.collections_editor_select_catalogs),
                style = MaterialTheme.typography.headlineMedium,
                color = PlayTorrioTheme.colors.TextPrimary
            )
            PlayTorrioButton(onClick = onBack) { Text(stringResource(R.string.collections_editor_done)) }
        }

        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = PlayTorrioTheme.spacing.sm, end = PlayTorrioTheme.spacing.sm, top = PlayTorrioTheme.spacing.xs, bottom = PlayTorrioTheme.spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.sm)
        ) {
            itemsIndexed(
                items = catalogs,
                key = { index, c -> "${c.addonId}_${c.type}_${c.catalogId}_$index" }
            ) { _, catalog ->
                val isAdded = alreadyAdded.any {
                    it is AddonCatalogCollectionSource && it.addonId == catalog.addonId && it.type == catalog.type && it.catalogId == catalog.catalogId
                }
                Card(
                    onClick = { onToggle(catalog) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.colors(
                        containerColor = if (isAdded) PlayTorrioTheme.colors.Secondary.copy(alpha = 0.15f) else PlayTorrioTheme.colors.BackgroundCard,
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                    ),
                    border = CardDefaults.border(
                        border = if (isAdded) Border(
                            border = BorderStroke(PlayTorrioTheme.spacing.hairline, PlayTorrioTheme.colors.Secondary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        ) else Border.None,
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        )
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md)),
                    scale = CardDefaults.scale(focusedScale = 1.01f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PlayTorrioTheme.spacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = catalog.catalogName.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                color = PlayTorrioTheme.colors.TextPrimary
                            )
                            val supportingGenreText = when {
                                catalog.genreRequired -> stringResource(R.string.collections_editor_genre_required)
                                catalog.genreOptions.isNotEmpty() -> stringResource(R.string.collections_editor_genre_optional)
                                else -> null
                            }
                            Text(
                                text = listOfNotNull("${catalog.type} - ${catalog.addonName}", supportingGenreText).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayTorrioTheme.colors.TextTertiary
                            )
                        }
                        if (isAdded) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.collection_editor_remove_cd),
                                tint = PlayTorrioTheme.colors.TextSecondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_add),
                                tint = PlayTorrioTheme.colors.TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
