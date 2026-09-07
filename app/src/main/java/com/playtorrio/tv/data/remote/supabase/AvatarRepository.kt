package com.playtorrio.tv.data.remote.supabase

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.playtorrio.tv.data.local.MemberCatalogStorage
import com.playtorrio.tv.domain.model.ServerConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MemberAvatarBucket = "membership-profile-avatars"
private const val MemberAvatarTag = "MemberAvatars"
private const val AvatarCatalogRefreshIntervalMs = 15 * 60_000L

internal fun isAvatarCatalogRefreshDue(lastRefreshAtMs: Long, nowMs: Long): Boolean {
    return lastRefreshAtMs <= 0L ||
        nowMs < lastRefreshAtMs ||
        nowMs - lastRefreshAtMs >= AvatarCatalogRefreshIntervalMs
}

@Serializable
private data class StoredAvatarCatalogPayload(
    val standardItems: List<SupabaseAvatarCatalogItem> = emptyList(),
    val memberItems: List<SupabaseMemberAvatarCatalogItem> = emptyList(),
    val standardLoaded: Boolean = false,
    val memberLoaded: Boolean = false
)

data class AvatarCatalogItem(
    val id: String,
    val displayName: String,
    val imageUrl: String,
    val category: String,
    val sortOrder: Int,
    val bgColor: String? = null,
    val memberOnly: Boolean = false
)

