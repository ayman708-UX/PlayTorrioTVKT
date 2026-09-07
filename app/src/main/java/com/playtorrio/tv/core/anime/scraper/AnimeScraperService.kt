package com.playtorrio.tv.core.anime.scraper

import android.util.Log
import com.playtorrio.tv.core.anime.extractors.*
import com.playtorrio.tv.core.anime.model.AnimeMedia
import com.playtorrio.tv.core.anime.model.AnimeStreamResult
import com.playtorrio.tv.core.scraper.HttpStreamLivenessValidator
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeScraperService @Inject constructor(
    private val livenessValidator: HttpStreamLivenessValidator
) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val megaPlay = MegaPlayExtractor(httpClient)
    private val reCloud = ReCloudExtractor(httpClient)
    private val tryEmbed = TryEmbedExtractor(httpClient)
    private val dulo = DuloExtractor(httpClient)
    private val luna = LunaExtractor(httpClient)
    private val aniDb = AniDbExtractor(httpClient)
    private val aniNeko = AniNekoExtractor(httpClient)
    private val oneTwoThree = OneTwoThreeAnimeExtractor(httpClient)
    private val aniHQ = AniHQExtractor(httpClient)
    private val aniPM = AniPMExtractor(httpClient)
    private val vidNest = VidNestExtractor(httpClient)
    private val watchHentai = WatchHentaiExtractor(httpClient)
    private val hentaini = HentainiExtractor(httpClient)

    companion object {
        private const val TAG = "AnimeScraperService"

        fun cleanAnimeTitle(raw: String): String {
            var s = raw
            s = s.replace(Regex("""\s*-\s*Episode\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""\s*•\s*Ep\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""\s*•\s*Episode\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""\(TV\)""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""\[.*?]"""), "")
            return s.trim()
        }
    }

    fun scrapeStreams(
        anime: AnimeMedia,
        episodeNumber: Int,
        categoryFilter: String? = null // "sub", "dub", or null for both
    ): Flow<AnimeStreamResult> = channelFlow {
        val seenUrls = Collections.synchronizedSet(mutableSetOf<String>())
        val rawTitles = listOf(
            anime.titleEnglish,
            anime.titleRomaji,
            anime.titleUserPreferred,
            anime.titleNative
        ).filter { it.isNotBlank() }

        val titleCandidates = buildList {
            for (t in rawTitles) {
                val cleaned = cleanAnimeTitle(t)
                if (cleaned.isNotBlank() && !contains(cleaned)) {
                    add(cleaned)
                }
                if (!contains(t)) {
                    add(t)
                }
            }
        }

        val cats = if (!categoryFilter.isNullOrBlank()) {
            listOf(categoryFilter.lowercase())
        } else {
            listOf("sub", "dub")
        }

        val isAdultContent = anime.isAdult ||
            anime.genres.any {
                it.contains("hentai", ignoreCase = true) ||
                it.contains("erotica", ignoreCase = true) ||
                it.contains("ecchi", ignoreCase = true)
            }

        coroutineScope {
            val jobs = mutableListOf<Job>()

            if (isAdultContent) {
                // 1. Adult Providers: WatchHentai
                jobs += launch(Dispatchers.IO) {
                    try {
                        val res = watchHentai.extract(titleCandidates, episodeNumber)
                        if (res != null) {
                            sendIfAlive(res, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "WatchHentai error: ${e.message}")
                    }
                }

                // 2. Adult Providers: Hentaini
                jobs += launch(Dispatchers.IO) {
                    try {
                        val res = hentaini.extract(titleCandidates, episodeNumber)
                        if (res != null) {
                            sendIfAlive(res, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Hentaini error: ${e.message}")
                    }
                }
            }

            // Standard anime providers (also run for non-adult, and fallback for adult if indexed)
            for (cat in cats) {
                // 1. MegaPlay
                jobs += launch(Dispatchers.IO) {
                    try {
                        val res = megaPlay.extract(anime.id, episodeNumber, cat)
                        if (res != null) {
                            sendIfAlive(res, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "MegaPlay error: ${e.message}")
                    }
                }

                // 2. ReCloud
                jobs += launch(Dispatchers.IO) {
                    try {
                        val res = reCloud.extract(anime.id, episodeNumber, cat)
                        if (res != null) {
                            sendIfAlive(res, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "ReCloud error: ${e.message}")
                    }
                }

                // 3. TryEmbed
                jobs += launch(Dispatchers.IO) {
                    try {
                        val results = tryEmbed.extractAll(anime.id, episodeNumber, cat)
                        for (r in results) {
                            sendIfAlive(r, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TryEmbed error: ${e.message}")
                    }
                }

                // 4. Luna
                jobs += launch(Dispatchers.IO) {
                    try {
                        val results = luna.extract(anime.id, episodeNumber, cat)
                        for (r in results) {
                            sendIfAlive(r, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Luna error: ${e.message}")
                    }
                }

                // 5. AniDB
                if (titleCandidates.isNotEmpty()) {
                    jobs += launch(Dispatchers.IO) {
                        try {
                            val res = aniDb.extract(titleCandidates, episodeNumber, cat)
                            if (res != null) {
                                sendIfAlive(res, seenUrls)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "AniDB error: ${e.message}")
                        }
                    }
                }

                // 6. AniNeko
                if (titleCandidates.isNotEmpty()) {
                    jobs += launch(Dispatchers.IO) {
                        try {
                            val results = aniNeko.extract(titleCandidates, episodeNumber, cat)
                            for (r in results) {
                                sendIfAlive(r, seenUrls)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "AniNeko error: ${e.message}")
                        }
                    }
                }

                // 7. AniHQ
                if (titleCandidates.isNotEmpty()) {
                    jobs += launch(Dispatchers.IO) {
                        try {
                            val results = aniHQ.extract(titleCandidates, episodeNumber, cat)
                            for (r in results) {
                                sendIfAlive(r, seenUrls)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "AniHQ error: ${e.message}")
                        }
                    }
                }

                // 8. AniPM
                jobs += launch(Dispatchers.IO) {
                    try {
                        val results = aniPM.extract(anime.id, episodeNumber, cat, anime.displayTitle)
                        for (r in results) {
                            sendIfAlive(r, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "AniPM error: ${e.message}")
                    }
                }

                // 9. VidNest
                jobs += launch(Dispatchers.IO) {
                    try {
                        val results = vidNest.extract(anime.id, episodeNumber, cat)
                        for (r in results) {
                            sendIfAlive(r, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "VidNest error: ${e.message}")
                    }
                }
            }

            // 10. Dulo (Sub direct)
            jobs += launch(Dispatchers.IO) {
                try {
                    val results = dulo.extract(anime.id, episodeNumber)
                    for (r in results) {
                        sendIfAlive(r, seenUrls)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dulo error: ${e.message}")
                }
            }

            // 11. 123Anime
            if (titleCandidates.isNotEmpty()) {
                jobs += launch(Dispatchers.IO) {
                    try {
                        val res = oneTwoThree.extract(titleCandidates, episodeNumber)
                        if (res != null) {
                            sendIfAlive(res, seenUrls)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "123Anime error: ${e.message}")
                    }
                }
            }

            // Wait for all extractors to complete
            jobs.joinAll()
        }
    }

    private suspend fun ProducerScope<AnimeStreamResult>.sendIfAlive(
        result: AnimeStreamResult,
        seenUrls: MutableSet<String>
    ) {
        if (!seenUrls.add(result.streamUrl)) return
        if (livenessValidator.isStreamAlive(result.streamUrl, result.headers)) {
            send(result)
        }
    }
}
