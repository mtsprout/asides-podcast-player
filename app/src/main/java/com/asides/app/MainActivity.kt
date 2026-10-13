package com.asides.app

import android.Manifest
import android.os.Build
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.asides.app.service.PlaybackService
import com.asides.app.service.PlayerCommands
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
import androidx.compose.foundation.layout.Column
import com.asides.app.ui.ChartScreen
import com.asides.app.ui.ReviewBar
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
import com.asides.app.ui.AppSettings
import com.asides.app.ui.AsidesTheme
import com.asides.app.ui.EpisodeScreen
import com.asides.app.ui.LibraryScreen
import com.asides.app.ui.PlayerConnection
import com.asides.app.ui.PodcastScreen
import com.asides.app.ui.SettingsScreen
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
        sendDebugPlayerCommand(intent)
    }

    /**
     * Debug builds only: `--es debug_command BACK_10|FORWARD_30|RESTART|NEXT_EPISODE` sends that command
     * to the playback session through an ordinary media controller, which is the path the lock-screen
     * card and notification use, so the buttons can be tried without touching the screen.
     */
    private fun sendDebugPlayerCommand(intent: Intent) {
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // `--ei debug_seek_from_end 15` jumps to 15 s before the end, to watch an episode finish by itself.
        val fromEnd = if (debuggable && intent.hasExtra("debug_seek_from_end")) intent.getIntExtra("debug_seek_from_end", 0) else null
        if (fromEnd != null) {
            val seekToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val seekFuture = MediaController.Builder(this, seekToken).buildAsync()
            seekFuture.addListener({
                val c = seekFuture.get()
                c.seekTo((c.duration - fromEnd * 1000L).coerceAtLeast(0))
                c.release()
            }, ContextCompat.getMainExecutor(this))
        }
        val name = intent.getStringExtra("debug_command")?.takeIf { debuggable } ?: return
        val action = when (name) {
            "BACK_10" -> PlayerCommands.BACK_10
            "FORWARD_30" -> PlayerCommands.FORWARD_30
            "RESTART" -> PlayerCommands.RESTART
            "NEXT_EPISODE" -> PlayerCommands.NEXT_EPISODE
            else -> return
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val controller = future.get()
            controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY)
                .addListener({ controller.release() }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
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
        sendDebugPlayerCommand(intent)
        // Debug builds only: `--es refresh_test 2` forgets each podcast's newest 2 episodes, turns
        // notifications on, and runs the background refresh once, so the whole chain can be tried.
        val refreshTest = if (debuggable) intent.getStringExtra("refresh_test")?.toIntOrNull() else null
        // Debug builds only: `--es debug_subscribe url1,url2` subscribes to feeds without any tapping.
        val debugFeeds = if (debuggable) intent.getStringExtra("debug_subscribe")?.split(",") else null
        debugFeeds?.let { feeds -> lifecycleScope.launch { feeds.forEach { runCatching { repo.subscribe(it.trim()) } } } }
        // Debug builds only: `--es debug_export name.zip` / `--es debug_import name.zip` run a backup or restore
        // against a file in the app's cache folder, so the logic can be tested without the file picker.
        val exportName = if (debuggable) intent.getStringExtra("debug_export") else null
        val importName = if (debuggable) intent.getStringExtra("debug_import") else null
        lifecycleScope.launch {
            exportName?.let {
                val file = java.io.File(cacheDir, it)
                file.outputStream().use { out -> repo.exportBackup(out, AppSettings.snapshot(), "debug") }
                android.util.Log.d("Asides", "debug_export: wrote ${file.length()} bytes to $it")
            }
            importName?.let {
                val summary = java.io.File(cacheDir, it).inputStream().use { input -> repo.importBackup(input) }
                android.util.Log.d("Asides", "debug_import: $summary")
            }
        }
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
                    selected = route == "library" || route.startsWith("podcast") || route.startsWith("episode") || route.startsWith("preview") || route.startsWith("chart") || route == "settings",
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
        Column(Modifier.padding(padding)) {
        ReviewBar(player)
        NavHost(nav, startDestination = "library", modifier = Modifier.weight(1f)) {
            composable("library") {
                LibraryScreen(
                    repo,
                    onOpenPodcast = { nav.navigate("podcast/$it") },
                    onOpenPreview = {
                        nav.navigate("preview?feed=${Uri.encode(it.feedUrl)}&title=${Uri.encode(it.title)}&art=${Uri.encode(it.artworkUrl.orEmpty())}")
                    },
                    onOpenSettings = { nav.navigate("settings") },
                    player = player,
                    onOpenEpisode = { nav.navigate("episode/$it") },
                    onOpenAsides = { nav.navigateTab("annotations") },
                    onOpenGenre = { nav.navigate("chart?genre=${it ?: 0}") },
                )
            }
            composable("chart?genre={genre}", arguments = listOf(navArgument("genre") { type = NavType.IntType; defaultValue = 0 })) { e ->
                ChartScreen(repo, e.arguments!!.getInt("genre").takeIf { it != 0 }) {
                    nav.navigate("preview?feed=${Uri.encode(it.feedUrl)}&title=${Uri.encode(it.title)}&art=${Uri.encode(it.artworkUrl.orEmpty())}")
                }
            }
            composable("settings") { SettingsScreen(repo, player) }
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
                EpisodeScreen(
                    repo, e.arguments!!.getString("id")!!.toLong(), player,
                    onOpenTranscript = { nav.navigate("transcript/$it") },
                    // Autoplay moved on: show the new episode in place of this one, so Back doesn't revisit it.
                    onSwitchEpisode = { nav.navigate("episode/$it") { popUpTo("episode/{id}") { inclusive = true } } },
                )
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
}

private fun androidx.navigation.NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
