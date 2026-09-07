package com.playtorrio.tv.updater

import com.playtorrio.tv.data.remote.dto.GitHubReleaseDto

internal object ReleaseSelector {

    fun eligibleReleases(
        releases: List<GitHubReleaseDto>,
        channel: UpdateChannel = UpdateChannel.STABLE
    ): List<GitHubReleaseDto> = releases
        .filterNot(GitHubReleaseDto::draft)
        .filter { release ->
            VersionUtils.parse(release.tagName) != null || VersionUtils.parse(release.name) != null
        }
        .sortedWith { a, b ->
            val vA = VersionUtils.parse(a.tagName) ?: VersionUtils.parse(a.name)
            val vB = VersionUtils.parse(b.tagName) ?: VersionUtils.parse(b.name)
            when {
                vA != null && vB != null -> vB.compareTo(vA)
                vA != null -> -1
                vB != null -> 1
                else -> 0
            }
        }
}
