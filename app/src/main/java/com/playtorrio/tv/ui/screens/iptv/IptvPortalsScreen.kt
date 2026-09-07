@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.playtorrio.tv.ui.screens.iptv

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.playtorrio.tv.core.iptv.model.CatalogSource
import com.playtorrio.tv.core.iptv.model.VerifiedPortal

@Composable
fun IptvPortalsScreen(
    viewModel: IptvViewModel,
    onBack: () -> Unit,
    onOpenPortal: (VerifiedPortal) -> Unit
) {
    val portals by viewModel.verifiedPortals.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val favoriteKeys by viewModel.favoritePortalKeys.collectAsState()
    val canGetMore by viewModel.canGetMore.collectAsState()
    val scrapeSource by viewModel.scrapeSource.collectAsState()

    var isManageMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    var newUser by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    val allSelected = portals.isNotEmpty() && selectedKeys.size == portals.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isManageMode) "Manage IPTV Portals (${selectedKeys.size} selected)" else "IPTV Portals Manager",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isManageMode) "Select portals to bulk delete or manage" else if (statusText.isNotBlank()) statusText else "Active verified Xtream-Codes portals (${portals.size}) • Press Right to Favorite / Delete",
                        color = Color(0x88FFFFFF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isManageMode) {
                    // Select All / Deselect All
                    Button(
                        onClick = {
                            selectedKeys = if (allSelected) {
                                emptySet()
                            } else {
                                portals.map { it.key }.toSet()
                            }
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (allSelected) "Deselect All" else "Select All", fontSize = 12.sp, maxLines = 1)
                    }

                    // Delete Selected
                    Button(
                        onClick = {
                            viewModel.deletePortals(selectedKeys)
                            selectedKeys = emptySet()
                            isManageMode = false
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        enabled = selectedKeys.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            disabledContainerColor = Color(0x33DC2626)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete (${selectedKeys.size})", fontSize = 12.sp, maxLines = 1)
                    }

                    // Done / Exit Manage
                    Button(
                        onClick = {
                            isManageMode = false
                            selectedKeys = emptySet()
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, maxLines = 1)
                    }
                } else {
                    // Manage Toggle Button
                    if (portals.isNotEmpty()) {
                        Button(
                            onClick = { isManageMode = true },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Manage", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    if (canGetMore && !isScraping) {
                        Button(
                            onClick = { viewModel.getMorePortals() },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get More", fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = { viewModel.toggleScrapeSource() },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        enabled = !isScraping,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scrapeSource == CatalogSource.CLOUD_VAULT) Color(0xFF4F46E5) else Color(0xFFEA580C)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Source: ${scrapeSource.label}", fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Portal", fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { viewModel.scrapePortals(reset = true) },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        enabled = !isScraping,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isScraping) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScraping) "Scraping..." else "Scrape Portals", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (portals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isScraping) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = statusText, color = Color.White, fontSize = 14.sp)
                    }
                } else {
                    Text(
                        text = "No verified portals available. Click 'Scrape Portals' (${scrapeSource.label}) or switch source.",
                        color = Color(0x88FFFFFF),
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(portals, key = { it.key }) { portal ->
                    val isFav = favoriteKeys.contains(portal.key)
                    val isSelected = selectedKeys.contains(portal.key)
                    PortalCardRow(
                        portal = portal,
                        isFavorite = isFav,
                        isManageMode = isManageMode,
                        isSelected = isSelected,
                        onOpen = {
                            if (isManageMode) {
                                selectedKeys = if (isSelected) selectedKeys - portal.key else selectedKeys + portal.key
                            } else {
                                onOpenPortal(portal)
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavoritePortal(portal.key) },
                        onDelete = { viewModel.deletePortal(portal) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAdding) showAddDialog = false },
            title = { Text("Add Xtream-Codes Portal", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("Server URL (http://host:port)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = newUser,
                        onValueChange = { newUser = it },
                        label = { Text("Username") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    if (addError != null) {
                        Text(text = addError!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUrl.isNotBlank() && newUser.isNotBlank() && newPass.isNotBlank()) {
                            isAdding = true
                            addError = null
                            viewModel.addManualPortal(newUrl, newUser, newPass) { success ->
                                isAdding = false
                                if (success) {
                                    showAddDialog = false
                                    newUrl = ""
                                    newUser = ""
                                    newPass = ""
                                } else {
                                    addError = "Failed to authenticate against portal."
                                }
                            }
                        }
                    },
                    enabled = !isAdding
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Connect & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isAdding) {
                    Text("Cancel", color = Color(0xAAFFFFFF))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun PortalCardRow(
    portal: VerifiedPortal,
    isFavorite: Boolean,
    isManageMode: Boolean = false,
    isSelected: Boolean = false,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Main Portal Card (Click/Enter to Open or Select)
        Surface(
            onClick = onOpen,
            modifier = Modifier
                .weight(1f)
                .height(68.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF131C2E),
                focusedContainerColor = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0x1AFFFFFF)),
                    shape = RoundedCornerShape(12.dp)
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(12.dp)
                )
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(0xFF38BDF8)
                            else if (isFavorite) Color(0x33F59E0B)
                            else Color(0x2238BDF8)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Add
                            else if (isFavorite) Icons.Default.Star
                            else Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (isSelected) Color.Black
                            else if (isFavorite) Color(0xFFFBBF24)
                            else Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = portal.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isFavorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33F59E0B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "FAVORITE",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${portal.portal.url} • User: ${portal.portal.username} • Expiry: ${portal.expiry} • Cons: ${portal.maxConnections}",
                        color = Color(0x88FFFFFF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isManageMode) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF38BDF8) else Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons (Only visible in normal mode, right-navigable)
        if (!isManageMode) {
            // 2. Favorite Toggle Button
            Surface(
                onClick = onToggleFavorite,
                modifier = Modifier.size(52.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isFavorite) Color(0x22EF4444) else Color(0xFF131C2E),
                    focusedContainerColor = if (isFavorite) Color(0x44EF4444) else Color(0xFF1E293B)
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(1.dp, if (isFavorite) Color(0x66EF4444) else Color(0x1AFFFFFF)),
                        shape = RoundedCornerShape(12.dp)
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, if (isFavorite) Color(0xFFEF4444) else Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(12.dp)
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else Color(0xAAFFFFFF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 3. Delete Button
            Surface(
                onClick = onDelete,
                modifier = Modifier.size(52.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF131C2E),
                    focusedContainerColor = Color(0x33EF4444)
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(border = BorderStroke(1.dp, Color(0x1AFFFFFF)), shape = RoundedCornerShape(12.dp)),
                    focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFFEF4444)), shape = RoundedCornerShape(12.dp))
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
