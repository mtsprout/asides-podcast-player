package com.asides.app

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.asides.app.service.FeedRefreshWorker
import com.asides.app.service.Notifier
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.asides.app.ui.StartupSplash
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asides.app.ui.AllAnnotationsScreen
import com.asides.app.ui.AsidesTheme
import com.asides.app.ui.EpisodeScreen
import com.asides.app.ui.LibraryScreen
import com.asides.app.ui.PlayerConnection
import com.asides.app.ui.PodcastScreen
import com.asides.app.ui.PreviewScreen
import com.asides.app.ui.TranscriptScreen

class MainActivity : ComponentActivity() {
    private companion object {
        const val SYSTEM_SPLASH_MS = 800L
    }

    /** A screen to open, set by tapping a notification; consumed once the navigation graph is up. */
    private var pendingRoute by mutableStateOf<String?>(null)
    private var skipStartupRefresh = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(Notifier.EXTRA_ROUTE)
    }

    override fun onStart() {
        super.onStart()
        // Look for new episodes whenever the app comes to the front (podcasts checked in the last half
        // hour are skipped). The test hook below runs its own refresh instead.
        if (!skipStartupRefresh) {
            val repo = (application as AsidesApp).repo
            lifecycleScope.launch { repo.refreshAll() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val started = SystemClock.elapsedRealtime()
        // Keep the system splash up long enough for its animation to play out.
        splash.setKeepOnScreenCondition { SystemClock.elapsedRealtime() - started < SYSTEM_SPLASH_MS }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AsidesApp
        val repo = app.repo
        // Pick up any notes still waiting for a transcript (e.g. recorded while offline).
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val retry = if (debuggable) intent.getStringExtra("retranscribe") else null
        pendingRoute = intent.getStringExtra(Notifier.EXTRA_ROUTE)
        // Debug builds only: `--es refresh_test 2` forgets each podcast's newest 2 episodes, turns
        // notifications on, and runs the background refresh once, so the whole chain can be tried.
        val refreshTest = if (debuggable) intent.getStringExtra("refresh_test")?.toIntOrNull() else null
        // Debug builds only: `--es debug_subscribe url1,url2` subscribes to feeds without any tapping.
        val debugFeeds = if (debuggable) intent.getStringExtra("debug_subscribe")?.split(",") else null
        debugFeeds?.let { feeds -> lifecycleScope.launch { feeds.forEach { runCatching { repo.subscribe(it.trim()) } } } }
        skipStartupRefresh = refreshTest != null
        if (refreshTest != null) {
            lifecycleScope.launch {
                repo.simulateNewEpisodes(refreshTest)
                WorkManager.getInstance(this@MainActivity).enqueue(OneTimeWorkRequestBuilder<FeedRefreshWorker>().build())
            }
        }
        lifecycleScope.launch {
            // Debug builds only: `adb shell am start ... --es retranscribe 1` re-runs every note.
            if (retry != null) repo.clearTranscripts()
            repo.transcribePending(app.transcriber)
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        // Debug builds only: `--es debug_theme light|dark` previews a theme without changing the phone's.
        val forcedDark = if (debuggable) intent.getStringExtra("debug_theme")?.let { it == "dark" } else null
        setContent {
            AsidesTheme(dark = forcedDark ?: isSystemInDarkTheme()) {
                Box(Modifier.fillMaxSize()) {
                    App(repo, pendingRoute) { pendingRoute = null }
                    StartupSplash(version)
                }
            }
        }
    }
}

@Composable
private fun App(repo: com.asides.app.data.Repository, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val context = LocalContext.current
    val player = remember { PlayerConnection(context) }
    DisposableEffect(Unit) {
        player.connect()
        onDispose { player.release() }
    }

    // Voice annotations need the mic, and the foreground service needs the notification permission.
    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { launcher.launch(permissions) }

    val nav = rememberNavController()
    // A tapped notification names the screen it wants.
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            nav.navigate(it)
            onRouteHandled()
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "library" || route.startsWith("podcast") || route.startsWith("episode") || route.startsWith("preview"),
                    onClick = { nav.navigateTab("library") },
                    icon = { Icon(Icons.Default.Podcasts, null) },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = route == "annotations",
                    onClick = { nav.navigateTab("annotations") },
                    icon = { Icon(Icons.Default.Mic, null) },
                    label = { Text("Asides") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "library", modifier = Modifier.padding(padding)) {
            composable("library") {
                LibraryScreen(
                    repo,
                    onOpenPodcast = { nav.navigate("podcast/$it") },
                    onOpenPreview = {
                        nav.navigate("preview?feed=${Uri.encode(it.feedUrl)}&title=${Uri.encode(it.title)}&art=${Uri.encode(it.artworkUrl.orEmpty())}")
                    },
                )
            }
            composable(
                "preview?feed={feed}&title={title}&art={art}",
                arguments = listOf("feed", "title", "art").map {
                    navArgument(it) { type = NavType.StringType; defaultValue = "" }
                },
            ) { e ->
                val args = e.arguments!!
                PreviewScreen(
                    repo, args.getString("feed").orEmpty(), args.getString("title").orEmpty(),
                    args.getString("art")?.takeIf { it.isNotEmpty() },
                ) { nav.navigate("podcast/$it") }
            }
            composable("podcast/{id}") { e ->
                PodcastScreen(repo, e.arguments!!.getString("id")!!.toLong()) { nav.navigate("episode/$it") }
            }
            composable("episode/{id}") { e ->
                EpisodeScreen(repo, e.arguments!!.getString("id")!!.toLong(), player) { nav.navigate("transcript/$it") }
            }
            composable("transcript/{id}") { e ->
                TranscriptScreen(repo, e.arguments!!.getString("id")!!.toLong(), player)
            }
            composable("annotations") {
                AllAnnotationsScreen(repo, player, { nav.navigate("episode/$it") }, { nav.navigate("transcript/$it") })
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
