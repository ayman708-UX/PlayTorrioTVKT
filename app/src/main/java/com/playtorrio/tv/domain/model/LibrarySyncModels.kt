package com.playtorrio.tv.domain.model

data class LibrarySyncKey(
    val contentId: String,
    val contentType: String
)

data class LibraryDeltaEvent(
    val eventId: Long,
    val operation: String,
    val item: SavedLibraryItem
)

data class LibrarySyncState(
    val items: List<SavedLibraryItem> = emptyList(),
    val deltaCursorEventId: Long = 0L,
    val deltaInitialized: Boolean = false,
    val pendingUpsertKeys: List<LibrarySyncKey> = emptyList(),
    val pendingDeleteKeys: List<LibrarySyncKey> = emptyList(),
    val mutationRevision: Long = 0L
) {
    val hasPendingMutations: Boolean
        get() = pendingUpsertKeys.isNotEmpty() || pendingDeleteKeys.isNotEmpty()
}

data class LibrarySnapshotApplyResult(
    val state: LibrarySyncState,
    val preservedLocalItems: Boolean
)

data class LibraryDeltaApplyResult(
    val state: LibrarySyncState,
    val appliedUpserts: Int,
    val appliedDeletes: Int
)

fun SavedLibraryItem.toLibrarySyncKey(): LibrarySyncKey {
    return LibrarySyncKey(
        contentId = id,
        contentType = type
    )
}

fun librarySyncIdentity(contentId: String, contentType: String): String {
    return "${contentType.trim().lowercase()}:${contentId.trim()}"
}
