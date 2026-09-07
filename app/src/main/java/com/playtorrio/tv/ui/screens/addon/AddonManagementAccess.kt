package com.playtorrio.tv.ui.screens.addon

import com.playtorrio.tv.core.server.AddonWebConfigMode
import com.playtorrio.tv.domain.model.ExperienceMode
import com.playtorrio.tv.domain.model.UserProfile

internal object AddonManagementAccess {

    fun isReadOnly(profile: UserProfile?): Boolean {
        return profile?.let { !it.isPrimary && it.usesPrimaryAddons } == true
    }

    fun webConfigMode(
        profile: UserProfile?,
        experienceMode: ExperienceMode = ExperienceMode.ADVANCED
    ): AddonWebConfigMode {
        return when {
            isReadOnly(profile) -> AddonWebConfigMode.COLLECTIONS_ONLY
            else -> AddonWebConfigMode.ADDONS_ONLY
        }
    }
}
