package com.playtorrio.tv.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.music.DeezerAlbumDetail
import com.playtorrio.tv.data.music.DeezerAlbumRef
import com.playtorrio.tv.data.music.DeezerService
import com.playtorrio.tv.data.music.DeezerTrack
import com.playtorrio.tv.data.music.MusicAudioExtractor
import com.playtorrio.tv.data.trailer.TrailerPlaybackSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MusicViewModel"

data class MusicPlaylist(
    val name: String,
    val trackIds: List<String> = emptyList()
)

/** Screens the user can be on */
enum class MusicView {
    BROWSE,            // chart + search results
    LIKED_TRACKS,      // saved tracks screen
    LIKED_ALBUMS,      // saved albums screen
    PLAYLISTS,         // list of playlists
    PLAYLIST_DETAIL,   // single playlist contents
    ALBUM_DETAIL       // album track listing
}

/** Dialogs that float over any screen */
enum class MusicDialog { NONE, PLAYER, ADD_TO_PLAYLIST, CREATE_PLAYLIST }

data class MusicUiState(
    // ── view navigation ──────────────────────────────────────────────
    val currentView: MusicView = MusicView.BROWSE,
    val dialog: MusicDialog = MusicDialog.NONE,

    // ── browse / search ──────────────────────────────────────────────
    val isLoading: Boolean = true,
    val chartTracks: List<DeezerTrack> = emptyList(),
    val chartAlbums: List<DeezerAlbumRef> = emptyList(),
    val searchQuery: String = "",
    val searchTracks: List<DeezerTrack> = emptyList(),
    val searchAlbums: List<DeezerAlbumRef> = emptyList(),
    val isSearching: Boolean = false,

    // ── album detail ─────────────────────────────────────────────────
    val currentAlbum: DeezerAlbumDetail? = null,
    val isAlbumLoading: Boolean = false,

    // ── saved data ───────────────────────────────────────────────────
    val savedAlbumIds: Set<Long> = emptySet(),
    val savedAlbums: List<DeezerAlbumDetail> = emptyList(),
    val isLoadingSavedAlbums: Boolean = false,
    val savedTrackIds: Set<Long> = emptySet(),
    val savedTracks: List<DeezerTrack> = emptyList(),

    // ── playlists ────────────────────────────────────────────────────
    val playlists: List<MusicPlaylist> = emptyList(),
    val viewingPlaylistIndex: Int = -1,
    val viewingPlaylistTracks: List<DeezerTrack> = emptyList(),
    val pendingPlaylistTrack: DeezerTrack? = null,

    // ── player ───────────────────────────────────────────────────────
    val currentTrack: DeezerTrack? = null,
    val currentSource: TrailerPlaybackSource? = null,
    val isExtracting: Boolean = false,
    val queue: List<DeezerTrack> = emptyList(),
    val queueIndex: Int = 0,

    // ── library dropdown ─────────────────────────────────────────────
    val libraryExpanded: Boolean = false
)

class MusicViewModel : ViewModel() {
    private val _ui = MutableStateFlow(MusicUiState())
    val ui = _ui.asStateFlow()
    private val gson = Gson()
    private var searchJob: Job? = null
    private var extractJob: Job? = null
    val trackCache = mutableMapOf<Long, DeezerTrack>()
    private val albumCache = mutableMapOf<Long, DeezerAlbumDetail>()

    init { loadSavedData(); loadChart() }

    // ── Navigation ───────────────────────────────────────────────────────────

    fun navigateTo(view: MusicView) {
        _ui.value = _ui.value.copy(currentView = view, libraryExpanded = false)
        if (view == MusicView.LIKED_ALBUMS) loadSavedAlbums()
    }

    fun goBack(): Boolean {
        val s = _ui.value
        // Dialog takes priority
        if (s.dialog == MusicDialog.PLAYER) { closePlayer(); return true }
        if (s.dialog != MusicDialog.NONE) { closeDialog(); return true }
        // Sub-views go back to browse
        if (s.currentView != MusicView.BROWSE) {
            _ui.value = s.copy(currentView = MusicView.BROWSE, currentAlbum = null)
            return true
        }
        return false
    }

