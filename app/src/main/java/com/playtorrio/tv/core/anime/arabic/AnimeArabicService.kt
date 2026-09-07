package com.playtorrio.tv.core.anime.arabic

import android.util.Base64
import android.util.Log
import com.playtorrio.tv.core.anime.model.AnimeEpisode
import com.playtorrio.tv.core.anime.model.AnimeMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ArabicAnimeCard(
    val slug: String,
    val title: String,
    val cover: String? = null,
    val tag: String? = null,
    val rating: String? = null,
    val episodeBadge: String? = null
) {
    fun toAnimeMedia(): AnimeMedia {
        val epCount = episodeBadge?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 0
        val score = ((rating?.toDoubleOrNull() ?: 0.0) * 10).roundToInt()
        val isMovie = tag?.contains("فيلم", ignoreCase = true) == true || tag?.contains("Movie", ignoreCase = true) == true

        return AnimeMedia(
            id = slug.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) },
            titleRomaji = title,
            titleEnglish = title,
            titleNative = title,
            titleUserPreferred = title,
            coverImageLarge = cover.orEmpty(),
            coverImageExtraLarge = cover.orEmpty(),
            bannerImage = cover.orEmpty(),
            format = if (!episodeBadge.isNullOrBlank()) episodeBadge else if (isMovie) "MOVIE" else "TV",
            status = tag ?: "FINISHED",
            totalEpisodes = epCount,
            averageScore = score,
            description = title,
            slug = slug,
            isArabic = true
        )
    }
}

data class ArabicAnimeDetails(
    val slug: String,
    val title: String,
    val cover: String? = null,
    val banner: String? = null,
    val description: String = "",
    val status: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val studio: String? = null,
    val genres: List<String> = emptyList(),
    val episodes: List<AnimeEpisode> = emptyList(),
    val related: List<AnimeMedia> = emptyList()
) {
    fun toAnimeMedia(): AnimeMedia {
        val score = ((rating?.toDoubleOrNull() ?: 0.0) * 10).roundToInt()
        val y = year?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() } ?: 0
        val isMovie = status?.contains("فيلم", ignoreCase = true) == true || genres.any { it.contains("فيلم", ignoreCase = true) }

        return AnimeMedia(
            id = slug.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) },
            titleRomaji = title,
            titleEnglish = title,
            titleNative = title,
            titleUserPreferred = title,
            coverImageLarge = cover.orEmpty(),
            coverImageExtraLarge = cover.orEmpty(),
            bannerImage = banner?.ifBlank { cover } ?: cover.orEmpty(),
            format = if (isMovie) "MOVIE" else "TV",
            status = status ?: "FINISHED",
            totalEpisodes = episodes.size,
            averageScore = score,
            description = description,
            studioName = studio.orEmpty(),
            seasonYear = y,
            genres = genres,
            recommendations = related,
            slug = slug,
            isArabic = true
        )
    }
}

data class ArabicHomeFeed(
    val spotlight: List<AnimeMedia> = emptyList(),
    val recentEpisodes: List<AnimeMedia> = emptyList(),
    val trending: List<AnimeMedia> = emptyList(),
    val popularMovies: List<AnimeMedia> = emptyList(),
    val topSeasonal: List<AnimeMedia> = emptyList(),
    val seasonal: List<AnimeMedia> = emptyList(),
    val legendary: List<AnimeMedia> = emptyList(),
    val upcoming: List<AnimeMedia> = emptyList(),
    val misc: List<Pair<String, List<AnimeMedia>>> = emptyList()
)

@Singleton
class AnimeArabicService @Inject constructor() {

