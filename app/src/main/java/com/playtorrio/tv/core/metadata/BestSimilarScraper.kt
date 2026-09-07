package com.playtorrio.tv.core.metadata

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class BSAutocompleteHit(
    val id: Int,
    val slug: String,
    val label: String,
    val title: String,
    val year: Int?,
    val isTv: Boolean,
    val url: String
)

data class BSItem(
    val id: Int,
    val slug: String,
    val title: String,
    val year: Int?,
    val rating: Double?,
    val voteCount: String?,
    val thumbUrl: String,
    val similarityPercent: Int?,
    val genre: String?,
    val country: String?,
    val duration: String?,
    val story: String?,
    val styleTags: List<String> = emptyList(),
    val plotTags: List<String> = emptyList(),
    val audienceTags: List<String> = emptyList(),
    val itemType: String? = null
) {
    val isTv: Boolean
        get() {
            val t = itemType?.lowercase().orEmpty()
            if (t.contains("tv") || t.contains("series") || t.contains("serial") || t.contains("show")) return true
            if (t.contains("movie") || t.contains("film")) return false
            if (slug.contains("serial") || slug.contains("tv-series") || slug.contains("series")) return true
            val dur = duration?.lowercase().orEmpty()
            return dur.contains("eps") || dur.contains("episode") || dur.contains("serial") || dur.contains("season")
        }

    val displayTitle: String
        get() = if (year != null) "$title ($year)" else title
}

data class BSDetails(
    val id: Int,
    val slug: String,
    val title: String,
    val year: Int?,
    val rating: Double?,
    val voteCount: String?,
    val thumbUrl: String,
    val story: String?,
    val genre: String?,
    val country: String?,
    val duration: String?,
    val similar: List<BSItem> = emptyList()
)

object BestSimilarScraper {
    private const val TAG = "BestSimilarScraper"
    const val BASE_URL = "https://bestsimilar.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val autocompleteCache = ConcurrentHashMap<String, List<BSAutocompleteHit>>()
    private val detailsCache = ConcurrentHashMap<Int, BSDetails>()

