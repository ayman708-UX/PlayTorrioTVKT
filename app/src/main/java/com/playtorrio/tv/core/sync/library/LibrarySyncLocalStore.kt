package com.playtorrio.tv.core.sync.library

import com.playtorrio.tv.domain.model.LibraryDeltaApplyResult
import com.playtorrio.tv.domain.model.LibraryDeltaEvent
import com.playtorrio.tv.domain.model.LibrarySnapshotApplyResult
import com.playtorrio.tv.domain.model.LibrarySyncState
import com.playtorrio.tv.domain.model.SavedLibraryItem

interface LibrarySyncLocalStore {
    suspend fun getSyncState(profileId: Int): LibrarySyncState

    suspend fun applyRemoteSnapshot(
        profileId: Int,
        remoteItems: Collection<SavedLibraryItem>,
        cursorEventId: Long
    ): LibrarySnapshotApplyResult

    suspend fun applyRemoteDelta(
        profileId: Int,
        events: Collection<LibraryDeltaEvent>
    ): LibraryDeltaApplyResult

    suspend fun queueAllItemsForPush(profileId: Int): LibrarySyncState

    suspend fun acknowledgePush(
        profileId: Int,
        expectedMutationRevision: Long
    ): Boolean
}
