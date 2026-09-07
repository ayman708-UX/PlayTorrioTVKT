package com.playtorrio.tv.data.repository

import com.playtorrio.tv.core.tracking.TrackingCapability
import com.playtorrio.tv.core.tracking.TrackingProvider
import com.playtorrio.tv.core.tracking.TrackingProviderDescriptor
import com.playtorrio.tv.core.tracking.TrackingProviderId
import com.playtorrio.tv.data.local.TraktAuthDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Singleton
class TraktTrackingProvider @Inject constructor(
    authDataStore: TraktAuthDataStore,
    override val scrobbler: TraktTrackingScrobbler
) : TrackingProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.TRAKT,
        displayName = "Trakt",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
            TrackingCapability.COMMENTS,
            TrackingCapability.RECOMMENDATIONS
        )
    )
    override val isAuthenticated = authDataStore.isEffectivelyAuthenticated
        .stateIn(scope, SharingStarted.Eagerly, false)
}
