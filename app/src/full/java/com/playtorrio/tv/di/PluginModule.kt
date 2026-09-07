package com.playtorrio.tv.di

import com.playtorrio.tv.core.auth.AuthManager
import com.playtorrio.tv.core.plugin.PluginManager
import com.playtorrio.tv.core.plugin.PluginRuntime
import com.playtorrio.tv.core.plugin.cloudstream.ExternalExtensionLoader
import com.playtorrio.tv.core.plugin.cloudstream.ExternalExtensionRunner
import com.playtorrio.tv.core.plugin.cloudstream.ExternalRepoParser
import com.playtorrio.tv.core.sync.PluginSyncService
import com.playtorrio.tv.data.local.PluginDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun providePluginRuntime(): PluginRuntime {
        return PluginRuntime()
    }

    @Provides
    @Singleton
    fun providePluginManager(
        dataStore: PluginDataStore,
        runtime: PluginRuntime,
        pluginSyncService: PluginSyncService,
        authManager: AuthManager,
        externalRepoParser: ExternalRepoParser,
        externalExtensionLoader: ExternalExtensionLoader,
        externalExtensionRunner: ExternalExtensionRunner
    ): PluginManager {
        return PluginManager(
            dataStore, runtime, pluginSyncService, authManager,
            externalRepoParser, externalExtensionLoader, externalExtensionRunner
        )
    }
}