    suspend fun autocomplete(term: String): List<BSAutocompleteHit> = withContext(Dispatchers.IO) {
        val q = term.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val cacheKey = q.lowercase()
        autocompleteCache[cacheKey]?.let { return@withContext it }

        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "$BASE_URL/site/autocomplete?term=$encoded"

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext emptyList()

            val body = res.body?.string() ?: return@withContext emptyList()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val data: Map<String, Any?> = gson.fromJson(body, mapType) ?: return@withContext emptyList()
            val movies = data["movie"] as? List<*> ?: return@withContext emptyList()

            val out = mutableListOf<BSAutocompleteHit>()
            for (m in movies) {
                if (m !is Map<*, *>) continue
                val id = m["id"]?.toString()?.toIntOrNull() ?: continue
                val hitUrl = m["url"]?.toString().orEmpty()
                val label = m["label"]?.toString().orEmpty()
                if (hitUrl.isEmpty() || label.isEmpty()) continue

                val isTv = m["serial"]?.toString() == "1"
                val yearMatch = Regex("""\((\d{4})\)\s*$""").find(label)
                val year = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                val title = label.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()
                val slug = hitUrl.removePrefix("/movies/")

                out.add(
                    BSAutocompleteHit(
                        id = id,
                        slug = slug,
                        label = label,
                        title = title,
                        year = year,
                        isTv = isTv,
                        url = hitUrl
                    )
                )
            }

            autocompleteCache[cacheKey] = out
            out
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Autocomplete error: ${e.message}")
            emptyList()
        }
    }

    suspend fun findBest(
        title: String,
        year: Int? = null,
        isTv: Boolean = false
    ): BSAutocompleteHit? = withContext(Dispatchers.IO) {
        val cleanRaw = title.trim()
        if (cleanRaw.isEmpty()) return@withContext null

        val terms = mutableListOf<String>()
        terms.add(cleanRaw)

        val separators = listOf(":", " - ", " – ", " — ")
        for (sep in separators) {
            if (cleanRaw.contains(sep)) {
                val parts = cleanRaw.split(sep)
                val prefix = parts.firstOrNull()?.trim().orEmpty()
                val suffix = parts.drop(1).joinToString(sep).trim()
                if (prefix.isNotEmpty() && !terms.contains(prefix)) terms.add(prefix)
                if (suffix.isNotEmpty() && !terms.contains(suffix)) terms.add(suffix)
            }
        }

        val allHits = mutableMapOf<Int, BSAutocompleteHit>()
        for (term in terms) {
            val hits = autocomplete(term)
            for (h in hits) {
                allHits[h.id] = h
            }
        }

        if (allHits.isEmpty()) return@withContext null

        var best: BSAutocompleteHit? = null
        var bestScore = -999.0

        val normTarget = normalizeTitle(title)
        val targetWords = normTarget.split(Regex("""\s+""")).filter { it.isNotEmpty() }.toSet()

        for (h in allHits.values) {
            var score = 0.0
            val normHit = normalizeTitle(h.title)
            val hitWords = normHit.split(Regex("""\s+""")).filter { it.isNotEmpty() }.toSet()

            // 1. Title match
            if (normHit == normTarget) {
                score += 8.0
            } else if (normTarget.startsWith(normHit) || normHit.startsWith(normTarget)) {
                score += 4.0
            } else {
                val intersection = targetWords.intersect(hitWords).size
                if (intersection > 0 && targetWords.isNotEmpty()) {
                    score += (intersection.toDouble() / targetWords.size) * 4.0
                }
            }

            // 2. Format Match (Movie vs TV Show)
            if (h.isTv == isTv) {
                score += 3.0
            } else {
                score -= 4.0
            }

            // 3. Strict Year Scoring
            if (year != null && h.year != null) {
                val diff = abs(h.year - year)
                when {
                    diff == 0 -> score += 6.0
                    diff == 1 -> score += 3.0
                    diff == 2 -> score += 1.0
                    diff <= 4 -> score -= 4.0
                    else -> score -= 15.0
                }
            } else if (year != null && h.year == null) {
                score -= 1.0
            }

            if (score > bestScore) {
                bestScore = score
                best = h
            }
        }

        if (bestScore >= 6.0) best else null
    }

    suspend fun fetchDetails(id: Int, slug: String): BSDetails? = withContext(Dispatchers.IO) {
        detailsCache[id]?.let { return@withContext it }

        val url = "$BASE_URL/movies/$slug"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Referer", "$BASE_URL/")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext null

            val html = res.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)

            val h1 = doc.selectFirst("h1")
            var title = ""
            if (h1 != null) {
                val raw = h1.text().trim()
                val m = Regex("""^Movies?\s+(?:Like|Similar to)\s+(.+)$""", RegexOption.IGNORE_CASE).find(raw)
                title = (m?.groupValues?.getOrNull(1) ?: raw).trim()
            }

            val pageTitle = doc.selectFirst("title")?.text().orEmpty()
            val ym = Regex("""\((\d{4})\)""").find(pageTitle)
            val year = ym?.groupValues?.getOrNull(1)?.toIntOrNull()

            val relRoot = doc.selectFirst("#movie-rel-list")
            val heroAttrs = mutableMapOf<String, String>()
            for (el in doc.select(".attr")) {
                if (relRoot != null && el.parents().contains(relRoot)) continue
                val entry = el.selectFirst(".entry")?.text()?.trim().orEmpty()
                val value = el.selectFirst(".value")
                if (entry.isEmpty() || value == null) continue
                val key = entry.replace(":", "").trim().lowercase()
                heroAttrs[key] = value.text().trim()
            }

            val heroRating = doc.selectFirst(".rat-rating")?.text()?.let { extractNumber(it) }?.toDoubleOrNull()
            val heroVotes = doc.selectFirst(".rat-vote")?.text()?.replace(Regex("[^A-Z0-9.,]"), "")

            val items = mutableListOf<BSItem>()
            if (relRoot != null) {
                for (node in relRoot.select(".item.item-movie")) {
                    val dataId = node.attr("data-id").toIntOrNull() ?: continue
                    val nameAnchor = node.selectFirst("a.name") ?: continue
                    val href = nameAnchor.attr("href").trim()
                    val itemSlug = href.removePrefix("/movies/")
                    val label = nameAnchor.text().trim()

                    val ymItem = Regex("""\((\d{4})\)\s*$""").find(label)
                    val itemYear = ymItem?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val itemTitle = label.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()

                    val ratingTxt = node.selectFirst(".rat-rating")?.text().orEmpty()
                    val voteTxt = node.selectFirst(".rat-vote")?.text().orEmpty()
                    val imgSrc = node.selectFirst("img")?.attr("src").orEmpty()
                    val thumb = if (imgSrc.startsWith("http")) imgSrc else "$BASE_URL$imgSrc"
                    val smt = node.selectFirst(".smt-value")?.text().orEmpty()
                    val simPct = smt.replace("%", "").trim().toIntOrNull()

                    val attrMap = mutableMapOf<String, String>()
                    val tagMap = mutableMapOf<String, List<String>>()
                    for (at in node.select(".attr")) {
                        val entry = at.selectFirst(".entry")?.text()?.trim().orEmpty()
                        val value = at.selectFirst(".value")
                        if (entry.isEmpty() || value == null) continue
                        val key = entry.replace(":", "").trim().lowercase()
                        if (at.hasClass("attr-tag")) {
                            tagMap[key] = value.text()
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() && it != "..." }
                        } else {
                            attrMap[key] = value.text().trim()
                        }
                    }

                    val typeLabel = node.selectFirst(".attr-types, .label-default")?.text()?.trim()

                    items.add(
                        BSItem(
                            id = dataId,
                            slug = itemSlug,
                            title = itemTitle,
                            year = itemYear,
                            rating = extractNumber(ratingTxt)?.toDoubleOrNull(),
                            voteCount = voteTxt.replace(Regex("[^A-Z0-9.,]"), "").ifBlank { null },
                            thumbUrl = thumb,
                            similarityPercent = simPct,
                            genre = attrMap["genre"],
                            country = attrMap["country"],
                            duration = attrMap["duration"],
                            story = attrMap["story"],
                            styleTags = tagMap["style"] ?: emptyList(),
                            plotTags = tagMap["plot"] ?: emptyList(),
                            audienceTags = tagMap["audience"] ?: emptyList(),
                            itemType = typeLabel
                        )
                    )
                }
            }

            items.sortByDescending { it.similarityPercent ?: -1 }

            val details = BSDetails(
                id = id,
                slug = slug,
                title = title,
                year = year,
                rating = heroRating,
                voteCount = heroVotes,
                thumbUrl = "",
                story = heroAttrs["story"],
                genre = heroAttrs["genre"],
                country = heroAttrs["country"],
                duration = heroAttrs["duration"],
                similar = items
            )

            detailsCache[id] = details
            details
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Fetch details error for $slug: ${e.message}")
            null
        }
    }

    suspend fun getSimilar(
        title: String,
        year: Int? = null,
        isTv: Boolean = false
    ): List<BSItem> = withContext(Dispatchers.IO) {
        val hit = findBest(title, year, isTv) ?: return@withContext emptyList()
        val details = fetchDetails(hit.id, hit.slug)
        details?.similar ?: emptyList()
    }

    private fun normalizeTitle(str: String): String {
        return str
            .lowercase()
            .replace(Regex("""[^\w\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun extractNumber(s: String): String? {
        val m = Regex("""(\d+(?:\.\d+)?)""").find(s)
        return m?.groupValues?.getOrNull(1)
    }
}
