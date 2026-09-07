package com.playtorrio.tv.core.anime.metadata

import android.util.Log
import com.playtorrio.tv.core.anime.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnilistService @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val memoryCache = ConcurrentHashMap<String, JSONObject>()

    companion object {
        private const val TAG = "AnilistService"
        private const val ENDPOINT = "https://graphql.anilist.co"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun currentSeason(): String {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-12
            return when (month) {
                in 1..3 -> "WINTER"
                in 4..6 -> "SPRING"
                in 7..9 -> "SUMMER"
                else -> "FALL"
            }
        }

        fun nextSeason(): String = when (currentSeason()) {
            "WINTER" -> "SPRING"
            "SPRING" -> "SUMMER"
            "SUMMER" -> "FALL"
            else -> "WINTER"
        }

        fun nextSeasonYear(): Int {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            return if (currentSeason() == "FALL") year + 1 else year
        }

        private const val MEDIA_FIELDS = """
            id
            idMal
            isAdult
            title {
              romaji
              english
              native
              userPreferred
            }
            coverImage {
              extraLarge
              large
              medium
              color
            }
            bannerImage
            format
            status
            episodes
            duration
            genres
            averageScore
            meanScore
            popularity
            favourites
            season
            seasonYear
            description(asHtml: false)
            studios(isMain: true) {
              nodes {
                id
                name
              }
            }
            trailer {
              id
              site
            }
            nextAiringEpisode {
              airingAt
              timeUntilAiring
              episode
            }
        """
    }

    private suspend fun postGraphQL(query: String, variables: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val cacheKey = "$query:${variables.toString()}"
        memoryCache[cacheKey]?.let { return@withContext it }

        try {
            val bodyObj = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }
            val requestBody = bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
                )
                .addHeader("Origin", "https://anilist.co")
                .addHeader("Referer", "https://anilist.co/")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawStr = response.body?.string() ?: return@withContext null
                    val json = JSONObject(rawStr)
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        memoryCache[cacheKey] = data
                        return@withContext data
                    }
                } else {
                    Log.w(TAG, "AniList error ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AniList request failed: ${e.message}", e)
        }
        null
    }

    suspend fun fetchTrendingAnime(page: Int = 1, perPage: Int = 20): List<AnimeMedia> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
        }
        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun fetchPopularThisSeason(page: Int = 1, perPage: Int = 20): List<AnimeMedia> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("season", currentSeason())
            put("seasonYear", Calendar.getInstance().get(Calendar.YEAR))
        }
        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun fetchUpcomingNextSeason(page: Int = 1, perPage: Int = 20): List<AnimeMedia> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("season", nextSeason())
            put("seasonYear", nextSeasonYear())
        }
        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun fetchTopRated(page: Int = 1, perPage: Int = 20): List<AnimeMedia> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, sort: SCORE_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
        }
        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun fetchByGenre(genre: String, page: Int = 1, perPage: Int = 20): List<AnimeMedia> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}genre: String) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, genre: ${'$'}genre, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("genre", genre)
        }
        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun searchAnime(
        search: String = "",
        genre: String? = null,
        year: Int? = null,
        season: String? = null,
        format: String? = null,
        status: String? = null,
        sort: String = "SEARCH_MATCH",
        isAdult: Boolean = false,
        page: Int = 1,
        perPage: Int = 30
    ): List<AnimeMedia> {
        val hasSearch = search.trim().isNotEmpty()
        val sortClause = if (hasSearch) "[SEARCH_MATCH, POPULARITY_DESC]" else "[$sort]"
        val isAdultFinal = isAdult || (genre != null && genre.equals("hentai", ignoreCase = true))

        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String, ${'$'}genre: String, ${'$'}year: Int, ${'$'}season: MediaSeason, ${'$'}format: MediaFormat, ${'$'}status: MediaStatus, ${'$'}isAdult: Boolean) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(type: ANIME, search: ${'$'}search, genre: ${'$'}genre, seasonYear: ${'$'}year, season: ${'$'}season, format: ${'$'}format, status: ${'$'}status, sort: $sortClause, isAdult: ${'$'}isAdult) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("isAdult", isAdultFinal)
            if (hasSearch) put("search", search.trim())
            if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) put("genre", genre)
            if (year != null && year > 0) put("year", year)
            if (!season.isNullOrBlank() && !season.equals("All", ignoreCase = true)) put("season", season.uppercase())
            if (!format.isNullOrBlank() && !format.equals("All", ignoreCase = true)) put("format", format.uppercase())
            if (!status.isNullOrBlank() && !status.equals("All", ignoreCase = true)) put("status", status.uppercase())
        }

        val data = postGraphQL(query, vars)
        return parseMediaList(data)
    }

    suspend fun fetchAnimeDetails(anilistId: Int): AnimeMedia? {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                $MEDIA_FIELDS
                characters(sort: ROLE, perPage: 12) {
                  nodes {
                    id
                    name {
                      full
                      native
                      userPreferred
                    }
                    image {
                      large
                      medium
                    }
                  }
                }
                relations {
                  edges {
                    relationType
                    node {
                      id
                      title {
                        userPreferred
                        english
                        romaji
                      }
                      format
                      type
                      status
                      coverImage {
                        large
                        medium
                      }
                    }
                  }
                }
                recommendations(perPage: 14, sort: RATING_DESC) {
                  nodes {
                    mediaRecommendation {
                      id
                      title {
                        userPreferred
                        english
                        romaji
                      }
                      coverImage {
                        large
                        medium
                      }
                      format
                      averageScore
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val vars = JSONObject().apply {
            put("id", anilistId)
        }
        val data = postGraphQL(query, vars) ?: return null
        val mediaObj = data.optJSONObject("Media") ?: return null
        return parseSingleAnimeMedia(mediaObj)
    }

    fun getEpisodes(anime: AnimeMedia): List<AnimeEpisode> {
        val count = when {
            anime.totalEpisodes > 0 -> anime.totalEpisodes
            anime.nextAiring != null && anime.nextAiring.episode > 1 -> anime.nextAiring.episode - 1
            anime.format.equals("MOVIE", ignoreCase = true) -> 1
            else -> 24
        }
        return (1..count).map { epNum ->
            AnimeEpisode(
                number = epNum,
                title = "Episode $epNum",
                thumbnail = anime.backdropUrl,
                description = "Episode $epNum of ${anime.displayTitle}"
            )
        }
    }

    private fun parseMediaList(data: JSONObject?): List<AnimeMedia> {
        if (data == null) return emptyList()
        val page = data.optJSONObject("Page") ?: return emptyList()
        val mediaArray = page.optJSONArray("media") ?: return emptyList()
        val results = mutableListOf<AnimeMedia>()
        for (i in 0 until mediaArray.length()) {
            val item = mediaArray.optJSONObject(i) ?: continue
            results.add(parseSingleAnimeMedia(item))
        }
        return results
    }

    private fun JSONObject.optCleanString(name: String, fallback: String = ""): String {
        if (!has(name) || isNull(name)) return fallback
        val v = optString(name).trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun JSONObject.optCleanStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val v = optString(name).trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun parseSingleAnimeMedia(json: JSONObject): AnimeMedia {
        val title = json.optJSONObject("title") ?: JSONObject()
        val cover = json.optJSONObject("coverImage") ?: JSONObject()
        val studiosNode = json.optJSONObject("studios")?.optJSONArray("nodes")
        var studioName = ""
        if (studiosNode != null && studiosNode.length() > 0) {
            studioName = studiosNode.optJSONObject(0)?.optCleanString("name").orEmpty()
        }

        val trailerData = json.optJSONObject("trailer")
        val trailerSite = trailerData?.optCleanStringOrNull("site")
        val trailerUrl = if (trailerSite?.equals("youtube", ignoreCase = true) == true) {
            trailerData.optCleanStringOrNull("id")
        } else null

        val nextAiringData = json.optJSONObject("nextAiringEpisode")
        val nextAiring = if (nextAiringData != null) {
            AnimeNextAiring(
                episode = nextAiringData.optInt("episode", 1),
                timeUntilAiringSeconds = nextAiringData.optLong("timeUntilAiring", 0L),
                airingAtTimestamp = nextAiringData.optLong("airingAt", 0L)
            )
        } else null

        val genresList = mutableListOf<String>()
        val genresArr = json.optJSONArray("genres")
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val g = genresArr.optString(i).trim()
                if (g.isNotBlank() && !g.equals("null", ignoreCase = true)) {
                    genresList.add(g)
                }
            }
        }

        val charactersList = mutableListOf<AnimeCharacter>()
        val charsArr = json.optJSONObject("characters")?.optJSONArray("nodes")
        if (charsArr != null) {
            for (i in 0 until charsArr.length()) {
                val c = charsArr.optJSONObject(i) ?: continue
                val nameObj = c.optJSONObject("name") ?: JSONObject()
                val imgObj = c.optJSONObject("image") ?: JSONObject()
                val fullName = nameObj.optCleanString("userPreferred")
                    .ifBlank { nameObj.optCleanString("full", "Unknown") }
                val imgLarge = imgObj.optCleanString("large")
                    .ifBlank { imgObj.optCleanString("medium") }
                charactersList.add(
                    AnimeCharacter(
                        id = c.optInt("id"),
                        nameFull = fullName,
                        nameNative = nameObj.optCleanString("native"),
                        imageLarge = imgLarge,
                        role = c.optCleanString("role")
                    )
                )
            }
        }

        val relationsList = mutableListOf<AnimeRelation>()
        val relsArr = json.optJSONObject("relations")?.optJSONArray("edges")
        if (relsArr != null) {
            for (i in 0 until relsArr.length()) {
                val edge = relsArr.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val tObj = node.optJSONObject("title") ?: JSONObject()
                val cObj = node.optJSONObject("coverImage") ?: JSONObject()
                val relTitle = tObj.optCleanString("english")
                    .ifBlank { tObj.optCleanString("userPreferred") }
                    .ifBlank { tObj.optCleanString("romaji") }
                relationsList.add(
                    AnimeRelation(
                        relationType = edge.optCleanString("relationType", "RELATED"),
                        id = node.optInt("id"),
                        title = relTitle,
                        format = node.optCleanString("format"),
                        status = node.optCleanString("status"),
                        coverUrl = cObj.optCleanString("large").ifBlank { cObj.optCleanString("medium") }
                    )
                )
            }
        }

        val recsList = mutableListOf<AnimeMedia>()
        val recsArr = json.optJSONObject("recommendations")?.optJSONArray("nodes")
        if (recsArr != null) {
            for (i in 0 until recsArr.length()) {
                val r = recsArr.optJSONObject(i) ?: continue
                val mediaRec = r.optJSONObject("mediaRecommendation") ?: continue
                recsList.add(parseSingleAnimeMedia(mediaRec))
            }
        }

        val rawDesc = json.optCleanString("description")
            .replace(Regex("<[^>]*>|&[^;]+;"), " ")
            .trim()

        val parsedEps = json.optInt("episodes", 0)
        val computedTotalEps = when {
            parsedEps > 0 -> parsedEps
            nextAiring != null && nextAiring.episode > 1 -> nextAiring.episode - 1
            json.optCleanString("format").equals("MOVIE", ignoreCase = true) -> 1
            else -> 0
        }

        return AnimeMedia(
            id = json.optInt("id"),
            idMal = if (json.has("idMal") && !json.isNull("idMal")) json.optInt("idMal") else null,
            titleRomaji = title.optCleanString("romaji"),
            titleEnglish = title.optCleanString("english"),
            titleNative = title.optCleanString("native"),
            titleUserPreferred = title.optCleanString("userPreferred"),
            coverImageExtraLarge = cover.optCleanString("extraLarge"),
            coverImageLarge = cover.optCleanString("large"),
            coverImageMedium = cover.optCleanString("medium"),
            coverColor = cover.optCleanStringOrNull("color"),
            bannerImage = json.optCleanString("bannerImage"),
            format = json.optCleanString("format", "TV"),
            status = json.optCleanString("status", "FINISHED"),
            totalEpisodes = computedTotalEps,
            durationMinutes = json.optInt("duration", 24),
            genres = genresList,
            averageScore = json.optInt("averageScore", 0),
            meanScore = json.optInt("meanScore", 0),
            popularity = json.optInt("popularity", 0),
            favourites = json.optInt("favourites", 0),
            season = json.optCleanString("season"),
            seasonYear = json.optInt("seasonYear", 0),
            description = rawDesc,
            studioName = studioName,
            trailerUrl = trailerUrl,
            trailerSite = trailerSite,
            nextAiring = nextAiring,
            characters = charactersList,
            relations = relationsList,
            recommendations = recsList,
            isAdult = json.optBoolean("isAdult", false)
        )
    }
}
