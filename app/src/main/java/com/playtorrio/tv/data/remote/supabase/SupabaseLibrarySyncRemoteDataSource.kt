package com.playtorrio.tv.data.remote.supabase

import com.playtorrio.tv.core.auth.AuthManager
import com.playtorrio.tv.core.sync.SyncClientIdentity
import com.playtorrio.tv.core.sync.library.LIBRARY_MUTATION_BATCH_SIZE
import com.playtorrio.tv.core.sync.library.LibrarySyncRemoteDataSource
import com.playtorrio.tv.core.sync.library.collectOffsetPages
import com.playtorrio.tv.core.sync.library.forEachMutationBatch
import com.playtorrio.tv.core.sync.putSyncOriginClientId
import com.playtorrio.tv.domain.model.LibraryDeltaEvent
import com.playtorrio.tv.domain.model.LibrarySyncKey
import com.playtorrio.tv.domain.model.PosterShape
import com.playtorrio.tv.domain.model.SavedLibraryItem
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseLibrarySyncRemoteDataSource @Inject constructor(
    private val authManager: AuthManager,
    private val postgrest: Postgrest,
    private val syncClientIdentity: SyncClientIdentity
) : LibrarySyncRemoteDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun pullSnapshot(
        profileId: Int,
        pageSize: Int
    ): List<SavedLibraryItem> =
        collectOffsetPages(pageSize) { limit, offset ->
            val params = buildJsonObject {
                put("p_profile_id", profileId)
                put("p_limit", limit)
                put("p_offset", offset)
            }
            withJwtRefreshRetry {
                postgrest.rpc("sync_pull_library", params)
                    .decodeList<LibrarySnapshotDto>()
            }.map(LibrarySnapshotDto::toSavedLibraryItem)
        }

    override suspend fun getDeltaCursor(profileId: Int): Long {
        val params = buildJsonObject {
            put("p_profile_id", profileId)
        }
        return withJwtRefreshRetry {
            postgrest.rpc("sync_get_library_delta_cursor", params).decodeAs<Long>()
        }
    }

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int
    ): List<LibraryDeltaEvent> {
        val params = buildJsonObject {
            put("p_profile_id", profileId)
            put("p_since_event_id", sinceEventId)
            put("p_limit", limit)
        }
        return withJwtRefreshRetry {
            postgrest.rpc("sync_pull_library_delta", params)
                .decodeList<LibraryDeltaDto>()
        }.map { event ->
            LibraryDeltaEvent(
                eventId = event.eventId,
                operation = event.operation,
                item = event.toSavedLibraryItem()
            )
        }
    }

    override suspend fun pushItems(
        profileId: Int,
        items: Collection<SavedLibraryItem>
    ) {
        forEachMutationBatch(items, LIBRARY_MUTATION_BATCH_SIZE) { batch ->
            val params = buildJsonObject {
                put(
                    "p_items",
                    json.encodeToJsonElement(batch.map(SavedLibraryItem::toMutationDto))
                )
                put("p_profile_id", profileId)
                putSyncOriginClientId(syncClientIdentity)
            }
            withJwtRefreshRetry {
                postgrest.rpc("sync_push_library_items", params)
            }
        }
    }

    override suspend fun deleteItems(
        profileId: Int,
        keys: Collection<LibrarySyncKey>
    ) {
        forEachMutationBatch(keys, LIBRARY_MUTATION_BATCH_SIZE) { batch ->
            val params = buildJsonObject {
                put(
                    "p_keys",
                    json.encodeToJsonElement(batch.map(LibrarySyncKey::toDeleteDto))
                )
                put("p_profile_id", profileId)
                putSyncOriginClientId(syncClientIdentity)
            }
            withJwtRefreshRetry {
                postgrest.rpc("sync_delete_library_items", params)
            }
        }
    }

    private suspend fun <T> withJwtRefreshRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!authManager.refreshSessionIfJwtExpired(error)) throw error
            block()
        }
    }
}

private interface LibraryRemoteFields {
    val contentId: String
    val contentType: String
    val name: String
    val poster: String?
    val posterShape: String
    val background: String?
    val description: String?
    val releaseInfo: String?
    val imdbRating: Float?
    val genres: List<String>
    val addonBaseUrl: String?
    val addedAt: Long
}

@Serializable
private data class LibrarySnapshotDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("content_id") override val contentId: String,
    @SerialName("content_type") override val contentType: String,
    override val name: String = "",
    override val poster: String? = null,
    @SerialName("poster_shape") override val posterShape: String = "POSTER",
    override val background: String? = null,
    override val description: String? = null,
    @SerialName("release_info") override val releaseInfo: String? = null,
    @SerialName("imdb_rating") override val imdbRating: Float? = null,
    override val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") override val addonBaseUrl: String? = null,
    @SerialName("added_at") override val addedAt: Long = 0L,
    @SerialName("profile_id") val profileId: Int = 1
) : LibraryRemoteFields

@Serializable
private data class LibraryDeltaDto(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") override val contentId: String,
    @SerialName("content_type") override val contentType: String,
    override val name: String = "",
    override val poster: String? = null,
    @SerialName("poster_shape") override val posterShape: String = "POSTER",
    override val background: String? = null,
    override val description: String? = null,
    @SerialName("release_info") override val releaseInfo: String? = null,
    @SerialName("imdb_rating") override val imdbRating: Float? = null,
    override val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") override val addonBaseUrl: String? = null,
    @SerialName("added_at") override val addedAt: Long = 0L
) : LibraryRemoteFields

@Serializable
private data class LibraryMutationDto(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val name: String,
    val poster: String?,
    @SerialName("poster_shape") val posterShape: String,
    val background: String?,
    val description: String?,
    @SerialName("release_info") val releaseInfo: String?,
    @SerialName("imdb_rating") val imdbRating: Float?,
    val genres: List<String>,
    @SerialName("addon_base_url") val addonBaseUrl: String?,
    @SerialName("added_at") val addedAt: Long
)

@Serializable
private data class LibraryDeleteDto(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String
)

private fun LibraryRemoteFields.toSavedLibraryItem(): SavedLibraryItem {
    return SavedLibraryItem(
        id = contentId,
        type = contentType,
        name = name,
        poster = poster,
        posterShape = posterShape.toPosterShape(),
        background = background,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        addonBaseUrl = addonBaseUrl,
        addedAt = addedAt
    )
}

private fun SavedLibraryItem.toMutationDto(): LibraryMutationDto {
    return LibraryMutationDto(
        contentId = id,
        contentType = type,
        name = name,
        poster = poster,
        posterShape = posterShape.name,
        background = background,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        addonBaseUrl = addonBaseUrl,
        addedAt = addedAt
    )
}

private fun LibrarySyncKey.toDeleteDto(): LibraryDeleteDto {
    return LibraryDeleteDto(
        contentId = contentId,
        contentType = contentType
    )
}

private fun String.toPosterShape(): PosterShape {
    return runCatching { PosterShape.valueOf(trim().uppercase()) }
        .getOrDefault(PosterShape.POSTER)
}
