package com.playtorrio.tv.core.debrid

import com.playtorrio.tv.data.remote.dto.PremiumizeDirectDownloadFileDto
import com.playtorrio.tv.domain.model.StreamClientResolve
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumizeDirectDownloadFileSelector @Inject constructor() {
    fun selectFile(
        files: List<PremiumizeDirectDownloadFileDto>,
        resolve: StreamClientResolve,
        season: Int?,
        episode: Int?
    ): PremiumizeDirectDownloadFileDto? {
        if (files.isEmpty()) return null

        val targetSeason = season ?: resolve.season
        val targetEpisode = episode ?: resolve.episode

        return DebridMediaMatcher.pickMediaFile(
            files = files,
            fileIndex = resolve.fileIdx,
            filename = resolve.filename ?: resolve.torrentName,
            season = targetSeason,
            episode = targetEpisode,
            episodeTitle = resolve.title,
            name = { it.path?.takeIf { p -> p.isNotBlank() } ?: it.displayName() },
            size = { it.size ?: 0L }
        )
    }
}
