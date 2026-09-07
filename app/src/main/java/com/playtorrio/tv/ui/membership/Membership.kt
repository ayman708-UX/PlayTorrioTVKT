package com.playtorrio.tv.ui.membership

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.playtorrio.tv.domain.model.MemberAccess
import com.playtorrio.tv.domain.model.MemberTier

val LocalMemberAccess = staticCompositionLocalOf { MemberAccess.None }

object Membership {
    val access: MemberAccess
        @Composable
        @ReadOnlyComposable
        get() = LocalMemberAccess.current

    val tier: MemberTier?
        @Composable
        @ReadOnlyComposable
        get() = access.tier
}
