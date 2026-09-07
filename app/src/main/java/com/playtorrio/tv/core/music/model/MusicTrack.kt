package com.playtorrio.tv.core.music.model

data class MusicTrack(
    val id: String, // YouTube videoId
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null, // e.g. "3:22"
    val durationSeconds: Long? = null,
    val thumbnailUrl: String,
    val backdropUrl: String? = null
)

data class MusicRow(
    val title: String,
    val tracks: List<MusicTrack>
)
