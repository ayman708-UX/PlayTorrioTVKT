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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.playtorrio.tv.ui.components.PlayTorrioDialog
import com.playtorrio.tv.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CollectionEditorScreen(
    viewModel: CollectionEditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var lastFocusedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val folderFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    var folderToDelete by remember { mutableStateOf<CollectionFolder?>(null) }
    val deleteDialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(folderToDelete) {
        if (folderToDelete != null) {
            repeat(3) { withFrameNanos { } }
            try {
                deleteDialogFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(uiState.showFolderEditor) {
        if (!uiState.showFolderEditor && lastFocusedFolderId != null) {
            val targetId = lastFocusedFolderId!!
            repeat(3) { withFrameNanos { } }

            try {
                folderFocusRequesters[targetId]?.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    if (uiState.showFolderEditor) {
        FolderEditorContent(
            viewModel = viewModel,
            uiState = uiState
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = PlayTorrioTheme.spacing.xxxl),
        contentPadding = PaddingValues(start = PlayTorrioTheme.spacing.xxxl, end = PlayTorrioTheme.spacing.xxxl, bottom = PlayTorrioTheme.spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.none)
    ) {
        item(key = "header") {
            Text(
                text = if (uiState.isNew) stringResource(R.string.collections_new) else stringResource(R.string.collections_editor_edit_collection),
                style = MaterialTheme.typography.headlineMedium,
                color = PlayTorrioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xl))
        }

        item(key = "title") {
            Text(
                text = stringResource(R.string.collections_editor_row_title),
                style = MaterialTheme.typography.labelLarge,
                color = PlayTorrioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayTorrioTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.setTitle(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.collections_editor_placeholder_name)
                )
                val canSaveCollection = uiState.title.isNotBlank() && uiState.folders.isNotEmpty()
                PlayTorrioButton(onClick = { viewModel.save { onBack() } }, enabled = canSaveCollection) {
                    Text(stringResource(R.string.collections_editor_save))
                }
            }
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))
        }

        item(key = "backdrop") {
            Text(
                text = stringResource(R.string.collections_editor_backdrop),
                style = MaterialTheme.typography.labelLarge,
                color = PlayTorrioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
            PlayTorrioTextField(
                value = uiState.backdropImageUrl,
                onValueChange = { viewModel.setBackdropImageUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.collections_editor_placeholder_backdrop)
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))
        }

        item(key = "pin_to_top") {
            Card(
                onClick = { viewModel.setPinToTop(!uiState.pinToTop) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.colors(
                    containerColor = PlayTorrioTheme.colors.BackgroundCard,
                    focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                        shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                    )
                ),
                shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md)),
                scale = CardDefaults.scale(focusedScale = 1.02f)
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
                            text = stringResource(R.string.collections_editor_pin_above),
                            style = MaterialTheme.typography.titleMedium,
                            color = PlayTorrioTheme.colors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                        Text(
                            text = stringResource(R.string.collections_editor_pin_above_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayTorrioTheme.colors.TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(PlayTorrioTheme.spacing.md))
                    Switch(
                        checked = uiState.pinToTop,
                        onCheckedChange = { viewModel.setPinToTop(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PlayTorrioTheme.colors.Secondary,
                            checkedTrackColor = PlayTorrioTheme.colors.Secondary.copy(alpha = 0.3f),
                            uncheckedThumbColor = PlayTorrioTheme.colors.TextSecondary,
                            uncheckedTrackColor = PlayTorrioTheme.colors.BackgroundCard
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))
        }

        item(key = "focus_glow") {
            Card(
                onClick = { viewModel.setFocusGlowEnabled(!uiState.focusGlowEnabled) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.colors(
                    containerColor = PlayTorrioTheme.colors.BackgroundCard,
                    focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                        shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                    )
                ),
                shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md)),
                scale = CardDefaults.scale(focusedScale = 1.02f)
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
                            text = stringResource(R.string.collections_editor_focus_glow),
                            style = MaterialTheme.typography.titleMedium,
                            color = PlayTorrioTheme.colors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                        Text(
                            text = stringResource(R.string.collections_editor_focus_glow_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayTorrioTheme.colors.TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(PlayTorrioTheme.spacing.md))
                    Switch(
                        checked = uiState.focusGlowEnabled,
                        onCheckedChange = { viewModel.setFocusGlowEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PlayTorrioTheme.colors.Secondary,
                            checkedTrackColor = PlayTorrioTheme.colors.Secondary.copy(alpha = 0.3f),
                            uncheckedThumbColor = PlayTorrioTheme.colors.TextSecondary,
                            uncheckedTrackColor = PlayTorrioTheme.colors.BackgroundCard
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.lg))
        }

        item(key = "view_mode") {
            Text(
                text = stringResource(R.string.collections_editor_view_mode),
                style = MaterialTheme.typography.labelLarge,
                color = PlayTorrioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.sm)
            ) {
                val viewModes = listOf(
                    FolderViewMode.TABBED_GRID to stringResource(R.string.collections_editor_view_mode_tabs),
                    FolderViewMode.ROWS to stringResource(R.string.collections_editor_view_mode_rows),
                    FolderViewMode.FOLLOW_LAYOUT to stringResource(R.string.collections_editor_view_mode_follow)
                )
                viewModes.forEach { (mode, label) ->
                    val isSelected = uiState.viewMode == mode
                    Button(
                        onClick = { viewModel.setViewMode(mode) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) PlayTorrioTheme.colors.Secondary.copy(alpha = 0.3f) else PlayTorrioTheme.colors.BackgroundCard,
                            contentColor = if (isSelected) PlayTorrioTheme.colors.Secondary else PlayTorrioTheme.colors.TextSecondary,
                            focusedContainerColor = PlayTorrioTheme.colors.FocusBackground,
                            focusedContentColor = PlayTorrioTheme.colors.Primary
                        ),
                        border = ButtonDefaults.border(
                            border = if (isSelected) Border(
                                border = BorderStroke(PlayTorrioTheme.spacing.xxs, PlayTorrioTheme.colors.Secondary),
                                shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                            ) else Border.None,
                            focusedBorder = Border(
                                border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                                shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                            )
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md))
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.md))
        }

        if (uiState.viewMode == FolderViewMode.TABBED_GRID) {
            item(key = "show_all_tab") {
                Card(
                    onClick = { viewModel.setShowAllTab(!uiState.showAllTab) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.colors(
                        containerColor = PlayTorrioTheme.colors.BackgroundCard,
                        focusedContainerColor = PlayTorrioTheme.colors.FocusBackground
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(
                            border = PlayTorrioTheme.focusRing.border(PlayTorrioTheme.spacing.xxs),
                            shape = RoundedCornerShape(PlayTorrioTheme.radii.md)
                        )
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.md)),
                    scale = CardDefaults.scale(focusedScale = 1.02f)
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
                                text = stringResource(R.string.collections_editor_show_all_tab),
                                style = MaterialTheme.typography.titleMedium,
                                color = PlayTorrioTheme.colors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                            Text(
                                text = stringResource(R.string.collections_editor_show_all_tab_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayTorrioTheme.colors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(PlayTorrioTheme.spacing.md))
                        Switch(
                            checked = uiState.showAllTab,
                            onCheckedChange = { viewModel.setShowAllTab(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PlayTorrioTheme.colors.Secondary,
                                checkedTrackColor = PlayTorrioTheme.colors.Secondary.copy(alpha = 0.3f),
                                uncheckedThumbColor = PlayTorrioTheme.colors.TextSecondary,
                                uncheckedTrackColor = PlayTorrioTheme.colors.BackgroundCard
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.md))
            }
        }

        item(key = "folders_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.collections_editor_folders),
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayTorrioTheme.colors.TextPrimary
                )
                Text(
                    text = stringResource(
                        if (uiState.folders.size == 1) R.string.collection_editor_folder_count_one
                        else R.string.collection_editor_folder_count_other,
                        uiState.folders.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayTorrioTheme.colors.TextTertiary
                )
            }
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.md))
        }

        itemsIndexed(
            items = uiState.folders,
            key = { _, folder -> folder.id }
        ) { index, folder ->
            val editFocusRequester = folderFocusRequesters.getOrPut(folder.id) { FocusRequester() }
            Box(modifier = Modifier.padding(start = PlayTorrioTheme.spacing.sm, end = PlayTorrioTheme.spacing.sm, bottom = PlayTorrioTheme.spacing.sm)) {
                FolderListItem(
                    folder = folder,
                    isFirst = index == 0,
                    isLast = index == uiState.folders.size - 1,
                    editFocusRequester = editFocusRequester,
                    onEdit = {
                        lastFocusedFolderId = folder.id
                        viewModel.editFolder(folder.id)
                    },
                    onDelete = { folderToDelete = folder },
                    onMoveUp = { viewModel.moveFolderUp(index) },
                    onMoveDown = { viewModel.moveFolderDown(index) }
                )
            }
        }

        item(key = "add_folder") {
            val addFolderFocusRequester = folderFocusRequesters.getOrPut("add_folder") { FocusRequester() }
            Box(modifier = Modifier.padding(start = PlayTorrioTheme.spacing.sm, end = PlayTorrioTheme.spacing.sm, top = PlayTorrioTheme.spacing.xs)) {
                PlayTorrioButton(
                    onClick = {
                        lastFocusedFolderId = "add_folder"
                        viewModel.addFolder()
                    },
                    modifier = Modifier.focusRequester(addFolderFocusRequester)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                    Spacer(modifier = Modifier.width(PlayTorrioTheme.spacing.sm))
                    Text(stringResource(R.string.collections_editor_add_folder))
                }
            }
        }
    }

    folderToDelete?.let { folder ->
        PlayTorrioDialog(
            onDismiss = { folderToDelete = null },
            title = stringResource(R.string.collections_editor_delete_folder_title),
            subtitle = stringResource(R.string.collections_editor_delete_folder_subtitle, folder.title),
            suppressFirstKeyUp = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md, Alignment.End)
            ) {
                PlayTorrioButton(
                    onClick = { folderToDelete = null },
                    modifier = Modifier.focusRequester(deleteDialogFocusRequester)
                ) {
                    Text(stringResource(R.string.collections_cancel))
                }
                PlayTorrioButton(
                    onClick = {
                        viewModel.removeFolder(folder.id)
                        folderToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.cd_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderListItem(
    folder: CollectionFolder,
    isFirst: Boolean,
    isLast: Boolean,
    editFocusRequester: FocusRequester? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(PlayTorrioTheme.radii.md),
        colors = SurfaceDefaults.colors(containerColor = PlayTorrioTheme.colors.BackgroundCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlayTorrioTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val shapeLabel = stringResource(
                    when (folder.tileShape) {
                        com.playtorrio.tv.domain.model.PosterShape.POSTER -> R.string.collections_editor_shape_poster
                        com.playtorrio.tv.domain.model.PosterShape.LANDSCAPE -> R.string.collections_editor_shape_wide
                        com.playtorrio.tv.domain.model.PosterShape.SQUARE -> R.string.collections_editor_shape_square
                    }
                )
                Text(
                    text = "$shapeLabel - ${stringResource(R.string.collections_editor_source_count, folder.sources.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayTorrioTheme.colors.TextTertiary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.xxs)) {
                PlayTorrioButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.cd_move_up), tint = if (!isFirst) PlayTorrioTheme.colors.TextSecondary else PlayTorrioTheme.colors.TextTertiary)
                }
                PlayTorrioButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.cd_move_down), tint = if (!isLast) PlayTorrioTheme.colors.TextSecondary else PlayTorrioTheme.colors.TextTertiary)
                }
                PlayTorrioButton(
                    onClick = onEdit,
                    modifier = if (editFocusRequester != null) Modifier.focusRequester(editFocusRequester) else Modifier
                ) {
                    Icon(Icons.Default.Edit, stringResource(R.string.cd_edit), tint = PlayTorrioTheme.colors.TextSecondary)
                }
                PlayTorrioButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = PlayTorrioTheme.colors.TextSecondary)
                }
            }
        }
    }
}
