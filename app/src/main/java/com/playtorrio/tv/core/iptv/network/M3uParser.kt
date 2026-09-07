package com.playtorrio.tv.core.iptv.network

import com.playtorrio.tv.core.iptv.model.M3uChannel

object M3uParser {
    fun parse(content: String): List<M3uChannel> {
        if (content.isBlank()) return emptyList()

        val text = content.replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.split("\n")

        val out = mutableListOf<M3uChannel>()
        var pendingName: String? = null
        var pendingLogo = ""
        var pendingGroup = ""
        var pendingTvgId = ""
        var pendingTvgName = ""

        fun resetPending() {
            pendingName = null
            pendingLogo = ""
            pendingGroup = ""
            pendingTvgId = ""
            pendingTvgName = ""
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTM3U")) continue

            if (line.startsWith("#EXTINF")) {
                val commaIdx = line.indexOf(',')
                val attrPart = if (commaIdx > 0) line.substring("#EXTINF".length, commaIdx) else line.substring("#EXTINF".length)
                val namePart = if (commaIdx > 0) line.substring(commaIdx + 1).trim() else ""

                val attrs = parseAttrs(attrPart)
                pendingTvgId = attrs["tvg-id"].orEmpty()
                pendingTvgName = attrs["tvg-name"].orEmpty()
                pendingLogo = attrs["tvg-logo"].orEmpty()
                pendingGroup = attrs["group-title"].orEmpty()
                pendingName = if (namePart.isNotEmpty()) namePart else if (pendingTvgName.isNotEmpty()) pendingTvgName else "Unknown"
                continue
            }

            if (line.startsWith("#EXTGRP:")) {
                pendingGroup = line.substring("#EXTGRP:".length).trim()
                continue
            }

            if (line.startsWith("#")) continue

            val url = line
            if (!looksLikeUrl(url)) continue

            out.add(
                M3uChannel(
                    name = pendingName ?: url,
                    url = url,
                    logo = pendingLogo,
                    group = pendingGroup,
                    tvgId = pendingTvgId,
                    tvgName = pendingTvgName
                )
            )
            resetPending()
        }

        return out
    }

    private fun looksLikeUrl(s: String): Boolean {
        val lower = s.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtmps://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("udp://") ||
            lower.startsWith("rtp://")
    }

    private fun parseAttrs(input: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var s = input.trim()
        if (s.startsWith(":")) s = s.substring(1).trim()

        val durRegex = Regex("""^-?\d+(\.\d+)?""")
        val durMatch = durRegex.find(s)
        if (durMatch != null) {
            s = s.substring(durMatch.range.last + 1).trim()
        }

        val re = Regex("""([a-zA-Z0-9_\-]+)=("([^"]*)"|'([^']*)'|([^\s,]+))""")
        for (m in re.findAll(s)) {
            val key = m.groupValues[1].lowercase()
            val v = m.groupValues[3].ifEmpty { m.groupValues[4].ifEmpty { m.groupValues[5] } }
            result[key] = v
        }
        return result
    }
}
