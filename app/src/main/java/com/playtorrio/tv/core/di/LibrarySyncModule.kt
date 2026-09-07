package com.playtorrio.tv.core.di

import com.playtorrio.tv.core.sync.library.LibrarySyncLocalStore
import com.playtorrio.tv.core.sync.library.LibrarySyncRemoteDataSource
import com.playtorrio.tv.data.local.LibraryPreferences
import com.playtorrio.tv.data.remote.supabase.SupabaseLibrarySyncRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibrarySyncModule {
    @Binds
    @Singleton
    abstract fun bindLibrarySyncLocalStore(
        implementation: LibraryPreferences
    ): LibrarySyncLocalStore

    @Binds
    @Singleton
    abstract fun bindLibrarySyncRemoteDataSource(
        implementation: SupabaseLibrarySyncRemoteDataSource
    ): LibrarySyncRemoteDataSource
}
