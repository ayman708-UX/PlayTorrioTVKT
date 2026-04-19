package com.playtorrio.tv.ui.screens.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.api.TmdbClient
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioMeta
import com.playtorrio.tv.data.stremio.StremioService
import com.playtorrio.tv.data.stremio.StremioStream
import com.playtorrio.tv.data.stremio.StremioVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StremioDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val meta: StremioMeta? = null,
    // Streams
    val streams: List<StremioStream> = emptyList(),
    val isLoadingStreams: Boolean = false,
    // Stream overlay
    val showStreamOverlay: Boolean = false,
    val selectedVideoId: String? = null,
    val selectedVideoTitle: String? = null,
    // Season grouping (series only)
    val selectedSeason: Int = 1,
    // TMDB redirect — if set, the screen should navigate to this route
    val tmdbRedirectRoute: String? = null
)

class StremioDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StremioDetailUiState())
    val uiState: StateFlow<StremioDetailUiState> = _uiState

    private var currentAddonId: String = ""
    private var currentType: String = ""
    private var currentMetaId: String = ""

    fun load(addonId: String, type: String, stremioId: String) {
        if (_uiState.value.meta != null && currentMetaId == stremioId) return

        currentAddonId = addonId
        currentType = type
        currentMetaId = stremioId

        viewModelScope.launch {
            _uiState.value = StremioDetailUiState(isLoading = true)
            try {
                val addons = StremioAddonRepository.getAddons()
                val preferredAddon = currentAddonId.takeIf { it != "_auto_" }
                val meta = StremioService.getMeta(
                    addons = addons,
                    type = type,
                    id = stremioId,
                    preferredAddonId = preferredAddon
                )
                if (meta != null) {
                    val defaultSeason = meta.videos
                        ?.mapNotNull { it.season }
                        ?.filter { it > 0 }
                        ?.minOrNull() ?: 1
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        meta = meta,
                        selectedSeason = defaultSeason
                    )
                    val hasVideoEntries = !meta.videos.isNullOrEmpty()
                    // Keep users on the detail page first.
                    // Movie sources are loaded only when Watch is pressed.
                    // Collection-style movie metas still expose items in meta.videos.
                    if (meta.behaviorHints?.defaultVideoId != null && !hasVideoEntries && type != "movie") {
                        val videoId = meta.behaviorHints.defaultVideoId
                        loadStreams(videoId, null)
                    }
                } else if (stremioId.startsWith("tt")) {
                    Log.w(
                        "StremioDetailVM",
                        "No Stremio meta for type=$type id=$stremioId preferredAddon=$preferredAddon"
                    )
                    // No addon provides meta for this IMDB ID — try TMDB redirect
                    val tmdbApi = TmdbClient.api
                    val tmdbKey = TmdbClient.API_KEY
                    val findResult = tmdbApi.findByExternalId(stremioId, tmdbKey)
                    val movie = findResult.movieResults.firstOrNull()
                    val tv = findResult.tvResults.firstOrNull()
                    val route = when {
                        movie != null -> "detail/${movie.id}/true"
                        tv != null -> "detail/${tv.id}/false"
                        else -> null
                    }
                    if (route != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            tmdbRedirectRoute = route
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Content not found"
                        )
                    }
                } else {
                    Log.w(
                        "StremioDetailVM",
                        "No Stremio meta for custom id type=$type id=$stremioId preferredAddon=$preferredAddon"
                    )
                    val fallbackStreams = getStreamsWithTypeFallback(
                        addons = addons,
                        requestedType = type,
                        id = stremioId,
                        preferredAddonId = preferredAddon
                    )
                    if (fallbackStreams.isNotEmpty()) {
                        val title = extractDisplayTitle(stremioId)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            meta = StremioMeta(id = stremioId, type = type, name = title),
                            showStreamOverlay = true,
                            selectedVideoId = stremioId,
                            selectedVideoTitle = title,
                            streams = fallbackStreams,
                            isLoadingStreams = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Content not found"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("StremioDetailVM", "Failed to load meta", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load"
                )
            }
        }
    }

    /** Load streams for a specific video (episode). Null videoTitle uses meta name. */
    fun loadStreams(videoId: String, videoTitle: String?) {
        _uiState.value = _uiState.value.copy(
            showStreamOverlay = true,
            selectedVideoId = videoId,
            selectedVideoTitle = videoTitle ?: _uiState.value.meta?.name,
            streams = emptyList(),
            isLoadingStreams = true
        )
        viewModelScope.launch {
            try {
                val addons = StremioAddonRepository.getAddons()
                val preferredAddon = currentAddonId.takeIf { it != "_auto_" }
                val streams = getStreamsWithTypeFallback(
                    addons = addons,
                    requestedType = currentType,
                    id = videoId,
                    preferredAddonId = preferredAddon
                )
                if (streams.isEmpty()) {
                    Log.w(
                        "StremioDetailVM",
                        "No streams type=$currentType videoId=$videoId preferredAddon=$preferredAddon"
                    )
                }
                _uiState.value = _uiState.value.copy(
                    streams = streams,
                    isLoadingStreams = false
                )
            } catch (e: Exception) {
                Log.e("StremioDetailVM", "Failed to load streams", e)
                _uiState.value = _uiState.value.copy(isLoadingStreams = false)
            }
        }
    }

    private suspend fun getStreamsWithTypeFallback(
        addons: List<com.playtorrio.tv.data.stremio.InstalledAddon>,
        requestedType: String,
        id: String,
        preferredAddonId: String?
    ): List<StremioStream> {
        val candidateTypes = listOf(requestedType, "movie", "series", "channel", "tv")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        for (type in candidateTypes) {
            val streams = StremioService.getStreams(
                addons = addons,
                type = type,
                id = id,
                preferredAddonId = preferredAddonId
            )
            if (streams.isNotEmpty()) {
                if (type != requestedType) {
                    Log.w(
                        "StremioDetailVM",
                        "Resolved streams via fallback type=$type for requestedType=$requestedType id=$id"
                    )
                }
                return streams
            }
        }
        return emptyList()
    }

    private fun extractDisplayTitle(id: String): String {
        return if (id.startsWith("http://") || id.startsWith("https://")) {
            runCatching {
                val uri = android.net.Uri.parse(id)
                uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    ?.replace('-', ' ')
                    ?: (uri.host ?: id)
            }.getOrDefault(id)
        } else {
            id
        }
    }

    fun loadStreamsForVideo(video: StremioVideo) {
        val inlineStreams = video.streams.orEmpty()
        if (inlineStreams.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                showStreamOverlay = true,
                selectedVideoId = video.id,
                selectedVideoTitle = video.title ?: _uiState.value.meta?.name,
                streams = inlineStreams,
                isLoadingStreams = false
            )
            return
        }
        loadStreams(video.id, video.title)
    }

    fun dismissStreamOverlay() {
        _uiState.value = _uiState.value.copy(
            showStreamOverlay = false,
            streams = emptyList(),
            isLoadingStreams = false
        )
    }

    fun selectSeason(season: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = season)
    }
}
