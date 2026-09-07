package com.playtorrio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.local.AnimeSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnimeSettingsViewModel @Inject constructor(
    private val dataStore: AnimeSettingsDataStore
) : ViewModel() {

    private val _isArabicAnime = MutableStateFlow(false)
    val isArabicAnime: StateFlow<Boolean> = _isArabicAnime.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.isArabicAnime.collectLatest { arabic ->
                _isArabicAnime.update { arabic }
            }
        }
    }

    fun setArabicAnime(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setArabicAnime(enabled)
        }
    }

    fun toggleArabicAnime() {
        setArabicAnime(!_isArabicAnime.value)
    }
}
