package com.playtorrio.tv.ui.navigation

import com.playtorrio.tv.ui.theme.PlayTorrioMotion

import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.playtorrio.tv.core.build.AppFeaturePolicy
import com.playtorrio.tv.domain.model.ExperienceMode
import com.playtorrio.tv.ui.screens.CatalogSeeAllScreen
import com.playtorrio.tv.ui.screens.ExperienceModeSelectionScreen
import com.playtorrio.tv.ui.screens.LayoutSelectionScreen
import com.playtorrio.tv.ui.screens.detail.MetaDetailsScreen
import com.playtorrio.tv.ui.screens.home.HomeScreen
import com.playtorrio.tv.ui.screens.addon.AddonManagerScreen
import com.playtorrio.tv.ui.screens.addon.CatalogOrderScreen
import com.playtorrio.tv.ui.screens.library.LibraryScreen
import com.playtorrio.tv.domain.model.Subtitle
import com.playtorrio.tv.ui.screens.player.PlayerExitReason
import com.playtorrio.tv.ui.screens.player.PlayerScreen
import com.playtorrio.tv.ui.screens.player.PostPlayRecommendation
import com.playtorrio.tv.ui.screens.player.StreamSidecarSubtitles
import com.playtorrio.tv.ui.screens.plugin.PluginScreen
import com.playtorrio.tv.ui.screens.search.DiscoverScreen
import com.playtorrio.tv.ui.screens.search.SearchScreen
import com.playtorrio.tv.ui.screens.settings.AboutScreen
import com.playtorrio.tv.ui.screens.settings.LayoutSettingsScreen
import com.playtorrio.tv.ui.screens.settings.LicensesAttributionsScreen
import com.playtorrio.tv.ui.screens.settings.PlaybackSettingsScreen
import com.playtorrio.tv.ui.screens.settings.SettingsScreen
import com.playtorrio.tv.ui.screens.settings.SupportersContributorsScreen
import com.playtorrio.tv.ui.screens.settings.ThemeSettingsScreen
import com.playtorrio.tv.ui.screens.settings.TrackingSettingsScreen
import com.playtorrio.tv.ui.screens.settings.TmdbSettingsScreen
import com.playtorrio.tv.ui.screens.stream.StreamScreen
import com.playtorrio.tv.ui.screens.home.ContinueWatchingItem
import com.playtorrio.tv.ui.screens.account.AuthQrSignInScreen
import com.playtorrio.tv.ui.screens.cast.CastDetailScreen
import com.playtorrio.tv.ui.screens.profile.ProfileSelectionMode
import com.playtorrio.tv.ui.screens.profile.ProfileSelectionScreen
import com.playtorrio.tv.ui.screens.tmdb.TmdbEntityBrowseScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.playtorrio.tv.core.iptv.model.IptvPortal
import com.playtorrio.tv.core.iptv.model.VerifiedPortal
import com.playtorrio.tv.ui.screens.iptv.IptvHomeScreen
import com.playtorrio.tv.ui.screens.iptv.IptvPortalBrowserScreen
import com.playtorrio.tv.ui.screens.iptv.IptvPortalsScreen
import com.playtorrio.tv.ui.screens.iptv.IptvViewModel
import com.playtorrio.tv.ui.screens.home.HeroBackdropState
import com.playtorrio.tv.ui.screens.anime.AnimeHomeScreen
import com.playtorrio.tv.ui.screens.anime.AnimeSearchScreen
import com.playtorrio.tv.ui.screens.anime.AnimeDetailsScreen
import com.playtorrio.tv.ui.screens.anime.AnimeStreamScreen
import com.playtorrio.tv.ui.screens.music.MusicHomeScreen
import com.playtorrio.tv.ui.screens.music.MusicPlayerScreen
import com.playtorrio.tv.ui.screens.audiobook.AudiobookHomeScreen
import com.playtorrio.tv.ui.screens.audiobook.AudiobookPlayerScreen

