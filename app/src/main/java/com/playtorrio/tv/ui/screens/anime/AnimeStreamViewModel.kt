package com.playtorrio.tv.ui.screens.anime

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.core.anime.arabic.AnimeArabicExtractor
import com.playtorrio.tv.core.anime.arabic.AnimeArabicService
import com.playtorrio.tv.core.anime.arabic.ArabicAnimeDetails
import com.playtorrio.tv.core.anime.metadata.AnilistService
import com.playtorrio.tv.core.anime.model.AnimeEpisode
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.anime.scraper.AnimeScraperService
import com.playtorrio.tv.data.local.AnimeSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimeStreamUiState(
    val animeId: Int = 0,
    val rawAnimeId: String = "",
    val episodeNumber: Int = 1,
    val title: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val episodeTitle: String? = null,
    val totalEpisodes: Int = 0,
    val isAdult: Boolean = false,
    val selectedCategoryFilter: String? = null, // null = ALL, "sub", "dub"
    val isScraping: Boolean = true,
    val streams: List<AnimeStreamResult> = emptyList()
)

@HiltViewModel
class AnimeStreamViewModel @Inject constructor(
    private val scraperService: AnimeScraperService,
    private val anilistService: AnilistService,
    private val animeArabicService: AnimeArabicService,
    private val animeArabicExtractor: AnimeArabicExtractor,
    private val animeSettingsDataStore: AnimeSettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawAnimeId: String = savedStateHandle.get<String>("animeId")
        ?: (savedStateHandle.get<Any>("animeId")?.toString().orEmpty())
    private val animeId: Int = rawAnimeId.toIntOrNull() ?: 0
    private val episodeNumber: Int = savedStateHandle.get<String>("episodeNumber")?.toIntOrNull()
        ?: (savedStateHandle.get<Any>("episodeNumber") as? Int) ?: 1
    private val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val poster: String? = savedStateHandle.get<String>("poster")
    private val backdrop: String? = savedStateHandle.get<String>("backdrop")
    private val episodeTitle: String? = savedStateHandle.get<String>("episodeTitle")
    private val totalEpisodes: Int = savedStateHandle.get<String>("totalEpisodes")?.toIntOrNull()
        ?: (savedStateHandle.get<Any>("totalEpisodes") as? Int) ?: 0
    private val isAdult: Boolean = savedStateHandle.get<String>("isAdult")?.toBooleanStrictOrNull()
        ?: (savedStateHandle.get<Any>("isAdult") as? Boolean) ?: false

    private val _uiState = MutableStateFlow(
        AnimeStreamUiState(
            animeId = animeId,
            rawAnimeId = rawAnimeId,
            episodeNumber = episodeNumber,
            title = title,
            poster = poster,
            backdrop = backdrop,
            episodeTitle = episodeTitle,
            totalEpisodes = totalEpisodes,
            isAdult = isAdult
        )
    )
    val uiState: StateFlow<AnimeStreamUiState> = _uiState.asStateFlow()

    private var scrapeJob: Job? = null
    private var resolvedAnime: AnimeMedia? = null
    private var arabicDetails: ArabicAnimeDetails? = null
    private var isArabicMode: Boolean = false

    init {
        loadAndScrape()
    }

    private fun loadAndScrape() {
        viewModelScope.launch {
            isArabicMode = animeSettingsDataStore.isArabicAnime.first() || (animeId <= 0 && rawAnimeId.isNotBlank())

            if (isArabicMode) {
                try {
                    val slug = if (rawAnimeId.isNotBlank()) rawAnimeId else animeId.toString()
                    arabicDetails = animeArabicService.getDetails(slug)
                } catch (_: Exception) {}

                if (arabicDetails == null && title.isNotBlank()) {
                    try {
                        val search = animeArabicService.search(title)
                        val firstSlug = search.firstOrNull()?.slug.orEmpty()
                        if (firstSlug.isNotBlank()) {
                            arabicDetails = animeArabicService.getDetails(firstSlug)
                        }
                    } catch (_: Exception) {}
                }

                arabicDetails?.let { details ->
                    val ep = details.episodes.find { it.number == episodeNumber }
                        ?: details.episodes.getOrNull(episodeNumber - 1)
                    _uiState.update { current ->
                        current.copy(
                            title = current.title.ifBlank { details.title.ifBlank { AnimeArabicService.humanizeSlug(details.slug) } },
                            totalEpisodes = if (current.totalEpisodes <= 0) details.episodes.size else current.totalEpisodes,
                            poster = current.poster ?: details.cover,
                            backdrop = current.backdrop ?: details.banner ?: details.cover,
                            episodeTitle = current.episodeTitle ?: ep?.title
                        )
                    }
                }
                startArabicScrape()
            } else {
                try {
                    if (animeId > 0) {
                        resolvedAnime = anilistService.fetchAnimeDetails(animeId)
                    }
                } catch (_: Exception) {}

                if (resolvedAnime == null && title.isNotBlank()) {
                    try {
                        val searchResults = anilistService.searchAnime(title)
                        resolvedAnime = searchResults.firstOrNull()
                    } catch (_: Exception) {}
                }

                resolvedAnime?.let { found ->
                    _uiState.update { current ->
                        current.copy(
                            title = current.title.ifBlank { found.displayTitle },
                            totalEpisodes = if (current.totalEpisodes <= 0) found.totalEpisodes else current.totalEpisodes,
                            poster = current.poster ?: found.coverUrl.takeIf { it.isNotBlank() },
                            backdrop = current.backdrop ?: found.backdropUrl.takeIf { it.isNotBlank() }
                        )
                    }
                }

                startScrape(categoryFilter = null)
            }
        }
    }

    fun setCategoryFilter(filter: String?) {
        if (_uiState.value.selectedCategoryFilter == filter) return
        _uiState.update { it.copy(selectedCategoryFilter = filter) }
        if (isArabicMode) {
            startArabicScrape()
        } else {
            startScrape(filter)
        }
    }

    private fun startArabicScrape() {
        scrapeJob?.cancel()
        _uiState.update { it.copy(isScraping = true, streams = emptyList()) }

        scrapeJob = viewModelScope.launch {
            try {
                val details = arabicDetails
                val ep = details?.episodes?.find { it.number == episodeNumber }
                    ?: details?.episodes?.getOrNull(episodeNumber - 1)

                val watchPath = ep?.watchPath.orEmpty()
                if (watchPath.isNotBlank()) {
                    val resolved = animeArabicExtractor.resolveEpisode(
                        watchPath = watchPath,
                        episodeNumber = episodeNumber,
                        animeTitle = _uiState.value.title
                    )
                    _uiState.update { current ->
                        current.copy(streams = resolved)
                    }
                }
            } catch (e: Exception) {
                Log.w("AnimeStreamViewModel", "Arabic scrape error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isScraping = false) }
            }
        }
    }

    private fun startScrape(categoryFilter: String?) {
        scrapeJob?.cancel()
        _uiState.update { it.copy(isScraping = true, streams = emptyList()) }

        scrapeJob = viewModelScope.launch {
            val anime = resolvedAnime ?: AnimeMedia(
                id = animeId,
                titleEnglish = title,
                titleUserPreferred = title,
                totalEpisodes = totalEpisodes,
                isAdult = isAdult
            )

            try {
                scraperService.scrapeStreams(
                    anime = anime,
                    episodeNumber = episodeNumber,
                    categoryFilter = categoryFilter
                ).collect { newStream ->
                    _uiState.update { current ->
                        if (current.streams.any { it.streamUrl == newStream.streamUrl }) {
                            current
                        } else {
                            current.copy(streams = current.streams + newStream)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AnimeStreamViewModel", "Scrape error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isScraping = false) }
            }
        }
    }
}
