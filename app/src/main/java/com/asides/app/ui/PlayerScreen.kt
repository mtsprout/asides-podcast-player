package com.asides.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asides.app.data.Episode
import com.asides.app.data.Podcast
import com.asides.app.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One episode: the player, its show notes, and the voice annotations made on it. */
@Composable
fun EpisodeScreen(
    repo: Repository,
    episodeId: Long,
    player: PlayerConnection,
    onOpenTranscript: (Long) -> Unit,
    onSwitchEpisode: (Long) -> Unit,
) {
    val episode by repo.episode(episodeId).collectAsState(null)
    val annotations by repo.annotationsFor(episodeId).collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val podcastId = episode?.podcastId
    // Opening an episode clears its "new" marker.
    LaunchedEffect(episode?.isNew) { if (episode?.isNew == true) repo.markSeen(episodeId) }
    val podcast by remember(podcastId) { podcastId?.let { repo.podcast(it) } ?: flowOf(null) }.collectAsState(null)

    // When playback moves on to the next episode by itself, this screen follows it.
    var wasPlayingHere by remember(episodeId) { mutableStateOf(false) }
    LaunchedEffect(player.currentEpisodeId) {
        if (player.currentEpisodeId == episodeId) wasPlayingHere = true
        else if (wasPlayingHere && player.currentEpisodeId != -1L) onSwitchEpisode(player.currentEpisodeId)
    }

    // What will play next, in the order the podcast's page lists episodes.
    val context = LocalContext.current
    val newestFirst = remember { context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE).getBoolean("newestFirst", false) }
    val nextEpisode by produceState<Episode?>(null, episode?.id, episode?.played) {
        value = episode?.let { repo.nextEpisode(it.id, newestFirst) }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Playing") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Details") })
            // Keep the label on one line and show a count only when there is one.
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                Text(
                    "Asides" + if (annotations.isEmpty()) "" else " ${annotations.size}",
                    maxLines = 1, softWrap = false,
                )
            })
        }
        val e = episode ?: return@Column
        when (tab) {
            0 -> PlayingTab(
                e, podcast, nextEpisode, player,
                onNext = {
                    nextEpisode?.let { next ->
                        scope.launch {
                            // Keep our place in this episode before moving on.
                            if (player.currentEpisodeId == e.id) repo.savePosition(e.id, player.positionMs, player.durationMs)
                            player.play(next, podcast)
                        }
                    }
                },
                onTogglePlayed = { scope.launch { repo.setPlayed(e.id, !e.played) } },
            )
            1 -> DetailsTab(repo, e, podcast)
            else -> AnnotationList(
                annotations, player,
                onOpenTranscript = onOpenTranscript,
                onDelete = { id -> scope.launch { repo.deleteAnnotation(id) } },
                onJump = { a ->
                    player.play(e, podcast)
                    player.seekTo(a.positionMs)
                    tab = 0
                },
            )
        }
    }
}

