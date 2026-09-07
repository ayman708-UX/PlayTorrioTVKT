package com.playtorrio.tv.core.plugin.cloudstream

import com.lagradost.cloudstream3.TvType

/** Map CloudStream TvType to PlayTorrioTV content type string ("movie" or "tv"). */
fun TvType.toPlayTorrioType(): String = when (this) {
    TvType.Movie, TvType.AnimeMovie, TvType.Documentary, TvType.Torrent -> "movie"
    else -> "tv"
}

/** Parse TvType from string name, case-insensitive. */
fun tvTypeFromString(value: String): TvType? = TvType.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true)
}
