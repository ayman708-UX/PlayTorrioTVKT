package com.playtorrio.tv.data.reader

// ── Manga ──────────────────────────────────────────────────────────
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
)

data class MangaChapter(
    val id: String,
    val number: Double,
    val name: String = "",
    val url: String = "",
    val rawName: String = ""
) {
    companion object {
        fun fromRaw(id: String, rawName: String, url: String): MangaChapter {
            var cleaned = rawName
            if (cleaned.lowercase().startsWith("chapter")) {
                cleaned = cleaned.substring(7).trim()
            }
            val sepRe = Regex("[:\\-–]")
            val sep = sepRe.find(cleaned)
            val numberStr: String
            val title: String
            if (sep != null && sep.range.first > 0) {
                numberStr = cleaned.substring(0, sep.range.first).trim()
                title = cleaned.substring(sep.range.first + 1).trim()
            } else {
                numberStr = cleaned.trim()
                title = ""
            }
            val number = numberStr.toDoubleOrNull() ?: 0.0
            return MangaChapter(id = id, number = number, name = title, url = url, rawName = rawName)
        }
    }
}

// ── Comics ─────────────────────────────────────────────────────────
data class Comic(
    val title: String,
    val url: String,
    val poster: String,
    val status: String = "",
    val publication: String = "",
    val summary: String = "",
    /** '' or 'rco' = readcomiconline.li, 'rcoru' = readcomicsonline.ru */
    val source: String = ""
)

data class ComicChapter(
    val title: String,
    val url: String,
    val dateAdded: String = ""
)

data class ComicDetails(
    val comic: Comic,
    val otherName: String = "None",
    val genres: List<String> = emptyList(),
    val publisher: String = "Unknown",
    val writer: String = "Unknown",
    val artist: String = "Unknown",
    val publicationDate: String = "Unknown",
    val chapters: List<ComicChapter> = emptyList()
)
