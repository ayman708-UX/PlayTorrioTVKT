package com.playtorrio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authSessionNoticeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_session_notice_store",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() }
)

enum class StartupAuthNotice {
    PLAYTORRIO,
    TRAKT
}

@Singleton
class AuthSessionNoticeDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val hadPlayTorrioAuthKey = booleanPreferencesKey("had_playtorrio_auth")
    private val playtorrioExplicitLogoutKey = booleanPreferencesKey("playtorrio_explicit_logout")
    private val pendingPlayTorrioNoticeKey = booleanPreferencesKey("pending_playtorrio_notice")

    private val hadTraktAuthKey = booleanPreferencesKey("had_trakt_auth")
    private val traktExplicitLogoutKey = booleanPreferencesKey("trakt_explicit_logout")
    private val pendingTraktNoticeKey = booleanPreferencesKey("pending_trakt_notice")

    val pendingNotice: Flow<StartupAuthNotice?> = context.authSessionNoticeDataStore.data.map { preferences ->
        when {
            preferences[pendingPlayTorrioNoticeKey] == true -> StartupAuthNotice.PLAYTORRIO
            preferences[pendingTraktNoticeKey] == true -> StartupAuthNotice.TRAKT
            else -> null
        }
    }

    suspend fun markPlayTorrioAuthenticated() {
        context.authSessionNoticeDataStore.edit { preferences ->
            preferences[hadPlayTorrioAuthKey] = true
            preferences[playtorrioExplicitLogoutKey] = false
            preferences[pendingPlayTorrioNoticeKey] = false
        }
    }

    suspend fun markPlayTorrioExplicitLogout() {
        context.authSessionNoticeDataStore.edit { preferences ->
            preferences[hadPlayTorrioAuthKey] = false
            preferences[playtorrioExplicitLogoutKey] = true
            preferences[pendingPlayTorrioNoticeKey] = false
        }
    }

    suspend fun markUnexpectedPlayTorrioLogoutIfNeeded(): Boolean {
        var marked = false
        context.authSessionNoticeDataStore.edit { preferences ->
            val hadAuth = preferences[hadPlayTorrioAuthKey] == true
            val explicitLogout = preferences[playtorrioExplicitLogoutKey] == true
            if (hadAuth && !explicitLogout) {
                preferences[pendingPlayTorrioNoticeKey] = true
                marked = true
            }
            preferences[hadPlayTorrioAuthKey] = false
            preferences[playtorrioExplicitLogoutKey] = false
        }
        return marked
    }

    suspend fun markTraktAuthenticated() {
        context.authSessionNoticeDataStore.edit { preferences ->
            preferences[hadTraktAuthKey] = true
            preferences[traktExplicitLogoutKey] = false
            preferences[pendingTraktNoticeKey] = false
        }
    }

    suspend fun markTraktExplicitLogout() {
        context.authSessionNoticeDataStore.edit { preferences ->
            preferences[hadTraktAuthKey] = false
            preferences[traktExplicitLogoutKey] = true
            preferences[pendingTraktNoticeKey] = false
        }
    }

    suspend fun markUnexpectedTraktLogoutIfNeeded(): Boolean {
        var marked = false
        context.authSessionNoticeDataStore.edit { preferences ->
            val hadAuth = preferences[hadTraktAuthKey] == true
            val explicitLogout = preferences[traktExplicitLogoutKey] == true
            if (hadAuth && !explicitLogout) {
                preferences[pendingTraktNoticeKey] = true
                marked = true
            }
            preferences[hadTraktAuthKey] = false
            preferences[traktExplicitLogoutKey] = false
        }
        return marked
    }

    suspend fun markTraktReconnectRequired() {
        context.authSessionNoticeDataStore.edit { preferences ->
            preferences[pendingTraktNoticeKey] = true
            preferences[hadTraktAuthKey] = false
            preferences[traktExplicitLogoutKey] = false
        }
    }

    suspend fun consumeNotice(notice: StartupAuthNotice) {
        context.authSessionNoticeDataStore.edit { preferences ->
            when (notice) {
                StartupAuthNotice.PLAYTORRIO -> preferences[pendingPlayTorrioNoticeKey] = false
                StartupAuthNotice.TRAKT -> preferences[pendingTraktNoticeKey] = false
            }
        }
    }
}
