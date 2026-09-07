package com.playtorrio.tv.ui.screens.settings

import com.playtorrio.tv.data.simkl.SimklAuthState
import com.playtorrio.tv.data.simkl.SimklConnectionMode

internal fun shouldRefreshMissingSimklIdentity(
    state: SimklAuthState,
    isRefreshBlocked: Boolean
): Boolean = state.isAuthenticated && state.username.isNullOrBlank() && !isRefreshBlocked

internal fun shouldCancelSimklPolling(mode: SimklConnectionMode): Boolean =
    mode == SimklConnectionMode.DISCONNECTED