@Composable
fun PlayTorrioNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    hideBuiltInHeaders: Boolean = false
) {
    fun isStreamToPlayer(from: String, to: String): Boolean {
        return from.startsWith("stream/") && to.startsWith("player/")
    }

    fun isPlayerToStream(from: String, to: String): Boolean {
        return from.startsWith("player/") && to.startsWith("stream/")
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            val from = initialState.destination.route.orEmpty()
            val to = targetState.destination.route.orEmpty()
            val isAutoPlayNav = targetState.arguments
                ?.getString("autoPlayNav")
                ?.toBooleanStrictOrNull() == true
            if (isStreamToPlayer(from, to) && isAutoPlayNav) {
                EnterTransition.None
            } else {
                fadeIn(animationSpec = tween(PlayTorrioMotion.tokens.durations.medium))
            }
        },
        exitTransition = {
            val from = initialState.destination.route.orEmpty()
            val to = targetState.destination.route.orEmpty()
            val isAutoPlayNav = targetState.arguments
                ?.getString("autoPlayNav")
                ?.toBooleanStrictOrNull() == true
            if (isStreamToPlayer(from, to) && isAutoPlayNav) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = tween(PlayTorrioMotion.tokens.durations.medium))
            }
        },
        popEnterTransition = {
            val from = initialState.destination.route.orEmpty()
            val to = targetState.destination.route.orEmpty()
            val isAutoPlayNav = initialState.arguments
                ?.getString("autoPlayNav")
                ?.toBooleanStrictOrNull() == true
            if (isPlayerToStream(from, to) && isAutoPlayNav) {
                EnterTransition.None
            } else {
                fadeIn(animationSpec = tween(PlayTorrioMotion.tokens.durations.medium))
            }
        },
        popExitTransition = {
            val from = initialState.destination.route.orEmpty()
            val to = targetState.destination.route.orEmpty()
            val isAutoPlayNav = initialState.arguments
                ?.getString("autoPlayNav")
                ?.toBooleanStrictOrNull() == true
            if (isPlayerToStream(from, to) && isAutoPlayNav) {
                ExitTransition.None
            } else {
                fadeOut(animationSpec = tween(PlayTorrioMotion.tokens.durations.medium))
            }
        }
    ) {
        composable(Screen.ExperienceModeSelection.route) {
            ExperienceModeSelectionScreen(
                onContinue = { mode ->
                    val destination = if (mode == ExperienceMode.ESSENTIAL) {
                        Screen.Home.route
                    } else {
                        Screen.LayoutSelection.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.ExperienceModeSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.LayoutSelection.route) {
            LayoutSelectionScreen(
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.LayoutSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            fun isAnimeContinueWatching(item: ContinueWatchingItem): Boolean {
                return when (item) {
                    is ContinueWatchingItem.InProgress -> {
                        item.progress.contentType.equals("anime", ignoreCase = true) ||
                            item.progress.contentId.startsWith("anime_", ignoreCase = true) ||
                            item.progress.videoId.startsWith("anime_", ignoreCase = true) ||
                            item.progress.videoId.startsWith("kitsu:", ignoreCase = true) ||
                            item.progress.videoId.startsWith("mal:", ignoreCase = true) ||
                            item.progress.videoId.startsWith("anilist:", ignoreCase = true) ||
                            item.genres.any { it.equals("anime", ignoreCase = true) }
                    }
                    is ContinueWatchingItem.NextUp -> {
                        item.info.contentType.equals("anime", ignoreCase = true) ||
                            item.info.contentId.startsWith("anime_", ignoreCase = true) ||
                            item.info.videoId.startsWith("anime_", ignoreCase = true) ||
                            item.info.videoId.startsWith("kitsu:", ignoreCase = true) ||
                            item.info.videoId.startsWith("mal:", ignoreCase = true) ||
                            item.info.videoId.startsWith("anilist:", ignoreCase = true) ||
                            item.info.genres.any { it.equals("anime", ignoreCase = true) }
                    }
                }
            }

            fun createContinueWatchingRoute(
                item: ContinueWatchingItem,
                manualSelection: Boolean = false,
                startFromBeginning: Boolean = false
            ): String {
                if (isAnimeContinueWatching(item)) {
                    return when (item) {
                        is ContinueWatchingItem.InProgress -> {
                            val rawContentId = item.progress.contentId
                            val animeId = when {
                                rawContentId.startsWith("anime_", ignoreCase = true) ->
                                    rawContentId.removePrefix("anime_").toIntOrNull() ?: 0
                                item.progress.videoId.startsWith("anime_", ignoreCase = true) ->
                                    item.progress.videoId.removePrefix("anime_").toIntOrNull() ?: 0
                                else -> rawContentId.toIntOrNull() ?: 0
                            }
                            Screen.AnimeStream.createRoute(
                                animeId = animeId,
                                episodeNumber = item.progress.episode ?: 1,
                                title = item.progress.name,
                                poster = item.progress.poster,
                                backdrop = item.progress.backdrop,
                                episodeTitle = item.progress.episodeTitle
                            )
                        }
                        is ContinueWatchingItem.NextUp -> {
                            val rawContentId = item.info.contentId
                            val animeId = when {
                                rawContentId.startsWith("anime_", ignoreCase = true) ->
                                    rawContentId.removePrefix("anime_").toIntOrNull() ?: 0
                                item.info.videoId.startsWith("anime_", ignoreCase = true) ->
                                    item.info.videoId.removePrefix("anime_").toIntOrNull() ?: 0
                                else -> rawContentId.toIntOrNull() ?: 0
                            }
                            Screen.AnimeStream.createRoute(
                                animeId = animeId,
                                episodeNumber = item.info.episode,
                                title = item.info.name,
                                poster = item.info.poster,
                                backdrop = item.info.backdrop,
                                episodeTitle = item.info.episodeTitle
                            )
                        }
                    }
                }

                return when (item) {
                    is ContinueWatchingItem.InProgress -> Screen.Stream.createRoute(
                        videoId = item.progress.videoId,
                        contentType = item.progress.contentType,
                        title = item.progress.name,
                        poster = item.progress.poster,
                        backdrop = item.progress.backdrop,
                        logo = item.progress.logo,
                        season = item.progress.season,
                        episode = item.progress.episode,
                        episodeName = item.progress.episodeTitle,
                        genres = null,
                        year = null,
                        contentId = item.progress.contentId,
                        contentName = item.progress.name,
                        runtime = null,
                        manualSelection = manualSelection,
                        returnToDetailOnBack = item.progress.contentType.equals("series", ignoreCase = true),
                        returnToHomeOnBack = true,
                        startFromBeginning = startFromBeginning,
                        contentLanguage = item.contentLanguage
                    )
                    is ContinueWatchingItem.NextUp -> Screen.Stream.createRoute(
                        videoId = item.info.videoId,
                        contentType = item.info.contentType,
                        title = item.info.name,
                        poster = item.info.poster,
                        backdrop = item.info.backdrop,
                        logo = item.info.logo,
                        season = item.info.season,
                        episode = item.info.episode,
                        episodeName = item.info.episodeTitle,
                        genres = null,
                        year = null,
                        contentId = item.info.contentId,
                        contentName = item.info.name,
                        runtime = null,
                        manualSelection = manualSelection,
                        returnToDetailOnBack = item.info.contentType.equals("series", ignoreCase = true),
                        returnToHomeOnBack = true,
                        startFromBeginning = startFromBeginning,
                        contentLanguage = item.info.contentLanguage
                    )
                }
            }

            HomeScreen(
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    val heroBackdrop = HeroBackdropState.consumeAndClear()
                    if (itemType.equals("anime", ignoreCase = true) || itemId.startsWith("anime_", ignoreCase = true)) {
                        val rawId = itemId.removePrefix("anime_").toIntOrNull()
                        if (rawId != null && rawId > 0) {
                            navController.navigate(Screen.AnimeDetail.createRoute(rawId, heroBackdrop))
                            return@HomeScreen
                        }
                    }
                    navController.navigate(
                        Screen.Detail.createRoute(
                            itemId = itemId,
                            itemType = itemType,
                            addonBaseUrl = addonBaseUrl,
                            heroBackdropUrl = heroBackdrop
                        )
                    )
                },
                onContinueWatchingClick = { item ->
                    navController.navigate(createContinueWatchingRoute(item))
                },
                onContinueWatchingStartFromBeginning = { item ->
                    navController.navigate(
                        createContinueWatchingRoute(item, startFromBeginning = true)
                    )
                },
                onContinueWatchingPlayManually = { item ->
                    navController.navigate(
                        createContinueWatchingRoute(item, manualSelection = true)
                    )
                },
                onNavigateToCatalogSeeAll = { catalogId, addonId, type ->
                    navController.navigate(Screen.CatalogSeeAll.createRoute(catalogId, addonId, type))
                },
                onNavigateToFolderDetail = { collectionId, folderId ->
                    navController.navigate(Screen.FolderDetail.createRoute(collectionId, folderId))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemType") { type = NavType.StringType },
                navArgument("addonBaseUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("returnFocusSeason") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("returnFocusEpisode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("returnToHomeOnBack") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("heroBackdropUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("playOnLoad") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("manualSelection") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                }
            )
        ) { backStackEntry ->
            val detailArgs = backStackEntry.arguments
            val savedState = backStackEntry.savedStateHandle
            val returnToHomeOnBack = detailArgs
                ?.getString("returnToHomeOnBack")
                ?.toBooleanStrictOrNull() == true
            val returnFocusSeason by savedState.getStateFlow(
                "returnFocusSeason", detailArgs?.getString("returnFocusSeason")?.toIntOrNull()
            ).collectAsState()
            val returnFocusEpisode by savedState.getStateFlow(
                "returnFocusEpisode", detailArgs?.getString("returnFocusEpisode")?.toIntOrNull()
            ).collectAsState()
            val heroRestoreToken by savedState.getStateFlow(
                "heroRestoreToken", 0
            ).collectAsState()
            val heroBackdropUrl = detailArgs?.getString("heroBackdropUrl")?.takeIf { it.isNotBlank() }
            val playOnLoad = detailArgs?.getString("playOnLoad")?.toBooleanStrictOrNull() == true
            val manualSelection = detailArgs?.getString("manualSelection")?.toBooleanStrictOrNull() == true
            MetaDetailsScreen(
                returnFocusSeason = returnFocusSeason,
                returnFocusEpisode = returnFocusEpisode,
                heroRestoreToken = heroRestoreToken,
                heroBackdropUrl = heroBackdropUrl,
                playOnLoad = playOnLoad,
                playOnLoadManually = manualSelection,
                onReturnFocusConsumed = {
                    savedState["returnFocusSeason"] = null
                    savedState["returnFocusEpisode"] = null
                },
                onBackPress = {
                    if (returnToHomeOnBack) {
                        val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(Screen.Home.route) {
                                launchSingleTop = true
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onNavigateToCastDetail = { personId, personName, preferCrew ->
                    navController.navigate(Screen.CastDetail.createRoute(personId, personName, preferCrew))
                },
                onNavigateToTmdbEntityBrowse = { entityKind, entityId, entityName, sourceType ->
                    navController.navigate(
                        Screen.TmdbEntityBrowse.createRoute(
                            entityKind = entityKind,
                            entityId = entityId,
                            entityName = entityName,
                            sourceType = sourceType
                        )
                    )
                },
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onPlayClick = { videoId, contentType, contentId, title, poster, backdrop, logo, season, episode, episodeName, genres, year, runtime, contentLanguage ->
                    val isAnime = contentType.equals("anime", ignoreCase = true) ||
                        contentId.startsWith("anime_", ignoreCase = true) ||
                        videoId.startsWith("anime_", ignoreCase = true) ||
                        genres?.contains("anime", ignoreCase = true) == true
                    if (isAnime) {
                        val rawAnimeId = when {
                            contentId.startsWith("anime_", ignoreCase = true) -> contentId.removePrefix("anime_").toIntOrNull() ?: 0
                            videoId.startsWith("anime_", ignoreCase = true) -> videoId.removePrefix("anime_").toIntOrNull() ?: 0
                            else -> contentId.toIntOrNull() ?: 0
                        }
                        navController.navigate(
                            Screen.AnimeStream.createRoute(
                                animeId = rawAnimeId,
                                episodeNumber = episode ?: 1,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                episodeTitle = episodeName
                            )
                        )
                    } else {
                        navController.navigate(
                            Screen.Stream.createRoute(
                                videoId = videoId,
                                contentType = contentType,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                logo = logo,
                                season = season,
                                episode = episode,
                                episodeName = episodeName,
                                genres = genres,
                                year = year,
                                contentId = contentId,
                                contentName = title,
                                runtime = runtime,
                                returnToDetailOnBack = contentType.equals("series", ignoreCase = true),
                                contentLanguage = contentLanguage
                            )
                        )
                    }
                },
                onPlayManuallyClick = { videoId, contentType, contentId, title, poster, backdrop, logo, season, episode, episodeName, genres, year, runtime, contentLanguage ->
                    val isAnime = contentType.equals("anime", ignoreCase = true) ||
                        contentId.startsWith("anime_", ignoreCase = true) ||
                        videoId.startsWith("anime_", ignoreCase = true) ||
                        genres?.contains("anime", ignoreCase = true) == true
                    if (isAnime) {
                        val rawAnimeId = when {
                            contentId.startsWith("anime_", ignoreCase = true) -> contentId.removePrefix("anime_").toIntOrNull() ?: 0
                            videoId.startsWith("anime_", ignoreCase = true) -> videoId.removePrefix("anime_").toIntOrNull() ?: 0
                            else -> contentId.toIntOrNull() ?: 0
                        }
                        navController.navigate(
                            Screen.AnimeStream.createRoute(
                                animeId = rawAnimeId,
                                episodeNumber = episode ?: 1,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                episodeTitle = episodeName
                            )
                        )
                    } else {
                        navController.navigate(
                            Screen.Stream.createRoute(
                                videoId = videoId,
                                contentType = contentType,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                logo = logo,
                                season = season,
                                episode = episode,
                                episodeName = episodeName,
                                genres = genres,
                                year = year,
                                contentId = contentId,
                                contentName = title,
                                runtime = runtime,
                                manualSelection = true,
                                returnToDetailOnBack = contentType.equals("series", ignoreCase = true),
                                contentLanguage = contentLanguage
                            )
                        )
                    }
                },
                onPlayStartFromBeginningClick = { videoId, contentType, contentId, title, poster, backdrop, logo, season, episode, episodeName, genres, year, runtime, contentLanguage ->
                    val isAnime = contentType.equals("anime", ignoreCase = true) ||
                        contentId.startsWith("anime_", ignoreCase = true) ||
                        videoId.startsWith("anime_", ignoreCase = true) ||
                        genres?.contains("anime", ignoreCase = true) == true
                    if (isAnime) {
                        val rawAnimeId = when {
                            contentId.startsWith("anime_", ignoreCase = true) -> contentId.removePrefix("anime_").toIntOrNull() ?: 0
                            videoId.startsWith("anime_", ignoreCase = true) -> videoId.removePrefix("anime_").toIntOrNull() ?: 0
                            else -> contentId.toIntOrNull() ?: 0
                        }
                        navController.navigate(
                            Screen.AnimeStream.createRoute(
                                animeId = rawAnimeId,
                                episodeNumber = episode ?: 1,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                episodeTitle = episodeName
                            )
                        )
                    } else {
                        navController.navigate(
                            Screen.Stream.createRoute(
                                videoId = videoId,
                                contentType = contentType,
                                title = title,
                                poster = poster,
                                backdrop = backdrop,
                                logo = logo,
                                season = season,
                                episode = episode,
                                episodeName = episodeName,
                                genres = genres,
                                year = year,
                                contentId = contentId,
                                contentName = title,
                                runtime = runtime,
                                startFromBeginning = true,
                                returnToDetailOnBack = contentType.equals("series", ignoreCase = true),
                                contentLanguage = contentLanguage
                            )
                        )
                    }
                }
            )
        }

        composable(
            route = Screen.Stream.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("poster") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backdrop") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("logo") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("season") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("genres") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("runtime") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("manualSelection") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("returnToDetailOnBack") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("returnToHomeOnBack") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("startFromBeginning") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("contentLanguage") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val streamArgs = backStackEntry.arguments
            val streamSavedState = backStackEntry.savedStateHandle
            val restoreSourceSelection by streamSavedState.getStateFlow(
                SOURCE_SELECTION_RESTORE_STATE_KEY, false
            ).collectAsState()
            val returnToDetailOnBack = streamArgs
                ?.getString("returnToDetailOnBack")
                ?.toBooleanStrictOrNull() == true
            val returnToHomeOnBack = streamArgs
                ?.getString("returnToHomeOnBack")
                ?.toBooleanStrictOrNull() == true
            val startFromBeginning = streamArgs
                ?.getString("startFromBeginning")
                ?.toBooleanStrictOrNull() == true
            StreamScreen(
                startFromBeginning = startFromBeginning,
                restoreSourceSelection = restoreSourceSelection,
                onSourceSelectionRestoreHandled = {
                    streamSavedState[SOURCE_SELECTION_RESTORE_STATE_KEY] = false
                },
                onBackPress = {
                    val streamContentType = streamArgs?.getString("contentType").orEmpty()
                    val streamContentId = streamArgs?.getString("contentId").orEmpty()
                    val season = streamArgs?.getString("season")?.toIntOrNull()
                    val episode = streamArgs?.getString("episode")?.toIntOrNull()
                    if (streamContentType.equals("series", ignoreCase = true) && streamContentId.isNotBlank()) {
                        val detailEntry = runCatching { navController.getBackStackEntry(Screen.Detail.route) }.getOrNull()
                        if (detailEntry != null) {
                            detailEntry.savedStateHandle["returnFocusSeason"] = season
                            detailEntry.savedStateHandle["returnFocusEpisode"] = episode
                            navController.popBackStack(Screen.Detail.route, inclusive = false)
                        } else {
                            navController.navigate(
                                Screen.Detail.createRoute(
                                    itemId = streamContentId,
                                    itemType = streamContentType,
                                    addonBaseUrl = null,
                                    returnFocusSeason = season,
                                    returnFocusEpisode = episode,
                                    returnToHomeOnBack = returnToHomeOnBack,
                                    heroBackdropUrl = streamArgs?.getString("backdrop")
                                )
                            ) {
                                popUpTo(Screen.Stream.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onStreamSelected = { playbackInfo ->
                    val streamUrl = playbackInfo.url
                        ?: if (playbackInfo.isTorrent) "torrent://${playbackInfo.infoHash}" else null
                    streamUrl?.let { url ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                streamUrl = url,
                                title = playbackInfo.title,
                                streamName = playbackInfo.streamName,
                                year = playbackInfo.year,
                                headers = playbackInfo.headers,
                                contentId = playbackInfo.contentId,
                                contentType = playbackInfo.contentType,
                                contentName = playbackInfo.contentName,
                                poster = playbackInfo.poster,
                                backdrop = playbackInfo.backdrop,
                                logo = playbackInfo.logo,
                                videoId = playbackInfo.videoId,
                                season = playbackInfo.season,
                                episode = playbackInfo.episode,
                                episodeTitle = playbackInfo.episodeTitle,
                                bingeGroup = playbackInfo.bingeGroup,
                                autoPlayNav = false,
                                returnToDetailOnBack = returnToDetailOnBack,
                                returnToHomeOnBack = returnToHomeOnBack,
                                filename = playbackInfo.filename,
                                videoHash = playbackInfo.videoHash,
                                videoSize = playbackInfo.videoSize,
                                startFromBeginning = startFromBeginning,
                                addonName = playbackInfo.addonName,
                                addonLogo = playbackInfo.addonLogo,
                                streamDescription = playbackInfo.streamDescription,
                                infoHash = playbackInfo.infoHash,
                                fileIdx = playbackInfo.fileIdx,
                                sources = playbackInfo.sources,
                                contentLanguage = playbackInfo.contentLanguage
                            )
                        )
                    }
                },
                onAutoPlayResolved = { playbackInfo ->
                    val autoPlayUrl = playbackInfo.url
                        ?: if (playbackInfo.isTorrent) "torrent://${playbackInfo.infoHash}" else null
                    autoPlayUrl?.let { url ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                streamUrl = url,
                                title = playbackInfo.title,
                                streamName = playbackInfo.streamName,
                                year = playbackInfo.year,
                                headers = playbackInfo.headers,
                                contentId = playbackInfo.contentId,
                                contentType = playbackInfo.contentType,
                                contentName = playbackInfo.contentName,
                                poster = playbackInfo.poster,
                                backdrop = playbackInfo.backdrop,
                                logo = playbackInfo.logo,
                                videoId = playbackInfo.videoId,
                                season = playbackInfo.season,
                                episode = playbackInfo.episode,
                                episodeTitle = playbackInfo.episodeTitle,
                                bingeGroup = playbackInfo.bingeGroup,
                                autoPlayNav = true,
                                returnToDetailOnBack = returnToDetailOnBack,
                                returnToHomeOnBack = returnToHomeOnBack,
                                filename = playbackInfo.filename,
                                videoHash = playbackInfo.videoHash,
                                videoSize = playbackInfo.videoSize,
                                startFromBeginning = startFromBeginning,
                                addonName = playbackInfo.addonName,
                                addonLogo = playbackInfo.addonLogo,
                                streamDescription = playbackInfo.streamDescription,
                                infoHash = playbackInfo.infoHash,
                                fileIdx = playbackInfo.fileIdx,
                                sources = playbackInfo.sources,
                                contentLanguage = playbackInfo.contentLanguage
                            )
                        ) {
                            popUpTo(Screen.Stream.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("streamName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("year") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("headers") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("poster") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backdrop") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("logo") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("videoId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("season") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("bingeGroup") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("autoPlayNav") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("returnToDetailOnBack") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("returnToHomeOnBack") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("filename") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("videoHash") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("videoSize") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("startFromBeginning") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                },
                navArgument("addonName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("addonLogo") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("streamDescription") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("contentLanguage") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("cloudSessionToken") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("launchStartedAtMs") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            fun popBackToStream(): Boolean {
                val autoPlayNavigation = backStackEntry.arguments
                    ?.getString("autoPlayNav")
                    ?.toBooleanStrictOrNull() == true
                val restoreEntry = navController.previousBackStackEntry?.takeIf { previousEntry ->
                    shouldArmSourceSelectionRestore(
                        autoPlayNavigation = autoPlayNavigation,
                        previousRoute = previousEntry.destination.route
                    )
                }
                val returnedToStream = navController.popBackStack(Screen.Stream.route, inclusive = false)
                if (returnedToStream && restoreEntry != null) {
                    restoreEntry.savedStateHandle[SOURCE_SELECTION_RESTORE_STATE_KEY] = true
                }
                return returnedToStream
            }

            fun navigateFromPostPlay(
                recommendation: PostPlayRecommendation,
                playOnLoad: Boolean,
                manualSelection: Boolean = false
            ) {
                val returnToHomeOnBack = backStackEntry.arguments
                    ?.getString("returnToHomeOnBack")
                    ?.toBooleanStrictOrNull() == true
                val playbackRootRoute = postPlayRecommendationPopUpRoute(
                    navController.previousBackStackEntry?.destination?.route
                )
                navController.navigate(
                    Screen.Detail.createRoute(
                        itemId = recommendation.id,
                        itemType = recommendation.contentType,
                        addonBaseUrl = recommendation.sourceAddonBaseUrl,
                        returnToHomeOnBack = returnToHomeOnBack,
                        heroBackdropUrl = recommendation.backdrop,
                        playOnLoad = playOnLoad,
                        manualSelection = manualSelection
                    )
                ) {
                    popUpTo(playbackRootRoute) { inclusive = true }
                }
            }

            PlayerScreen(
                onBackPress = { currentVideoId, currentSeason, currentEpisode, autoPlayEnabled, playbackCompleted ->
                    val args = backStackEntry.arguments
                    val initialSeason = args?.getString("season")?.toIntOrNull()
                    val initialEpisode = args?.getString("episode")?.toIntOrNull()
                    val episodeChangedInPlace = (currentSeason != null || currentEpisode != null) &&
                        (currentSeason != initialSeason || currentEpisode != initialEpisode)
                    val returnToDetailOnBack = args?.getString("returnToDetailOnBack")
                        ?.toBooleanStrictOrNull() == true
                    val returnToHomeOnBack = args?.getString("returnToHomeOnBack")
                        ?.toBooleanStrictOrNull() == true
                    val contentType = args?.getString("contentType").orEmpty()
                    val contentId = args?.getString("contentId").orEmpty()
                    val focusSeason = currentSeason ?: initialSeason
                    val focusEpisode = currentEpisode ?: initialEpisode
                    fun returnToDetail() {
                        val detailEntry = navController.currentBackStack.value
                            .lastOrNull {
                                val itemId = it.arguments?.getString("itemId").orEmpty()
                                val itemType = it.arguments?.getString("itemType").orEmpty()
                                it.destination.route?.startsWith("detail/") == true &&
                                    itemId == contentId &&
                                    (itemType.isBlank() || contentType.isBlank() || itemType.equals(contentType, ignoreCase = true))
                            }
                        if (detailEntry != null) {
                            detailEntry.savedStateHandle["returnFocusSeason"] = focusSeason
                            detailEntry.savedStateHandle["returnFocusEpisode"] = focusEpisode
                            navController.popBackStack(Screen.Detail.route, inclusive = false)
                        } else {
                            navController.navigate(
                                Screen.Detail.createRoute(
                                    itemId = contentId,
                                    itemType = contentType,
                                    addonBaseUrl = null,
                                    returnFocusSeason = focusSeason,
                                    returnFocusEpisode = focusEpisode,
                                    returnToHomeOnBack = returnToHomeOnBack,
                                    heroBackdropUrl = args?.getString("backdrop")
                                )
                            ) {
                                popUpTo(Screen.Player.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    when {
                        episodeChangedInPlace && autoPlayEnabled -> {
                            // autoplay moved to next episode — skip Stream, go to detail
                            if (returnToDetailOnBack && contentType.equals("series", ignoreCase = true) && contentId.isNotBlank()) {
                                returnToDetail()
                            } else {
                                navController.popBackStack()
                            }
                        }
                        episodeChangedInPlace && !autoPlayEnabled -> {
                            // manual stream switch to next episode — go to Stream of current episode
                            val videoId = currentVideoId ?: args?.getString("videoId").orEmpty()
                            if (videoId.isNotBlank() && contentType.isNotBlank()) {
                                navController.navigate(
                                    Screen.Stream.createRoute(
                                        videoId = videoId,
                                        contentType = contentType,
                                        title = args?.getString("title").orEmpty(),
                                        poster = args?.getString("poster"),
                                        backdrop = args?.getString("backdrop"),
                                        logo = args?.getString("logo"),
                                        season = focusSeason,
                                        episode = focusEpisode,
                                        year = args?.getString("year"),
                                        contentId = contentId.takeIf { it.isNotBlank() },
                                        contentName = args?.getString("contentName"),
                                        manualSelection = true,
                                        returnToDetailOnBack = returnToDetailOnBack,
                                        returnToHomeOnBack = returnToHomeOnBack
                                    )
                                ) {
                                    popUpTo(Screen.Stream.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.popBackStack()
                            }
                        }
                        else -> {
                            // normal back — skip Stream screen if episode/movie was completed
                            val skipStreamScreen = playbackCompleted && contentId.isNotBlank()
                            if (skipStreamScreen) {
                                returnToDetail()
                            } else {
                                val returnedToStream = popBackToStream()
                                if (!returnedToStream) {
                                    if (returnToDetailOnBack && contentType.equals("series", ignoreCase = true) && contentId.isNotBlank()) {
                                        returnToDetail()
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                    }
                },
                onPlaybackEnded = { nextVideoId, nextSeason, nextEpisode, exitReason ->
                    val args = backStackEntry.arguments
                    val contentType = args?.getString("contentType").orEmpty()
                    val contentId = args?.getString("contentId").orEmpty()
                    val returnToDetailOnBack = args?.getString("returnToDetailOnBack")
                        ?.toBooleanStrictOrNull() == true
                    val returnToHomeOnBack = args?.getString("returnToHomeOnBack")
                        ?.toBooleanStrictOrNull() == true
                    if (nextVideoId != null && nextSeason != null && nextEpisode != null) {
                        val route = Screen.Stream.createRoute(
                            videoId = nextVideoId,
                            contentType = contentType,
                            title = args?.getString("title").orEmpty(),
                            poster = args?.getString("poster"),
                            backdrop = args?.getString("backdrop"),
                            logo = args?.getString("logo"),
                            season = nextSeason,
                            episode = nextEpisode,
                            episodeName = null,
                            genres = null,
                            year = args?.getString("year"),
                            contentId = contentId.takeIf { it.isNotBlank() },
                            contentName = args?.getString("contentName"),
                            runtime = null,
                            returnToDetailOnBack = returnToDetailOnBack,
                            returnToHomeOnBack = returnToHomeOnBack
                        )
                        navController.navigate(route) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                        }
                    } else {
                        if (exitReason == PlayerExitReason.StillWatchingPrompt) {
                            val detailEntry = navController.currentBackStack.value
                                .lastOrNull { it.destination.route?.startsWith("detail/") == true }
                            if (detailEntry != null) {
                                detailEntry.savedStateHandle["returnFocusSeason"] = null
                                detailEntry.savedStateHandle["returnFocusEpisode"] = null
                                val token = (detailEntry.savedStateHandle.get<Int>("heroRestoreToken") ?: 0) + 1
                                detailEntry.savedStateHandle["heroRestoreToken"] = token
                                navController.popBackStack(Screen.Detail.route, inclusive = false)
                            } else {
                                val contentId = args?.getString("contentId").orEmpty()
                                val contentType = args?.getString("contentType").orEmpty()
                                val returnToHomeOnBack = args?.getString("returnToHomeOnBack")
                                    ?.toBooleanStrictOrNull() == true
                                if (contentId.isNotBlank()) {
                                    navController.navigate(
                                        Screen.Detail.createRoute(
                                            itemId = contentId,
                                            itemType = contentType,
                                            addonBaseUrl = null,
                                            returnToHomeOnBack = returnToHomeOnBack,
                                            heroBackdropUrl = args?.getString("backdrop")
                                        )
                                    ) {
                                        popUpTo(Screen.Player.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else {
                                    val poppedToStream = navController.popBackStack(Screen.Stream.route, inclusive = true)
                                    if (!poppedToStream) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        } else {
                            val contentId = args?.getString("contentId").orEmpty()
                            val contentType = args?.getString("contentType").orEmpty()
                            val returnToHomeOnBack = args?.getString("returnToHomeOnBack")
                                ?.toBooleanStrictOrNull() == true
                            val focusSeason = args?.getString("season")?.toIntOrNull()
                            val focusEpisode = args?.getString("episode")?.toIntOrNull()
                            if (contentId.isNotBlank()) {
                                val detailEntry = navController.currentBackStack.value
                                    .lastOrNull {
                                        val itemId = it.arguments?.getString("itemId").orEmpty()
                                        val itemType = it.arguments?.getString("itemType").orEmpty()
                                        it.destination.route?.startsWith("detail/") == true &&
                                            itemId == contentId &&
                                            (itemType.isBlank() || contentType.isBlank() || itemType.equals(contentType, ignoreCase = true))
                                    }
                                if (detailEntry != null) {
                                    detailEntry.savedStateHandle["returnFocusSeason"] = focusSeason
                                    detailEntry.savedStateHandle["returnFocusEpisode"] = focusEpisode
                                    navController.popBackStack(Screen.Detail.route, inclusive = false)
                                } else {
                                    navController.navigate(
                                        Screen.Detail.createRoute(
                                            itemId = contentId,
                                            itemType = contentType,
                                            addonBaseUrl = null,
                                            returnFocusSeason = focusSeason,
                                            returnFocusEpisode = focusEpisode,
                                            returnToHomeOnBack = returnToHomeOnBack,
                                            heroBackdropUrl = args?.getString("backdrop")
                                        )
                                    ) {
                                        popUpTo(Screen.Player.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                val poppedToStream = navController.popBackStack(Screen.Stream.route, inclusive = true)
                                if (!poppedToStream) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                },
                onPlayRecommendation = { recommendation, manualSelection ->
                    navigateFromPostPlay(
                        recommendation = recommendation,
                        playOnLoad = true,
                        manualSelection = manualSelection
                    )
                },
                onOpenRecommendationDetails = { recommendation ->
                    navigateFromPostPlay(
                        recommendation = recommendation,
                        playOnLoad = false
                    )
                },
                onPlaybackErrorBack = {
                    val returnedToStream = popBackToStream()
                    if (!returnedToStream) {
                        val args = backStackEntry.arguments
                        val videoId = args?.getString("videoId").orEmpty()
                        val contentType = args?.getString("contentType").orEmpty()
                        val title = args?.getString("title").orEmpty()

                        if (videoId.isBlank() || contentType.isBlank() || title.isBlank()) {
                            navController.popBackStack()
                        } else {
                            val route = Screen.Stream.createRoute(
                                videoId = videoId,
                                contentType = contentType,
                                title = title,
                                poster = args?.getString("poster"),
                                backdrop = args?.getString("backdrop"),
                                logo = args?.getString("logo"),
                                season = args?.getString("season")?.toIntOrNull(),
                                episode = args?.getString("episode")?.toIntOrNull(),
                                episodeName = args?.getString("episodeTitle"),
                                genres = null,
                                year = args?.getString("year"),
                                contentId = args?.getString("contentId"),
                                contentName = args?.getString("contentName"),
                                runtime = null,
                                manualSelection = true,
                                returnToDetailOnBack = args?.getString("returnToDetailOnBack")
                                    ?.toBooleanStrictOrNull() == true,
                                returnToHomeOnBack = args?.getString("returnToHomeOnBack")
                                    ?.toBooleanStrictOrNull() == true
                            )

                            navController.navigate(route) {
                                popUpTo(Screen.Player.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }

        composable(Screen.Search.route) { backStackEntry ->
            val searchViewModel: com.playtorrio.tv.ui.screens.search.SearchViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(backStackEntry)
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    val heroBackdrop = HeroBackdropState.consumeAndClear()
                    navController.navigate(
                        Screen.Detail.createRoute(
                            itemId = itemId,
                            itemType = itemType,
                            addonBaseUrl = addonBaseUrl,
                            heroBackdropUrl = heroBackdrop
                        )
                    )
                },
                onNavigateToSeeAll = { catalogId, addonId, type ->
                    navController.navigate(
                        Screen.CatalogSeeAll.createRoute(catalogId, addonId, type, fromSearch = true)
                    )
                },
                onOpenDiscover = { navController.navigate(Screen.Discover.route) }
            )
        }

        composable(Screen.Discover.route) {
            DiscoverScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    val heroBackdrop = HeroBackdropState.consumeAndClear()
                    navController.navigate(
                        Screen.Detail.createRoute(
                            itemId = itemId,
                            itemType = itemType,
                            addonBaseUrl = addonBaseUrl,
                            heroBackdropUrl = heroBackdrop
                        )
                    )
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onCloudPlaybackResolved = { info ->
                    val filename = info.filename ?: info.file.name
                    navController.navigate(
                        Screen.Player.createRoute(
                            streamUrl = info.url,
                            title = filename,
                            streamName = filename,
                            contentType = "cloud",
                            contentName = info.item.name,
                            videoId = "${info.item.stableKey}:${info.file.stableKey}",
                            season = 1,
                            episode = info.sequenceIndex + 1,
                            episodeTitle = filename,
                            filename = filename,
                            videoSize = info.videoSizeBytes,
                            addonName = info.item.providerName,
                            streamDescription = info.item.name,
                            cloudSessionToken = info.sessionToken
                        )
                    )
                }
            )
        }

        composable(Screen.Iptv.route) {
            val iptvViewModel: IptvViewModel = hiltViewModel()
            IptvHomeScreen(
                viewModel = iptvViewModel,
                onOpenPortals = { navController.navigate(Screen.IptvPortals.route) },
                onPlayStream = { streamUrl, title ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            streamUrl = streamUrl,
                            title = title,
                            contentType = "live",
                            streamName = title,
                            headers = mapOf("User-Agent" to "VLC/3.0.20 LibVLC/3.0.20"),
                            returnToHomeOnBack = false
                        )
                    )
                }
            )
        }

        composable(Screen.IptvPortals.route) {
            val iptvViewModel: IptvViewModel = hiltViewModel()
            IptvPortalsScreen(
                viewModel = iptvViewModel,
                onBack = { navController.popBackStack() },
                onOpenPortal = { portal ->
                    navController.navigate(
                        Screen.IptvPortalBrowser.createRoute(
                            portalUrl = portal.portal.url,
                            portalUser = portal.portal.username,
                            portalPass = portal.portal.password,
                            portalName = portal.name,
                            portalExpiry = portal.expiry
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.IptvPortalBrowser.route,
            arguments = listOf(
                navArgument("portalUrl") { type = NavType.StringType },
                navArgument("portalUser") { type = NavType.StringType },
                navArgument("portalPass") { type = NavType.StringType },
                navArgument("portalName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("portalExpiry") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val iptvViewModel: IptvViewModel = hiltViewModel()
            val portalUrl = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("portalUrl").orEmpty(), "UTF-8")
            val portalUser = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("portalUser").orEmpty(), "UTF-8")
            val portalPass = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("portalPass").orEmpty(), "UTF-8")
            val portalName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("portalName").orEmpty(), "UTF-8")
            val portalExpiry = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("portalExpiry").orEmpty(), "UTF-8")

            val verifiedPortal = VerifiedPortal(
                portal = IptvPortal(url = portalUrl, username = portalUser, password = portalPass, source = "Browser"),
                name = portalName.ifEmpty { portalUser },
                expiry = portalExpiry.ifEmpty { "Active" }
            )

            IptvPortalBrowserScreen(
                viewModel = iptvViewModel,
                portal = verifiedPortal,
                onBack = { navController.popBackStack() },
                onPlayStream = { streamUrl, title ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            streamUrl = streamUrl,
                            title = title,
                            contentType = "live",
                            streamName = title,
                            headers = mapOf("User-Agent" to "VLC/3.0.20 LibVLC/3.0.20"),
                            returnToHomeOnBack = false
                        )
                    )
                }
            )
        }

        composable(Screen.Anime.route) {
            AnimeHomeScreen(
                onNavigateToDetail = { animeId ->
                    navController.navigate(Screen.AnimeDetail.createRoute(animeId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.AnimeSearch.route)
                }
            )
        }

        composable(Screen.Music.route) {
            MusicHomeScreen(
                onNavigateToPlayer = {
                    navController.navigate(Screen.MusicPlayer.route)
                }
            )
        }

        composable(Screen.MusicPlayer.route) {
            MusicPlayerScreen(
                onBackPress = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Audiobooks.route) {
            AudiobookHomeScreen(
                onNavigateToPlayer = {
                    navController.navigate(Screen.AudiobookPlayer.route)
                }
            )
        }

        composable(Screen.AudiobookPlayer.route) {
            AudiobookPlayerScreen(
                onBackPress = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AnimeSearch.route) {
            AnimeSearchScreen(
                onNavigateToDetail = { animeId ->
                    navController.navigate(Screen.AnimeDetail.createRoute(animeId))
                }
            )
        }

        composable(
            route = Screen.AnimeDetail.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
                navArgument("heroBackdropUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AnimeDetailsScreen(
                onBackPress = { navController.popBackStack() },
                onNavigateToDetail = { animeId ->
                    navController.navigate(Screen.AnimeDetail.createRoute(animeId))
                },
                onNavigateToStream = { animeId, epNum, title, poster, backdrop, epTitle, totalEps, isAdult ->
                    navController.navigate(
                        Screen.AnimeStream.createRoute(
                            animeId = animeId,
                            episodeNumber = epNum,
                            title = title,
                            poster = poster,
                            backdrop = backdrop,
                            episodeTitle = epTitle,
                            totalEpisodes = totalEps,
                            isAdult = isAdult
                        )
                    )
                }
            )
        }

        composable(
            route = Screen.AnimeStream.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
                navArgument("episodeNumber") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("poster") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backdrop") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("episodeTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("totalEpisodes") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "0"
                },
                navArgument("isAdult") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "false"
                }
            )
        ) { backStackEntry ->
            val streamArgs = backStackEntry.arguments
            val title = streamArgs?.getString("title").orEmpty()
            val poster = streamArgs?.getString("poster")
            val backdrop = streamArgs?.getString("backdrop")
            val epNum = streamArgs?.getString("episodeNumber")?.toIntOrNull() ?: 1
            val epTitle = streamArgs?.getString("episodeTitle")
            val animeId = streamArgs?.getString("animeId").orEmpty()

            AnimeStreamScreen(
                onBackPress = { navController.popBackStack() },
                onStreamSelected = { streamResult ->
                    val sidecarSubtitles = streamResult.tracks.map { track ->
                        val displayLang = track.lang.ifBlank { track.label.ifBlank { "en" } }
                        Subtitle(
                            id = track.url,
                            url = track.url,
                            lang = displayLang,
                            addonName = streamResult.serverName,
                            addonLogo = null,
                            isStreamProvided = true
                        )
                    }
                    StreamSidecarSubtitles.set(streamResult.streamUrl, sidecarSubtitles)

                    navController.navigate(
                        Screen.Player.createRoute(
                            streamUrl = streamResult.streamUrl,
                            title = title,
                            streamName = "${streamResult.serverName} • ${streamResult.category}",
                            contentType = "anime",
                            contentId = "anime_$animeId",
                            contentName = title,
                            episode = epNum,
                            episodeTitle = epTitle,
                            poster = poster,
                            backdrop = backdrop,
                            headers = streamResult.headers,
                            filename = if (streamResult.isDirectMp4) "video.mp4" else "master.m3u8",
                            returnToDetailOnBack = true
                        )
                    )
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onNavigateToTracking = { navController.navigate(Screen.Tracking.route) },
                onNavigateToAddons = { navController.navigate(Screen.AddonManager.route) },
                onNavigateToPlugins = { navController.navigate(Screen.Plugins.route) },
                onNavigateToAuthQrSignIn = { navController.navigate(Screen.AuthQrSignIn.route) },
                onNavigateToManageProfiles = { navController.navigate(Screen.ManageProfiles.route) },
                onNavigateToSupportersContributors = {
                    navController.navigate(Screen.SupportersContributors.route)
                },
                onNavigateToLicensesAttributions = {
                    navController.navigate(Screen.LicensesAttributions.route)
                }
            )
        }

        composable(Screen.ManageProfiles.route) {
            ProfileSelectionScreen(
                onProfileSelected = {},
                screenMode = ProfileSelectionMode.Management,
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.Tracking.route) {
            TrackingSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.TmdbSettings.route) {
            TmdbSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.PlaybackSettings.route) {
            PlaybackSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBackPress = { navController.popBackStack() },
                onNavigateToSupportersContributors = {
                    navController.navigate(Screen.SupportersContributors.route)
                },
                onNavigateToLicensesAttributions = {
                    navController.navigate(Screen.LicensesAttributions.route)
                }
            )
        }

        if (AppFeaturePolicy.supportPlayTorrioEnabled) {
            composable(Screen.SupportersContributors.route) {
                SupportersContributorsScreen(
                    onBackPress = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.LicensesAttributions.route) {
            LicensesAttributionsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.AddonManager.route) {
            AddonManagerScreen(
                showBuiltInHeader = !hideBuiltInHeaders,
                onBackPress = { navController.popBackStack() },
                onNavigateToCatalogOrder = { navController.navigate(Screen.CatalogOrder.route) },
                onNavigateToCollections = { navController.navigate(Screen.Collections.route) }
            )
        }

        composable(Screen.CatalogOrder.route) {
            CatalogOrderScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.Collections.route) {
            com.playtorrio.tv.ui.screens.collection.CollectionManagementScreen(
                onNavigateToEditor = { collectionId ->
                    navController.navigate(Screen.CollectionEditor.createRoute(collectionId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CollectionEditor.route,
            arguments = listOf(
                navArgument("collectionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            com.playtorrio.tv.ui.screens.collection.CollectionEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.StringType },
                navArgument("folderId") { type = NavType.StringType }
            )
        ) {
            com.playtorrio.tv.ui.screens.collection.FolderDetailScreen(
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    val heroBackdrop = HeroBackdropState.consumeAndClear()
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl, heroBackdropUrl = heroBackdrop))
                },
                onBack = { navController.popBackStack() }
            )
        }

        if (AppFeaturePolicy.pluginsEnabled) {
            composable(Screen.Plugins.route) {
                PluginScreen(
                    onBackPress = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Account.route) {
            AuthQrSignInScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.AuthSignIn.route) {
            AuthQrSignInScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.AuthQrSignIn.route) {
            AuthQrSignInScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(Screen.LayoutSettings.route) {
            LayoutSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CatalogSeeAll.route,
            arguments = listOf(
                navArgument("catalogId") { type = NavType.StringType },
                navArgument("addonId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("fromSearch") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val catalogId = backStackEntry.arguments?.getString("catalogId") ?: ""
            val addonId = backStackEntry.arguments?.getString("addonId") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val fromSearch = backStackEntry.arguments?.getBoolean("fromSearch") ?: false

            // When coming from search, get the SearchViewModel from the Search back stack entry
            // so we share the same data (existing results + pagination)
            val searchBackStackEntry = androidx.compose.runtime.remember(fromSearch) {
                if (fromSearch) {
                    try { navController.getBackStackEntry(Screen.Search.route) } catch (_: Exception) { null }
                } else null
            }
            val searchViewModel: com.playtorrio.tv.ui.screens.search.SearchViewModel? =
                if (searchBackStackEntry != null) {
                    androidx.hilt.navigation.compose.hiltViewModel<com.playtorrio.tv.ui.screens.search.SearchViewModel>(searchBackStackEntry)
                } else null
            val homeBackStackEntry = androidx.compose.runtime.remember {
                try { navController.getBackStackEntry(Screen.Home.route) } catch (_: Exception) { null }
            }
            val homeViewModel: com.playtorrio.tv.ui.screens.home.HomeViewModel =
                if (homeBackStackEntry != null) {
                    androidx.hilt.navigation.compose.hiltViewModel(homeBackStackEntry)
                } else {
                    androidx.hilt.navigation.compose.hiltViewModel(backStackEntry)
                }

            CatalogSeeAllScreen(
                catalogId = catalogId,
                addonId = addonId,
                type = type,
                searchViewModel = searchViewModel,
                viewModel = homeViewModel,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                },
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CastDetail.route,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("personName") { type = NavType.StringType },
                navArgument("preferCrew") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            CastDetailScreen(
                onBackPress = { navController.popBackStack() },
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                }
            )
        }

        composable(
            route = Screen.TmdbEntityBrowse.route,
            arguments = listOf(
                navArgument("entityKind") { type = NavType.StringType },
                navArgument("entityId") { type = NavType.IntType },
                navArgument("entityName") { type = NavType.StringType },
                navArgument("sourceType") {
                    type = NavType.StringType
                    defaultValue = "tv"
                }
            )
        ) {
            TmdbEntityBrowseScreen(
                onBackPress = { navController.popBackStack() },
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl))
                }
            )
        }
    }
}
