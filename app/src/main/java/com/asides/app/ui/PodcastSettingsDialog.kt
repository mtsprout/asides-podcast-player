package com.asides.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asides.app.data.PodcastSettingsStore
import com.asides.app.data.StartPosition

/** Settings that apply to one podcast only; each change is saved at once. */
@Composable
fun PodcastSettingsDialog(store: PodcastSettingsStore, podcastId: Long, title: String, onDismiss: () -> Unit) {
    var settings by remember { mutableStateOf(store.get(podcastId)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Starts from the speed you normally use.
    val normalSpeed = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE).getFloat("speed", 1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.ifEmpty { "Podcast settings" }, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow(
                    "Own playback speed",
                    "Plays this podcast at its own speed. Off uses your normal speed.",
                    checked = settings.speed != null,
                    onChecked = {
                        val v = if (it) normalSpeed else null
                        store.setSpeed(podcastId, v)
                        settings = settings.copy(speed = v)
                    },
                )
                settings.speed?.let { speed ->
                    Stepper(
                        value = "%.1f×".format(speed),
                        onMinus = { change(store, podcastId, speed - 0.1f) { settings = settings.copy(speed = it) } },
                        onPlus = { change(store, podcastId, speed + 0.1f) { settings = settings.copy(speed = it) } },
                        canMinus = speed > 0.5f, canPlus = speed < 2.0f,
                    )
                }
                HorizontalDivider()
                Text("Skip intro", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Starts each new episode this far in. An episode you've already started carries on where you left it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Stepper(
                    value = if (settings.skipIntroSec == 0) "Off" else "${settings.skipIntroSec} s",
                    onMinus = {
                        val s = (settings.skipIntroSec - 5).coerceAtLeast(0)
                        store.setSkipIntro(podcastId, s); settings = settings.copy(skipIntroSec = s)
                    },
                    onPlus = {
                        val s = (settings.skipIntroSec + 5).coerceAtMost(StartPosition.MAX_SKIP_SEC)
                        store.setSkipIntro(podcastId, s); settings = settings.copy(skipIntroSec = s)
                    },
                    canMinus = settings.skipIntroSec > 0, canPlus = settings.skipIntroSec < StartPosition.MAX_SKIP_SEC,
                )
                HorizontalDivider()
                SettingRow(
                    "Download new episodes on Wi-Fi",
                    "When a refresh finds new episodes, the newest three are saved to the phone. Waits for Wi-Fi.",
                    checked = settings.autoDownload,
                    onChecked = { store.setAutoDownload(podcastId, it); settings = settings.copy(autoDownload = it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun change(store: PodcastSettingsStore, podcastId: Long, raw: Float, apply: (Float) -> Unit) {
    val v = Math.round(raw.coerceIn(0.5f, 2.0f) * 10) / 10f
    store.setSpeed(podcastId, v)
    apply(v)
}

@Composable
private fun SettingRow(label: String, hint: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = onMinus, enabled = canMinus) { Icon(Icons.Default.Remove, "Less", Modifier.size(24.dp)) }
        Text(value, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onPlus, enabled = canPlus) { Icon(Icons.Default.Add, "More", Modifier.size(24.dp)) }
    }
}
