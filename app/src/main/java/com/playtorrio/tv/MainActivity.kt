package com.playtorrio.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playtorrio.tv.data.AppPreferences
import com.playtorrio.tv.data.torrent.TorrServerService
import com.playtorrio.tv.ui.screens.DetailScreen
import com.playtorrio.tv.ui.screens.HomeScreen
import com.playtorrio.tv.ui.screens.PersonScreen
import com.playtorrio.tv.ui.screens.SettingsScreen
import com.playtorrio.tv.ui.screens.SplashScreen
import com.playtorrio.tv.ui.screens.StudioScreen
import com.playtorrio.tv.ui.screens.profile.ProfileSelectScreen
import com.playtorrio.tv.ui.screens.detail.StremioDetailScreen
import com.playtorrio.tv.ui.screens.music.MusicScreen
import com.playtorrio.tv.ui.screens.audiobook.AudiobookScreen
import com.playtorrio.tv.ui.screens.search.SearchScreen
import com.playtorrio.tv.ui.screens.stremio.StremioCatalogScreen
import com.playtorrio.tv.ui.theme.PlayTorrioTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide system navigation bar to prevent pill flicker on gesture-nav devices
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Pre-start TorrServer + fetch trackers in background
        com.playtorrio.tv.data.profile.ProfileManager.init(this)
        AppPreferences.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            TorrServerService.warmup(this@MainActivity)
        }

        setContent {
            PlayTorrioTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                    composable("splash") {
                        SplashScreen(onFinished = {
                            navController.navigate("profile_select") {
                                popUpTo("splash") { inclusive = true }
                            }
                        })
                    }
                    composable("profile_select") {
                        ProfileSelectScreen(navController = navController)
                    }
                    composable("home") {
                        HomeScreen(navController = navController)
                    }
                    composable("search") {
                        SearchScreen(navController = navController)
                    }
                    composable("settings") {
                        SettingsScreen(navController = navController)
                    }
                    composable("music") {
                        MusicScreen(navController = navController)
                    }
                    composable("audiobooks") {
                        AudiobookScreen(navController = navController)
                    }
                    composable(
                        "detail/{mediaId}/{isMovie}",
                        arguments = listOf(
                            navArgument("mediaId") { type = NavType.IntType },
                            navArgument("isMovie") { type = NavType.BoolType }
                        )
                    ) { backStackEntry ->
                        DetailScreen(
                            mediaId = backStackEntry.arguments!!.getInt("mediaId"),
                            isMovie = backStackEntry.arguments!!.getBoolean("isMovie"),
                            navController = navController
                        )
                    }
                    composable(
                        "person/{personId}",
                        arguments = listOf(
                            navArgument("personId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        PersonScreen(
                            personId = backStackEntry.arguments!!.getInt("personId"),
                            navController = navController
                        )
                    }
                    composable(
                        "studio/{companyId}",
                        arguments = listOf(
                            navArgument("companyId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        StudioScreen(
                            companyId = backStackEntry.arguments!!.getInt("companyId"),
                            isNetwork = false,
                            navController = navController
                        )
                    }
                    composable(
                        "network/{networkId}",
                        arguments = listOf(
                            navArgument("networkId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        StudioScreen(
                            companyId = backStackEntry.arguments!!.getInt("networkId"),
                            isNetwork = true,
                            navController = navController
                        )
                    }
                    composable(
                        "stremio_detail/{addonId}/{type}/{stremioId}",
                        arguments = listOf(
                            navArgument("addonId") { type = NavType.StringType },
                            navArgument("type") { type = NavType.StringType },
                            navArgument("stremioId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        StremioDetailScreen(
                            addonId = backStackEntry.arguments!!.getString("addonId")!!
                                .let { android.net.Uri.decode(it) },
                            type = backStackEntry.arguments!!.getString("type")!!
                                .let { android.net.Uri.decode(it) },
                            stremioId = backStackEntry.arguments!!.getString("stremioId")!!
                                .let { android.net.Uri.decode(it) },
                            navController = navController
                        )
                    }
                    composable(
                        "stremio_catalog/{addonId}/{type}/{catalogId}/{title}",
                        arguments = listOf(
                            navArgument("addonId") { type = NavType.StringType },
                            navArgument("type") { type = NavType.StringType },
                            navArgument("catalogId") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        StremioCatalogScreen(
                            addonId = backStackEntry.arguments!!.getString("addonId")!!
                                .let { android.net.Uri.decode(it) },
                            type = backStackEntry.arguments!!.getString("type")!!
                                .let { android.net.Uri.decode(it) },
                            catalogId = backStackEntry.arguments!!.getString("catalogId")!!
                                .let { android.net.Uri.decode(it) },
                            title = backStackEntry.arguments!!.getString("title")!!
                                .let { android.net.Uri.decode(it) },
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}
