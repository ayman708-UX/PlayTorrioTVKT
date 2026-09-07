package com.playtorrio.tv.core.di

import com.playtorrio.tv.data.simkl.AndroidSimklAuthStorage
import com.playtorrio.tv.data.simkl.AndroidSimklSyncStorage
import com.playtorrio.tv.data.simkl.SimklAuthStorage
import com.playtorrio.tv.data.simkl.SimklApiSyncRemote
import com.playtorrio.tv.data.simkl.SimklSyncRemote
import com.playtorrio.tv.data.simkl.SimklSyncStorage
import com.playtorrio.tv.core.tracking.TrackingLibraryProvider
import com.playtorrio.tv.core.tracking.TrackingProvider
import com.playtorrio.tv.data.repository.TraktTrackingLibraryProvider
import com.playtorrio.tv.data.repository.TraktTrackingProvider
import com.playtorrio.tv.data.simkl.SimklLibraryService
import com.playtorrio.tv.core.tracking.TrackingHistoryWriter
import com.playtorrio.tv.core.tracking.TrackingProgressProvider
import com.playtorrio.tv.data.repository.TraktTrackingHistoryWriter
import com.playtorrio.tv.data.repository.TraktTrackingProgressProvider
import com.playtorrio.tv.data.simkl.SimklTrackingHistoryWriter
import com.playtorrio.tv.data.simkl.SimklTrackingProgressProvider
import com.playtorrio.tv.data.simkl.SimklTrackingProvider
import com.playtorrio.tv.core.profile.ProfileScopedCredentialStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindSimklAuthStorage(storage: AndroidSimklAuthStorage): SimklAuthStorage

    @Binds
    @IntoSet
    abstract fun bindSimklProfileScopedCredentialStore(
        storage: AndroidSimklAuthStorage
    ): ProfileScopedCredentialStore

    @Binds
    @Singleton
    abstract fun bindSimklSyncStorage(storage: AndroidSimklSyncStorage): SimklSyncStorage

    @Binds
    @Singleton
    abstract fun bindSimklSyncRemote(remote: SimklApiSyncRemote): SimklSyncRemote

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingLibraryProvider(
        provider: TraktTrackingLibraryProvider
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingLibraryProvider(
        provider: SimklLibraryService
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProgressProvider(
        provider: TraktTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProgressProvider(
        provider: SimklTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingHistoryWriter(
        writer: TraktTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingHistoryWriter(
        writer: SimklTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProvider(
        provider: TraktTrackingProvider
    ): TrackingProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProvider(
        provider: SimklTrackingProvider
    ): TrackingProvider
}
