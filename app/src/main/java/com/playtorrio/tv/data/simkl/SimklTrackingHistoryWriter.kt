package com.playtorrio.tv.data.simkl

import com.playtorrio.tv.core.profile.ProfileManager
import com.playtorrio.tv.core.tracking.TrackingHistoryItem
import com.playtorrio.tv.core.tracking.TrackingHistoryWriter
import com.playtorrio.tv.core.tracking.TrackingMediaReference
import com.playtorrio.tv.core.tracking.TrackingMutationResult
import com.playtorrio.tv.core.tracking.TrackingProviderId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimklTrackingHistoryWriter @Inject constructor(
    private val service: SimklMutationService,
    private val syncRepository: SimklSyncRepository,
    private val profileManager: ProfileManager
) : TrackingHistoryWriter {
    override val providerId = TrackingProviderId.SIMKL

    override suspend fun addToHistory(
        profileId: Int,
        items: Collection<TrackingHistoryItem>
    ): TrackingMutationResult {
        if (profileId != profileManager.activeProfileId.value) return TrackingMutationResult(0)
        syncRepository.ensureLoaded()
        val snapshot = syncRepository.state.value.snapshot
        return service.addToHistory(
            items.map { item ->
                val enriched = snapshot.enrichMediaReference(item.media)
                item.copy(media = enriched.resolveAnimeEpisodeForSimkl())
            }
        )
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>
    ): TrackingMutationResult {
        if (profileId != profileManager.activeProfileId.value) return TrackingMutationResult(0)
        syncRepository.ensureLoaded()
        val snapshot = syncRepository.state.value.snapshot
        return service.removeFromHistory(
            items.map { ref ->
                snapshot.enrichMediaReference(ref).resolveAnimeEpisodeForSimkl()
            }
        )
    }
}
