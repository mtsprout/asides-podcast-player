package com.asides.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asides.app.data.DistanceUnit
import com.asides.app.data.Repository
import com.asides.app.data.Walk
import com.asides.app.data.WalkTotals
import com.asides.app.data.Walks
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

internal fun formatSteps(steps: Long) = String.format(Locale.getDefault(), "%,d", steps)

/** "35 min", "1 h 20 min". */
internal fun formatMinutes(ms: Long): String {
    val min = ms / 60_000
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}

private fun distance(steps: Long): String =
    Walks.formatDistance(Walks.distanceMetres(steps, AppSettings.stride.cm), AppSettings.resolvedUnit)

@Composable
private fun timeOfDay(ms: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(ms))

private fun dayLabel(ms: Long, zone: ZoneId, now: Long): String {
    val day = Walks.date(ms, zone)
    val today = Walks.date(now, zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val locale = Locale.getDefault()
            val pattern = DateFormat.getBestDateTimePattern(locale, if (day.year == today.year) "EEEEMMMd" else "EEEMMMdyyyy")
            day.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}

/** The Walks tab: totals for today, this week, this month and all time, then every walk by day. */
@Composable
fun WalksScreen(repo: Repository, onOpenWalk: (Long) -> Unit) {
    val walks by repo.walks.collectAsState(initial = null)
    val artwork by repo.walkArtwork.collectAsState(initial = emptyMap())
    val list = walks ?: return
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val firstDay: DayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val summary = remember(list, now / 60_000) { Walks.summarize(list, now, zone, firstDay) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text("Walks", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item(key = "totals") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalCard("Today", summary.today, Modifier.weight(1f), highlight = true)
                    TotalCard("This week", summary.week, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalCard("This month", summary.month, Modifier.weight(1f))
                    TotalCard("All time", summary.all, Modifier.weight(1f))
                }
                StrideNote()
            }
        }
        if (list.isEmpty()) item(key = "empty") {
            Text(
                "No walks yet. Play an episode on headphones and head out; walks of 100 steps or more show up here.",
                Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        list.groupBy { Walks.date(it.id, zone) }.forEach { (day, dayWalks) ->
            item(key = "d-$day") {
                val total = dayWalks.sumOf { it.steps }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Text(dayLabel(dayWalks.first().id, zone, now), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (dayWalks.size > 1) {
                        Text("${formatSteps(total)} steps · ${distance(total)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(dayWalks, key = { "w-${it.id}" }) { w ->
                WalkRow(w, artwork[w.id].orEmpty()) { onOpenWalk(w.id) }
            }
        }
        item(key = "end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}

@Composable
private fun TotalCard(label: String, t: WalkTotals, modifier: Modifier, highlight: Boolean = false) {
    val colors = if (highlight) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else CardDefaults.cardColors()
    Card(modifier, colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatSteps(t.steps), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("steps", style = MaterialTheme.typography.labelSmall)
            Text("${distance(t.steps)} · ${formatMinutes(t.activeMs)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Distances are estimates; say what they rest on, and how to make them better. */
@Composable
private fun StrideNote() {
    val stride = AppSettings.stride
    val unit = AppSettings.resolvedUnit
    val strideText = if (unit == DistanceUnit.Mi) "%.1f in".format(stride.cm / 2.54) else "%.0f cm".format(stride.cm)
    val text = when (stride.source) {
        com.asides.app.data.StrideSource.Measured -> "Distances use your measured stride of $strideText."
        com.asides.app.data.StrideSource.Height -> "Distances use a stride of $strideText estimated from your height. " +
            "For a closer figure, open a walk of known length and set its real distance."
        com.asides.app.data.StrideSource.Average -> "Distances use an average stride of $strideText. Add your height in " +
            "Settings, or open a walk of known length and set its real distance."
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WalkRow(w: Walk, art: List<String?>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${formatSteps(w.steps)} steps · ${distance(w.steps)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${timeOfDay(w.id)} · ${formatMinutes(w.activeMs)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                art.take(3).forEach { Artwork(it, 32.dp) }
            }
        }
    }
}

/** One walk: its numbers, the episodes heard on it, setting its real distance, deleting it. */
@Composable
fun WalkScreen(repo: Repository, walkId: Long, onOpenEpisode: (Long) -> Unit, onDeleted: () -> Unit) {
    val walk by repo.walk(walkId).collectAsState(initial = null)
    val episodes by repo.walkEpisodes(walkId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var askDistance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val w = walk ?: return
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "head") {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dayLabel(w.id, zone, System.currentTimeMillis()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${timeOfDay(w.id)} – ${timeOfDay(w.endedAt)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item(key = "numbers") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Steps", formatSteps(w.steps), Modifier.weight(1f))
                Stat("Distance", distance(w.steps), Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Listening", formatMinutes(w.activeMs), Modifier.weight(1f))
                val perMin = if (w.activeMs >= 60_000) w.steps * 60_000 / w.activeMs else 0
                Stat("Pace", if (perMin > 0) "$perMin steps/min" else "–", Modifier.weight(1f))
            }
        }
        if (episodes.isNotEmpty()) item(key = "heard") {
            Text("Listened to", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(episodes, key = { "e-${it.episodeId}" }) { e ->
            Card(Modifier.fillMaxWidth().clickable { onOpenEpisode(e.episodeId) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(e.podcastArtworkUrl, 44.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(e.episodeTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(e.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text("${formatSteps(e.steps)} steps · ${distance(e.steps)} · ${formatMinutes(e.activeMs)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { askDistance = true }) { Text("Set this walk's real distance…") }
                Text(
                    "If you know how far this walk really was (a measured route, a track), enter it and every walk's " +
                        "distance is worked out from your real stride.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmDelete = true }) { Text("Delete this walk") }
            }
        }
    }

    if (askDistance) RealDistanceDialog(w.steps, onDismiss = { askDistance = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this walk?") },
            text = { Text("Its steps come off your totals. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.deleteWalk(w.id); onDeleted() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RealDistanceDialog(steps: Long, onDismiss: () -> Unit) {
    val unit = AppSettings.resolvedUnit
    val name = if (unit == DistanceUnit.Mi) "miles" else "kilometres"
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Real distance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How far was this walk of ${formatSteps(steps)} steps, in $name?")
                OutlinedTextField(
                    text, { text = it; error = null }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = text.trim().replace(',', '.').toDoubleOrNull()
                val stride = amount?.let { Walks.strideFrom(steps, it * Walks.metresPer(unit)) }
                if (stride == null) {
                    error = "That works out to an unlikely stride. Check the distance."
                } else {
                    AppSettings.chooseMeasuredStrideCm(stride)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
