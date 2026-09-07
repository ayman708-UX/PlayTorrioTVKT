package com.playtorrio.tv.core.network

import java.util.Locale

/**
 * Automatically resolves all required Referer, Origin, User-Agent, and Cookie
 * headers for known streaming CDNs (ported from PlayTorrioV3 PlayerSettings).
 */
object CdnHeaderResolver {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun resolveStreamHeaders(url: String, initialHeaders: Map<String, String>? = null): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["Connection"] = "keep-alive"
        h["Accept"] = "*/*"
        h["User-Agent"] = DEFAULT_USER_AGENT

        initialHeaders?.let { h.putAll(it) }

        val lower = url.lowercase(Locale.ROOT)
        when {
            lower.contains("hakunaymatata.com") -> {
                h["User-Agent"] = "Lavf/60.16.100"
            }
            lower.contains("movieboxnoob.cc") ||
            lower.contains("moviebox.ph") ||
            lower.contains("cinejoy.to") ||
            lower.contains("cinejoy") -> {
                h["Referer"] = "https://cinejoy.to/"
                h["Origin"] = "https://cinejoy.to"
            }
            lower.contains("peakstorm.top") ||
            lower.contains("majorplay.net") ||
            lower.contains("slast430did.com") ||
            lower.contains("vidzy.cc") ||
            lower.contains("vimeos.zip") ||
            lower.contains("wecollege.net") -> {
                h["Referer"] = "https://www.movy.bz/"
                h["Origin"] = "https://www.movy.bz"
            }
            lower.contains("chillflix.lol") -> {
                h["Referer"] = "https://www.chillflix.lol/"
                h["Origin"] = "https://www.chillflix.lol"
            }
            lower.contains("hclod.qzz.io") || lower.contains("watchplay.shop") -> {
                h["Referer"] = "https://v1.watchplay.shop/"
                h["Origin"] = "https://v1.watchplay.shop"
            }
            lower.contains("valhallastream") || lower.contains("1shows.app") || lower.contains("rivestream") -> {
                h["Referer"] = "https://www.rivestream.app/"
                h["Origin"] = "https://www.rivestream.app"
            }
            lower.contains("videasy") || lower.contains("speedracelight") -> {
                h["Referer"] = "https://player.videasy.to/"
                h["Origin"] = "https://player.videasy.to"
            }
            lower.contains("streamraiwind.stream") || lower.contains("vuflix.co") -> {
                h["Referer"] = "https://vuflix.co/"
                h["Origin"] = "https://vuflix.co"
            }
            lower.contains("net77.cc") || lower.contains("nm-cdn4.top") -> {
                h["Referer"] = "https://net77.cc/"
                h["Origin"] = "https://net77.cc"
            }
            lower.contains("gn1r5n.org") || lower.contains("owphbf24.com") -> {
                h["Referer"] = "https://gn1r5n.org/"
                h["Origin"] = "https://gn1r5n.org"
            }
            lower.contains("watching.onl") ||
            lower.contains("livedns.my") ||
            lower.contains("sugevideo.xyz") ||
            lower.contains("anivideo.sbs") ||
            lower.contains("trycloud.pro") ||
            lower.contains("cloudvideo.lat") ||
            lower.contains("megaplay.buzz") ||
            lower.contains("vidwish.live") ||
            (initialHeaders?.get("Referer")?.contains("megaplay.buzz") == true) ||
            (initialHeaders?.get("Referer")?.contains("vidwish") == true) -> {
                h["Referer"] = "https://megaplay.buzz/"
                h["Origin"] = "https://megaplay.buzz"
                h["Cookie"] = "SITE_TOTAL_ID=ce655f0eea754f2888ea98ded373e3b5"
            }
            lower.contains("anidb.app") ||
            lower.contains("hls.anidb.app") ||
            (initialHeaders?.get("Referer")?.contains("anidb.app") == true) -> {
                h["Referer"] = "https://anidb.app/"
                h["Origin"] = "https://anidb.app"
            }
            lower.contains("play.xpass.top") -> {
                h["Referer"] = "https://play.xpass.top/"
                h["Origin"] = "https://play.xpass.top"
            }
            lower.contains("flystream.net") -> {
                h["Referer"] = "https://flystream.net/"
            }
            lower.contains("movienig.ht") -> {
                h["Referer"] = "https://movienig.ht/"
                h["Origin"] = "https://movienig.ht"
            }
            lower.contains("vidsrc.me") -> {
                h["Referer"] = "https://vidsrc.me/"
            }
            lower.contains("cloudorchestranova.com") -> {
                h["Referer"] = "https://cloudorchestranova.com/"
            }
            lower.contains("downloadeverythingfromeverywhere.com") -> {
                h["Referer"] = "https://downloadeverythingfromeverywhere.com/"
                h["Origin"] = "https://downloadeverythingfromeverywhere.com"
            }
            lower.contains("echovideo.ru") ||
            (initialHeaders?.get("Referer")?.contains("echovideo.ru") == true) -> {
                h["Referer"] = "https://play2.echovideo.ru/"
                h["Origin"] = "https://play2.echovideo.ru"
            }
            lower.contains("cloudwindow-route.com") ||
            lower.contains("eugenemakedraw.com") ||
            lower.contains("delivery-node") ||
            (initialHeaders?.get("Referer")?.contains("anihq.cc") == true) -> {
                h["Referer"] = "https://anihq.cc/"
                h["Origin"] = "https://anihq.cc"
            }
            lower.contains("4animo.xyz") ||
            (initialHeaders?.get("Referer")?.contains("4animo.xyz") == true) -> {
                h["Referer"] = "https://cdn.4animo.xyz/"
                h["Origin"] = "https://cdn.4animo.xyz"
            }
            lower.contains("dulo.gd") ||
            lower.contains("sabrina-stream-proxy") ||
            (initialHeaders?.get("Referer")?.contains("dulo.gd") == true) -> {
                h["Referer"] = "https://d.dulo.gd/"
                h["Origin"] = "https://d.dulo.gd"
            }
            lower.contains("anineko.to") ||
            (initialHeaders?.get("Referer")?.contains("anineko.to") == true) -> {
                h["Referer"] = "https://anineko.to/"
                h["Origin"] = "https://anineko.to"
            }
            lower.contains("hentaini.com") ||
            (initialHeaders?.get("Referer")?.contains("hentaini.com") == true) -> {
                h["Referer"] = "https://hentaini.com/"
                h["Origin"] = "https://hentaini.com"
            }
            lower.contains("luna-stream.me") ||
            (initialHeaders?.get("Referer")?.contains("luna-stream.me") == true) -> {
                h["Referer"] = "https://luna-stream.me/"
                h["Origin"] = "https://luna-stream.me"
            }
            lower.contains("animanga.fun") -> {
                h["Referer"] = "https://megaplay.buzz/"
                h["Origin"] = "https://megaplay.buzz"
            }
            lower.contains("glendale-plumbing.com") -> {
                if (initialHeaders?.get("Referer")?.contains("cine.su") == true) {
                    h["Referer"] = "https://cine.su/"
                    h["Origin"] = "https://cine.su"
                } else {
                    h["Referer"] = "https://cinesrc.st/"
                    h["Origin"] = "https://cinesrc.st"
                }
            }
            lower.contains("cinesrc.st") -> {
                h["Referer"] = "https://cinesrc.st/"
                h["Origin"] = "https://cinesrc.st"
            }
            lower.contains("cine.su") -> {
                h["Referer"] = "https://cine.su/"
                h["Origin"] = "https://cine.su"
            }
            lower.contains("vidfast.vc") -> {
                h["Referer"] = "https://vidfast.vc/"
                h["Origin"] = "https://vidfast.vc"
            }
            lower.contains("vidup.to") -> {
                h["Referer"] = "https://vidup.to/"
                h["Origin"] = "https://vidup.to"
            }
            lower.contains("vidgod.space") -> {
                h["Referer"] = "https://vidgod.space/"
                h["Origin"] = "https://vidgod.space"
            }
            lower.contains("vidrock.ru") -> {
                h["Referer"] = "https://vidrock.ru/"
                h["Origin"] = "https://vidrock.ru"
            }
            lower.contains("vixsrc.to") -> {
                h["Referer"] = "https://vixsrc.to/"
                h["Origin"] = "https://vixsrc.to"
            }
            lower.contains("mapple.club") -> {
                h["Referer"] = "https://mapple.club/"
                h["Origin"] = "https://mapple.club"
            }
        }

        return h
    }
}
