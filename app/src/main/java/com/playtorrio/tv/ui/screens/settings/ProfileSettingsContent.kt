@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.playtorrio.tv.ui.screens.settings

import com.playtorrio.tv.ui.theme.PlayTorrioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.playtorrio.tv.R

@Composable
internal fun ProfileSettingsContent(
    onManageProfiles: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PlayTorrioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.profile_manage_button),
                subtitle = null,
                onClick = onManageProfiles,
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}
