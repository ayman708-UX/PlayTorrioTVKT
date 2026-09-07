package com.playtorrio.tv.core.manga.model

data class MangaChapter(
    val id: String,
    val number: Double,
    val name: String = "",
    val url: String = "",
    val rawName: String = "",
    val date: String = ""
) {
    companion object {
        private val LAST_READ_REGEX = Regex("(?i)Last Read")
        private val CHAPTER_NUMBER_REGEX = Regex("(?i)(?:chapter|ch\\.?|\\b)\\s*(\\d+(?:\\.\\d+)?)")
        private val NUMBER_ONLY_REGEX = Regex("(\\d+(?:\\.\\d+)?)")
        private val SEPARATOR_REGEX = Regex("[:\\-–]\\s*(.+)")

        fun fromRaw(id: String, rawName: String, url: String, date: String = ""): MangaChapter {
            val cleanRaw = rawName.replace(LAST_READ_REGEX, "").trim()
            val numberMatch = CHAPTER_NUMBER_REGEX.find(cleanRaw) ?: NUMBER_ONLY_REGEX.find(cleanRaw)
            val number = numberMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0

            val separatorMatch = SEPARATOR_REGEX.find(cleanRaw)
            var title = separatorMatch?.groupValues?.getOrNull(1)?.trim() ?: cleanRaw
            if (title.isBlank() || title.equals("last read", ignoreCase = true)) {
                title = if (number > 0.0) {
                    if (number % 1.0 == 0.0) "Chapter ${number.toInt()}" else "Chapter $number"
                } else {
                    cleanRaw
                }
            }

            return MangaChapter(
                id = id,
                number = number,
                name = title,
                url = url,
                rawName = cleanRaw,
                date = date
            )
        }
    }
}
