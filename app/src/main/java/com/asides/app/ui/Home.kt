package com.asides.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asides.app.data.Discover
import com.asides.app.data.Episode
import com.asides.app.data.Podcast
import com.asides.app.data.Repository
import com.asides.app.data.SearchResult
import com.asides.app.service.PlaybackService
import com.asides.app.service.Review
import kotlinx.coroutines.launch

/** "23 min left" / "1 h 5 min left" for the remaining part of an episode. */
internal fun minutesLeft(remainingMs: Long): String {
    val m = ((remainingMs + 59_999) / 60_000).coerceAtLeast(1)
    return if (m < 60) "$m min left" else "${m / 60} h ${m % 60} min left"
}

/** The episode you were last listening to, with a progress bar and a play button. Hidden if there is none. */
@Composable
internal fun ContinueCard(repo: Repository, player: PlayerConnection, onOpenEpisode: (Long) -> Unit) {
    val context = LocalContext.current
    val saved = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getLong(PlaybackService.LAST_EPISODE_PREF, -1L) }
    // While reviewing an aside the player holds another episode; "continue" stays the one you left.
    val id = if (Review.info == null && player.currentEpisodeId > 0) player.currentEpisodeId else saved
    if (id <= 0) return
    val episode by produceState<Episode?>(null, id) { repo.episode(id).collect { value = it } }
    val e = episode ?: return
    if (e.played) return
    val podcast by produceState<Podcast?>(null, e.podcastId) { repo.podcast(e.podcastId).collect { value = it } }
    val here = player.currentEpisodeId == e.id
    val position = if (here) player.positionMs else e.positionMs
    val duration = if (here && player.durationMs > 0) player.durationMs else e.durationMs
    val playing = here && player.isPlaying
    Card(
        Modifier.fillMaxWidth().clickable { onOpenEpisode(e.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                podcast?.artworkUrl, null,
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Continue listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Text(
                    listOfNotNull(podcast?.title, if (duration > 0) minutesLeft(duration - position) else null).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                )
                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            FilledIconButton(onClick = { if (here) player.togglePlay() else player.play(e, podcast) }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
            }
        }
    }
}

/** A row of what to listen to next: each followed podcast's next unplayed episode, new ones first. */
@Composable
internal fun UpNextRow(repo: Repository, podcasts: List<Podcast>, onOpenEpisode: (Long) -> Unit) {
    val unplayed by repo.unplayed.collectAsState(emptyList())
    val context = LocalContext.current
    val newestFirst = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("newestFirst", false) }
    val picks = remember(unplayed, newestFirst) { Discover.upNext(unplayed, newestFirst) }
    val byId = remember(podcasts) { podcasts.associateBy { it.id } }
    if (picks.size < 2) return   // one podcast's next episode is just its tile; the row earns its space with a choice
    Column {
        SectionTitle("Up next")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(picks, key = { it.id }) { e ->
                Column(Modifier.width(120.dp).clickable { onOpenEpisode(e.id) }) {
                    Box { TileArtwork(byId[e.podcastId]?.artworkUrl) }
                    Text(
                        (if (e.isNew) "New · " else "") + e.title, Modifier.padding(top = 4.dp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    )
                }
            }
        }
    }
}

/** The three newest asides, trimmed to a few words. Tap one to hear it in context. */
@Composable
internal fun RecentAsides(repo: Repository, player: PlayerConnection, onOpenEpisode: (Long) -> Unit, onOpenAsides: () -> Unit) {
    val recent by remember { repo.recentAsides(3) }.collectAsState(emptyList())
    if (recent.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Recent asides") }
            TextButton(onClick = onOpenAsides) { Text("All") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            recent.forEach { r ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { player.startReview(r.episodeId, r.positionMs); onOpenEpisode(r.episodeId) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Discover.snippet(r.transcript), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.podcastTitle} · ${formatTime(r.positionMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.PlayArrow, "Hear in context", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The Discover part of the front page: interest chips (the ones you follow first) and a way into the Top chart. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverHeader(onOpenGenre: (Int?) -> Unit, onOpenTop: () -> Unit) {
    val followed = AppSettings.interests
    val ordered = remember(followed) { Discover.genres.sortedByDescending { it.id in followed } }
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Discover")
        Text(
            if (followed.isEmpty()) "Pick an interest to browse its top podcasts. Follow it there to get suggestions here."
            else "Your interests come first. Tap one to browse it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ordered.forEach { g ->
                FilterChip(selected = g.id in followed, onClick = { onOpenGenre(g.id) }, label = { Text(g.name) })
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenTop).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Top podcasts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The top podcasts overall ([genreId] null) or in one category, with a button to follow the category as an interest. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartScreen(repo: Repository, genreId: Int?, onOpenPreview: (SearchResult) -> Unit) {
    val podcasts by repo.podcasts.collectAsState(emptyList())
    val subscribed = remember(podcasts) { podcasts.map { it.feedUrl }.toSet() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(emptySet<String>()) }
    var attempt by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val chart by produceState<List<SearchResult>?>(null, genreId, attempt) {
        failed = false
        value = try { repo.topPodcasts(genreId, 25) } catch (e: Exception) { failed = true; null }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(if (genreId == null) "Top podcasts" else Discover.genreName(genreId)) }
                if (genreId != null) {
                    val on = genreId in AppSettings.interests
                    FilterChip(selected = on, onClick = { AppSettings.toggleInterest(genreId) }, label = { Text(if (on) "Following" else "Follow interest") })
                }
            }
        }
        val list = chart
        when {
            list == null && failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't load the chart.", Modifier.weight(1f))
                    TextButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
            list == null -> item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator() }
            list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { Text("Nothing found.") }
            else -> items(list, key = { "c-${it.feedUrl}" }, span = { GridItemSpan(2) }) { r ->
                ResultTile(r, r.feedUrl in subscribed, r.feedUrl in busy, { onOpenPreview(r) }) {
                    scope.launch {
                        busy += r.feedUrl
                        runCatching { if (r.feedUrl in subscribed) repo.unsubscribe(r.feedUrl) else repo.subscribe(r.feedUrl) }
                        busy -= r.feedUrl
                    }
                }
            }
        }
    }
}
