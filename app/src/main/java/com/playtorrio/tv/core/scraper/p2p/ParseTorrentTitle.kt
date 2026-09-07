package com.playtorrio.tv.core.scraper.p2p

data class ParsedTorrentInfo(
    val title: String = "",
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeRange: Pair<Int, Int>? = null,
    val resolution: String? = null,
    val quality: String? = null,
    val codec: String? = null,
    val audio: String? = null,
    val channels: Double? = null,
    val container: String? = null,
    val service: String? = null,
    val group: String? = null,
    val isHdr: Boolean = false,
    val isDolbyVision: Boolean = false,
    val rawMap: Map<String, Any> = emptyMap()
)

class ParseTorrentTitle {

    private data class Handler(
        val name: String,
        val func: (String, MutableMap<String, Any>) -> Int?
    )

    private val handlers = mutableListOf<Handler>()

    init {
        runCatching { addDefaults() }
        runCatching { addTorrServerHandlers() }
    }

    private fun addDefaults() {
        // Year
        addHandler("year", Regex("""[^a-zA-Z0-9](?!^)[(\[]?((?:19[0-9]|20[012])[0-9])[)\]]?"""), type = "integer")

        // Resolution
        addHandler("resolution", Regex("""([0-9]{3,4}[pi])""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("resolution", Regex("""\b(4k)""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("resolution", Regex("""FHD|\b1080\b""", RegexOption.IGNORE_CASE), value = "1080p")
        addHandler("resolution", Regex("""UHD""", RegexOption.IGNORE_CASE), value = "4k")

        // Extended
        addHandler("extended", Regex("""EXTENDED(?:[\s.]CUT)?""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("theatrical", Regex("""Theatrical(?:[. ]Cut)?"""), type = "boolean")
        addHandler("uncut", Regex(""".+\bUNCUT\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("openmatte", Regex("""OPEN[. ]MATTE""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("downscaled", Regex("""\bDS4K\b""", RegexOption.IGNORE_CASE), value = "4k")
        addHandler("hybrid", Regex("""\bhybrid(\b|\d)""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("convert", Regex("""CONVERT"""), type = "boolean")
        addHandler("hardcoded", Regex("""HC|HARDCODED"""), type = "boolean")
        addHandler("remux", Regex("""REMUX""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("proper", Regex("""\b(?:REAL.)?PROPER\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("repack", Regex("""REPACK|RERIP""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("internal", Regex("""\b[iI]NTERNAL\b"""), type = "boolean")
        addHandler("retail", Regex("""\bRetail\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("remastered", Regex("""\bRemaster(?:ed)?\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("unrated", Regex("""\bunrated|uncensored\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("extras", Regex("""(?<=\b[12]\d{3}\b).*(\b|\.)\b(Extras?|Bonus|Extended[ ._-]Clip|Special Feature[s]?)\b""", RegexOption.IGNORE_CASE), type = "boolean")
        addHandler("criterion", Regex("""\bCriterion\b"""), type = "boolean")
        addHandler("region", Regex("""(?:\b|[Dd](?:vd|VD))(R[0-9])"""))

        // Container
        addHandler("container", Regex("""\b(MKV|AVI|MP4)\b""", RegexOption.IGNORE_CASE), type = "lowercase")

        // Source / Quality
        addHandler("source", Regex("""\b(?:HD-?)?CAM\b"""), type = "lowercase")
        addHandler("source", Regex("""\b(?:HD-?)?T(?:ELE)?S(?:YNC)?\b""", RegexOption.IGNORE_CASE), value = "telesync")
        addHandler("source", Regex("""\bHD-?Rip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bBRRip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bBDRip|BluRayRip\b""", RegexOption.IGNORE_CASE), value = "bdrip")
        addHandler("source", Regex("""\bDVDRip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bDVD(?:R[0-9])?\b""", RegexOption.IGNORE_CASE), value = "dvd")
        addHandler("source", Regex("""\bDVDscr\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\b(?:HD-?)?TVRip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bTC\b"""), type = "lowercase")
        addHandler("source", Regex("""\bPPVRip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bR5\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bVHSSCR\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""((?:\bBlu-?Ray)|((?:\b|\d)BR))\b""", RegexOption.IGNORE_CASE), value = "bluray")
        addHandler("source", Regex("""\bWEB(?:-?DL)?\b(?!-?RIP)""", RegexOption.IGNORE_CASE), value = "web-dl")
        addHandler("source", Regex("""\bWEB-?Rip\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\b(?:DL|WEB|BD|BR)MUX\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\b(DivX|XviD)\b"""), type = "lowercase")
        addHandler("source", Regex("""HDTV""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bIMAX[. -]Enhanced\b""", RegexOption.IGNORE_CASE), value = "imax-enhanced")
        addHandler("source", Regex("""\bIMAX\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bHDDVD\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bNTSC\b""", RegexOption.IGNORE_CASE), type = "lowercase")
        addHandler("source", Regex("""\bPAL\b""", RegexOption.IGNORE_CASE), type = "lowercase")

        // Service
        addHandler("service", Regex("""\bAMZN|Amazon\b""", RegexOption.IGNORE_CASE), value = "AMZN")
        addHandler("service", Regex("""\bAUBC\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bATVP\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bBNGE\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bDLWP\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bDSCP\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bDSNP\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bFDNG\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bHULU\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("service", Regex("""\bH?MAX\b"""), value = "HMAX")
        addHandler("service", Regex("""\b(NFLX|NF|Netflix)\b""", RegexOption.IGNORE_CASE), value = "NFLX")
        addHandler("service", Regex("""\biT(?:unes)\b"""), value = "iT")

        // Codec
        addHandler("codec", Regex("""h[-. ]?265|hevc""", RegexOption.IGNORE_CASE), value = "h265")
        addHandler("codec", Regex("""h[-. ]?264|avc""", RegexOption.IGNORE_CASE), value = "h264")
        addHandler("codec", Regex("""dvix|mpeg2|divx|xvid|x[-. ]?26[45]""", RegexOption.IGNORE_CASE), type = "lowercase")

        // Color
        addHandler("color", Regex("""\bHDR(?:10)?\b""", RegexOption.IGNORE_CASE), value = "HDR")
        addHandler("color", Regex("""\bSDR\b""", RegexOption.IGNORE_CASE), type = "uppercase")
        addHandler("color", Regex("""\b(?:DV|DoVi|Dolby\sVision)\b""", RegexOption.IGNORE_CASE), value = "DV")

        // Audio
        addHandler("audio", Regex("""\bATMOS\b|DA\d""", RegexOption.IGNORE_CASE), value = "atmos")
        addHandler("audio", Regex("""MD|MP3|mp3|FLAC|TrueHD"""), type = "lowercase")
        addHandler("audio", Regex("""\bDD-EX(\b|\d)""", RegexOption.IGNORE_CASE), value = "dd-ex")
        addHandler("audio", Regex("""\bDD(?:\+|P)|EAC-?3""", RegexOption.IGNORE_CASE), value = "ddp")
        addHandler("audio", Regex("""\b(DD(?!-EX)(?:\b|\d)|AC-?3)""", RegexOption.IGNORE_CASE), value = "dd")
        addHandler("audio", Regex("""AAC(?:[. ]?2[. ]0)?"""), value = "aac")
        addHandler("audio", Regex("""DTS-ES"""), type = "lowercase")
        addHandler("audio", Regex("""DTS-HD[\s-.]?(MA|Master Audio)"""), value = "dts-hd-ma")
        addHandler("audio", Regex("""DTS(?:[- ]?HD)"""), value = "dts-hd", skipIfAlreadyFound = true)
        addHandler("audio", Regex("""DTS"""), value = "dts", skipIfAlreadyFound = true)

        // Channels
        addHandler("channels", Regex("""\d+[.\s](?:1|0)\b""", RegexOption.IGNORE_CASE))
        addHandler("channels", Regex("""2(?:ch)"""), value = 2.0)
        addHandler("channels", Regex("""6(?:ch)"""), value = 5.1)
        addHandler("channels", Regex("""8(?:ch)"""), value = 7.1)

        // Bit depth
        addHandler("bitdepth", Regex("""\b(8|10|12|16|24)[-\s.]?bits?\b""", RegexOption.IGNORE_CASE), type = "integer")

        // Group & Encoder
        runCatching {
            addHandler("group", Regex("""-[\s\[\(]*(?:\w+[\s\]\)]+)?(\w+(?:\.\w+)?(?<!\.mkv|\.mp4))[)\]]?(?:\.(?:mkv|mp4))?$""", RegexOption.IGNORE_CASE))
        }
        runCatching {
            addHandler("encoder", Regex("""-[\s\[\(]*(?:(\w+)[\s\]\)]+)\w+(?:\.\w+)?(?<!\.mkv|\.mp4)[)\]]?(?:\.(?:mkv|mp4))?$""", RegexOption.IGNORE_CASE))
        }

        // Season
        addHandler("season", Regex("""([0-9]{1,2})[xX×✕✖]all""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""S([0-9]{1,2}) ?E[0-9]{1,2}""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""([0-9]{1,2})[xX×✕✖][0-9]{1,2}""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""(?:Saison|Season)[. _-]?([0-9]{1,2})""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""\bS([0-9]{1,2})(?![0-9])""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""\[([0-9]{1,2})[._ -]([0-9]{1,2})\]""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""\b0*([0-9]{1,2})\s*-\s*0*[0-9]{1,2}\b"""), type = "integer")

        // Episode
        addHandler("episode", Regex("""S[0-9]{1,2} ?E([0-9]{1,5})""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("episode", Regex("""[0-9]{1,2}[xX×✕✖]([0-9]{1,5})""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("episode", Regex("""[ée]p(?:isode)?[. _-]?([0-9]{1,5})""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("episode", Regex("""\bE([0-9]{1,5})\b""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("episode", Regex("""\[[0-9]{1,2}[._ -]([0-9]{1,2})\]""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("episode", Regex("""\b0*[0-9]{1,2}\s*-\s*0*([0-9]{1,2})\b"""), type = "integer")
        addHandler("episode", Regex("""^0*([0-9]{1,4})[ ._-]+[a-zA-Z]"""), type = "integer")
        addHandler("episode", Regex("""(?:^|[\\/])0*([0-9]{1,4})\.[a-zA-Z0-9]+$"""), type = "integer")

        // Language
        addHandler("language", Regex("""\bMULTi(?:Lang|-audio|-VF2)?\b""", RegexOption.IGNORE_CASE), value = "multi")
        addHandler("language", Regex("""Dual(?:[- ]Audio)?|[ .]DL[ .]""", RegexOption.IGNORE_CASE), value = "dual")
        addHandler("language", Regex("""\bENG(?:LISH)?\b""", RegexOption.IGNORE_CASE), value = "eng")
    }

    private fun addTorrServerHandlers() {
        addHandler("episode", Regex("""(\d{1,4})[- |. ]серия|серия[- |. ](\d{1,4})""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""sezon[- |. ](\d{1,3})|(\d{1,3})[- |. ]sezon""", RegexOption.IGNORE_CASE), type = "integer")
        addHandler("season", Regex("""сезон[- |. ](\d{1,3})|(\d{1,3})[- |. ]сезон""", RegexOption.IGNORE_CASE), type = "integer")
    }

    private fun addHandler(
        name: String,
        regex: Regex,
        value: Any? = null,
        type: String? = null,
        skipIfAlreadyFound: Boolean = false
    ) {
        handlers.add(Handler(name) { title, result ->
            if (skipIfAlreadyFound && result.containsKey(name)) {
                return@Handler null
            }

            val match = regex.find(title) ?: return@Handler null
            val rawMatch = match.value
            val cleanMatch = if (match.groups.size > 1) {
                match.groups[1]?.value ?: match.groups[match.groups.size - 1]?.value
            } else null

            val finalValue: Any = value ?: when (type) {
                "lowercase" -> (cleanMatch ?: rawMatch).lowercase()
                "uppercase" -> (cleanMatch ?: rawMatch).uppercase()
                "boolean" -> true
                "integer" -> (cleanMatch ?: rawMatch).toIntOrNull() ?: rawMatch
                "float" -> (cleanMatch ?: rawMatch).toDoubleOrNull() ?: rawMatch
                else -> cleanMatch ?: rawMatch
            }

            if (!result.containsKey(name)) {
                result[name] = finalValue
            }

            match.range.first
        })
    }

    fun parse(rawTitle: String): ParsedTorrentInfo {
        return try {
            val title = normalizeTorrentTitle(rawTitle)
            val result = mutableMapOf<String, Any>()
            var endOfTitle = title.length

            for (handler in handlers) {
                val matchIndex = handler.func(title, result)
                if (matchIndex != null && matchIndex < endOfTitle) {
                    endOfTitle = matchIndex
                }
            }

            // Multi-episode range check (e.g. S01E01-E04, S05E03-E04, 01x01-04, 01-04, E01-E04)
            val rangeRegexes = listOf(
                Regex("""S[0-9]{1,2}[ ._x-]*E([0-9]{1,4})[ ._x\-\+~]+(?:E|e)?([0-9]{1,4})""", RegexOption.IGNORE_CASE),
                Regex("""[0-9]{1,2}[xX]([0-9]{1,4})[ ._x\-\+~]+(?:[0-9]{1,2}[xX])?([0-9]{1,4})""", RegexOption.IGNORE_CASE),
                Regex("""\bE([0-9]{1,4})[ ._x\-\+~]+(?:E|e)?([0-9]{1,4})\b""", RegexOption.IGNORE_CASE),
                Regex("""\b0*([0-9]{1,4})\s*-\s*0*([0-9]{1,4})\b""")
            )

            var episodeRange: Pair<Int, Int>? = null
            for (r in rangeRegexes) {
                val m = r.find(title)
                if (m != null) {
                    val start = m.groups[1]?.value?.toIntOrNull()
                    val end = m.groups[2]?.value?.toIntOrNull()
                    if (start != null && end != null && end > start && (end - start) <= 25) {
                        episodeRange = Pair(start, end)
                        break
                    }
                }
            }

            val cleanedTitle = cleanTitle(title.substring(0, endOfTitle))
            val colorStr = result["color"]?.toString()

            ParsedTorrentInfo(
                title = cleanedTitle,
                year = (result["year"] as? Number)?.toInt(),
                season = (result["season"] as? Number)?.toInt(),
                episode = (result["episode"] as? Number)?.toInt(),
                episodeRange = episodeRange,
                resolution = result["resolution"]?.toString(),
                quality = result["source"]?.toString(),
                codec = result["codec"]?.toString()?.replace(Regex("[ .-]"), ""),
                audio = result["audio"]?.toString(),
                channels = (result["channels"] as? Number)?.toDouble(),
                container = result["container"]?.toString(),
                service = result["service"]?.toString(),
                group = result["group"]?.toString(),
                isHdr = colorStr.equals("HDR", ignoreCase = true),
                isDolbyVision = colorStr.equals("DV", ignoreCase = true),
                rawMap = result
            )
        } catch (_: Exception) {
            ParsedTorrentInfo(title = rawTitle)
        }
    }

    /**
     * Parses full directory file path (e.g. "Show/Season 05/01 - Episode.mkv")
     * merging parent folder season metadata if the filename alone only contains episode numbering.
     */
    fun parsePath(fullPath: String): ParsedTorrentInfo {
        val normalized = fullPath.replace('\\', '/')
        val segments = normalized.split('/').filter { it.trim().isNotEmpty() }
        if (segments.isEmpty()) return parse(fullPath)

        val filename = segments.last()
        var fileResult = parse(filename)

        // If season is already detected in filename, return it
        if (fileResult.season != null) {
            return fileResult
        }

        // Inspect parent directories for season (e.g. "Season 05", "S05", "S5")
        for (i in segments.size - 2 downTo 0) {
            val folder = segments[i]
            val folderResult = parse(folder)
            if (folderResult.season != null) {
                fileResult = fileResult.copy(season = folderResult.season)
                break
            }
        }

        return fileResult
    }

    private fun cleanTitle(rawTitle: String): String {
        var cleaned = rawTitle.replace(Regex("""^\.+|\.+$"""), "")
        if (!cleaned.contains(" ") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", " ")
        }
        cleaned = cleaned.replace("_", " ")
        cleaned = cleaned.replace(Regex("""([(_]|- )$"""), "").trim()
        return cleaned
    }

    companion object {
        private val defaultParser = ParseTorrentTitle()

        fun parse(title: String): ParsedTorrentInfo = defaultParser.parse(title)

        fun parsePath(fullPath: String): ParsedTorrentInfo = defaultParser.parsePath(fullPath)

        fun normalizeTorrentTitle(title: String): String {
            return title
                .replace("×", "x")
                .replace("✕", "x")
                .replace("✖", "x")
                .replace("Х", "x")
                .replace("х", "x")
        }
    }
}
