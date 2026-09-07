package com.playtorrio.tv.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playtorrio.tv.R
import com.playtorrio.tv.updater.UpdateChannel
import com.playtorrio.tv.updater.UpdateViewModel

@Composable
internal fun UpdateChannelSettings(initialFocusRequester: FocusRequester?) {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = hiltViewModel(context as ComponentActivity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsActionRow(
        title = stringResource(R.string.about_check_updates),
        subtitle = stringResource(R.string.about_check_updates_subtitle),
        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
        modifier = if (initialFocusRequester != null) {
            Modifier.focusRequester(initialFocusRequester)
        } else {
            Modifier
        },
        onClick = {
            viewModel.checkForUpdates(force = true, showNoUpdateFeedback = true)
        }
    )

    SettingsToggleRow(
        title = stringResource(R.string.about_update_banner_title),
        subtitle = stringResource(R.string.about_update_banner_subtitle),
        checked = state.updateBannerEnabled,
        onToggle = {
            viewModel.setUpdateBannerEnabled(!state.updateBannerEnabled)
        }
    )
}