    fun toggleLibrary() { _ui.value = _ui.value.copy(libraryExpanded = !_ui.value.libraryExpanded) }
    fun collapseLibrary() { _ui.value = _ui.value.copy(libraryExpanded = false) }

    // ── Chart ────────────────────────────────────────────────────────────────

    private fun loadChart() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true)
            val (tracks, albums) = DeezerService.getChart()
            tracks.forEach { trackCache[it.id] = it }
            _ui.value = _ui.value.copy(isLoading = false, chartTracks = tracks, chartAlbums = albums)
            refreshSavedTracks()
            Log.i(TAG, "Chart loaded: ${tracks.size} tracks, ${albums.size} albums")
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) { _ui.value = _ui.value.copy(searchQuery = query) }

    fun search(query: String) {
        if (query.isBlank()) {
            _ui.value = _ui.value.copy(searchTracks = emptyList(), searchAlbums = emptyList(), isSearching = false)
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isSearching = true, currentView = MusicView.BROWSE)
            val (tracks, albums) = DeezerService.search(query)
            tracks.forEach { trackCache[it.id] = it }
            _ui.value = _ui.value.copy(searchTracks = tracks, searchAlbums = albums, isSearching = false)
        }
    }

    // ── Album detail ─────────────────────────────────────────────────────────

    fun openAlbum(id: Long) {
        _ui.value = _ui.value.copy(currentView = MusicView.ALBUM_DETAIL, isAlbumLoading = true, currentAlbum = null)
        viewModelScope.launch {
            val album = albumCache[id] ?: DeezerService.getAlbum(id)
            album?.let { albumCache[it.id] = it; it.tracks?.data?.forEach { t -> trackCache[t.id] = t } }
            _ui.value = _ui.value.copy(isAlbumLoading = false, currentAlbum = album)
        }
    }

    // ── Liked tracks ─────────────────────────────────────────────────────────

    fun toggleSaveTrack(track: DeezerTrack) {
        trackCache[track.id] = track
        val ids = _ui.value.savedTrackIds.toMutableSet()
        if (ids.contains(track.id)) ids.remove(track.id) else ids.add(track.id)
        AppPreferences.savedTrackIds = ids.map { it.toString() }.toSet()
        _ui.value = _ui.value.copy(savedTrackIds = ids, savedTracks = ids.mapNotNull { trackCache[it] })
    }

    fun isTrackSaved(id: Long) = _ui.value.savedTrackIds.contains(id)

    // ── Liked albums ─────────────────────────────────────────────────────────

    fun toggleSaveAlbum(id: Long) {
        val c = _ui.value.savedAlbumIds.toMutableSet()
        if (c.contains(id)) c.remove(id) else c.add(id)
        _ui.value = _ui.value.copy(savedAlbumIds = c)
        AppPreferences.savedAlbumIds = c.map { it.toString() }.toSet()
        // Refresh saved albums list if we're on that screen
        if (_ui.value.currentView == MusicView.LIKED_ALBUMS) loadSavedAlbums()
    }

    fun isAlbumSaved(id: Long) = _ui.value.savedAlbumIds.contains(id)

    private fun loadSavedAlbums() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoadingSavedAlbums = true)
            val ids = _ui.value.savedAlbumIds.toList()
            val albums = ids.mapNotNull { id ->
                albumCache[id] ?: DeezerService.getAlbum(id)?.also { albumCache[it.id] = it }
            }
            _ui.value = _ui.value.copy(savedAlbums = albums, isLoadingSavedAlbums = false)
        }
    }

    // ── Playlist CRUD ────────────────────────────────────────────────────────

    fun showAddToPlaylist(track: DeezerTrack) {
        trackCache[track.id] = track
        _ui.value = _ui.value.copy(dialog = MusicDialog.ADD_TO_PLAYLIST, pendingPlaylistTrack = track)
    }

    fun showCreatePlaylist() { _ui.value = _ui.value.copy(dialog = MusicDialog.CREATE_PLAYLIST) }

    fun closeDialog() {
        val returnDialog = if (_ui.value.currentTrack != null && _ui.value.dialog != MusicDialog.PLAYER) MusicDialog.PLAYER else MusicDialog.NONE
        _ui.value = _ui.value.copy(dialog = returnDialog, pendingPlaylistTrack = null)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val lists = _ui.value.playlists.toMutableList()
        val pending = _ui.value.pendingPlaylistTrack
        val tids = if (pending != null) listOf(pending.id.toString()) else emptyList()
        lists.add(MusicPlaylist(name = name, trackIds = tids))
        savePlaylists(lists)
        val returnDialog = if (_ui.value.currentTrack != null) MusicDialog.PLAYER else MusicDialog.NONE
        _ui.value = _ui.value.copy(playlists = lists, dialog = returnDialog, pendingPlaylistTrack = null)
    }

    fun addTrackToPlaylist(playlistIndex: Int) {
        val track = _ui.value.pendingPlaylistTrack ?: return
        val lists = _ui.value.playlists.toMutableList()
        if (playlistIndex !in lists.indices) return
        val pl = lists[playlistIndex]
        val tid = track.id.toString()
        if (pl.trackIds.contains(tid)) return
        lists[playlistIndex] = pl.copy(trackIds = pl.trackIds + tid)
        savePlaylists(lists)
        val returnDialog = if (_ui.value.currentTrack != null) MusicDialog.PLAYER else MusicDialog.NONE
        _ui.value = _ui.value.copy(playlists = lists, dialog = returnDialog, pendingPlaylistTrack = null)
        // Refresh playlist detail tracks if viewing this playlist
        val viewIdx = _ui.value.viewingPlaylistIndex
        if (viewIdx == playlistIndex) {
            viewModelScope.launch {
                val tracks = resolveTrackIds(lists[playlistIndex].trackIds)
                if (_ui.value.viewingPlaylistIndex == playlistIndex) {
                    _ui.value = _ui.value.copy(viewingPlaylistTracks = tracks)
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistIndex: Int, trackId: Long) {
        val lists = _ui.value.playlists.toMutableList()
        if (playlistIndex !in lists.indices) return
        val pl = lists[playlistIndex]
        lists[playlistIndex] = pl.copy(trackIds = pl.trackIds.filter { it != trackId.toString() })
        savePlaylists(lists)
        _ui.value = _ui.value.copy(
            playlists = lists,
            viewingPlaylistTracks = _ui.value.viewingPlaylistTracks.filter { it.id != trackId }
        )
    }

    fun deletePlaylist(index: Int) {
        val lists = _ui.value.playlists.toMutableList()
        if (index !in lists.indices) return
        lists.removeAt(index)
        savePlaylists(lists)
        _ui.value = _ui.value.copy(playlists = lists, currentView = MusicView.PLAYLISTS, viewingPlaylistIndex = -1)
    }

    fun openPlaylistDetail(index: Int) {
        val pl = _ui.value.playlists.getOrNull(index) ?: return
        // Show what we have from cache immediately, then fetch the rest in background.
        val cached = pl.trackIds.mapNotNull { trackCache[it.toLongOrNull() ?: 0] }
        _ui.value = _ui.value.copy(
            currentView = MusicView.PLAYLIST_DETAIL,
            viewingPlaylistIndex = index,
            viewingPlaylistTracks = cached
        )
        viewModelScope.launch {
            val tracks = resolveTrackIds(pl.trackIds)
            if (_ui.value.viewingPlaylistIndex == index) {
                _ui.value = _ui.value.copy(viewingPlaylistTracks = tracks)
            }
        }
    }

    // ── Playlist playback ────────────────────────────────────────────────────

    fun playAllPlaylist(index: Int) {
        val pl = _ui.value.playlists.getOrNull(index) ?: return
        viewModelScope.launch {
            val tracks = resolveTrackIds(pl.trackIds)
            if (tracks.isEmpty()) return@launch
            playTrack(tracks[0], tracks, 0)
        }
    }

    fun shufflePlaylist(index: Int) {
        val pl = _ui.value.playlists.getOrNull(index) ?: return
        viewModelScope.launch {
            val tracks = resolveTrackIds(pl.trackIds).shuffled()
            if (tracks.isEmpty()) return@launch
            playTrack(tracks[0], tracks, 0)
        }
    }

    /** Resolve a list of stringified track ids into [DeezerTrack]s. Uses the
     *  in-memory cache first and falls back to the Deezer API for any tracks
     *  that weren't loaded this session (e.g. after app restart). Missing
     *  tracks are dropped. */
    private suspend fun resolveTrackIds(ids: List<String>): List<DeezerTrack> {
        if (ids.isEmpty()) return emptyList()
        val longIds = ids.mapNotNull { it.toLongOrNull() }
        val missing = longIds.filter { trackCache[it] == null }
        if (missing.isNotEmpty()) {
            val fetched = coroutineScope {
                missing.map { id -> async { DeezerService.getTrack(id) } }.awaitAll()
            }
            fetched.filterNotNull().forEach { trackCache[it.id] = it }
        }
        return longIds.mapNotNull { trackCache[it] }
    }

    // ── Player ───────────────────────────────────────────────────────────────

    fun playTrack(track: DeezerTrack, queue: List<DeezerTrack>, index: Int) {
        trackCache[track.id] = track
        _ui.value = _ui.value.copy(
            currentTrack = track, queue = queue, queueIndex = index,
            dialog = MusicDialog.PLAYER,
            isExtracting = true, currentSource = null
        )
        startExtraction(track)
    }

    fun nextTrack() {
        val s = _ui.value; if (s.queue.isEmpty()) return
        val i = (s.queueIndex + 1).coerceAtMost(s.queue.size - 1); if (i == s.queueIndex) return
        val t = s.queue[i]; _ui.value = s.copy(currentTrack = t, queueIndex = i, isExtracting = true, currentSource = null)
        startExtraction(t)
    }

    fun prevTrack() {
        val s = _ui.value; if (s.queue.isEmpty()) return
        val i = (s.queueIndex - 1).coerceAtLeast(0); if (i == s.queueIndex) return
        val t = s.queue[i]; _ui.value = s.copy(currentTrack = t, queueIndex = i, isExtracting = true, currentSource = null)
        startExtraction(t)
    }

    fun closePlayer() {
        extractJob?.cancel()
        _ui.value = _ui.value.copy(
            dialog = MusicDialog.NONE,
            currentTrack = null, currentSource = null, isExtracting = false
        )
    }

    private fun startExtraction(track: DeezerTrack) {
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            val source = MusicAudioExtractor.extract(track.title, track.artist.name)
            if (_ui.value.currentTrack?.id == track.id) {
                _ui.value = _ui.value.copy(currentSource = source, isExtracting = false)
                Log.i(TAG, if (source != null) "Audio ready: ${track.title}" else "Extract failed: ${track.title}")
            }
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun savePlaylists(lists: List<MusicPlaylist>) { AppPreferences.musicPlaylists = gson.toJson(lists) }

    private fun refreshSavedTracks() {
        val ids = _ui.value.savedTrackIds
        // Show whatever we have from cache instantly, then fetch the rest.
        _ui.value = _ui.value.copy(savedTracks = ids.mapNotNull { trackCache[it] })
        viewModelScope.launch {
            val tracks = resolveTrackIds(ids.map { it.toString() })
            _ui.value = _ui.value.copy(savedTracks = tracks)
        }
    }

    private fun loadSavedData() {
        val albumIds = AppPreferences.savedAlbumIds.mapNotNull { it.toLongOrNull() }.toSet()
        val trackIds = AppPreferences.savedTrackIds.mapNotNull { it.toLongOrNull() }.toSet()
        val playlists = try {
            val type = object : TypeToken<List<MusicPlaylist>>() {}.type
            gson.fromJson<List<MusicPlaylist>>(AppPreferences.musicPlaylists, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        _ui.value = _ui.value.copy(savedAlbumIds = albumIds, savedTrackIds = trackIds, playlists = playlists)
    }
}
