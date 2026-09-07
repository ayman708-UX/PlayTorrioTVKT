package com.playtorrio.tv.core.anime.scraper

import com.playtorrio.tv.core.anime.extractors.*
import com.playtorrio.tv.core.anime.model.AnimeMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AnimeScraperTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Test
    fun cleanAnimeTitleRemovesNoise() {
        assertEquals("Solo Leveling", AnimeScraperService.cleanAnimeTitle("Solo Leveling (TV)"))
        assertEquals("Solo Leveling Season 2", AnimeScraperService.cleanAnimeTitle("Solo Leveling Season 2 - Episode 1 [English Sub]"))
        assertEquals("Demon Slayer", AnimeScraperService.cleanAnimeTitle("Demon Slayer • Ep 1"))
    }

    @Test
    fun aniNekoExtractsStreams() = runBlocking {
        val extractor = AniNekoExtractor(client)
        val results = extractor.extract(
            titleCandidates = listOf("Solo Leveling", "Ore dake Level Up na Ken"),
            episodeNumber = 1,
            category = "sub"
        )
        println("AniNeko results: " + results.size)
        for (r in results) {
            println(" -> " + r.serverName + " | " + r.category + " | " + r.streamUrl)
        }
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().streamUrl.startsWith("http"))
    }

    @Test
    fun aniPmExtractsStreams() = runBlocking {
        val extractor = AniPMExtractor(client)
        val results = extractor.extract(
            anilistId = 151807,
            episodeNumber = 1,
            category = "sub",
            title = "Solo Leveling"
        )
        println("AniPM results: " + results.size)
        for (r in results) {
            println(" -> " + r.serverName + " | " + r.category + " | " + r.streamUrl)
        }
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun animeScraperServiceCollectsStreams() = runBlocking {
        val service = AnimeScraperService()
        val dummyAnime = AnimeMedia(
            id = 151807,
            titleEnglish = "Solo Leveling",
            titleRomaji = "Ore dake Level Up na Ken",
            titleUserPreferred = "Solo Leveling",
            titleNative = "나 혼자만 레벨업",
            format = "TV",
            totalEpisodes = 12,
            averageScore = 85,
            genres = listOf("Action", "Adventure", "Fantasy"),
            isAdult = false,
            description = ""
        )

        val streams = mutableListOf<com.playtorrio.tv.core.anime.model.AnimeStreamResult>()
        val job = launch {
            service.scrapeStreams(dummyAnime, 1, "sub").collect { s ->
                println("Scraped Stream: [" + s.serverName + "] " + s.streamUrl.take(80) + "...")
                streams.add(s)
            }
        }
        delay(15000)
        job.cancel()

        println("Total streams scraped: " + streams.size)
        assertTrue(streams.isNotEmpty())
    }
}
