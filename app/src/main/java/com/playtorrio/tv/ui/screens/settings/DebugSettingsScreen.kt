@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.playtorrio.tv.ui.screens.settings

import com.playtorrio.tv.ui.theme.PlayTorrioTheme

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import com.playtorrio.tv.R
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.playtorrio.tv.ui.components.LoadingIndicator
import com.playtorrio.tv.ui.components.PlayTorrioDialog
import com.playtorrio.tv.ui.screens.account.InputField

@Composable
fun DebugSettingsContent(
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showErrorDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.debug_title),
            style = MaterialTheme.typography.headlineMedium,
            color = PlayTorrioTheme.colors.Secondary
        )

        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))

        Text(
            text = stringResource(R.string.debug_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = PlayTorrioTheme.colors.TextSecondary
        )

        Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xl))

        val debugListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = debugListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = PlayTorrioTheme.spacing.md, bottom = PlayTorrioTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
        ) {
            // ── Popup / Dialog Testing ──
            item(key = "debug_popup_header") {
                Text(
                    text = stringResource(R.string.debug_section_popup),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = PlayTorrioTheme.spacing.xs)
                )
            }

            item(key = "debug_playback_error") {
                DebugActionCard(
                    title = stringResource(R.string.debug_playback_error_title),
                    subtitle = stringResource(R.string.debug_playback_error_subtitle),
                    onClick = { showErrorDialog = true }
                )
            }

            item(key = "debug_progress_header") {
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.debug_section_progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = PlayTorrioTheme.spacing.xs)
                )
            }

            item(key = "debug_progress_indicator") {
                DebugProgressIndicatorCard()
            }

            // ── Feature Toggles ──
            item(key = "debug_feature_toggles_header") {
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.debug_section_features),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = PlayTorrioTheme.spacing.xs)
                )
            }

            item(key = "debug_toggle_account_tab") {
                DebugToggleCard(
                    title = stringResource(R.string.debug_account_tab_title),
                    subtitle = stringResource(R.string.debug_account_tab_subtitle),
                    checked = uiState.accountTabEnabled,
                    onToggle = { viewModel.onEvent(DebugSettingsEvent.ToggleAccountTab(it)) }
                )
            }

            item(key = "debug_toggle_sync_code") {
                DebugToggleCard(
                    title = stringResource(R.string.debug_sync_code_title),
                    subtitle = stringResource(R.string.debug_sync_code_subtitle),
                    checked = uiState.syncCodeFeaturesEnabled,
                    onToggle = { viewModel.onEvent(DebugSettingsEvent.ToggleSyncCodeFeatures(it)) }
                )
            }

            item(key = "debug_member_tier") {
                DebugMemberTierCard(
                    selectedTier = uiState.memberTier,
                    onTierSelected = { viewModel.onEvent(DebugSettingsEvent.SelectMemberTier(it)) }
                )
            }

            item(key = "debug_toggle_compose_highlighter") {
                DebugToggleCard(
                    title = stringResource(R.string.advanced_compose_highlighter),
                    subtitle = stringResource(R.string.advanced_compose_highlighter_subtitle),
                    checked = uiState.composeHighlighterEnabled,
                    onToggle = { viewModel.onEvent(DebugSettingsEvent.ToggleComposeHighlighter(it)) }
                )
            }

            item(key = "debug_toggle_buffer_logs") {
                DebugToggleCard(
                    title = stringResource(R.string.debug_buffer_logs_title),
                    subtitle = stringResource(R.string.debug_buffer_logs_subtitle),
                    checked = uiState.bufferLogsEnabled,
                    onToggle = { viewModel.onEvent(DebugSettingsEvent.ToggleBufferLogs(it)) }
                )
            }

            // ── Library Testing ──
            item(key = "debug_library_header") {
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.debug_section_library),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = PlayTorrioTheme.spacing.xs)
                )
            }

            item(key = "debug_generate_library") {
                DebugGenerateLibraryCard(
                    isLoading = uiState.generateLibraryLoading,
                    result = uiState.generateLibraryResult,
                    onGenerate = { count ->
                        viewModel.onEvent(DebugSettingsEvent.GenerateLibraryItems(count))
                    }
                )
            }

            // ── Manual Sign In ──
            item(key = "debug_account_header") {
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.debug_section_account),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayTorrioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = PlayTorrioTheme.spacing.xs)
                )
            }

            item(key = "debug_sign_in_card") {
                DebugSignInCard(
                    isLoading = uiState.signInLoading,
                    result = uiState.signInResult,
                    onSignIn = { email, password ->
                        viewModel.onEvent(DebugSettingsEvent.SignIn(email, password))
                    }
                )
            }
        }
        SettingsVerticalScrollIndicators(state = debugListState)
        }
    }

    if (showErrorDialog) {
        PlayTorrioDialog(
            onDismiss = { showErrorDialog = false },
            title = stringResource(R.string.debug_error_dialog_title),
            subtitle = stringResource(R.string.debug_error_dialog_subtitle)
        ) {
            DebugDialogButton(
                text = stringResource(R.string.debug_dismiss),
                onClick = { showErrorDialog = false }
            )
        }
    }
}

