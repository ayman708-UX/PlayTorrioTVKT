package com.playtorrio.tv.data.stremio

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// ─── Addon Manifest ──────────────────────────────────────────────────────────

data class AddonManifest(
    val id: String = "",
    val version: String = "",
    val name: String = "",
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<CatalogDeclaration> = emptyList(),
    // resources can be strings or objects — parsed by ResourceDescriptorDeserializer
    val resources: List<ResourceDescriptor> = emptyList(),
    val idPrefixes: List<String>? = null,
    @SerializedName("behaviorHints")
    val behaviorHints: AddonBehaviorHints? = null,
    val config: List<ConfigField>? = null
)

data class CatalogDeclaration(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val extra: List<ExtraProperty> = emptyList(),
    val extraSupported: List<String>? = null,
    val extraRequired: List<String>? = null
) {
    /** True when this catalog can be shown on the board (no required extras) */
    val isBoard: Boolean
        get() = extra.none { it.isRequired }

    /** True when this catalog supports free-text search via the `search` extra */
    val isSearchable: Boolean
        get() = extra.any { it.name == "search" }
}

data class ExtraProperty(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String>? = null,
    val optionsLimit: Int? = null
)

data class AddonBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    @SerializedName("p2p")
    val p2p: Boolean = false,
    val adult: Boolean = false,
    val hasAds: Boolean = false
)

data class ConfigField(
    val key: String = "",
    val type: String = "text", // text, password, checkbox, select, number
    val title: String = "",
    val options: List<String>? = null,
    @SerializedName("default")
    val defaultValue: String? = null,
    val required: Boolean = false
)

/**
 * Normalised descriptor for a resource entry in the manifest.
 * Handles both string form ("catalog") and object form
 * {"name":"catalog","types":["movie"],"idPrefixes":["tt"]}.
 */
data class ResourceDescriptor(
    val name: String,
    /** null = fall back to manifest-level types */
    val types: List<String>? = null,
    /** null = fall back to manifest-level idPrefixes */
    val idPrefixes: List<String>? = null
)

/** Gson deserializer that handles string OR object resource entries. */
class ResourceDescriptorDeserializer : JsonDeserializer<ResourceDescriptor> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ResourceDescriptor {
        return if (json.isJsonPrimitive) {
            ResourceDescriptor(name = json.asString)
        } else {
            val obj = json.asJsonObject
            ResourceDescriptor(
                name = obj.get("name")?.asString ?: "",
                types = obj.getAsJsonArray("types")?.map { it.asString },
                idPrefixes = obj.getAsJsonArray("idPrefixes")?.map { it.asString }
            )
        }
    }
}

// ─── Installed Addon (persisted) ─────────────────────────────────────────────

data class InstalledAddon(
    /** Manifest URL stripped of /manifest.json — used as base for all requests */
    val transportUrl: String,
    val manifest: AddonManifest
)

// ─── Catalog / Meta Response ─────────────────────────────────────────────────

data class CatalogResponse(val metas: List<StremioMetaPreview> = emptyList())

data class MetaResponse(val meta: StremioMeta? = null)

data class StremioMetaPreview(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    /** poster (default), landscape, square */
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val links: List<MetaLink>? = null,
    // runtime/year can appear in abbreviated meta too
    val runtime: String? = null,
    val year: Int? = null
)

data class StremioMeta(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val genres: List<String>? = null,
    val imdbRating: String? = null,
    val director: List<String>? = null,
    val cast: List<String>? = null,
    val writer: List<String>? = null,
    val website: String? = null,
    val trailers: List<StremioTrailer>? = null,
    val videos: List<StremioVideo>? = null,
    val links: List<MetaLink>? = null,
    val behaviorHints: MetaBehaviorHints? = null
)

data class MetaBehaviorHints(
    val defaultVideoId: String? = null,
    val hasScheduledVideos: Boolean? = null
)

data class MetaLink(
    val name: String = "",
    val category: String = "",
    val url: String = ""
)

data class StremioTrailer(
    val source: String = "",
    val type: String = "Trailer"
)

// ─── Video (episode entry inside Meta) ───────────────────────────────────────

data class StremioVideo(
    val id: String = "",
    val title: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Non-empty ⇒ skip /stream/ endpoint; these are the authoritative streams */
    val streams: List<StremioStream>? = null
)

// ─── Stream ───────────────────────────────────────────────────────────────────

data class StreamResponse(val streams: List<StremioStream> = emptyList())

data class StremioStream(
    /** Direct playable URL (http/https, magnet:, stremio-yt://) */
    val url: String? = null,
    /** BitTorrent info-hash; client must build the magnet URI */
    val infoHash: String? = null,
    /** Index of the file inside the torrent (used with infoHash) */
    val fileIdx: Int? = null,
    /** URL to open in the system browser instead of playing */
    val externalUrl: String? = null,
    /** YouTube video ID */
    val ytId: String? = null,
    /** Embed URL for iframe player */
    val playerFrameUrl: String? = null,
    /** Source label shown in the picker (addon name / quality) */
    val name: String? = null,
    /** Short text beneath the name */
    val title: String? = null,
    /** Longer description (codec, size, seeds …) */
    val description: String? = null,
    val behaviorHints: StreamBehaviorHints? = null,
    /** Subtitle tracks embedded with this stream */
    val subtitles: List<StremioSubtitle>? = null,
    /** Tracker announce URLs (for infoHash streams) */
    val sources: List<String>? = null,
    // ── client-injected ──
    /** Which addon provided this stream (injected by StremioService on aggregation) */
    val addonName: String? = null,
    val addonId: String? = null
)

data class StreamBehaviorHints(
    /** true = player must proxy the stream (not web-ready) */
    val notWebReady: Boolean? = null,
    /** Group ID for binge-watching queue */
    val bingeGroup: String? = null,
    val proxyHeaders: ProxyHeaders? = null,
    /** OpenSubtitles video hash */
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
    val countryWhitelist: List<String>? = null
)

data class ProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null
)

// ─── Subtitles ────────────────────────────────────────────────────────────────

data class SubtitlesResponse(val subtitles: List<StremioSubtitle> = emptyList())

data class StremioSubtitle(
    val id: String? = null,
    val lang: String = "",
    @SerializedName("lang_code") val langCode: String? = null,
    val url: String = "",
    val name: String? = null,
    val title: String? = null
)

// ─── Addon Catalog (discover addons) ─────────────────────────────────────────

data class AddonCatalogResponse(val addons: List<AddonManifest> = emptyList())

// ─── Stream Routing ───────────────────────────────────────────────────────────

/** Sealed class representing the resolved playback action for a Stremio stream. */
sealed class StreamRoute {
    /** Play via TorrServer (magnet or infoHash→magnet built by client) */
    data class Torrent(val magnet: String, val fileIdx: Int? = null) : StreamRoute()

    /** Play directly in ExoPlayer */
    data class DirectUrl(
        val url: String,
        val headers: Map<String, String>? = null
    ) : StreamRoute()

    /** Open in device browser */
    data class External(val url: String) : StreamRoute()

    /** YouTube player */
    data class YouTube(val ytId: String) : StreamRoute()

    /** iframe embed – not supported on TV; treat as External */
    data class IFrame(val url: String) : StreamRoute()

    /** stremio:// deep link — handle in-app (detail, search, etc.) */
    data class StremioDeepLink(
        val action: String,   // "detail", "search", "discover", etc.
        val type: String? = null,
        val id: String? = null,
        val videoId: String? = null,
        val query: String? = null
    ) : StreamRoute()

    object Unsupported : StreamRoute()
}
