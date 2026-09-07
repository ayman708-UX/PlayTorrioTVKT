package com.playtorrio.tv.core.profile

interface ProfileScopedCredentialStore {
    fun removeProfile(profileId: Int)
    fun clearAllProfiles()
}
