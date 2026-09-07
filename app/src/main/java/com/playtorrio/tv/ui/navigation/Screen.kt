package com.playtorrio.tv.ui.navigation

import android.os.SystemClock
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Detail : Screen("detail/{itemId}/{itemType}?addonBaseUrl={addonBaseUrl}&returnFocusSeason={returnFocusSeason}&returnFocusEpisode={returnFocusEpisode}&returnToHomeOnBack={returnToHomeOnBack}&heroBackdropUrl={heroBackdropUrl}&playOnLoad={playOnLoad}&manualSelection={manualSelection}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            itemId: String,
            itemType: String,
            addonBaseUrl: String? = null,
            returnFocusSeason: Int? = null,
            returnFocusEpisode: Int? = null,
            returnToHomeOnBack: Boolean = false,
            heroBackdropUrl: String? = null,
            playOnLoad: Boolean = false,
            manualSelection: Boolean = false
        ): String {
            val encodedItemId = encode(itemId)
            val encodedItemType = encode(itemType)
            val encodedAddon = addonBaseUrl?.let { encode(it) } ?: ""
            val encodedHeroBackdrop = heroBackdropUrl?.let { encode(it) } ?: ""
            return "detail/$encodedItemId/$encodedItemType?addonBaseUrl=$encodedAddon&returnFocusSeason=${returnFocusSeason ?: ""}&returnFocusEpisode=${returnFocusEpisode ?: ""}&returnToHomeOnBack=$returnToHomeOnBack&heroBackdropUrl=$encodedHeroBackdrop&playOnLoad=$playOnLoad&manualSelection=$manualSelection"
        }
    }
    data object Stream : Screen("stream/{videoId}/{contentType}/{title}?poster={poster}&backdrop={backdrop}&logo={logo}&season={season}&episode={episode}&episodeName={episodeName}&genres={genres}&year={year}&contentId={contentId}&contentName={contentName}&runtime={runtime}&manualSelection={manualSelection}&returnToDetailOnBack={returnToDetailOnBack}&returnToHomeOnBack={returnToHomeOnBack}&startFromBeginning={startFromBeginning}&contentLanguage={contentLanguage}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            videoId: String,
            contentType: String,
            title: String,
            poster: String? = null,
            backdrop: String? = null,
            logo: String? = null,
            season: Int? = null,
            episode: Int? = null,
            episodeName: String? = null,
            genres: String? = null,
            year: String? = null,
            contentId: String? = null,
            contentName: String? = null,
            runtime: Int? = null,
            manualSelection: Boolean = false,
            returnToDetailOnBack: Boolean = false,
            returnToHomeOnBack: Boolean = false,
            startFromBeginning: Boolean = false,
            contentLanguage: String? = null
        ): String {
            val encodedVideoId = encode(videoId)
            val encodedContentTypePath = encode(contentType)
            val encodedTitle = encode(title)
            val encodedPoster = poster?.let { encode(it) } ?: ""
            val encodedBackdrop = backdrop?.let { encode(it) } ?: ""
            val encodedLogo = logo?.let { encode(it) } ?: ""
            val encodedEpisodeName = episodeName?.let { encode(it) } ?: ""
            val encodedGenres = genres?.let { encode(it) } ?: ""
            val encodedYear = year?.let { encode(it) } ?: ""
            val encodedContentId = contentId?.let { encode(it) } ?: ""
            val encodedContentName = contentName?.let { encode(it) } ?: ""
            val encodedContentLanguage = contentLanguage?.let { encode(it) } ?: ""
            return "stream/$encodedVideoId/$encodedContentTypePath/$encodedTitle?poster=$encodedPoster&backdrop=$encodedBackdrop&logo=$encodedLogo&season=${season ?: ""}&episode=${episode ?: ""}&episodeName=$encodedEpisodeName&genres=$encodedGenres&year=$encodedYear&contentId=$encodedContentId&contentName=$encodedContentName&runtime=${runtime ?: ""}&manualSelection=$manualSelection&returnToDetailOnBack=$returnToDetailOnBack&returnToHomeOnBack=$returnToHomeOnBack&startFromBeginning=$startFromBeginning&contentLanguage=$encodedContentLanguage"
        }
    }
    data object Player : Screen("player/{streamUrl}/{title}?streamName={streamName}&year={year}&headers={headers}&contentId={contentId}&contentType={contentType}&contentName={contentName}&poster={poster}&backdrop={backdrop}&logo={logo}&videoId={videoId}&season={season}&episode={episode}&episodeTitle={episodeTitle}&bingeGroup={bingeGroup}&autoPlayNav={autoPlayNav}&returnToDetailOnBack={returnToDetailOnBack}&returnToHomeOnBack={returnToHomeOnBack}&filename={filename}&videoHash={videoHash}&videoSize={videoSize}&startFromBeginning={startFromBeginning}&addonName={addonName}&addonLogo={addonLogo}&streamDescription={streamDescription}&infoHash={infoHash}&fileIdx={fileIdx}&sources={sources}&contentLanguage={contentLanguage}&cloudSessionToken={cloudSessionToken}&launchStartedAtMs={launchStartedAtMs}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            streamUrl: String,
            title: String,
            streamName: String? = null,
            year: String? = null,
            headers: Map<String, String>? = null,
            contentId: String? = null,
            contentType: String? = null,
            contentName: String? = null,
            poster: String? = null,
            backdrop: String? = null,
            logo: String? = null,
            videoId: String? = null,
            season: Int? = null,
            episode: Int? = null,
            episodeTitle: String? = null,
            bingeGroup: String? = null,
            autoPlayNav: Boolean = false,
            returnToDetailOnBack: Boolean = false,
            returnToHomeOnBack: Boolean = false,
            filename: String? = null,
            videoHash: String? = null,
            videoSize: Long? = null,
            startFromBeginning: Boolean = false,
            addonName: String? = null,
            addonLogo: String? = null,
            streamDescription: String? = null,
            infoHash: String? = null,
            fileIdx: Int? = null,
            sources: List<String>? = null,
            contentLanguage: String? = null,
            cloudSessionToken: String? = null,
            launchStartedAtMs: Long = SystemClock.elapsedRealtime()
        ): String {
            val encodedUrl = encode(streamUrl)
            val encodedTitle = encode(title)
            val encodedStreamName = streamName?.let { encode(it) } ?: ""
            val encodedYear = year?.let { encode(it) } ?: ""
            val encodedHeaders = headers?.let {
                encode(org.json.JSONObject(it).toString())
            } ?: ""
            val encodedContentId = contentId?.let { encode(it) } ?: ""
            val encodedContentType = contentType?.let { encode(it) } ?: ""
            val encodedContentName = contentName?.let { encode(it) } ?: ""
            val encodedPoster = poster?.let { encode(it) } ?: ""
            val encodedBackdrop = backdrop?.let { encode(it) } ?: ""
            val encodedLogo = logo?.let { encode(it) } ?: ""
            val encodedVideoId = videoId?.let { encode(it) } ?: ""
            val encodedEpisodeTitle = episodeTitle?.let { encode(it) } ?: ""
            val encodedBingeGroup = bingeGroup?.let { encode(it) } ?: ""
            val encodedFilename = filename?.let { encode(it) } ?: ""
            val encodedVideoHash = videoHash ?: ""
            val encodedAddonName = addonName?.let { encode(it) } ?: ""
            val encodedAddonLogo = addonLogo?.let { encode(it) } ?: ""
            val encodedStreamDescription = streamDescription?.let { encode(it) } ?: ""
            val encodedInfoHash = infoHash ?: ""
            val encodedSources = sources?.let { encode(org.json.JSONArray(it).toString()) } ?: ""
            val encodedContentLanguage = contentLanguage?.let { encode(it) } ?: ""
            val encodedCloudSessionToken = cloudSessionToken?.let { encode(it) } ?: ""
            return "player/$encodedUrl/$encodedTitle?streamName=$encodedStreamName&year=$encodedYear&headers=$encodedHeaders&contentId=$encodedContentId&contentType=$encodedContentType&contentName=$encodedContentName&poster=$encodedPoster&backdrop=$encodedBackdrop&logo=$encodedLogo&videoId=$encodedVideoId&season=${season ?: ""}&episode=${episode ?: ""}&episodeTitle=$encodedEpisodeTitle&bingeGroup=$encodedBingeGroup&autoPlayNav=$autoPlayNav&returnToDetailOnBack=$returnToDetailOnBack&returnToHomeOnBack=$returnToHomeOnBack&filename=$encodedFilename&videoHash=$encodedVideoHash&videoSize=${videoSize ?: ""}&startFromBeginning=$startFromBeginning&addonName=$encodedAddonName&addonLogo=$encodedAddonLogo&streamDescription=$encodedStreamDescription&infoHash=$encodedInfoHash&fileIdx=${fileIdx ?: ""}&sources=$encodedSources&contentLanguage=$encodedContentLanguage&cloudSessionToken=$encodedCloudSessionToken&launchStartedAtMs=$launchStartedAtMs"
        }
    }
    data object Search : Screen("search")
    data object Iptv : Screen("iptv")
    data object IptvPortals : Screen("iptv_portals")
    data object IptvPortalBrowser : Screen("iptv_portal_browser/{portalUrl}/{portalUser}/{portalPass}?portalName={portalName}&portalExpiry={portalExpiry}") {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            portalUrl: String,
            portalUser: String,
            portalPass: String,
            portalName: String? = null,
            portalExpiry: String? = null
        ): String {
            val u = encode(portalUrl)
            val usr = encode(portalUser)
            val pwd = encode(portalPass)
            val name = portalName?.let { encode(it) } ?: ""
            val exp = portalExpiry?.let { encode(it) } ?: ""
            return "iptv_portal_browser/$u/$usr/$pwd?portalName=$name&portalExpiry=$exp"
        }
    }
    data object Anime : Screen("anime")
    data object Manga : Screen("manga")
    data object MangaDetail : Screen("manga_detail/{seriesId}?heroBackdropUrl={heroBackdropUrl}") {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(seriesId: String, heroBackdropUrl: String? = null): String {
            val encId = encode(seriesId)
            val encBackdrop = heroBackdropUrl?.let { encode(it) } ?: ""
            return "manga_detail/$encId?heroBackdropUrl=$encBackdrop"
        }
    }
    data object MangaReader : Screen("manga_reader/{seriesId}/{chapterId}?chapterIndex={chapterIndex}&pageIndex={pageIndex}") {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            seriesId: String,
            chapterId: String,
            chapterIndex: Int = 0,
            pageIndex: Int = 0
        ): String {
            val encSeries = encode(seriesId)
            val encChapter = encode(chapterId)
            return "manga_reader/$encSeries/$encChapter?chapterIndex=$chapterIndex&pageIndex=$pageIndex"
        }
    }
    data object Music : Screen("music")
    data object MusicPlayer : Screen("music_player")
    data object Audiobooks : Screen("audiobooks")
    data object AudiobookPlayer : Screen("audiobook_player")
    data object AnimeSearch : Screen("anime_search")
    data object AnimeDetail : Screen("anime_detail/{animeId}?heroBackdropUrl={heroBackdropUrl}") {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(animeId: String, heroBackdropUrl: String? = null): String {
            val encId = encode(animeId)
            val encodedBackdrop = heroBackdropUrl?.let { encode(it) } ?: ""
            return "anime_detail/$encId?heroBackdropUrl=$encodedBackdrop"
        }

        fun createRoute(animeId: Int, heroBackdropUrl: String? = null): String =
            createRoute(animeId.toString(), heroBackdropUrl)
    }
    data object AnimeStream : Screen(
        "anime_stream/{animeId}/{episodeNumber}?title={title}&poster={poster}&backdrop={backdrop}&episodeTitle={episodeTitle}&totalEpisodes={totalEpisodes}&isAdult={isAdult}"
    ) {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            animeId: String,
            episodeNumber: Int,
            title: String,
            poster: String? = null,
            backdrop: String? = null,
            episodeTitle: String? = null,
            totalEpisodes: Int = 0,
            isAdult: Boolean = false
        ): String {
            val encId = encode(animeId)
            val encTitle = encode(title)
            val encPoster = poster?.let { encode(it) } ?: ""
            val encBackdrop = backdrop?.let { encode(it) } ?: ""
            val encEpTitle = episodeTitle?.let { encode(it) } ?: ""
            return "anime_stream/$encId/$episodeNumber?title=$encTitle&poster=$encPoster&backdrop=$encBackdrop&episodeTitle=$encEpTitle&totalEpisodes=$totalEpisodes&isAdult=$isAdult"
        }

        fun createRoute(
            animeId: Int,
            episodeNumber: Int,
            title: String,
            poster: String? = null,
            backdrop: String? = null,
            episodeTitle: String? = null,
            totalEpisodes: Int = 0,
            isAdult: Boolean = false
        ): String = createRoute(
            animeId = animeId.toString(),
            episodeNumber = episodeNumber,
            title = title,
            poster = poster,
            backdrop = backdrop,
            episodeTitle = episodeTitle,
            totalEpisodes = totalEpisodes,
            isAdult = isAdult
        )
    }
    data object Discover : Screen("discover")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object Tracking : Screen("trakt")
    data object TmdbSettings : Screen("tmdb_settings")
    data object ThemeSettings : Screen("theme_settings")
    data object PlaybackSettings : Screen("playback_settings")
    data object About : Screen("about")
    data object SupportersContributors : Screen("supporters_contributors")
    data object LicensesAttributions : Screen("licenses_attributions")
    data object AddonManager : Screen("addon_manager")
    data object CatalogOrder : Screen("catalog_order")
    data object Plugins : Screen("plugins")
    data object ExperienceModeSelection : Screen("experience_mode_selection")
    data object LayoutSelection : Screen("layout_selection")
    data object LayoutSettings : Screen("layout_settings")
    data object Account : Screen("account")
    data object ManageProfiles : Screen("manage_profiles")
    data object AuthSignIn : Screen("auth_sign_in")
    data object AuthQrSignIn : Screen("auth_qr_sign_in")
    data object SyncCodeGenerate : Screen("sync_code_generate")
    data object SyncCodeClaim : Screen("sync_code_claim")
    data object CatalogSeeAll : Screen("catalog_see_all/{catalogId}/{addonId}/{type}?fromSearch={fromSearch}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(catalogId: String, addonId: String, type: String, fromSearch: Boolean = false): String {
            return "catalog_see_all/${encode(catalogId)}/${encode(addonId)}/${encode(type)}?fromSearch=$fromSearch"
        }
    }

    data object Collections : Screen("collections")

    data object CollectionEditor : Screen("collection_editor?collectionId={collectionId}") {
        fun createRoute(collectionId: String? = null): String {
            return "collection_editor?collectionId=${collectionId ?: ""}"
        }
    }

    data object FolderDetail : Screen("folder_detail/{collectionId}/{folderId}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(collectionId: String, folderId: String): String {
            return "folder_detail/${encode(collectionId)}/${encode(folderId)}"
        }
    }

    data object ProfileSelection : Screen("profile_selection")

    data object CastDetail : Screen("cast_detail/{personId}/{personName}?preferCrew={preferCrew}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            personId: Int,
            personName: String,
            preferCrew: Boolean = false
        ): String {
            return "cast_detail/$personId/${encode(personName)}?preferCrew=$preferCrew"
        }
    }

    data object TmdbEntityBrowse : Screen(
        "tmdb_entity_browse/{entityKind}/{entityId}/{entityName}?sourceType={sourceType}"
    ) {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            entityKind: String,
            entityId: Int,
            entityName: String,
            sourceType: String
        ): String {
            return "tmdb_entity_browse/${encode(entityKind)}/$entityId/${encode(entityName)}?sourceType=${encode(sourceType)}"
        }
    }
}
