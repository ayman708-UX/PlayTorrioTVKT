package com.playtorrio.tv.core.sync.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySyncPagingTest {
    @Test
    fun snapshotPagingCollectsMoreThanTwoFullPages() = runBlocking {
        val source = (1..1_201).toList()
        val offsets = mutableListOf<Int>()

        val result = collectOffsetPages(pageSize = LIBRARY_SNAPSHOT_PAGE_SIZE) { limit, offset ->
            offsets += offset
            source.drop(offset).take(limit)
        }

        assertEquals(source, result)
        assertEquals(listOf(0, 500, 1_000), offsets)
    }

    @Test
    fun snapshotPagingTerminatesAfterExactPageMultiple() = runBlocking {
        val source = (1..1_000).toList()
        val offsets = mutableListOf<Int>()

        val result = collectOffsetPages(pageSize = LIBRARY_SNAPSHOT_PAGE_SIZE) { limit, offset ->
            offsets += offset
            source.drop(offset).take(limit)
        }

        assertEquals(source, result)
        assertEquals(listOf(0, 500, 1_000), offsets)
    }

    @Test
    fun deltaPagingConsumesEveryPageAndAdvancesCursor() = runBlocking {
        val events = (1L..1_201L).toList()
        val requestedCursors = mutableListOf<Long>()

        val cursor = consumeCursorPages(
            initialCursor = 0L,
            pageSize = LIBRARY_DELTA_PAGE_SIZE,
            fetchPage = { currentCursor, limit ->
                requestedCursors += currentCursor
                events.filter { it > currentCursor }.take(limit)
            },
            applyPage = { page, _ -> page.last() }
        )

        assertEquals(1_201L, cursor)
        assertEquals(listOf(0L, 500L, 1_000L), requestedCursors)
    }

    @Test
    fun deltaPagingTerminatesAfterExactPageMultiple() = runBlocking {
        val events = (1L..1_000L).toList()
        val requestedCursors = mutableListOf<Long>()

        val cursor = consumeCursorPages(
            initialCursor = 0L,
            pageSize = LIBRARY_DELTA_PAGE_SIZE,
            fetchPage = { currentCursor, limit ->
                requestedCursors += currentCursor
                events.filter { it > currentCursor }.take(limit)
            },
            applyPage = { page, _ -> page.last() }
        )

        assertEquals(1_000L, cursor)
        assertEquals(listOf(0L, 500L, 1_000L), requestedCursors)
    }

    @Test
    fun mutationBatchingPreservesLongUpsertAndDeleteSets() = runBlocking {
        val upserts = (1..1_201).toList()
        val deletes = (1..1_001).toList()
        val upsertBatches = mutableListOf<List<Int>>()
        val deleteBatches = mutableListOf<List<Int>>()

        forEachMutationBatch(upserts, batchSize = LIBRARY_MUTATION_BATCH_SIZE) { batch ->
            upsertBatches += batch
        }
        forEachMutationBatch(deletes, batchSize = LIBRARY_MUTATION_BATCH_SIZE) { batch ->
            deleteBatches += batch
        }

        assertEquals(listOf(500, 500, 201), upsertBatches.map { it.size })
        assertEquals(listOf(500, 500, 1), deleteBatches.map { it.size })
        assertEquals(upserts, upsertBatches.flatten())
        assertEquals(deletes, deleteBatches.flatten())
    }
}
