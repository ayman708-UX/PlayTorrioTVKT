package com.playtorrio.tv.core.di

import com.playtorrio.tv.data.repository.AddonRepositoryImpl
import com.playtorrio.tv.data.repository.CatalogRepositoryImpl
import com.playtorrio.tv.data.repository.LibraryRepositoryImpl
import com.playtorrio.tv.data.repository.MetaRepositoryImpl
import com.playtorrio.tv.data.repository.StreamRepositoryImpl
import com.playtorrio.tv.data.repository.SubtitleRepositoryImpl
import com.playtorrio.tv.data.repository.SyncRepositoryImpl
import com.playtorrio.tv.data.repository.WatchProgressRepositoryImpl
import com.playtorrio.tv.domain.repository.AddonRepository
import com.playtorrio.tv.domain.repository.CatalogRepository
import com.playtorrio.tv.domain.repository.LibraryRepository
import com.playtorrio.tv.domain.repository.MetaRepository
import com.playtorrio.tv.domain.repository.StreamRepository
import com.playtorrio.tv.domain.repository.SubtitleRepository
import com.playtorrio.tv.domain.repository.SyncRepository
import com.playtorrio.tv.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAddonRepository(impl: AddonRepositoryImpl): AddonRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindMetaRepository(impl: MetaRepositoryImpl): MetaRepository

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleRepository(impl: SubtitleRepositoryImpl): SubtitleRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository
}
