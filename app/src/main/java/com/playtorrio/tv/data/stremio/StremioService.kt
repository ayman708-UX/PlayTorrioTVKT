package com.playtorrio.tv.data.stremio

import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Core Stremio protocol HTTP client.
 *
 * Implements the resource-filtering algorithm from the spec:
 *   - catalog: match declared catalogs only
 *   - meta / stream / subtitles: check resource name + types + idPrefixes
 *
 * All network calls are guarded by timeouts and wrapped in runCatching so
 * partial failures never block on other results.
 */
object StremioService {

    private const val TAG = "StremioService"
    private const val CATALOG_TIMEOUT_MS = 10_000L
    private const val STREAM_TIMEOUT_MS  = 15_000L
    private const val META_TIMEOUT_MS    = 10_000L

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceDescriptor::class.java, ResourceDescriptorDeserializer())
        .create()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("%3A", ":")

    private fun buildCandidates(
        addons: List<InstalledAddon>,
        resourceName: String,
        type: String,
        id: String,
        preferredAddonId: String?
    ): List<InstalledAddon> {
        val preferred = preferredAddonId
            ?.takeIf { it.isNotBlank() }
            ?.let { pref -> addons.firstOrNull { it.manifest.id == pref } }

        val others = if (preferred != null) {
            addons.filterNot { it.manifest.id == preferred.manifest.id }
        } else {
            addons
        }

        val relevantOthers = others.filter { isRelevant(it, resourceName, type, id) }
        return if (preferred != null) listOf(preferred) + relevantOthers else relevantOthers
    }

    // ── Low-level fetch ───────────────────────────────────────────────────────

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val bodyPreview = resp.body?.string()?.take(220)
                    Log.w(TAG, "HTTP ${resp.code} for $url body=${bodyPreview ?: "<empty>"}")
                    null
                } else {
                    resp.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed: $url — ${e.message}")
            null
        }
    }

    // ── Extra prop encoding (spec §5) ─────────────────────────────────────────

    /**
     * Encode extra properties as a path segment before .json
     * e.g. {"search": "batman", "skip": "100"} → "search=batman&skip=100"
     */
    private fun encodeExtra(extra: Map<String, String>): String =
        extra.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    // ── Resource-matching algorithm (spec §12) ────────────────────────────────

    /**
     * Returns the ResourceDescriptor for [resourceName] from [manifest], or null
     * if the addon does not declare it.
     */
    private fun findResource(
        manifest: AddonManifest,
        resourceName: String
    ): ResourceDescriptor? =
        manifest.resources.firstOrNull { it.name == resourceName }

    /**
     * True if [addon] should be queried for [resourceName] with [type] and [id].
     * Implements the full filtering from spec §12.
     */
    fun isRelevant(
        addon: InstalledAddon,
        resourceName: String,
        type: String,
        id: String
    ): Boolean {
        val manifest = addon.manifest
        val res = findResource(manifest, resourceName) ?: return false
        val effectiveTypes = res.types ?: manifest.types
        if (type !in effectiveTypes) return false
        // idPrefixes filtering (never applies to catalog)
        val effectivePrefixes = res.idPrefixes ?: manifest.idPrefixes
        if (!effectivePrefixes.isNullOrEmpty()) {
            if (effectivePrefixes.none { id.startsWith(it) }) return false
        }
        return true
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    /**
     * Fetches a single catalog.
     * @param extra  optional extra props, e.g. mapOf("skip" to "100")
     */
    suspend fun getCatalog(
        addon: InstalledAddon,
        type: String,
        catalogId: String,
        extra: Map<String, String> = emptyMap()
    ): CatalogResponse? = withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
        val encodedType = encodePathSegment(type)
        val encodedCatalogId = encodePathSegment(catalogId)
        val extraSegment = if (extra.isNotEmpty()) "/${encodeExtra(extra)}" else ""
        val url = "${addon.transportUrl}/catalog/$encodedType/$encodedCatalogId$extraSegment.json"
        val body = get(url) ?: return@withTimeoutOrNull null
        runCatching { gson.fromJson(body, CatalogResponse::class.java) }.getOrNull()
    }

    /**
     * Loads all board-eligible catalog rows from all installed addons in parallel.
     * Skips catalogs that have any required extras.
     * Returns only rows with at least one item.
     */
    suspend fun loadBoard(addons: List<InstalledAddon>): List<BoardRow> = coroutineScope {
        val jobs = addons.flatMap { addon ->
            addon.manifest.catalogs
                .filter { it.isBoard }
                .map { catalog ->
                    async {
                        val resp = runCatching {
                            getCatalog(addon, catalog.type, catalog.id)
                        }.getOrNull()?.flatten()
                        if (!resp.isNullOrEmpty()) {
                            BoardRow(
                                addonId = addon.manifest.id,
                                addonName = addon.manifest.name,
                                catalogId = catalog.id,
                                catalogType = catalog.type,
                                title = "${addon.manifest.name} — ${catalog.name.ifBlank { catalog.id }}",
                                items = resp
                            )
                        } else null
                    }
                }
        }
        jobs.awaitAll().filterNotNull()
    }

    /**
     * Searches addons that declare a catalog with the "search" extra.
     * Returns non-empty rows only.
     */
    suspend fun search(addons: List<InstalledAddon>, query: String): List<BoardRow> =
        coroutineScope {
            val jobs = addons.flatMap { addon ->
                addon.manifest.catalogs
                    .filter { it.isSearchable }
                    .map { catalog ->
                        async {
                            val resp = runCatching {
                                getCatalog(
                                    addon,
                                    catalog.type,
                                    catalog.id,
                                    mapOf("search" to query)
                                )
                            }.getOrNull()?.flatten()
                            if (!resp.isNullOrEmpty()) {
                                BoardRow(
                                    addonId = addon.manifest.id,
                                    addonName = addon.manifest.name,
                                    catalogId = catalog.id,
                                    catalogType = catalog.type,
                                    title = "${addon.manifest.name}: $query",
                                    items = resp
                                )
                            } else null
                        }
                    }
            }
            jobs.awaitAll().filterNotNull()
        }

    // ── Meta ──────────────────────────────────────────────────────────────────

    /**
     * Fetches meta for a Stremio item. Returns the first successful response.
     */
    suspend fun getMeta(
        addons: List<InstalledAddon>,
        type: String,
        id: String,
        preferredAddonId: String? = null
    ): StremioMeta? = withContext(Dispatchers.IO) {
        val candidates = buildCandidates(addons, "meta", type, id, preferredAddonId)
        val encodedType = encodePathSegment(type)
        val encodedId = encodePathSegment(id)
        for (addon in candidates) {
            val result = withTimeoutOrNull(META_TIMEOUT_MS) {
                val url = "${addon.transportUrl}/meta/$encodedType/$encodedId.json"
                val body = get(url) ?: return@withTimeoutOrNull null
                runCatching { gson.fromJson(body, MetaResponse::class.java)?.meta }.getOrNull()
            }
            if (result != null) return@withContext result
        }
        null
    }

    // ── Streams ───────────────────────────────────────────────────────────────

    /**
     * Fetches streams from ALL relevant addons in parallel and aggregates results.
     * Injects addonName and addonId into each stream for display.
     */
    suspend fun getStreams(
        addons: List<InstalledAddon>,
        type: String,
        id: String,
        preferredAddonId: String? = null
    ): List<StremioStream> = coroutineScope {
        val candidates = buildCandidates(addons, "stream", type, id, preferredAddonId)
        val encodedType = encodePathSegment(type)
        val encodedId = encodePathSegment(id)
        val jobs = candidates.map { addon ->
            async {
                runCatching {
                    withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                        val url = "${addon.transportUrl}/stream/$encodedType/$encodedId.json"
                        val body = get(url) ?: return@withTimeoutOrNull emptyList()
                        val resp = gson.fromJson(body, StreamResponse::class.java)
                        resp?.streams?.map { stream ->
                            stream.copy(
                                addonName = addon.manifest.name,
                                addonId = addon.manifest.id
                            )
                        } ?: emptyList()
                    } ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }
        jobs.awaitAll().flatten()
    }

    // ── Subtitles ─────────────────────────────────────────────────────────────

    /**
     * Fetches subtitles from ALL relevant addons in parallel.
     * Also merges [inlineSubtitles] from the stream itself.
     */
    suspend fun getSubtitles(
        addons: List<InstalledAddon>,
        type: String,
        id: String,
        inlineSubtitles: List<StremioSubtitle> = emptyList(),
        videoHash: String? = null,
        videoSize: Long? = null,
        filename: String? = null,
        preferredAddonId: String? = null
    ): List<StremioSubtitle> = coroutineScope {
        val candidates = buildCandidates(addons, "subtitles", type, id, preferredAddonId)
        Log.i(TAG, "Subtitle candidates: ${candidates.size}/${addons.size} addons (type=$type id=$id)")
        for (a in addons) {
            val relevant = isRelevant(a, "subtitles", type, id)
            Log.i(TAG, "  addon '${a.manifest.name}' (${a.manifest.id}) resources=${a.manifest.resources.map { it.name }} types=${a.manifest.types} relevant=$relevant")
        }
        val encodedType = encodePathSegment(type)
        val encodedId = encodePathSegment(id)
        val extra = buildMap<String, String> {
            videoHash?.let { put("videoHash", it) }
            videoSize?.let { put("videoSize", it.toString()) }
            filename?.let { put("filename", it) }
        }
        val extraSegment = if (extra.isNotEmpty()) "/${encodeExtra(extra)}" else ""

        val jobs = candidates.map { addon ->
            async {
                runCatching {
                    withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                        val url = "${addon.transportUrl}/subtitles/$encodedType/$encodedId$extraSegment.json"
                        Log.i(TAG, "Subtitle fetch: $url")
                        val body = get(url) ?: return@withTimeoutOrNull emptyList()
                        val parsed = gson.fromJson(body, SubtitlesResponse::class.java)?.subtitles
                            ?: emptyList()
                        Log.i(TAG, "Subtitle fetch got ${parsed.size} from ${addon.manifest.name}")
                        parsed
                    } ?: emptyList()
                }.getOrElse { e ->
                    Log.i(TAG, "Subtitle fetch error from ${addon.manifest.name}: ${e.message}")
                    emptyList()
                }
            }
        }
        val addonSubs = jobs.awaitAll().flatten()
        (inlineSubtitles + addonSubs).distinctBy { it.url }
    }

    // ── Stream routing (spec §11) ─────────────────────────────────────────────

    /**
     * Resolves a [StremioStream] to the appropriate [StreamRoute].
     */
    fun routeStream(stream: StremioStream): StreamRoute {
        // infoHash → torrent
        if (!stream.infoHash.isNullOrBlank()) {
            val magnet = buildMagnet(stream.infoHash, stream.sources)
            return StreamRoute.Torrent(magnet = magnet, fileIdx = stream.fileIdx)
        }
        // url-based routing
        val url = stream.url
        if (!url.isNullOrBlank()) {
            return when {
                url.startsWith("magnet:") ->
                    StreamRoute.Torrent(magnet = url, fileIdx = stream.fileIdx)
                url.startsWith("stremio-yt://") -> {
                    val ytId = url.removePrefix("stremio-yt://")
                    StreamRoute.YouTube(ytId = ytId)
                }
                url.startsWith("http://") || url.startsWith("https://") -> {
                    val headers = stream.behaviorHints?.proxyHeaders?.request
                    StreamRoute.DirectUrl(url = url, headers = headers)
                }
                else -> StreamRoute.Unsupported
            }
        }
        // externalUrl → check for stremio:// deep links first, then browser
        if (!stream.externalUrl.isNullOrBlank()) {
            val deepLink = parseStremioDeepLink(stream.externalUrl)
            if (deepLink != null) return deepLink
            return StreamRoute.External(url = stream.externalUrl)
        }
        // ytId → YouTube
        if (!stream.ytId.isNullOrBlank()) {
            return StreamRoute.YouTube(ytId = stream.ytId)
        }
        // playerFrameUrl → treat as External on TV
        if (!stream.playerFrameUrl.isNullOrBlank()) {
            return StreamRoute.IFrame(url = stream.playerFrameUrl)
        }
        return StreamRoute.Unsupported
    }

    // ── Magnet builder ────────────────────────────────────────────────────────

    fun buildMagnet(infoHash: String, sources: List<String>? = null): String {
        val sb = StringBuilder("magnet:?xt=urn:btih:")
        sb.append(infoHash.lowercase())
        sources?.forEach { tracker ->
            if (tracker.startsWith("tracker:")) {
                sb.append("&tr=")
                sb.append(URLEncoder.encode(tracker.removePrefix("tracker:"), "UTF-8"))
            }
        }
        return sb.toString()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses a stremio:// deep link URL into a StreamRoute.StremioDeepLink.
     * Handles: stremio:///detail/{type}/{id}[/{videoId}], stremio:///search?search=..., etc.
     */
    fun parseStremioDeepLink(url: String): StreamRoute.StremioDeepLink? {
        if (!url.startsWith("stremio://")) return null
        // stremio:///detail/movie/tt1234567 → path = detail/movie/tt1234567
        val withoutScheme = url.removePrefix("stremio:///")
        val queryIdx = withoutScheme.indexOf('?')
        val pathPart = if (queryIdx >= 0) withoutScheme.substring(0, queryIdx) else withoutScheme
        val queryPart = if (queryIdx >= 0) withoutScheme.substring(queryIdx + 1) else ""
        val parts = pathPart.split("/").filter { it.isNotEmpty() }
        val action = parts.getOrNull(0) ?: return null

        return when (action) {
            "detail" -> StreamRoute.StremioDeepLink(
                action = "detail",
                type = parts.getOrNull(1),
                id = parts.getOrNull(2)?.let { URLDecoder.decode(it, "UTF-8") },
                videoId = parts.getOrNull(3)?.let { URLDecoder.decode(it, "UTF-8") }
            )
            "search" -> {
                val params = queryPart.split("&").associate { kv ->
                    val (k, v) = kv.split("=", limit = 2)
                    k to URLDecoder.decode(v, "UTF-8")
                }
                StreamRoute.StremioDeepLink(action = "search", query = params["search"])
            }
            else -> StreamRoute.StremioDeepLink(action = action)
        }
    }

    private fun CatalogResponse?.flatten(): List<StremioMetaPreview>? =
        this?.metas?.takeIf { it.isNotEmpty() }
}

// ─── Board row returned by loadBoard / search ─────────────────────────────────

data class BoardRow(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val catalogType: String = "",
    val title: String,
    val items: List<StremioMetaPreview>
)
