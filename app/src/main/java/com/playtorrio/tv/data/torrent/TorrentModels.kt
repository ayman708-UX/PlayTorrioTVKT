package com.playtorrio.tv.data.torrent

data class TorrentResult(
    val name: String,
    val magnetLink: String,
    val size: String,
    val seeders: Int,
    val leechers: Int,
    val source: String,     // e.g. "uindex", "knaben"
    val isSeasonPack: Boolean = false
)

data class TorrentSearchRequest(
    val title: String,          // Show/movie name
    val year: String? = null,   // Release year (movies)
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isMovie: Boolean
)