val BUILTIN_AVATARS: List<AvatarCatalogItem> = listOf(
    AvatarCatalogItem("spider_man", "Spider-Man", "https://cdn.mos.cms.futurecdn.net/TeMTjhZaFLdaNTKjyXeJPd.jpg", "movie", 1, "#E53935"),
    AvatarCatalogItem("iron_man", "Iron Man", "https://playcontestofchampions.com/wp-content/uploads/2023/04/champion-iron-man-infinity-war.webp", "movie", 2, "#FFB300"),
    AvatarCatalogItem("batman", "Batman", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTX3ZeC7Nbmc7S9w7f5Iaa2R7TJu85fZYxAgg&s", "movie", 3, "#212121"),
    AvatarCatalogItem("superman", "Superman", "https://preview.redd.it/what-about-superman-do-you-think-makes-him-the-greatest-v0-08c9a7jru54d1.jpeg?width=1080&crop=smart&auto=webp&s=2805c3c1d1d78470e1bf1b19a7b537685652b824", "movie", 4, "#1E88E5"),
    AvatarCatalogItem("wonder_woman", "Wonder Woman", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcReIReolJV8IaAgujrJwca9sAwo5-uQyMFp_Q&s", "movie", 5, "#D81B60"),
    AvatarCatalogItem("black_panther", "Black Panther", "https://www.sideshow.com/cdn-cgi/image/quality=90,f=auto/https://www.sideshow.com/storage/product-images/910233/black-panther-deluxe_marvel_gallery_61eb5a329c25b.jpg", "movie", 6, "#424242"),
    AvatarCatalogItem("thor", "Thor", "https://upload.wikimedia.org/wikipedia/en/3/3c/Chris_Hemsworth_as_Thor.jpg", "movie", 7, "#1976D2"),
    AvatarCatalogItem("hulk", "Hulk", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQaB6qXmmkFpFswsLVt6qz5swbXSeJkwG9XDQ&s", "movie", 8, "#43A047"),
    AvatarCatalogItem("doctor_strange", "Doctor Strange", "https://static.wikia.nocookie.net/disney/images/d/dc/Doctor_Strange_-_Profile.png/revision/latest?cb=20220804200852", "movie", 9, "#8E24AA"),
    AvatarCatalogItem("wolverine", "Wolverine", "https://img.asmedia.epimg.net/resizer/v2/JRNLXSYQ6FBXFHBEXX6IIEFOZU.jpg?auth=00797de5a03ca0ff06333b7f5cfc1c6b13a6afb52620be3157b11f4d2e9e22b7&width=1472&height=1104&smart=true", "movie", 10, "#FFB300"),
    AvatarCatalogItem("deadpool", "Deadpool", "https://static.wikia.nocookie.net/marvelcinematicuniverse/images/a/ad/Deadpool_Infobox.png/revision/latest/thumbnail/width/360/height/450?cb=20240522015012", "movie", 11, "#D32F2F"),
    AvatarCatalogItem("joker", "Joker", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSU0e1oje8lXK_q7SFTY1kOf-Qez45QIykAPg&s", "movie", 12, "#7B1FA2"),
    AvatarCatalogItem("harry_potter", "Harry Potter", "https://static.wikia.nocookie.net/neoencyclopedia/images/4/44/HarryPotter5poster.jpg/revision/latest?cb=20121121021021", "movie", 13, "#8D6E63"),
    AvatarCatalogItem("darth_vader", "Darth Vader", "https://m.media-amazon.com/images/M/MV5BNTQwMGU5MjUtZmFhMi00OGFkLWFiMDUtY2EzODk3NDM3NTI5XkEyXkFqcGc@._V1_QL75_UY281_CR31,0,500,281_.jpg", "movie", 14, "#212121"),
    AvatarCatalogItem("yoda", "Yoda", "https://hips.hearstapps.com/hmg-prod/images/grogu-baby-yoda-the-child-1606497947.png?crop=0.421xw:1.00xh;0.349xw,0&resize=1200:*", "movie", 15, "#4CAF50"),
    AvatarCatalogItem("barbie", "Barbie", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSPpd2dKUULlTHDgoufs2wFrK7KQlYsPp5WFw&s", "movie", 16, "#EC407A"),
    AvatarCatalogItem("walter_white", "Walter White", "https://static.wikia.nocookie.net/breakingbad/images/b/b4/Walter_2008.png/revision/latest/scale-to-width/360?cb=20200704164147", "tv", 17, "#388E3C"),
    AvatarCatalogItem("eleven", "Eleven", "https://s2.r29static.com/bin/entry/75e/x,80/2214031/image.jpg", "tv", 18, "#1E88E5"),
    AvatarCatalogItem("wednesday", "Wednesday", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ7JRhlHE_JfEz0mzEpdH7XwLrB9KbBBi1qhQ&s", "tv", 19, "#263238"),
    AvatarCatalogItem("goku", "Goku", "https://cdng.europosters.eu/pod_public/750/260203.jpg", "anime", 20, "#FB8C00"),
    AvatarCatalogItem("naruto", "Naruto", "https://cdng.europosters.eu/pod_public/1300/229900.jpg", "anime", 21, "#FF9800"),
    AvatarCatalogItem("pikachu", "Pikachu", "https://www.denofgeek.com/wp-content/uploads/2021/04/Pikachu.png?resize=768%2C432", "anime", 22, "#FBC02D"),
    AvatarCatalogItem("mario", "Mario", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ6PdNYIYdX1nnhvn1XVMKt3_qJxR5pMpXLfw&s", "gaming", 23, "#E53935"),
    AvatarCatalogItem("sonic", "Sonic", "https://yt3.ggpht.com/ZTmtyDVh-Bo0BLDWRp_FTfE4gwFbwC3-W5L23V97QRV2Ebsqk4P3Etg4vKk4UOtvIZTBce1sTYT6dw=s1024-nd-v1", "gaming", 24, "#1E88E5"),
    AvatarCatalogItem("master_chief", "Master Chief", "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQSgBQgPv5BqeQXRFJOLzN1HXWUEgUYtLSdfA&s", "gaming", 25, "#43A047")
)

private fun mergeWithBuiltinAvatars(remoteItems: List<AvatarCatalogItem>): List<AvatarCatalogItem> {
    if (remoteItems.isEmpty()) return BUILTIN_AVATARS
    val remoteIds = remoteItems.map { it.id }.toSet()
    return remoteItems + BUILTIN_AVATARS.filter { it.id !in remoteIds }
}

@Singleton
class AvatarRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val storage: Storage,
    private val serverConfiguration: ServerConfiguration,
    private val memberCatalogStorage: MemberCatalogStorage,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val storedCatalog = loadStoredCatalog()
    private var standardMetadata = storedCatalog?.standardItems.orEmpty()
    private var memberMetadata = storedCatalog?.memberItems.orEmpty()
    private var standardCatalogLoaded = storedCatalog?.standardLoaded == true
    private var memberCatalogLoaded = storedCatalog?.memberLoaded == true
    private var cachedStandardCatalog = if (standardCatalogLoaded) {
        mergeWithBuiltinAvatars(standardMetadata.map(::toStandardCatalogItem))
    } else {
        BUILTIN_AVATARS
    }
    private var cachedMemberCatalog = if (memberCatalogLoaded) {
        memberMetadata.mapNotNull(::loadCachedMemberAvatar)
    } else {
        null
    }
    private var standardRefreshJob: Job? = null
    private var memberRefreshJob: Job? = null
    private var lastStandardRefreshAtMs = 0L
    private var lastMemberRefreshAtMs = 0L

    suspend fun getAvatarCatalog(hasMemberAccess: Boolean = false): List<AvatarCatalogItem> {
        refreshStandardCatalogInBackground()
        val standardCatalog = getStandardAvatarCatalog()
        if (!hasMemberAccess) return standardCatalog
        val hadMemberCache = cachedMemberCatalog != null
        val memberCatalog = getMemberAvatarCatalog()
        if (hadMemberCache) refreshMemberCatalogInBackground()
        return standardCatalog + memberCatalog
    }

    private fun getStandardAvatarCatalog(): List<AvatarCatalogItem> {
        return cachedStandardCatalog
    }

    private suspend fun fetchStandardAvatarCatalog(): List<AvatarCatalogItem> {
        val remote = try {
            postgrest.rpc("get_avatar_catalog").decodeList<SupabaseAvatarCatalogItem>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(MemberAvatarTag, "Unable to load standard avatar catalog, using built-in catalog", error)
            emptyList()
        }
        val remoteCatalog = remote.map(::toStandardCatalogItem)
        if (remote.isNotEmpty()) {
            standardMetadata = remote
            standardCatalogLoaded = true
            lastStandardRefreshAtMs = SystemClock.elapsedRealtime()
            saveStoredCatalog()
        }
        val catalog = mergeWithBuiltinAvatars(remoteCatalog)
        cachedStandardCatalog = catalog
        return catalog
    }

    private suspend fun getMemberAvatarCatalog(): List<AvatarCatalogItem> {
        cachedMemberCatalog?.let { return it }
        return fetchMemberAvatarCatalog()
    }

    private suspend fun fetchMemberAvatarCatalog(): List<AvatarCatalogItem> {
        val remote = try {
            postgrest.rpc("get_member_profile_avatar_catalog")
                .decodeList<SupabaseMemberAvatarCatalogItem>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(MemberAvatarTag, "Unable to load supporter avatar catalog", error)
            return emptyList()
        }
        memberMetadata = remote
        memberCatalogLoaded = true
        lastMemberRefreshAtMs = SystemClock.elapsedRealtime()
        saveStoredCatalog()
        val catalog = coroutineScope {
            remote.map { item ->
                async {
                    try {
                        toMemberCatalogItem(item, cacheMemberAvatar(item))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(MemberAvatarTag, "Unable to load supporter avatar ${item.id}", error)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        cachedMemberCatalog = catalog
        return catalog
    }

    fun getAvatarImageUrl(avatarId: String, catalog: List<AvatarCatalogItem>): String? {
        if (avatarId.startsWith("http://") || avatarId.startsWith("https://")) return avatarId
        return catalog.find { it.id == avatarId || it.displayName.equals(avatarId, ignoreCase = true) }?.imageUrl
            ?: BUILTIN_AVATARS.find { it.id == avatarId || it.displayName.equals(avatarId, ignoreCase = true) }?.imageUrl
            ?: latestCachedMemberAvatar(avatarId)?.toURI()?.toString()
    }

    fun invalidateCache() {
        standardRefreshJob?.cancel()
        standardRefreshJob = null
        memberRefreshJob?.cancel()
        memberRefreshJob = null
        cachedStandardCatalog = BUILTIN_AVATARS
        cachedMemberCatalog = null
        standardMetadata = emptyList()
        memberMetadata = emptyList()
        standardCatalogLoaded = false
        memberCatalogLoaded = false
        lastStandardRefreshAtMs = 0L
        lastMemberRefreshAtMs = 0L
    }

    private fun refreshStandardCatalogInBackground() {
        if (standardRefreshJob?.isActive == true) return
        if (!isAvatarCatalogRefreshDue(lastStandardRefreshAtMs, SystemClock.elapsedRealtime())) return
        standardRefreshJob = scope.launch {
            try {
                fetchStandardAvatarCatalog()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(MemberAvatarTag, "Unable to refresh avatar catalog", error)
            }
        }
    }

    private fun refreshMemberCatalogInBackground() {
        if (memberRefreshJob?.isActive == true) return
        if (!isAvatarCatalogRefreshDue(lastMemberRefreshAtMs, SystemClock.elapsedRealtime())) return
        memberRefreshJob = scope.launch {
            try {
                fetchMemberAvatarCatalog()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(MemberAvatarTag, "Unable to refresh supporter avatars", error)
            }
        }
    }

    private fun loadStoredCatalog(): StoredAvatarCatalogPayload? {
        val payload = memberCatalogStorage.loadAvatarCatalogPayload().orEmpty().trim()
        if (payload.isEmpty()) return null
        return runCatching { json.decodeFromString<StoredAvatarCatalogPayload>(payload) }.getOrNull()
    }

    private fun saveStoredCatalog() {
        memberCatalogStorage.saveAvatarCatalogPayload(
            json.encodeToString(
                StoredAvatarCatalogPayload(
                    standardItems = standardMetadata,
                    memberItems = memberMetadata,
                    standardLoaded = standardCatalogLoaded,
                    memberLoaded = memberCatalogLoaded
                )
            )
        )
    }

    private fun toStandardCatalogItem(item: SupabaseAvatarCatalogItem): AvatarCatalogItem =
        AvatarCatalogItem(
            id = item.id,
            displayName = item.displayName,
            imageUrl = avatarImageUrl(item.storagePath),
            category = item.category,
            sortOrder = item.sortOrder,
            bgColor = item.bgColor
        )

    private fun loadCachedMemberAvatar(item: SupabaseMemberAvatarCatalogItem): AvatarCatalogItem? {
        val imageFile = memberAvatarFile(item).takeIf { it.isFile && it.length() > 0L } ?: return null
        return toMemberCatalogItem(item, imageFile)
    }

    private fun toMemberCatalogItem(item: SupabaseMemberAvatarCatalogItem, imageFile: File) =
        AvatarCatalogItem(
            id = item.id,
            displayName = item.displayName,
            imageUrl = imageFile.toURI().toString(),
            category = item.category,
            sortOrder = item.sortOrder,
            bgColor = item.bgColor,
            memberOnly = true
        )

    private fun avatarImageUrl(storagePath: String): String {
        if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) return storagePath
        val baseUrl = serverConfiguration.avatarPublicBaseUrl.orEmpty().trimEnd('/')
        return if (baseUrl.isNotEmpty()) "$baseUrl/$storagePath" else storagePath
    }

    private suspend fun cacheMemberAvatar(item: SupabaseMemberAvatarCatalogItem): File = withContext(Dispatchers.IO) {
        val imageFile = memberAvatarFile(item)
        if (imageFile.isFile && imageFile.length() > 0L) return@withContext imageFile

        val directory = imageFile.parentFile ?: return@withContext imageFile
        directory.mkdirs()
        val imageBytes = storage[MemberAvatarBucket].downloadAuthenticated(item.storagePath)
        val temporaryFile = directory.resolve(".${imageFile.name}.tmp")
        temporaryFile.writeBytes(imageBytes)
        if (!temporaryFile.renameTo(imageFile)) {
            temporaryFile.copyTo(imageFile, overwrite = true)
            temporaryFile.delete()
        }
        imageFile
    }

    private fun memberAvatarFile(item: SupabaseMemberAvatarCatalogItem): File {
        val extension = item.storagePath.substringAfterLast('.', "img")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "img"
        return context.cacheDir.resolve("member_profile_avatars/${item.id}-v${item.assetVersion}.$extension")
    }

    private fun latestCachedMemberAvatar(avatarId: String): File? {
        val prefix = "$avatarId-v"
        return context.cacheDir.resolve("member_profile_avatars").listFiles()
            ?.filter { file -> file.isFile && file.length() > 0L && file.name.startsWith(prefix) }
            ?.maxByOrNull { file ->
                file.name.removePrefix(prefix).substringBefore('.').toIntOrNull() ?: Int.MIN_VALUE
            }
    }
}
