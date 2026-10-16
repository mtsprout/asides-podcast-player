package com.asides.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.core.app.NotificationManagerCompat
import android.widget.Toast
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.map
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.flowOf
import com.asides.app.data.Podcast
import com.asides.app.data.ParsedFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import com.asides.app.service.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import com.asides.app.data.Discover
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asides.app.data.Annotation
import com.asides.app.data.AnnotationRow
import com.asides.app.data.Episode
import com.asides.app.data.Repository
import com.asides.app.data.SearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

internal fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun formatDate(ms: Long): String =
    if (ms == 0L) "Unknown date" else DateFormat.getMediumDateFormat(LocalContext.current).format(Date(ms))

@Composable
internal fun Artwork(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    if (url == null) {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

// ---------------------------------------------------------------- Library

/**
 * Text styles and spacing for episode lists, driven by the Compact / Regular / Large setting
 * ([AppSettings.listDensity]). Compact is the default.
 */
internal object EpisodeListStyle {
    val title @Composable get() = when (AppSettings.listDensity) {
        ListDensity.Compact -> MaterialTheme.typography.bodyMedium
        ListDensity.Regular -> MaterialTheme.typography.bodyLarge
        ListDensity.Large -> MaterialTheme.typography.titleMedium
    }
    val meta @Composable get() = when (AppSettings.listDensity) {
        ListDensity.Compact -> MaterialTheme.typography.labelSmall
        ListDensity.Regular -> MaterialTheme.typography.bodySmall
        ListDensity.Large -> MaterialTheme.typography.bodyMedium
    }
    val rowPadding get() = when (AppSettings.listDensity) {
        ListDensity.Compact -> 6.dp
        ListDensity.Regular -> 10.dp
        ListDensity.Large -> 14.dp
    }
    val checkButtonSize get() = when (AppSettings.listDensity) {
        ListDensity.Compact -> 40.dp
        ListDensity.Regular -> 44.dp
        ListDensity.Large -> 48.dp
    }
}

private val FullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** The round corner button on a podcast tile: + to subscribe, a check mark (tap to unsubscribe) when subscribed. */
@Composable
internal fun SubscribeBadge(subscribed: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = if (subscribed) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(6.dp).size(32.dp).clip(CircleShape).clickable(enabled = !busy, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                subscribed -> Icon(Icons.Default.Check, "Subscribed. Tap to unsubscribe", Modifier.size(18.dp))
                else -> Icon(Icons.Default.Add, "Subscribe", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Square artwork that fills the width it is given. */
@Composable
internal fun TileArtwork(url: String?) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (url == null) Box(modifier) else AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

internal fun plural(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repo: Repository,
    onOpenPodcast: (Long) -> Unit,
    onOpenPreview: (SearchResult) -> Unit,
    onOpenSettings: () -> Unit,
    player: PlayerConnection,
    onOpenEpisode: (Long) -> Unit,
    onOpenAsides: () -> Unit,
    onOpenGenre: (Int?) -> Unit,
) {
    val podcasts by repo.podcasts.collectAsState(emptyList())
    val subscribed = remember(podcasts) { podcasts.map { it.feedUrl }.toSet() }
    val asideCount by remember { repo.allAnnotations.map { it.size } }.collectAsState(0)
    val newCounts by repo.newCounts.collectAsState(emptyMap())
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var subscribing by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        scope.launch {
            busy = true; error = null
            try {
                if (q.startsWith("http://") || q.startsWith("https://")) {
                    val id = repo.subscribe(q); query = ""; results = null; onOpenPodcast(id)
                } else results = repo.search(q)
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            busy = false
        }
    }
    fun toggleSubscription(feedUrl: String) {
        scope.launch {
            subscribing += feedUrl; error = null
            try {
                if (feedUrl in subscribed) repo.unsubscribe(feedUrl) else repo.subscribe(feedUrl)
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            subscribing -= feedUrl
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; repo.refreshAll(force = true); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Wordmark centred at the top, then the search bar, then a one-line summary of the library.
        item(span = FullWidth) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Wordmark(fontSize = 44.sp, parentheses = true)
                IconButton(onClick = onOpenSettings, Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!AppSettings.introSeen) item(span = FullWidth) { IntroCard(onDismiss = { AppSettings.markIntroSeen(true) }) }
        item(span = FullWidth) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search, or paste a feed URL") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null; error = null; focus.clearFocus() }) {
                        Icon(Icons.Default.Close, "Clear")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = FullWidth) {
            val fresh = newCounts.values.sum()
            Text(
                if (podcasts.isEmpty()) AnnotatedString("Listen. Talk back.")
                else buildAnnotatedString {
                    append("${plural(podcasts.size, "podcast")} · ${plural(asideCount, "aside")}")
                    if (fresh > 0) {
                        append(" · ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)) {
                            append("$fresh new")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (busy) item(span = FullWidth) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { msg -> item(span = FullWidth) { Text(msg, color = MaterialTheme.colorScheme.error) } }

        if (results == null) {
            item(span = FullWidth) { ContinueCard(repo, player, onOpenEpisode) }
            item(span = FullWidth) { UpNextRow(repo, podcasts, player, onOpenEpisode) }
            item(span = FullWidth) { RecentAsides(repo, player, onOpenEpisode, onOpenAsides) }
        }
        if (podcasts.isNotEmpty() && results == null) {
            item(span = FullWidth) { SectionTitle("Your podcasts") }
            items(podcasts, key = { "p-${it.id}" }, span = { GridItemSpan(2) }) { p ->
                Column(Modifier.clickable { onOpenPodcast(p.id) }) {
                    Box {
                        TileArtwork(p.artworkUrl)
                        newCounts[p.id]?.let { NewPill(it, Modifier.align(Alignment.TopStart)) }
                        SubscribeBadge(
                            subscribed = true, busy = p.feedUrl in subscribing,
                            onClick = { toggleSubscription(p.feedUrl) }, modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                    Text(
                        p.title, Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    )
                }
            }
        }

        if (results == null) {
            item(span = FullWidth) { SectionTitle("Discover") }
            item(span = FullWidth) {
                // Learned from the feeds' own categories; shown once at least one followed podcast has one.
                val genresByFeed by (repo.genreStore?.all ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
                val top = Discover.topFollowedGenre(genresByFeed, podcasts.map { it.feedUrl })
                if (top != null) {
                    val (g, n) = top
                    ChartRow(
                        repo, g.id, "Because you follow $n ${g.name} podcast${if (n == 1) "" else "s"}",
                        subscribed, subscribing, onOpenPreview, { onOpenGenre(g.id) }, ::toggleSubscription, suggest = true,
                    )
                }
            }
            item(span = FullWidth) { ChartRow(repo, null, "Top podcasts", subscribed, subscribing, onOpenPreview, { onOpenGenre(null) }, ::toggleSubscription) }
            Discover.sections(AppSettings.interests).forEach { g ->
                item(span = FullWidth, key = "chart-${g.id}") {
                    ChartRow(repo, g.id, g.name, subscribed, subscribing, onOpenPreview, { onOpenGenre(g.id) }, ::toggleSubscription)
                }
            }
            item(span = FullWidth) { CategoryChips() }
        } else {
            item(span = FullWidth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Results") }
                    TextButton(onClick = { results = null; query = ""; focus.clearFocus() }) { Text("Clear") }
                }
            }
            val list = results!!
            if (list.isEmpty()) item(span = FullWidth) { Text("Nothing found.") }
            else items(list, key = { "t-${it.feedUrl}" }, span = { GridItemSpan(2) }) { r ->
                ResultTile(r, r.feedUrl in subscribed, r.feedUrl in subscribing, { onOpenPreview(r) }) { toggleSubscription(r.feedUrl) }
            }
        }
    }
    }
}

/** One podcast in a grid of search results or charts: artwork, a subscribe badge and the title. */
@Composable
internal fun ResultTile(r: SearchResult, subscribed: Boolean, busy: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Column(Modifier.clickable(onClick = onOpen)) {
        Box {
            TileArtwork(r.artworkUrl)
            SubscribeBadge(subscribed = subscribed, busy = busy, onClick = onToggle, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Text(
            r.title, Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
        )
    }
}

/** "3 new": episodes a refresh found that haven't been opened yet. */
@Composable
private fun NewPill(count: Int, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiary, modifier = modifier.padding(6.dp)) {
        Text(
            "$count new", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(repo: Repository, podcastId: Long, onOpenEpisode: (Long) -> Unit) {
    val all by repo.episodes(podcastId).collectAsState(emptyList())
    val podcast by repo.podcast(podcastId).collectAsState(null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
    var refreshing by remember { mutableStateOf(false) }
    // Look for new episodes when the page opens (skipped if checked in the last half hour).
    LaunchedEffect(podcastId) { repo.refreshPodcast(podcastId) }
    val downloads by Downloads.state.collectAsState()
    var confirmRemove by remember { mutableStateOf<Episode?>(null) }
    // While anything is downloading, keep the progress rings moving (and pick up finished files).
    val downloading = downloads.progress.isNotEmpty()
    LaunchedEffect(downloading) {
        while (downloading) {
            delay(1000)
            withContext(Dispatchers.IO) { Downloads.refresh() }
        }
    }
    // Oldest first by default, which is the natural order for working through a series.
    var newestFirst by remember { mutableStateOf(prefs.getBoolean("newestFirst", false)) }
    var hidePlayed by remember { mutableStateOf(prefs.getBoolean("hidePlayed", false)) }
    val episodes = remember(all, newestFirst, hidePlayed) {
        all.filter { !hidePlayed || !it.played }.let { l ->
            if (newestFirst) l.sortedByDescending { it.pubDate } else l.sortedBy { it.pubDate }
        }
    }

    // Selection: long-press starts it (or extends it as a range from the anchor); tap toggles one episode.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var anchor by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet(); anchor = null }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
        anchor = id
    }
    fun selectRangeTo(id: Long) {
        val from = episodes.indexOfFirst { it.id == anchor }
        val to = episodes.indexOfFirst { it.id == id }
        if (selected.isEmpty() || from < 0 || to < 0) { toggle(id); return }
        selected = selected + episodes.subList(minOf(from, to), maxOf(from, to) + 1).map { it.id }
        anchor = id
    }
    fun markSelected(played: Boolean) {
        val ids = selected
        selected = emptySet(); anchor = null
        scope.launch { repo.setPlayed(ids, played) }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; repo.refreshPodcast(podcastId, force = true); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
    // The action bar floats over the list so the rows don't jump when selection starts.
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(podcast?.artworkUrl, 96.dp)
                Text(
                    podcast?.title.orEmpty(), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                val notify = podcast?.notifyNew == true
                IconButton(onClick = {
                    scope.launch { repo.setNotify(podcastId, !notify) }
                    if (!notify && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        Toast.makeText(context, "Notifications are turned off for Asides in system settings.", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(
                        if (notify) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                        if (notify) "Turn off new-episode notifications" else "Notify me about new episodes",
                        tint = if (notify) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { newestFirst = !newestFirst; prefs.edit().putBoolean("newestFirst", newestFirst).apply() },
                    label = { Text(if (newestFirst) "Newest first" else "Oldest first") },
                    leadingIcon = { Icon(Icons.Default.SwapVert, null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = hidePlayed,
                    onClick = { hidePlayed = !hidePlayed; prefs.edit().putBoolean("hidePlayed", hidePlayed).apply() },
                    label = { Text("Hide played") },
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
        items(episodes, key = { it.id }) { e ->
            val isSelected = e.id in selected
            Row(
                Modifier.fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .combinedClickable(
                        onClick = { if (selected.isNotEmpty()) toggle(e.id) else onOpenEpisode(e.id) },
                        onLongClick = { if (selected.isEmpty()) toggle(e.id) else selectRangeTo(e.id) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = EpisodeListStyle.rowPadding)) {
                    val dim = if (e.played) 0.5f else 1f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e.isNew) {
                            Box(Modifier.padding(end = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                        }
                        Text(e.title, style = EpisodeListStyle.title, modifier = Modifier.alpha(dim))
                    }
                    Text(
                        formatDate(e.pubDate) + if (e.played) " · Played" else "",
                        style = EpisodeListStyle.meta, modifier = Modifier.alpha(dim),
                    )
                }
                val progress = downloads.progress[e.id]
                IconButton(
                    modifier = Modifier.size(EpisodeListStyle.checkButtonSize),
                    onClick = {
                        when {
                            e.id in downloads.done -> confirmRemove = e
                            progress != null -> scope.launch(Dispatchers.IO) { Downloads.remove(e.id) }
                            else -> scope.launch(Dispatchers.IO) { Downloads.start(e) }
                        }
                    },
                ) {
                    when {
                        e.id in downloads.done -> Icon(
                            Icons.Default.DownloadDone, "Downloaded. Tap to remove the download.",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        progress != null -> CircularProgressIndicator(
                            progress = { progress / 100f }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp,
                        )
                        else -> Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    modifier = Modifier.size(EpisodeListStyle.checkButtonSize),
                    onClick = { scope.launch { repo.setPlayed(e.id, !e.played) } },
                ) {
                    Icon(
                        if (e.played) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        if (e.played) "Mark unplayed" else "Mark played",
                    )
                }
            }
            HorizontalDivider()
        }
    }
    confirmRemove?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove download?") },
            text = { Text("\"${e.title}\" will be deleted from this phone. It can still be streamed, and your asides and place are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; scope.launch(Dispatchers.IO) { Downloads.remove(e.id) } }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
    if (selected.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptySet(); anchor = null }) { Icon(Icons.Default.Close, "Clear selection") }
                    Text("${selected.size} selected", Modifier.weight(1f), softWrap = false, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { selected = episodes.map { it.id }.toSet() }) { Text("All") }
                    val chosen = episodes.filter { it.id in selected }
                    val allDownloaded = chosen.isNotEmpty() && chosen.all { it.id in downloads.done }
                    TextButton(onClick = {
                        selected = emptySet(); anchor = null
                        scope.launch(Dispatchers.IO) {
                            if (allDownloaded) Downloads.removeAll(chosen.map { it.id }) else chosen.forEach(Downloads::start)
                        }
                    }) { Text(if (allDownloaded) "Remove" else "Download") }
                    TextButton(onClick = { markSelected(true) }) { Text("Played") }
                    TextButton(onClick = { markSelected(false) }) { Text("Unplayed") }
                }
                if (selected.size == 1) {
                    Text(
                        "Long-press another episode to select everything in between.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
    }
    }
}

/** A podcast you may not follow yet: description and recent episodes, read live from its feed. */
@Composable
fun PreviewScreen(repo: Repository, feedUrl: String, title: String, artworkUrl: String?, onOpenPodcast: (Long) -> Unit) {
    val podcasts by repo.podcasts.collectAsState(emptyList())
    val existing = podcasts.firstOrNull { it.feedUrl == feedUrl }
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<ParsedFeed?>(null) }
    var failed by remember { mutableStateOf(false) }
    var subscribing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    suspend fun load() {
        failed = false
        try { feed = repo.previewFeed(feedUrl) } catch (e: Exception) { failed = true }
    }
    LaunchedEffect(feedUrl) { load() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(artworkUrl ?: feed?.artworkUrl, 120.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(title.ifEmpty { feed?.title.orEmpty() }, style = MaterialTheme.typography.titleLarge)
                    feed?.author?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (existing != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenPodcast(existing.id) }, Modifier.weight(1f)) { Text("Open podcast") }
                    OutlinedButton(onClick = { scope.launch { repo.unsubscribe(feedUrl) } }) { Text("Unsubscribe") }
                }
            } else {
                Button(
                    enabled = !subscribing,
                    onClick = {
                        scope.launch {
                            subscribing = true
                            try { repo.subscribe(feedUrl) } catch (_: Exception) { failed = true }
                            subscribing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (subscribing) "Adding…" else "Subscribe") }
            }
        }
        val f = feed
        when {
            f == null && failed -> item {
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't load this podcast.", Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { load() } }) { Text("Retry") }
                }
            }
            f == null -> item { CircularProgressIndicator(Modifier.padding(vertical = 24.dp)) }
            else -> {
                item {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(
                            plural(f.episodes.size, "episode"), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = if (f.description.isEmpty()) 0.dp else 6.dp),
                        )
                        if (f.description.isNotEmpty()) {
                            Text(f.description, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                            if (f.description.length > 280) {
                                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Latest episodes", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                    HorizontalDivider()
                }
                items(f.episodes.sortedByDescending { it.pubDate }.take(10)) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = EpisodeListStyle.rowPadding)) {
                        Text(e.title, style = EpisodeListStyle.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(e.pubDate), style = EpisodeListStyle.meta)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Annotations

@Composable
internal fun AnnotationList(
    items: List<Annotation>,
    player: PlayerConnection,
    onOpenTranscript: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAudio: (Long) -> Unit,
    onJump: (Annotation) -> Unit,
) {
    if (items.isEmpty()) {
        Text("No asides yet for this episode.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(items, key = { it.id }) { a ->
            AnnotationItem(
                a.positionMs, a.transcript, a.audioPath, player,
                onJump = { onJump(a) }, onOpenTranscript = { onOpenTranscript(a.id) }, onDelete = { onDelete(a.id) },
                onDeleteAudio = { onDeleteAudio(a.id) },
            )
        }
    }
}

@Composable
private fun AnnotationItem(
    positionMs: Long,
    transcript: String?,
    audioPath: String,
    player: PlayerConnection,
    onJump: () -> Unit,
    onOpenTranscript: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAudio: () -> Unit,
) {
    val hasAudio = audioPath.isNotEmpty()
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        val keepText = hasAudio && !transcript.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete this aside?") },
            text = { Text(if (keepText) "You can delete just the recording and keep the transcript, or delete both. This can't be undone." else "This can't be undone.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (keepText) TextButton(onClick = { confirming = false; onDeleteAudio() }) { Text("Delete audio only") }
                    TextButton(onClick = { confirming = false; onDelete() }) { Text(if (keepText) "Delete both" else "Delete") }
                    TextButton(onClick = { confirming = false }) { Text("Cancel") }
                }
            },
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The timestamp, then the start of the note in the space before the buttons (one line, cut with "…").
        Row(Modifier.weight(1f).clickable(onClick = onJump), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), fontWeight = FontWeight.Bold)
            when {
                transcript == null -> Text(
                    "Transcribing…", Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                )
                transcript.isBlank() -> Text(
                    "No transcript available.", Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                )
                else -> Text(
                    transcript.trim().replace(Regex("\\s+"), " "), Modifier.weight(1f).padding(start = 12.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Dimmed and inert until there is a transcript to show (disabled IconButtons dim themselves).
        IconButton(enabled = !transcript.isNullOrBlank(), onClick = onOpenTranscript) { Icon(Icons.Default.Edit, "Show transcript") }
        IconButton(enabled = hasAudio, onClick = { player.playClip(audioPath) }) { Icon(Icons.Default.VolumeUp, "Play aside") }
        IconButton(onClick = { confirming = true }) { Icon(Icons.Default.Delete, "Delete aside") }
    }
    HorizontalDivider()
}

/** The full transcript of one note, with the recording and where in the episode it was made. It can be corrected or shared. */
@Composable
fun TranscriptScreen(repo: Repository, annotationId: Long, player: PlayerConnection) {
    val a by repo.annotation(annotationId).collectAsState(null)
    val rows by repo.allAnnotations.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val note = a ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aside at ${formatTime(note.positionMs)}", style = MaterialTheme.typography.titleLarge)
                Text(
                    DateFormat.getMediumDateFormat(context).format(Date(note.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(enabled = note.audioPath.isNotEmpty(), onClick = { player.playClip(note.audioPath) }) { Icon(Icons.Default.VolumeUp, "Play aside") }
            IconButton(
                enabled = !editing && rows.any { it.id == annotationId },
                onClick = {
                    val row = rows.first { it.id == annotationId }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AsidesText.plain(row))
                    context.startActivity(Intent.createChooser(send, "Share aside"))
                },
            ) { Icon(Icons.Default.Share, "Share aside") }
        }
        HorizontalDivider()
        if (editing) {
            OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("Transcript") }, minLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { repo.updateTranscript(annotationId, draft); editing = false } }) { Text("Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            SelectionContainer {
                Text(
                    note.transcript?.takeIf { it.isNotBlank() } ?: "No transcript available.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            TextButton(onClick = { draft = note.transcript.orEmpty(); editing = true }) { Text("Edit transcript") }
        }
    }
}

/** All annotations, grouped podcast → episode (newest first) → annotation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAnnotationsScreen(
    repo: Repository,
    player: PlayerConnection,
    onOpenEpisode: (Long) -> Unit,
    onOpenTranscript: (Long) -> Unit,
) {
    val allRows by repo.allAnnotations.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val rows = remember(allRows, query) { allRows.filter { AsidesText.matches(it, query) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(AsidesText.markdown(rows).toByteArray()) } }
        }
    }
    if (allRows.isEmpty()) {
        Text("No asides yet. Triple-press your headset button while an episode is playing.", Modifier.padding(16.dp))
        return
    }
    val byPodcast = rows.groupBy { it.podcastTitle }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "search") {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search asides") },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                )
                IconButton(
                    enabled = rows.isNotEmpty(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AsidesText.markdown(rows))
                        context.startActivity(Intent.createChooser(send, "Share asides"))
                    },
                ) { Icon(Icons.Default.Share, "Share asides") }
                IconButton(enabled = rows.isNotEmpty(), onClick = { save.launch("asides.md") }) { Icon(Icons.Default.Save, "Save asides as a file") }
            }
            if (rows.isEmpty()) Text("No asides match.", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        byPodcast.forEach { (podcast, podcastRows) ->
            item(key = "p-$podcast") {
                Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(podcastRows.first().podcastArtworkUrl, 40.dp)
                    Text(podcast, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
            podcastRows.groupBy { it.episodeId }.forEach { (episodeId, epRows) ->
                val head = epRows.first()
                item(key = "e-$episodeId") {
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenEpisode(episodeId) }) {
                        Text(
                            head.episodeTitle, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(epRows, key = { "a-${it.id}" }) { r: AnnotationRow ->
                    AnnotationItem(
                        r.positionMs, r.transcript, r.audioPath, player,
                        onJump = {
                            // Play it in context (ten seconds early) without losing your place; the bar offers Return.
                            player.startReview(episodeId, r.positionMs)
                            onOpenEpisode(episodeId)
                        },
                        onOpenTranscript = { onOpenTranscript(r.id) },
                        onDelete = { scope.launch { repo.deleteAnnotation(r.id) } },
                        onDeleteAudio = { scope.launch { repo.deleteAnnotationAudio(r.id) } },
                    )
                }
            }
        }
    }
}
