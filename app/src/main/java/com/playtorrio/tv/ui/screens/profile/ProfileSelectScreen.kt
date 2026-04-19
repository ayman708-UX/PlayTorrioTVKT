package com.playtorrio.tv.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.playtorrio.tv.data.profile.AvatarCatalog
import com.playtorrio.tv.data.profile.Profile
import com.playtorrio.tv.data.profile.ProfileManager

private val AccentPrimary = Color(0xFF818CF8)
private val SurfaceGlass = Color.White.copy(alpha = 0.06f)
private val SurfaceGlassBorder = Color.White.copy(alpha = 0.1f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfileSelectScreen(navController: NavController) {
    var profiles by remember { mutableStateOf(ProfileManager.loadProfiles()) }
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<Profile?>(null) }

    val refresh: () -> Unit = { profiles = ProfileManager.loadProfiles() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    when {
                        creating || profileToEdit != null -> {
                            creating = false
                            profileToEdit = null
                            true
                        }
                        editing -> { editing = false; true }
                        else -> false // let the system close the app
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp)
        ) {
            Text(
                text = if (editing) "Manage Profiles" else "Who's watching?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                ),
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentPrimary, AccentPrimary.copy(alpha = 0f))
                        )
                    )
            )

            Spacer(Modifier.height(48.dp))

            // Profile row (wrapping FlowRow-ish via a simple Row, max 5 fits)
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top
            ) {
                profiles.forEach { profile ->
                    ProfileTile(
                        profile = profile,
                        editing = editing,
                        canDelete = profiles.size > 1,
                        onSelect = {
                            ProfileManager.setActive(profile.id)
                            navController.navigate("home") {
                                popUpTo("profile_select") { inclusive = true }
                            }
                        },
                        onEdit = { profileToEdit = profile },
                        onDelete = {
                            ProfileManager.delete(profile.id)
                            refresh()
                            if (ProfileManager.loadProfiles().size <= 1) editing = false
                        }
                    )
                }
                if (ProfileManager.canAddMore()) {
                    AddProfileTile(onClick = { creating = true })
                }
            }

            Spacer(Modifier.height(32.dp))

            // Manage / Done button
            if (profiles.isNotEmpty()) {
                ManageButton(
                    editing = editing,
                    onClick = { editing = !editing }
                )
            }

            if (!ProfileManager.canAddMore() && !editing) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Maximum of ${ProfileManager.MAX_PROFILES} profiles",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }

        // Create / edit dialog
        AnimatedVisibility(
            visible = creating || profileToEdit != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ProfileEditDialog(
                existing = profileToEdit,
                canDelete = profiles.size > 1,
                onDismiss = {
                    creating = false
                    profileToEdit = null
                },
                onDelete = {
                    val target = profileToEdit
                    if (target != null) {
                        ProfileManager.delete(target.id)
                        profileToEdit = null
                        if (ProfileManager.loadProfiles().size <= 1) editing = false
                        refresh()
                    }
                },
                onSave = { name, imageUrl ->
                    val target = profileToEdit
                    if (target != null) {
                        ProfileManager.upsert(target.copy(name = name, imageUrl = imageUrl))
                    } else {
                        ProfileManager.create(name, imageUrl)
                    }
                    creating = false
                    profileToEdit = null
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    editing: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "profileTileScale"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(180),
        label = "profileRingAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(132.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .padding(10.dp)
                .then(
                    if (isFocused) Modifier.border(
                        width = 3.dp,
                        color = Color.White,
                        shape = CircleShape
                    ) else Modifier
                )
                .padding(if (isFocused) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .border(
                    width = if (isFocused) 4.dp else 2.dp,
                    color = if (isFocused) AccentPrimary
                    else SurfaceGlassBorder,
                    shape = CircleShape
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable {
                    if (editing) onEdit() else onSelect()
                },
            contentAlignment = Alignment.Center
        ) {
            if (!profile.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.imageUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(56.dp)
                )
            }

            if (editing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = profile.name,
            color = if (isFocused) AccentPrimary
            else Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) color.copy(alpha = 0.35f)
                else color.copy(alpha = 0.18f)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) color else color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.White else color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isFocused) Color.White else color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "addTileScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(132.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .padding(10.dp)
                .then(
                    if (isFocused) Modifier.border(
                        width = 3.dp,
                        color = Color.White,
                        shape = CircleShape
                    ) else Modifier
                )
                .padding(if (isFocused) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .border(
                    width = if (isFocused) 4.dp else 2.dp,
                    color = if (isFocused) AccentPrimary
                    else AccentPrimary.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add profile",
                tint = AccentPrimary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add profile",
            color = if (isFocused) AccentPrimary
            else Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ManageButton(editing: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "manageBtnScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) AccentPrimary
                else AccentPrimary.copy(alpha = 0.18f)
            )
            .border(
                width = if (isFocused) 3.dp else 1.5.dp,
                color = if (isFocused) Color.White else AccentPrimary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = null,
                tint = if (isFocused) Color.White else AccentPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (editing) "Done" else "Edit Profiles",
                color = if (isFocused) Color.White else AccentPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ProfileEditDialog(
    existing: Profile?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (name: String, imageUrl: String?) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedUrl by remember {
        mutableStateOf(existing?.imageUrl ?: AvatarCatalog.default())
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Scrim that closes on outside tap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center
    ) {
        // Card — swallow taps so they don't dismiss
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101010))
                .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existing == null) "Create profile" else "Edit profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButtonChip(
                    icon = Icons.Filled.Close,
                    tint = Color.White.copy(alpha = 0.7f),
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(20.dp))

            // Name field
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "NAME",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceGlass)
                        .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(AccentPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            keyboard?.hide()
                                            focusManager.moveFocus(FocusDirection.Down)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            keyboard?.hide()
                                            focusManager.moveFocus(FocusDirection.Up)
                                            true
                                        }
                                        Key.Back, Key.Escape -> {
                                            keyboard?.hide()
                                            focusManager.clearFocus(force = true)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                    if (name.isEmpty()) {
                        Text(
                            text = "Profile name",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "PICK AN AVATAR",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(10.dp))

            // Avatar grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 320.dp)
            ) {
                items(AvatarCatalog.all) { avatar ->
                    val isSelected = selectedUrl == avatar.url
                    var isFocused by remember { mutableStateOf(false) }
                    val itemScale by animateFloatAsState(
                        targetValue = if (isFocused) 1.10f else 1f,
                        animationSpec = tween(durationMillis = 160),
                        label = "avatarItemScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .scale(itemScale)
                            .clip(CircleShape)
                            .background(SurfaceGlass)
                            .border(
                                width = when {
                                    isFocused -> 4.dp
                                    isSelected -> 3.dp
                                    else -> 1.dp
                                },
                                color = when {
                                    isFocused -> Color.White
                                    isSelected -> AccentPrimary
                                    else -> SurfaceGlassBorder
                                },
                                shape = CircleShape
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { selectedUrl = avatar.url },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = avatar.url,
                            contentDescription = avatar.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(AccentPrimary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(AccentPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Save / Delete row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existing != null && canDelete) {
                    DialogActionButton(
                        label = "Delete",
                        icon = Icons.Filled.Delete,
                        accent = Color(0xFFEF4444),
                        filled = false,
                        enabled = true,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                }
                DialogActionButton(
                    label = if (existing == null) "Create" else "Save",
                    icon = Icons.Filled.Check,
                    accent = AccentPrimary,
                    filled = true,
                    enabled = name.isNotBlank(),
                    onClick = { onSave(name.trim(), selectedUrl) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        !enabled -> accent.copy(alpha = 0.25f)
        filled -> accent
        isFocused -> accent.copy(alpha = 0.35f)
        else -> accent.copy(alpha = 0.15f)
    }
    val fg = when {
        filled -> Color.Black
        isFocused -> Color.White
        else -> accent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = if (isFocused) 3.dp else 1.5.dp,
                color = if (isFocused) Color.White else accent.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun IconButtonChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurfaceGlass)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}
