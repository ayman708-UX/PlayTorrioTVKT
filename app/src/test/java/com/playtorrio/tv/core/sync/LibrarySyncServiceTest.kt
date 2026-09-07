package com.playtorrio.tv.core.sync

import com.playtorrio.tv.core.sync.library.LibrarySyncLocalStore
import com.playtorrio.tv.core.sync.library.LibrarySyncRemoteDataSource
import com.playtorrio.tv.domain.model.LibraryDeltaApplyResult
import com.playtorrio.tv.domain.model.LibraryDeltaEvent
import com.playtorrio.tv.domain.model.LibrarySnapshotApplyResult
import com.playtorrio.tv.domain.model.LibrarySyncKey
import com.playtorrio.tv.domain.model.LibrarySyncReducer
import com.playtorrio.tv.domain.model.LibrarySyncState
import com.playtorrio.tv.domain.model.PosterShape
import com.playtorrio.tv.domain.model.SavedLibraryItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncServiceTest {
    @Test
    fun bootstrapUsesSnapshotThenConsumesDelta() = runBlocking {
        val local = InMemoryLibrarySyncStore()
        val remote = FakeLibrarySyncRemote(
            snapshot = listOf(libraryItem("snapshot")),
            cursor = 10L,
            events = listOf(
                LibraryDeltaEvent(11L, "delete", libraryItem("snapshot")),
                LibraryDeltaEvent(12L, "upsert", libraryItem("delta"))
            )
        )
        val service = LibrarySyncService(remote, local)

        val result = service.syncFromRemote(profileId = 3).getOrThrow()

        assertTrue(result.usedSnapshot)
        assertEquals(1, result.appliedUpserts)
        assertEquals(1, result.appliedDeletes)
        assertEquals(listOf("delta"), local.state.items.map { it.id })
        assertEquals(12L, local.state.deltaCursorEventId)
        assertEquals(
            listOf("cursor:3", "snapshot:3", "delta:3:10"),
            remote.calls
        )
    }

    @Test
    fun deltaSyncConsumesMoreThanTwoPages() = runBlocking {
        val events = (1..1_201).map { index ->
            LibraryDeltaEvent(
                eventId = index.toLong(),
                operation = "upsert",
                item = libraryItem("item-$index")
            )
        }
        val local = InMemoryLibrarySyncStore(
            LibrarySyncState(deltaInitialized = true)
        )
        val remote = FakeLibrarySyncRemote(events = events)
        val service = LibrarySyncService(remote, local)

        val result = service.syncFromRemote(profileId = 5).getOrThrow()

        assertEquals(1_201, result.appliedUpserts)
        assertEquals(1_201, local.state.items.size)
        assertEquals(1_201L, local.state.deltaCursorEventId)
        assertEquals(
            listOf("delta:5:0", "delta:5:500", "delta:5:1000"),
            remote.calls
        )
    }

    @Test
    fun pendingLocalUpsertWinsRemoteDeleteAndIsPushed() = runBlocking {
        val localItem = libraryItem("local")
        val pendingState = LibrarySyncReducer.upsertLocal(
            state = LibrarySyncState(
                deltaCursorEventId = 4L,
                deltaInitialized = true
            ),
            item = localItem,
            nowEpochMs = 1L
        )
        val local = InMemoryLibrarySyncStore(pendingState)
        val remote = FakeLibrarySyncRemote(
            events = listOf(
                LibraryDeltaEvent(5L, "delete", localItem)
            )
        )
        val service = LibrarySyncService(remote, local)

        val result = service.syncFromRemote(profileId = 1).getOrThrow()

        assertEquals(listOf("local"), local.state.items.map { it.id })
        assertTrue(local.state.pendingUpsertKeys.isEmpty())
        assertEquals(listOf("local"), remote.pushedItems.map { it.id })
        assertEquals(5L, local.state.deltaCursorEventId)
        assertEquals(1, result.pushedUpserts)
    }

    @Test
    fun finalItemDeletionUsesExplicitDeleteRpc() = runBlocking {
        val pendingState = LibrarySyncReducer.deleteLocal(
            state = LibrarySyncState(
                items = listOf(libraryItem("final")),
                deltaInitialized = true
            ),
            contentId = "final",
            contentType = "movie"
        )
        val local = InMemoryLibrarySyncStore(pendingState)
        val remote = FakeLibrarySyncRemote()
        val service = LibrarySyncService(remote, local)

        val result = service.pushToRemote(profileId = 2)

        assertTrue(result.isSuccess)
        assertTrue(remote.pushedItems.isEmpty())
        assertEquals(listOf("final"), remote.deletedKeys.map { it.contentId })
        assertTrue(local.state.pendingDeleteKeys.isEmpty())
    }

    @Test
    fun failedDeleteKeepsPendingMutationForRetry() = runBlocking {
        val pendingState = LibrarySyncReducer.deleteLocal(
            state = LibrarySyncState(
                items = listOf(libraryItem("retry")),
                deltaInitialized = true
            ),
            contentId = "retry",
            contentType = "movie"
        )
        val local = InMemoryLibrarySyncStore(pendingState)
        val remote = FakeLibrarySyncRemote(deleteFailure = IllegalStateException("offline"))
        val service = LibrarySyncService(remote, local)

        val result = service.pushToRemote(profileId = 1)

        assertTrue(result.isFailure)
        assertEquals(listOf("retry"), local.state.pendingDeleteKeys.map { it.contentId })
    }

    @Test
    fun preBootstrapPushQueuesLegacyItemsIncrementally() = runBlocking {
        val local = InMemoryLibrarySyncStore(
            LibrarySyncState(
                items = listOf(libraryItem("one"), libraryItem("two")),
                deltaInitialized = false
            )
        )
        val remote = FakeLibrarySyncRemote()
        val service = LibrarySyncService(remote, local)

        val result = service.pushToRemote(profileId = 1)

        assertTrue(result.isSuccess)
        assertEquals(setOf("one", "two"), remote.pushedItems.map { it.id }.toSet())
        assertTrue(local.state.pendingUpsertKeys.isEmpty())
    }

    private fun libraryItem(id: String): SavedLibraryItem {
        return SavedLibraryItem(
            id = id,
            type = "movie",
            name = id,
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
            addonBaseUrl = null,
            addedAt = 1L
        )
    }
}

