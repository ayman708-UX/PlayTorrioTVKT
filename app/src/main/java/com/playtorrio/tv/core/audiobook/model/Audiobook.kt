package com.playtorrio.tv.core.audiobook.model

data class Audiobook(
    val uuid: String,
    val audioBookId: String,
    val dynamicSlugId: String,
    val title: String,
    val author: String = "",
    val coverImage: String = "",
    val source: String,
    val pageUrl: String
)

data class AudiobookChapter(
    val title: String,
    val url: String,
    val httpHeaders: Map<String, String>? = null,
    val isTorrent: Boolean = false,
    val torrentFileIndex: Int? = null,
    val infoHash: String? = null,
    val trackers: List<String> = emptyList()
)

data class AudiobookProgress(
    val audiobook: Audiobook,
    val chapterIndex: Int,
    val chapterTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AudiobookRow(
    val title: String,
    val books: List<Audiobook>
)
