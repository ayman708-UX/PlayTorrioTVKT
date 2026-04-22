package com.playtorrio.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.playtorrio.tv.ui.screens.player.PlayerScreen
import com.playtorrio.tv.ui.screens.player.PlayerViewModel
import com.playtorrio.tv.ui.theme.PlayTorrioTheme

class PlayerActivity : ComponentActivity() {

    private lateinit var viewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProvider(this)[PlayerViewModel::class.java]

        val title = intent.getStringExtra("title") ?: ""
        val logoUrl = intent.getStringExtra("logoUrl")
        val backdropUrl = intent.getStringExtra("backdropUrl")
        val posterUrl = intent.getStringExtra("posterUrl")
        val year = intent.getStringExtra("year")
        val rating = intent.getStringExtra("rating")
        val overview = intent.getStringExtra("overview")
        val isMovie = intent.getBooleanExtra("isMovie", true)
        val seasonNumber = intent.getIntExtra("seasonNumber", -1).takeIf { it > 0 }
        val episodeNumber = intent.getIntExtra("episodeNumber", -1).takeIf { it > 0 }
        val episodeTitle = intent.getStringExtra("episodeTitle")
        val tmdbId = intent.getIntExtra("tmdbId", -1)
        val imdbId = intent.getStringExtra("imdbId")
        // Continue-watching context
        val addonId = intent.getStringExtra("addonId")
        val stremioType = intent.getStringExtra("stremioType")
        val stremioId = intent.getStringExtra("stremioId")
        val streamPickKey = intent.getStringExtra("streamPickKey")
        val streamPickName = intent.getStringExtra("streamPickName")
        val resumePositionMs = intent.getLongExtra("resumePositionMs", -1L).takeIf { it > 0 }
        val fileIdx = intent.getIntExtra("fileIdx", -1).takeIf { it >= 0 }

        viewModel.setResumeContext(
            posterUrl = posterUrl,
            imdbId = imdbId,
            addonId = addonId,
            stremioType = stremioType,
            stremioId = stremioId,
            streamPickKey = streamPickKey,
            streamPickName = streamPickName,
            resumePositionMs = resumePositionMs,
            fileIdx = fileIdx,
        )

        val streamUrl = intent.getStringExtra("streamUrl")
        if (streamUrl != null) {
            // Streaming mode — direct HLS/MP4 URL from one of the online sources
            val referer = intent.getStringExtra("streamReferer") ?: ""
            val sourceIndex = intent.getIntExtra("sourceIndex", 1)
            viewModel.initStreamingPlayer(
                streamUrl = streamUrl,
                referer = referer,
                sourceIndex = sourceIndex,
                title = title,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                year = year,
                rating = rating,
                overview = overview,
                isMovie = isMovie,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                tmdbId = tmdbId,
                isIptv = intent.getBooleanExtra("isIptv", false),
            )
        } else {
            // Torrent mode — magnet URI via TorrServer
            val magnetUri = intent.getStringExtra("magnetUri") ?: run { finish(); return }
            viewModel.initPlayer(
                magnetUri = magnetUri,
                title = title,
                logoUrl = logoUrl,
                backdropUrl = backdropUrl,
                year = year,
                rating = rating,
                overview = overview,
                isMovie = isMovie,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                tmdbId = tmdbId
            )
        }

        setContent {
            PlayTorrioTheme {
                PlayerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.player?.pause()
        // Flush latest watch-progress synchronously so the home screen sees
        // it as soon as it resumes.
        viewModel.flushProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
