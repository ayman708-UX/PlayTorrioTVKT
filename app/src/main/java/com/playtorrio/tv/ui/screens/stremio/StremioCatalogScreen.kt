package com.playtorrio.tv.ui.screens.stremio

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.R
import com.playtorrio.tv.data.stremio.CatalogDeclaration
import com.playtorrio.tv.data.stremio.ExtraProperty
import com.playtorrio.tv.data.stremio.StremioMetaPreview

private val AccentSecondary = Color(0xFFC084FC)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.12f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StremioCatalogScreen(
    addonId: String,
    type: String,
    catalogId: String,
    title: String,
    navController: NavController,
    viewModel: StremioCatalogViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(addonId, type, catalogId) {
        viewModel.load(addonId, type, catalogId)
    }

    val selectedCatalog = state.catalogs.getOrNull(state.selectedCatalogIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    navController.popBackStack()
                    true
                } else false
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left catalog navbar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(290.dp)
                    .background(SurfaceGlass)
                    .border(1.dp, SurfaceGlassBorder)
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = (state.addonName.ifBlank { title }).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.stremio_catalogs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.catalogs.indices.toList()) { index ->
                            val catalog = state.catalogs[index]
                            CatalogMenuItem(
                                catalog = catalog,
                                isSelected = index == state.selectedCatalogIndex,
                                onClick = { viewModel.selectCatalog(index) }
                            )
                        }
                    }
                }
            }

            // Right content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (selectedCatalog?.name ?: title).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.items.size} items",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }

                // Extra filters menu (genre/options/etc.)
                val extrasWithOptions = selectedCatalog
                    ?.extra
                    ?.filter { !it.options.isNullOrEmpty() }
                    .orEmpty()

                if (extrasWithOptions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().focusGroup()
                    ) {
                        items(extrasWithOptions) { extra ->
                            ExtraFilterMenu(
                                extra = extra,
                                selectedValue = state.selectedExtras[extra.name],
                                onSelect = { selected -> viewModel.setExtraFilter(extra.name, selected) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.stremio_catalog_loading), color = Color.White.copy(alpha = 0.6f))
                    }
                } else if (state.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error ?: stringResource(R.string.common_failed), color = Color(0xFFF87171))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().focusGroup(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(state.items, key = { idx, item -> "${item.id}_$idx" }) { index, item ->
                            CatalogItemCard(item = item) {
                                val resolvedType = item.type.trim()
                                    .ifBlank { selectedCatalog?.type.orEmpty() }
                                    .ifBlank { type.trim() }
                                    .ifBlank { "movie" }
                                val encodedId = Uri.encode(item.id)
                                navController.navigate("stremio_detail/$addonId/$resolvedType/$encodedId")
                                if (index >= state.items.size - 6 && state.hasMore) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isLoadingMore) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.stremio_loading_more), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogMenuItem(
    catalog: CatalogDeclaration,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = when {
                isSelected -> AccentSecondary.copy(alpha = 0.22f)
                isFocused -> Color.White.copy(alpha = 0.09f)
                else -> Color.Transparent
            }
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = catalog.name.ifBlank { catalog.id },
            color = if (isSelected) AccentSecondary else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExtraFilterMenu(
    extra: ExtraProperty,
    selectedValue: String?,
    onSelect: (String?) -> Unit
) {
    val options = extra.options.orEmpty()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceGlass)
            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = extra.name.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                text = "ALL",
                isSelected = selectedValue == null,
                onClick = { if (!extra.isRequired) onSelect(null) }
            )
            options.forEach { option ->
                FilterChip(
                    text = option,
                    isSelected = selectedValue == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = when {
                isSelected -> AccentSecondary.copy(alpha = 0.25f)
                isFocused -> Color.White.copy(alpha = 0.13f)
                else -> Color.Transparent
            }
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = text,
            color = if (isSelected) AccentSecondary else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogItemCard(
    item: StremioMetaPreview,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 0.96f,
        animationSpec = tween(180),
        label = "catalogScale"
    )

    Box(
        modifier = Modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier.border(
                        width = 1.5.dp,
                        color = AccentSecondary.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) else Modifier
                ),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.35f to Color.Black.copy(alpha = 0.35f),
                                    1f to Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
