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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

        Section("Back up your asides") {
            Text(
                "Save your asides, with their recordings and transcripts, your subscriptions and your played marks " +
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
