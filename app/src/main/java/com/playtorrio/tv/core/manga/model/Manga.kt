package com.playtorrio.tv.core.manga.model

data class Manga(
    val id: String,
    val title: String,
    val coverSmall: String,
    val coverNormal: String,
    val type: String = "",
    val status: String = "",
    val year: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val synopsis: String = "",
    val url: String = ""
) {
    val displayType: String
        get() = type.ifBlank { "Manga" }

    val formattedTags: String
        get() = tags.joinToString(" • ")
}