    companion object {
        private const val TAG = "AnimeArabicService"
        const val BASE_URL = "https://animeslayer.to"
        private const val XOR_KEY = "asxwqa147"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val instance: AnimeArabicService by lazy { AnimeArabicService() }

        fun decodeHref(encoded: String): String? {
            return try {
                val decoded = Base64.decode(encoded.trim(), Base64.DEFAULT)
                val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
                val out = ByteArray(decoded.size)
                for (i in decoded.indices) {
                    out[i] = (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                }
                String(out, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        fun stripBrand(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return s
            val brandPattern = Regex(
                """(انمي\s*سلاير|أنمي\s*سلاير|انيمي\s*سلاير|انمى\s*سلاير|anime\s*slayer|animeslayer)""",
                RegexOption.IGNORE_CASE
            )
            val sepPattern = Regex("""\s*[-–—|•·:]+\s*""")
            while (true) {
                val matches = sepPattern.findAll(s).toList()
                if (matches.isEmpty()) break
                val last = matches.last()
                val tail = s.substring(last.range.last + 1).trim()
                if (tail.isEmpty() || brandPattern.containsMatchIn(tail)) {
                    s = s.substring(0, last.range.first).trim()
                    continue
                }
                break
            }
            s = s.replace(brandPattern, "").trim()
            s = s.replace(Regex("""\s{2,}"""), " ")
            s = s.replace(Regex("""^[-–—|•·:\s]+|[-–—|•·:\s]+$"""), "").trim()
            return s
        }

        fun decodeEntities(s: String): String {
            return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
        }

        fun stripTags(s: String): String = s.replace(Regex("""<[^>]+>"""), " ")

        fun normalizeImg(src: String): String {
            val pre = "serveproxy.com/?url="
            val i = src.indexOf(pre)
            return if (i >= 0) src.substring(i + pre.length) else src
        }

        fun humanizeSlug(slug: String): String {
            val raw = slug.replace(Regex("""-[a-z0-9]{2,5}$"""), "")
            return raw.split("-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val cacheTtlMs = 10 * 60 * 1000L

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "$BASE_URL$path"
        val cached = cache[url]
        if (cached != null && System.currentTimeMillis() < cached.second) {
            return@withContext cached.first
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ar,en;q=0.8")
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            cache[url] = Pair(body, System.currentTimeMillis() + cacheTtlMs)
            body
        }
    }

    suspend fun getHome(): ArabicHomeFeed = withContext(Dispatchers.IO) {
        try {
            val html = try {
                get("/ios")
            } catch (e: Exception) {
                Log.w(TAG, "get /ios failed (${e.message}), trying /home")
                get("/home")
            }
            parseHome(html)
        } catch (e: Exception) {
            Log.e(TAG, "getHome failed: ${e.message}", e)
            ArabicHomeFeed()
        }
    }

    suspend fun search(query: String): List<AnimeMedia> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        try {
            val body = get("/api/search.php?q=${URLEncoder.encode(q, "UTF-8")}")
            val raw = JSONArray(body)
            val out = mutableListOf<AnimeMedia>()
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i) ?: continue
                val href = item.optString("href", "")
                if (href.isBlank()) continue
                val slug = if (href.startsWith("/title/")) href.substring(7) else href.trimStart('/')
                val tag = item.optString("type", item.optString("status", ""))
                val title = stripBrand(decodeEntities(item.optString("title", ""))).trim()
                val image = normalizeImg(item.optString("image", ""))
                val card = ArabicAnimeCard(
                    slug = slug,
                    title = title.ifBlank { humanizeSlug(slug) },
                    cover = image.ifBlank { null },
                    tag = tag.ifBlank { null }
                )
                out.add(card.toAnimeMedia())
            }
            if (out.isNotEmpty()) return@withContext out
        } catch (e: Exception) {
            Log.w(TAG, "Search JSON parse failed, falling back to HTML: ${e.message}")
        }

        try {
            val html = get("/?s=${URLEncoder.encode(q, "UTF-8")}")
            val cards = parseCardGrid(html)
            return@withContext cards.map { it.toAnimeMedia() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDetails(slugParam: String): ArabicAnimeDetails = withContext(Dispatchers.IO) {
        val slug = if (slugParam.startsWith("/title/")) slugParam.substring(7) else slugParam
        val html = get("/title/$slug")
        parseDetails(html, slug)
    }

    private fun parseHome(html: String): ArabicHomeFeed {
        val headingRegex = Regex("""<h[12][^>]*>([\s\S]*?)<\/h[12]>""", RegexOption.MULTILINE)
        data class HeadingHit(val text: String, val start: Int, val end: Int)
        val headings = mutableListOf<HeadingHit>()
        for (m in headingRegex.findAll(html)) {
            val text = stripTags(m.groupValues[1]).trim()
            if (text.isNotBlank()) {
                headings.add(HeadingHit(text, m.range.first, m.range.last + 1))
            }
        }

        val spotlight = parseSpotlight(html).map { it.toAnimeMedia() }
        var recentEpisodes = emptyList<AnimeMedia>()
        var trending = emptyList<AnimeMedia>()
        var popularMovies = emptyList<AnimeMedia>()
        var topSeasonal = emptyList<AnimeMedia>()
        var seasonal = emptyList<AnimeMedia>()
        var legendary = emptyList<AnimeMedia>()
        var upcoming = emptyList<AnimeMedia>()
        val misc = mutableListOf<Pair<String, List<AnimeMedia>>>()

        for (i in headings.indices) {
            val h = headings[i]
            val endIdx = if (i + 1 < headings.size) headings[i + 1].start else html.length
            val section = html.substring(h.end, endIdx)
            val cards = parseCardGrid(section).map { it.toAnimeMedia() }
            if (cards.isEmpty()) continue
            val t = h.text
            when {
                t.contains("آخر الحلقات") || t.contains("Latest") -> recentEpisodes = cards
                t.contains("الأكثر شهرة") || t.contains("شعبية هذا") || t.contains("Trending") -> trending = cards
                t.contains("الأفلام الأكثر شعبية") || t.contains("Movies") -> popularMovies = cards
                t.contains("أفضل انميات") || t.contains("أفضل الأنميات") -> topSeasonal = cards
                t.contains("أنميات موسيمية") || t.contains("Seasonal") -> seasonal = cards
                t.contains("أسطورية") || t.contains("Legendary") -> legendary = cards
                t.contains("المنتظرة") || t.contains("Upcoming") -> upcoming = cards
                else -> misc.add(Pair(t, cards))
            }
        }

        return ArabicHomeFeed(
            spotlight = spotlight,
            recentEpisodes = recentEpisodes,
            trending = trending,
            popularMovies = popularMovies,
            topSeasonal = topSeasonal,
            seasonal = seasonal,
            legendary = legendary,
            upcoming = upcoming,
            misc = misc
        )
    }

    private fun parseSpotlight(html: String): List<ArabicAnimeCard> {
        val out = mutableListOf<ArabicAnimeCard>()
        val seen = mutableSetOf<String>()

        val heroImgMatch = Regex("""<img[^>]*?src="([^"]*banners[^"]*)"""").find(html)
        val heroHrefMatch = Regex("""href="(/title/([^"]+))"""").find(html)
        if (heroHrefMatch != null) {
            val slug = heroHrefMatch.groupValues[2]
            if (seen.add(slug)) {
                val cover = heroImgMatch?.groupValues?.get(1)?.let { normalizeImg(it) }
                out.add(ArabicAnimeCard(
                    slug = slug,
                    title = humanizeSlug(slug),
                    cover = cover
                ))
            }
        }

        val topChunk = html.take(70000)
        val regex = Regex("""href="(/title/([^"]+))"[\s\S]{0,4000}?(?:<img[^>]*?src="([^"]+)"[\s\S]{0,200})?""")
        for (m in regex.findAll(topChunk)) {
            if (out.size >= 8) break
            val slug = m.groupValues[2]
            if (!seen.add(slug)) continue
            val cover = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            out.add(ArabicAnimeCard(
                slug = slug,
                title = humanizeSlug(slug),
                cover = cover?.let { normalizeImg(it) }
            ))
        }
        return out
    }

    private fun parseCardGrid(chunk: String): List<ArabicAnimeCard> {
        val out = mutableListOf<ArabicAnimeCard>()
        val seen = mutableSetOf<String>()

        val regex = Regex("""data-href="([A-Za-z0-9+/=]+)"([\s\S]*?)(?=<\/swiper-slide>|<\/a>|$)""")

        for (m in regex.findAll(chunk)) {
            val encoded = m.groupValues[1]
            val cardHtml = m.groupValues[2]
            val decoded = decodeHref(encoded) ?: continue
            val slug = when {
                decoded.startsWith("/title/") -> decoded.substring("/title/".length).trim()
                decoded.startsWith("/e/") -> decoded.substring("/e/".length).substringBefore('#').trim()
                else -> continue
            }
            if (slug.isBlank() || !seen.add(slug)) continue

            val imgMatch = Regex("""<img[^>]*?src="([^"]+)"""").find(cardHtml)
            val cover = imgMatch?.groupValues?.get(1)?.let { normalizeImg(it) }

            val h5Match = Regex("""<h5[^>]*class="[^"]*title-text[^"]*"[^>]*>([^<]+)</h5>""").find(cardHtml)
            val spanMatch = Regex("""media-card-title[^>]*>([^<]+)</span>""").find(cardHtml)
            val altMatch = Regex("""<img[^>]*?alt="([^"]*)"""").find(cardHtml)
            val altVal = altMatch?.groupValues?.get(1)?.takeIf { !it.contains("scum of the brave", ignoreCase = true) }

            var title = (h5Match?.groupValues?.get(1)
                ?: spanMatch?.groupValues?.get(1)
                ?: altVal.orEmpty()).trim()
            title = stripBrand(title)
            if (title.isBlank() || title.contains("scum of the brave", ignoreCase = true)) {
                title = humanizeSlug(slug)
            }

            val tagMatch = Regex("""class="[^"]*text-gray-400[^"]*"[^>]*>([^<]+)</div>""").find(cardHtml)
                ?: Regex("""media-card-type[^>]*>([^<]+)<""").find(cardHtml)
            val tag = tagMatch?.groupValues?.get(1)?.let { stripTags(it).trim() }

            val rateMatch = Regex("""(?:تقييم|rating)\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(cardHtml)
            val rating = rateMatch?.groupValues?.get(1)

            val epMatch = Regex("""الحلقة\s*(\d+)""").find(cardHtml)
            val episodeBadge = epMatch?.let { "الحلقة ${it.groupValues[1]}" }

            out.add(ArabicAnimeCard(
                slug = slug,
                title = title,
                cover = cover,
                tag = tag,
                rating = rating,
                episodeBadge = episodeBadge
            ))
        }

        return out
    }


    private fun parseDetails(html: String, slug: String): ArabicAnimeDetails {
        var title = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.let { stripBrand(decodeEntities(it)) }
        if (title.isNullOrBlank()) title = humanizeSlug(slug)

        val banner = Regex("""<img[^>]*alt="[^"]*banner[^"]*"[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { normalizeImg(it) }

        val cover = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.let { normalizeImg(it) }

        var description = Regex("""<meta\s+name="description"\s+content="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.let { decodeEntities(it).trim() } ?: ""

        val synopsisMatch = Regex("""<p[^>]*class="[^"]*description[^"]*"[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { stripTags(it).trim() }
        if (synopsisMatch != null && synopsisMatch.length > description.length) {
            description = synopsisMatch
        }

        val status = Regex("""(مكتمل|يعرض الآن|قادم)""").find(html)?.groupValues?.get(1)
        val year = Regex("""بداية العرض[:\s]*([\d-]+)""").find(html)?.groupValues?.get(1)
        val rating = Regex("""التقييم[\s:]*([\d.]+)""").find(html)?.groupValues?.get(1)
        val studio = Regex("""الاستوديو[\s:]*([^<\n]+)""").find(html)?.groupValues?.get(1)?.let { stripTags(it).trim() }

        val genres = mutableListOf<String>()
        val genreBlock = Regex("""أصناف([\s\S]{0,400})""").find(html)?.groupValues?.get(1)
        if (genreBlock != null) {
            val raw = stripTags(genreBlock)
            for (g in raw.split(Regex("""[\s,،]+"""))) {
                val t = g.trim()
                if (t.length in 2..19 && !genres.contains(t)) {
                    genres.add(t)
                }
            }
        }

        val episodes = mutableListOf<AnimeEpisode>()
        val epsBlock = Regex("""const\s+episodes\s*=\s*\[([\s\S]*?)\];""").find(html)?.groupValues?.get(1)
        if (epsBlock != null) {
            val epRegex = Regex(
                """\{\s*n\s*:\s*(\d+)\s*,\s*title\s*:\s*"([^"]*)"\s*,\s*""" +
                """href\s*:\s*"([^"]*)"\s*,\s*desc\s*:\s*"([^"]*)"\s*,\s*""" +
                """views\s*:\s*"([^"]*)"\s*,\s*thumb\s*:\s*"([^"]*)"\s*\}"""
            )
            for (m in epRegex.findAll(epsBlock)) {
                val n = m.groupValues[1].toIntOrNull() ?: 0
                val epTitle = decodeEntities(m.groupValues[2]).trim()
                val encHref = m.groupValues[3]
                val relUrl = decodeHref(encHref).orEmpty()
                val desc = decodeEntities(m.groupValues[4]).trim()
                val thumb = m.groupValues[6].takeIf { it.isNotBlank() }?.let { normalizeImg(it) }

                episodes.add(AnimeEpisode(
                    number = n,
                    title = if (epTitle.isNotBlank()) epTitle else "الحلقة $n",
                    thumbnail = thumb ?: banner ?: cover.orEmpty(),
                    description = desc,
                    encodedHref = encHref,
                    watchPath = relUrl
                ))
            }
        }
        episodes.sortBy { it.number }
        val distinctEpisodes = episodes.distinctBy { it.number }

        val relatedIdx = html.indexOf("related-grid")
        val relatedCards = if (relatedIdx >= 0) {
            parseCardGrid(html.substring(relatedIdx)).map { it.toAnimeMedia() }
        } else emptyList()

        return ArabicAnimeDetails(
            slug = slug,
            title = title,
            cover = cover,
            banner = banner,
            description = description,
            status = status,
            year = year,
            rating = rating,
            studio = studio,
            genres = genres,
            episodes = distinctEpisodes,
            related = relatedCards
        )
    }
}