@Composable
private fun DebugProgressIndicatorCard() {
    Card(
        onClick = { },
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.debug_progress_indicator_title),
                style = MaterialTheme.typography.titleMedium,
                color = PlayTorrioTheme.colors.TextPrimary
            )
            Text(
                text = stringResource(R.string.debug_progress_indicator_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = PlayTorrioTheme.colors.TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DebugProgressIndicatorPreview(stringResource(R.string.debug_progress_indicator_small), 18.dp)
                DebugProgressIndicatorPreview(stringResource(R.string.debug_progress_indicator_default), PlayTorrioTheme.spacing.xxxl)
                DebugProgressIndicatorPreview(stringResource(R.string.debug_progress_indicator_large), 56.dp)
            }
        }
    }
}

@Composable
private fun DebugProgressIndicatorPreview(
    label: String,
    size: Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.sm)
    ) {
        LoadingIndicator(modifier = Modifier.size(size))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = PlayTorrioTheme.colors.TextSecondary
        )
    }
}

@Composable
private fun DebugToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        onClick = { onToggle(!checked) },
        modifier = Modifier
            .fillMaxWidth(),
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayTorrioTheme.colors.TextPrimary
                )
                Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayTorrioTheme.colors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(PlayTorrioTheme.spacing.md))

            Switch(
                checked = checked,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PlayTorrioTheme.colors.Secondary,
                    checkedTrackColor = PlayTorrioTheme.colors.Secondary.copy(alpha = 0.3f),
                    uncheckedThumbColor = PlayTorrioTheme.colors.TextSecondary,
                    uncheckedTrackColor = PlayTorrioTheme.colors.BackgroundCard
                )
            )
        }
    }
}

@Composable
private fun DebugActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PlayTorrioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(PlayTorrioTheme.spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PlayTorrioTheme.colors.TextSecondary
            )
        }
    }
}

@Composable
private fun DebugDialogButton(
    text: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = PlayTorrioTheme.colors.BackgroundCard,
            focusedContainerColor = PlayTorrioTheme.colors.Secondary
        ),
        shape = CardDefaults.shape(RoundedCornerShape(PlayTorrioTheme.radii.sm)),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) PlayTorrioTheme.colors.TextPrimary else PlayTorrioTheme.colors.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PlayTorrioTheme.spacing.md, horizontal = PlayTorrioTheme.spacing.lg),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DebugGenerateLibraryCard(
    isLoading: Boolean,
    result: String?,
    onGenerate: (count: Int) -> Unit
) {
    var countText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PlayTorrioTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.debug_generate_library_title),
            style = MaterialTheme.typography.titleMedium,
            color = PlayTorrioTheme.colors.TextPrimary
        )
        Text(
            text = stringResource(R.string.debug_generate_library_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PlayTorrioTheme.colors.TextSecondary
        )

        InputField(
            value = countText,
            onValueChange = { countText = it.filter { c -> c.isDigit() } },
            placeholder = stringResource(R.string.debug_generate_library_placeholder),
            keyboardType = KeyboardType.Number
        )

        if (result != null) {
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.startsWith("Failed")) PlayTorrioTheme.colors.Error else PlayTorrioTheme.colors.Secondary
            )
        }

        DebugDialogButton(
            text = if (isLoading) stringResource(R.string.debug_generating_library) else stringResource(R.string.debug_generate_library_button),
            onClick = {
                val count = countText.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (!isLoading && count != null && count > 0) {
                    onGenerate(count)
                }
            }
        )
    }
}

@Composable
private fun DebugSignInCard(
    isLoading: Boolean,
    result: String?,
    onSignIn: (email: String, password: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PlayTorrioTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.debug_manual_signin_title),
            style = MaterialTheme.typography.titleMedium,
            color = PlayTorrioTheme.colors.TextPrimary
        )
        Text(
            text = stringResource(R.string.debug_manual_signin_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PlayTorrioTheme.colors.TextSecondary
        )

        InputField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.debug_email_placeholder),
            keyboardType = KeyboardType.Email
        )

        InputField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.debug_password_placeholder),
            isPassword = true
        )

        if (result != null) {
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.startsWith("Failed")) PlayTorrioTheme.colors.Error else PlayTorrioTheme.colors.Secondary
            )
        }

        DebugDialogButton(
            text = if (isLoading) stringResource(R.string.debug_signing_in) else stringResource(R.string.debug_sign_in),
            onClick = {
                if (!isLoading && email.isNotBlank() && password.isNotBlank()) {
                    onSignIn(email.trim(), password)
                }
            }
        )
    }
}