@Composable
private fun PlayingTab(
    e: Episode,
    podcast: Podcast?,
    nextEpisode: Episode?,
    player: PlayerConnection,
    onNext: () -> Unit,
    onTogglePlayed: () -> Unit,
) {
    val context = LocalContext.current
    val active = player.currentEpisodeId == e.id
    LaunchedEffect(active) { while (active) { player.poll(); delay(500) } }
    LaunchedEffect(Unit) { player.poll() }
    val duration = if (active && player.durationMs > 0) player.durationMs else e.durationMs
    val position = if (active) player.positionMs else e.positionMs
    var showSpeeds by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(podcast?.artworkUrl, 260.dp)
        Text(e.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            podcast?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { if (active && duration > 0) player.seekTo((it * duration).toLong()) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelLarge)
            Text("-" + formatTime((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            // Small double arrows at the two ends: back to the start of this episode, and on to the next one.
            IconButton(modifier = Modifier.size(44.dp), enabled = active, onClick = { player.seekTo(0) }) {
                Icon(Icons.Default.KeyboardDoubleArrowLeft, "Back to the beginning", Modifier.size(28.dp))
            }
            IconButton(modifier = Modifier.size(64.dp), enabled = active, onClick = { player.skip(-10_000) }) {
                Icon(Icons.Default.Replay10, "Back 10 seconds", Modifier.size(40.dp))
            }
            FilledIconButton(
                modifier = Modifier.size(76.dp),
                onClick = { if (active) player.togglePlay() else player.play(e, podcast) },
            ) {
                Icon(
                    if (active && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (active && player.isPlaying) "Pause" else "Play",
                    Modifier.size(44.dp),
                )
            }
            IconButton(modifier = Modifier.size(64.dp), enabled = active, onClick = { player.skip(30_000) }) {
                Icon(Icons.Default.Forward30, "Forward 30 seconds", Modifier.size(40.dp))
            }
            IconButton(modifier = Modifier.size(44.dp), enabled = nextEpisode != null, onClick = onNext) {
                Icon(Icons.Default.KeyboardDoubleArrowRight, "Next episode", Modifier.size(28.dp))
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionItem(Icons.Default.Speed, "%.1f×".format(player.speed)) { showSpeeds = true }
                ActionItem(Icons.Default.Bedtime, player.sleepLabel ?: "Sleep") { showSleep = true }
                ActionItem(Icons.Default.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, e.title)
                        putExtra(Intent.EXTRA_TEXT, "${e.title}\n${e.audioUrl}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                Box {
                    ActionItem(Icons.Default.MoreVert, "More") { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (e.played) "Mark as unplayed" else "Mark as played") },
                            leadingIcon = {
                                Icon(if (e.played) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
                            },
                            onClick = { showMenu = false; onTogglePlayed() },
                        )
                        DropdownMenuItem(
                            text = { Text("Play next episode automatically") },
                            leadingIcon = {
                                Icon(if (player.autoplay) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
                            },
                            onClick = { showMenu = false; player.toggleAutoplay() },
                        )
                    }
                }
            }
        }
        if (player.autoplay) nextEpisode?.let {
            Text(
                "Up next: ${it.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Headset: triple-press play/pause to record an aside, then press once to save it. " +
                "Works with the screen off.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSpeeds) SpeedPicker(player.speed, onPick = { player.changeSpeed(it); showSpeeds = false }, onDismiss = { showSpeeds = false })
    if (showSleep) SleepPicker(
        active = player.sleepLabel != null,
        onPick = { player.setSleepTimer(it); showSleep = false },
        onDismiss = { showSleep = false },
    )
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** The episode's show notes, read from the podcast's feed. */
@Composable
private fun DetailsTab(repo: Repository, e: Episode, podcast: Podcast?) {
    var notes by remember(e.id) { mutableStateOf<String?>(null) }
    var failed by remember(e.id) { mutableStateOf(false) }
    val feedUrl = podcast?.feedUrl
    LaunchedEffect(e.id, feedUrl) {
        if (feedUrl == null) return@LaunchedEffect
        failed = false
        try { notes = repo.episodeDescription(feedUrl, e.audioUrl).orEmpty() } catch (_: Exception) { failed = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        Text(
            listOfNotNull(podcast?.title, formatDate(e.pubDate), e.durationMs.takeIf { it > 0 }?.let(::formatTime))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        when {
            failed -> Text("Couldn't load the show notes. They need a connection.")
            notes == null -> Text("Loading…")
            notes.isNullOrBlank() -> Text("This episode has no description.")
            else -> Text(notes.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A scrolling list of speeds from 0.5× to 2.0×; the current one is selected and scrolled into view. */
@Composable
internal fun SpeedPicker(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    val options = remember { (5..20).map { it / 10f } }
    val selectedIndex = options.indexOfFirst { abs(it - current) < 0.01f }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            LazyColumn(state = state, modifier = Modifier.heightIn(max = 320.dp)) {
                items(options) { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = abs(v - current) < 0.01f, onClick = null)
                        Text("%.1f×".format(v) + if (v == 1f) "  (normal)" else "", Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sleep timer choices; the service pauses playback when the time is up. */
@Composable
private fun SleepPicker(active: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(5 to "5 minutes", 10 to "10 minutes", 15 to "15 minutes", 30 to "30 minutes",
        45 to "45 minutes", 60 to "1 hour", -1 to "End of episode")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (minutes, label) ->
                    Text(
                        label,
                        Modifier.fillMaxWidth().clickable { onPick(minutes) }.padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (active) Text(
                    "Turn off", Modifier.fillMaxWidth().clickable { onPick(0) }.padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
