package com.playtorrio.tv.data.music

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "DeezerService"
private const val BASE = "https://deezer-proxy.aymanisthedude1.workers.dev/api"

// ── Models ───────────────────────────────────────────────────────────────────

data class DeezerTrack(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val preview: String? = null,
    val artist: DeezerArtist = DeezerArtist(),
    val album: DeezerAlbumRef? = null
)

data class DeezerArtist(
    val id: Long = 0,
    val name: String = "",
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null
)

data class DeezerAlbumRef(
    val id: Long = 0,
    val title: String = "",
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    val artist: DeezerArtist? = null
)

data class DeezerAlbumDetail(
    val id: Long = 0,
    val title: String = "",
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    val artist: DeezerArtist = DeezerArtist(),
    val tracks: DeezerTrackList? = null
)

data class DeezerTrackList(val data: List<DeezerTrack> = emptyList())
private data class SearchResult(val data: List<DeezerTrack>? = null)
private data class AlbumSearchResult(val data: List<DeezerAlbumRef>? = null)

// ── Service ──────────────────────────────────────────────────────────────────

object DeezerService {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val MAX_RETRIES = 4
    private const val RETRY_DELAY_MS = 1500L

    suspend fun getChart(): Pair<List<DeezerTrack>, List<DeezerAlbumRef>> =
        withContext(Dispatchers.IO) {
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val body = get("$BASE/chart")
                    val json = gson.fromJson(body, Map::class.java)
                    val tracksJson = gson.toJson((json["tracks"] as? Map<*, *>)?.get("data"))
                    val albumsJson = gson.toJson((json["albums"] as? Map<*, *>)?.get("data"))
                    val tracks = gson.fromJson(tracksJson, Array<DeezerTrack>::class.java)?.toList() ?: emptyList()
                    val albums = gson.fromJson(albumsJson, Array<DeezerAlbumRef>::class.java)?.toList() ?: emptyList()
                    if (tracks.isNotEmpty() || albums.isNotEmpty()) {
                        Log.i(TAG, "Chart ok on attempt ${attempt + 1}")
                        return@withContext tracks to albums
                    }
                    Log.i(TAG, "Chart empty on attempt ${attempt + 1}, retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Chart attempt ${attempt + 1} failed: ${e.message}")
                }
                if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
            emptyList<DeezerTrack>() to emptyList()
        }

    suspend fun search(query: String): Pair<List<DeezerTrack>, List<DeezerAlbumRef>> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            repeat(MAX_RETRIES) { attempt ->
                try {
                    val tracksDeferred = async { searchTracks(q) }
                    val albumsDeferred = async { searchAlbums(q) }
                    val tracks = tracksDeferred.await()
                    val albums = albumsDeferred.await()
                    if (tracks.isNotEmpty() || albums.isNotEmpty()) {
                        Log.i(TAG, "Search ok on attempt ${attempt + 1}")
                        return@withContext tracks to albums
                    }
                    Log.i(TAG, "Search empty on attempt ${attempt + 1}, retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Search attempt ${attempt + 1} failed: ${e.message}")
                }
                if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
            emptyList<DeezerTrack>() to emptyList()
        }

    private fun searchTracks(encodedQuery: String): List<DeezerTrack> {
        val body = get("$BASE/search?q=$encodedQuery")
        return gson.fromJson(body, SearchResult::class.java)?.data ?: emptyList()
    }

    private fun searchAlbums(encodedQuery: String): List<DeezerAlbumRef> {
        val body = get("$BASE/search/album?q=$encodedQuery")
        return gson.fromJson(body, AlbumSearchResult::class.java)?.data ?: emptyList()
    }

    suspend fun getAlbum(id: Long): DeezerAlbumDetail? = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val body = get("$BASE/album/$id")
                val album = gson.fromJson(body, DeezerAlbumDetail::class.java)
                if (album != null && album.id != 0L) {
                    Log.i(TAG, "Album $id ok on attempt ${attempt + 1}")
                    return@withContext album
                }
                Log.i(TAG, "Album $id empty on attempt ${attempt + 1}, retrying...")
            } catch (e: Exception) {
                Log.e(TAG, "Album $id attempt ${attempt + 1} failed: ${e.message}")
            }
            if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
        }
        null
    }

    /** Fetch a single track by Deezer track id. Used to rehydrate playlists/saved
     *  tracks after app restart when the in-memory cache is empty. */
    suspend fun getTrack(id: Long): DeezerTrack? = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val body = get("$BASE/track/$id")
                val track = gson.fromJson(body, DeezerTrack::class.java)
                if (track != null && track.id != 0L) return@withContext track
            } catch (e: Exception) {
                Log.e(TAG, "Track $id attempt ${attempt + 1} failed: ${e.message}")
            }
            if (attempt < MAX_RETRIES - 1) kotlinx.coroutines.delay(RETRY_DELAY_MS)
        }
        null
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "PlayTorrioTV/1.0")
            .build()
        return http.newCall(req).execute().use { it.body?.string() ?: "{}" }
    }
}
