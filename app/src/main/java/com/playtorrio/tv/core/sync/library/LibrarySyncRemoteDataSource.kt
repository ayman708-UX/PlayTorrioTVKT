package com.playtorrio.tv.core.sync.library

import com.playtorrio.tv.domain.model.LibraryDeltaEvent
import com.playtorrio.tv.domain.model.LibrarySyncKey
import com.playtorrio.tv.domain.model.SavedLibraryItem

interface LibrarySyncRemoteDataSource {
    suspend fun pullSnapshot(
        profileId: Int,
        pageSize: Int
    ): List<SavedLibraryItem>

    suspend fun getDeltaCursor(profileId: Int): Long

    suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int
    ): List<LibraryDeltaEvent>

    suspend fun pushItems(
        profileId: Int,
        items: Collection<SavedLibraryItem>
    )

    suspend fun deleteItems(
        profileId: Int,
        keys: Collection<LibrarySyncKey>
    )
}
