package com.asides.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asides.app.AsidesApp
import com.asides.app.data.BackupException
import com.asides.app.data.Repository
import com.asides.app.data.Opml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What an aside is and how to make one; shown on the welcome card and in Settings. */
private const val EXPLAINER =
    "While an episode plays, triple-tap your earbuds' button, say what's on your mind, then tap once to " +
        "save it. That's an aside: it's pinned to that moment in the episode, transcribed, and waiting in " +
        "the Asides tab. It works with the screen off. On a wired headset, triple-press its button."

/** The note on the home page for a first-time user. */
@Composable
fun IntroCard(onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Talk back to your podcasts", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(EXPLAINER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Got it") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun SettingsScreen(repo: Repository, player: PlayerConnection) {
    val context = LocalContext.current
    val app = context.applicationContext as AsidesApp
    val scope = rememberCoroutineScope()
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var busy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; backupMessage = null
            backupMessage = try {
                val summary = context.contentResolver.openOutputStream(uri)?.use { repo.exportBackup(it, AppSettings.snapshot(), version) }
                    ?: error("couldn't open that location")
                "Saved ${plural(summary.asides, "aside")} and ${plural(summary.podcasts, "podcast")} to the backup file."
            } catch (e: Exception) {
                "Couldn't save the backup: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; backupMessage = null
            backupMessage = try {
                val summary = context.contentResolver.openInputStream(uri)?.use { repo.importBackup(it) }
                    ?: error("couldn't open that file")
                AppSettings.apply(summary.settings)
                player.reloadSettings()
                // Restored asides that never got a transcript are transcribed now.
                scope.launch { repo.transcribePending(app.transcriber) }
                buildString {
                    append(if (summary.asidesRestored > 0) "Restored ${plural(summary.asidesRestored, "aside")}" else "No new asides to restore")
                    if (summary.podcastsAdded > 0) append(", and added ${plural(summary.podcastsAdded, "podcast")}")
                    append(".")
                    if (summary.asidesAlreadyHere > 0) append(" ${summary.asidesAlreadyHere} already here.")
                    if (summary.asidesWithoutAudio > 0) append(" ${plural(summary.asidesWithoutAudio, "aside")} had no recording and was skipped.")
                    append(" Your settings were restored too.")
                }
            } catch (e: BackupException) {
                e.message ?: "That file isn't an Asides backup."
            } catch (e: Exception) {
                "Couldn't restore: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    var opmlMessage by remember { mutableStateOf<String?>(null) }
    var opmlBusy by remember { mutableStateOf(false) }
    val opmlExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            opmlBusy = true; opmlMessage = null
            opmlMessage = try {
                val xml = repo.exportOpml()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) } ?: error("couldn't open that location")
                }
                "Saved your subscriptions."
            } catch (e: Exception) {
                "Couldn't save the list: ${e.message ?: e.javaClass.simpleName}"
            }
            opmlBusy = false
        }
    }
    val opmlImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            opmlBusy = true; opmlMessage = null
            opmlMessage = try {
                val feeds = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { Opml.parse(it) } ?: error("couldn't open that file")
                }
                if (feeds.isEmpty()) "No podcasts found in that file." else {
                    val r = repo.importOpml(feeds) { done, total -> opmlMessage = "Following $done of $total…" }
                    buildString {
                        append(if (r.added > 0) "Now following ${plural(r.added, "new podcast")}." else "Nothing new to follow.")
                        if (r.alreadyFollowed > 0) append(" ${r.alreadyFollowed} already followed.")
                        if (r.failed > 0) append(" ${r.failed} couldn't be reached; try again later.")
                    }
                }
            } catch (e: IllegalArgumentException) {
                e.message ?: "That file isn't an OPML subscription list."
            } catch (e: Exception) {
                "Couldn't import: ${e.message ?: e.javaClass.simpleName}"
            }
            opmlBusy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("Text size in episode lists") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListDensity.entries.forEach { density ->
                    FilterChip(
                        selected = AppSettings.listDensity == density,
                        onClick = { AppSettings.chooseListDensity(density) },
                        label = { Text(density.label) },
                    )
                }
            }
            // A live sample, so the choice can be judged without leaving the screen.
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = EpisodeListStyle.rowPadding)) {
                    Text("Day 27: The Birth of Moses (2023)", style = EpisodeListStyle.title)
                    Text("Jan 26, 2023", style = EpisodeListStyle.meta)
                }
            }
        }

        Section("Playback") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Play the next episode automatically", Modifier.weight(1f))
                Switch(checked = player.autoplay, onCheckedChange = { player.toggleAutoplay() })
            }
        }

        Section("Downloads") {
            val downloads by com.asides.app.service.Downloads.state.collectAsState()
            val scope = rememberCoroutineScope()
            var size by remember { mutableStateOf(0L) }
            var confirm by remember { mutableStateOf(false) }
            LaunchedEffect(downloads.done) {
                size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.asides.app.service.Downloads.sizeBytes() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Delete a download once the episode is played", Modifier.weight(1f))
                Switch(checked = AppSettings.autoDeleteDownloads, onCheckedChange = { AppSettings.chooseAutoDeleteDownloads(it) })
            }
            Text(
                "${downloads.done.size} downloaded, using %.0f MB. Marking an episode played (or finishing it) removes its download when this is on.".format(size / 1_000_000.0),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { confirm = true }, enabled = downloads.done.isNotEmpty() || downloads.progress.isNotEmpty()) {
                Text("Delete all downloads")
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Delete all downloads?") },
                    text = { Text("Downloaded episodes are removed from this phone. They can still be streamed, and your asides and places are kept.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { com.asides.app.service.Downloads.removeAll() }
                        }) { Text("Delete all") }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
                )
            }
        }

        Section("Bluetooth") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { granted -> AppSettings.chooseResumeOnBluetooth(granted) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Resume when connecting to a car", Modifier.weight(1f))
                Switch(checked = AppSettings.resumeOnBluetooth, onCheckedChange = { on ->
                    val needsPermission = on && android.os.Build.VERSION.SDK_INT >= 31 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (needsPermission) ask.launch(android.Manifest.permission.BLUETOOTH_CONNECT) else AppSettings.chooseResumeOnBluetooth(on)
                })
            }
            Text(
                "When your car connects over Bluetooth (headsets, earbuds and speakers are ignored), the episode you " +
                    "were last listening to picks up where you left off. Android asks once for permission to see " +
                    "Bluetooth connections. It relies on the car identifying itself as car audio, and it only works if Android lets Asides start " +
                    "playing from the background.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Recordings") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Save audio files", Modifier.weight(1f))
                Switch(checked = AppSettings.saveAudio, onCheckedChange = { AppSettings.chooseSaveAudio(it) })
            }
            Text(
                "Off: each aside's recording is deleted once it has been transcribed, and only the text is kept. " +
                    "On: recordings are kept so you can play them back, but they take up a lot of space (about 2 MB " +
                    "for every minute you talk) and make backups much larger. If a transcript can't be made, the " +
                    "recording is always kept. Turning this on doesn't bring back recordings that were already deleted.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Back up your asides") {
            Text(
                "Save your asides (transcripts, plus any recordings you've kept), your subscriptions and your played marks " +
                    "to one file you can keep anywhere: Drive, Downloads, an email to yourself. Restoring it on this " +
                    "or another phone only adds what's missing, so it's safe to do more than once.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    export.launch("asides-backup-$stamp.zip")
                }) { Text("Back up…") }
                OutlinedButton(enabled = !busy, onClick = {
                    import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }) { Text("Restore…") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            backupMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Section("Subscriptions (OPML)") {
            Text(
                "OPML is the subscription list other podcast apps read and write. Export yours to move to another app, " +
                    "or import a list from one to follow all its podcasts here. Importing only adds podcasts you " +
                    "don't follow yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !opmlBusy, onClick = { opmlExport.launch("asides-subscriptions.opml") }) { Text("Export…") }
                OutlinedButton(enabled = !opmlBusy, onClick = {
                    opmlImport.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "application/octet-stream", "text/plain"))
                }) { Text("Import…") }
            }
            if (opmlBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            opmlMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Section("How asides work") {
            Text(EXPLAINER, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { AppSettings.markIntroSeen(false) }) { Text("Show the welcome note again") }
        }

        HorizontalDivider()

        Section("About") {
            Text("Asides $version", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Free and open source under the MIT license. No accounts, no analytics: everything you make stays " +
                    "on your phone. Transcripts are made by your phone's speech recognizer, which may send the " +
                    "audio of an aside to Google to turn it into text.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mtsprout/asides-podcast-player")))
            }) { Text("Source code on GitHub") }
        }
    }
}