private class InMemoryLibrarySyncStore(
    initialState: LibrarySyncState = LibrarySyncState()
) : LibrarySyncLocalStore {
    var state: LibrarySyncState = initialState

    override suspend fun getSyncState(profileId: Int): LibrarySyncState {
        return state
    }

    override suspend fun applyRemoteSnapshot(
        profileId: Int,
        remoteItems: Collection<SavedLibraryItem>,
        cursorEventId: Long
    ): LibrarySnapshotApplyResult {
        return LibrarySyncReducer.applySnapshot(
            state = state,
            remoteItems = remoteItems,
            cursorEventId = cursorEventId
        ).also { result ->
            state = result.state
        }
    }

    override suspend fun applyRemoteDelta(
        profileId: Int,
        events: Collection<LibraryDeltaEvent>
    ): LibraryDeltaApplyResult {
        return LibrarySyncReducer.applyDelta(state, events).also { result ->
            state = result.state
        }
    }

    override suspend fun queueAllItemsForPush(profileId: Int): LibrarySyncState {
        state = LibrarySyncReducer.queueAllItemsForPush(state)
        return state
    }

    override suspend fun acknowledgePush(
        profileId: Int,
        expectedMutationRevision: Long
    ): Boolean {
        val updated = LibrarySyncReducer.acknowledgePush(
            state = state,
            expectedMutationRevision = expectedMutationRevision
        ) ?: return false
        state = updated
        return true
    }
}

private class FakeLibrarySyncRemote(
    private val snapshot: List<SavedLibraryItem> = emptyList(),
    private val cursor: Long = 0L,
    private val events: List<LibraryDeltaEvent> = emptyList(),
    private val deleteFailure: Exception? = null
) : LibrarySyncRemoteDataSource {
    val calls = mutableListOf<String>()
    val pushedItems = mutableListOf<SavedLibraryItem>()
    val deletedKeys = mutableListOf<LibrarySyncKey>()

    override suspend fun pullSnapshot(
        profileId: Int,
        pageSize: Int
    ): List<SavedLibraryItem> {
        calls += "snapshot:$profileId"
        return snapshot
    }

    override suspend fun getDeltaCursor(profileId: Int): Long {
        calls += "cursor:$profileId"
        return cursor
    }

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int
    ): List<LibraryDeltaEvent> {
        calls += "delta:$profileId:$sinceEventId"
        return events
            .filter { it.eventId > sinceEventId }
            .sortedBy { it.eventId }
            .take(limit)
    }

    override suspend fun pushItems(
        profileId: Int,
        items: Collection<SavedLibraryItem>
    ) {
        pushedItems += items
    }

    override suspend fun deleteItems(
        profileId: Int,
        keys: Collection<LibrarySyncKey>
    ) {
        deleteFailure?.let { throw it }
        deletedKeys += keys
    }
}
