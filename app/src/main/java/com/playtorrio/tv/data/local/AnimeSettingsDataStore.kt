package com.playtorrio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.playtorrio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "anime_settings"
    }

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    private val isArabicAnimeKey = booleanPreferencesKey("is_arabic_anime")

    val isArabicAnime: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { it[isArabicAnimeKey] ?: false }
    }

    suspend fun setArabicAnime(isArabic: Boolean) {
        store().edit { it[isArabicAnimeKey] = isArabic }
    }
}
