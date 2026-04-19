package com.playtorrio.tv.ui.screens.person

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class WorkFilter { ALL, MOVIES, TV }

data class PersonUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val person: PersonDetail? = null,
    val photos: List<PersonImage> = emptyList(),
    val castCredits: List<PersonCastCredit> = emptyList(),
    val crewCredits: List<PersonCrewCredit> = emptyList(),
    val filter: WorkFilter = WorkFilter.ALL
) {
    val filteredCast: List<PersonCastCredit>
        get() = when (filter) {
            WorkFilter.ALL -> castCredits
            WorkFilter.MOVIES -> castCredits.filter { it.isMovie }
            WorkFilter.TV -> castCredits.filter { !it.isMovie }
        }

    val filteredCrew: List<PersonCrewCredit>
        get() = when (filter) {
            WorkFilter.ALL -> crewCredits
            WorkFilter.MOVIES -> crewCredits.filter { it.isMovie }
            WorkFilter.TV -> crewCredits.filter { !it.isMovie }
        }
}

class PersonViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState

    private val api = TmdbClient.api
    private val key = TmdbClient.API_KEY

    fun load(personId: Int) {
        viewModelScope.launch {
            try {
                val detailDeferred = async { api.getPersonDetails(personId, key) }
                val creditsDeferred = async { api.getPersonCredits(personId, key) }
                val imagesDeferred = async { runCatching { api.getPersonImages(personId, key) }.getOrNull() }

                val detail = detailDeferred.await()
                val credits = creditsDeferred.await()
                val images = imagesDeferred.await()

                val castCredits = credits.cast
                    ?.distinctBy { it.id }
                    ?.sortedByDescending { it.popularity ?: 0.0 }
                    ?: emptyList()

                val crewCredits = credits.crew
                    ?.distinctBy { "${it.id}_${it.job}" }
                    ?.sortedByDescending { it.popularity ?: 0.0 }
                    ?: emptyList()

                _uiState.value = PersonUiState(
                    isLoading = false,
                    person = detail,
                    photos = images?.profiles ?: emptyList(),
                    castCredits = castCredits,
                    crewCredits = crewCredits
                )
            } catch (e: Exception) {
                Log.e("PersonViewModel", "Failed to load person", e)
                _uiState.value = PersonUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun setFilter(filter: WorkFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }
}
