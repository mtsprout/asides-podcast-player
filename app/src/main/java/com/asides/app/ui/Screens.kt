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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Text styles and spacing for episode lists. Kept in one place so a compact / regular / large
 * setting can drive them later (see ROADMAP.md).
 */
private object EpisodeListStyle {
    val title @Composable get() = MaterialTheme.typography.bodyMedium
    val meta @Composable get() = MaterialTheme.typography.labelSmall
    val rowPadding = 6.dp
    val checkButtonSize = 40.dp
}

private val FullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** The round corner button on a podcast tile: + to subscribe, a check mark (tap to unsubscribe) when subscribed. */
@Composable
private fun SubscribeBadge(subscribed: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun TileArtwork(url: String?) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (url == null) Box(modifier) else AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

private fun plural(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: Repository, onOpenPodcast: (Long) -> Unit, onOpenPreview: (SearchResult) -> Unit) {
    val podcasts by repo.podcasts.collectAsState(emptyList())
    val subscribed = remember(podcasts) { podcasts.map { it.feedUrl }.toSet() }
    val asideCount by remember { repo.allAnnotations.map { it.size } }.collectAsState(0)
    val newCounts by repo.newCounts.collectAsState(emptyMap())
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var top by remember { mutableStateOf<List<SearchResult>?>(null) }
    var topFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var subscribing by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadTop() {
        topFailed = false
        try { top = repo.topPodcasts() } catch (e: Exception) { topFailed = true }
    }
    LaunchedEffect(Unit) { if (top == null) loadTop() }

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
        item(span = FullWidth) {
            Column {
                Text("Asides", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (podcasts.isEmpty()) "Find something to listen to"
                    else buildString {
                        append("${plural(podcasts.size, "podcast")} · ${plural(asideCount, "aside")}")
                        val fresh = newCounts.values.sum()
                        if (fresh > 0) append(" · $fresh new")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        if (busy) item(span = FullWidth) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { msg -> item(span = FullWidth) { Text(msg, color = MaterialTheme.colorScheme.error) } }

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

        item(span = FullWidth) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(if (results != null) "Results" else "Top podcasts") }
                if (results != null) TextButton(onClick = { results = null; query = ""; focus.clearFocus() }) { Text("Clear") }
            }
        }
        val list = results ?: top
        when {
            list == null && topFailed -> item(span = FullWidth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't load the top podcasts.", Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { loadTop() } }) { Text("Retry") }
                }
            }
            list == null -> item(span = FullWidth) { CircularProgressIndicator() }
            list.isEmpty() -> item(span = FullWidth) { Text("Nothing found.") }
            else -> items(list, key = { "t-${it.feedUrl}" }, span = { GridItemSpan(2) }) { r ->
                Column(Modifier.clickable { onOpenPreview(r) }) {
                    Box {
                        TileArtwork(r.artworkUrl)
                        SubscribeBadge(
                            subscribed = r.feedUrl in subscribed, busy = r.feedUrl in subscribing,
                            onClick = { toggleSubscription(r.feedUrl) }, modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                    Text(
                        r.title, Modifier.padding(top = 4.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    )
                }
            }
        }
    }
    }
}

/** "3 new": episodes a refresh found that haven't been opened yet. */
@Composable
private fun NewPill(count: Int, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(6.dp)) {
        Text(
            "$count new", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
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
                        tint = if (notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            Box(Modifier.padding(end = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                        Text(e.title, style = EpisodeListStyle.title, modifier = Modifier.alpha(dim))
                    }
                    Text(
                        formatDate(e.pubDate) + if (e.played) " · Played" else "",
                        style = EpisodeListStyle.meta, modifier = Modifier.alpha(dim),
                    )
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
                if (f.description.isNotEmpty()) item {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(f.description, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                        if (f.description.length > 280) {
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
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
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onJump)) {
            Text(formatTime(positionMs), fontWeight = FontWeight.Bold)
            when {
                transcript == null -> Text("Transcribing…", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                transcript.isBlank() -> Text("No transcript", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
        }
        // Dimmed and inert until there is a transcript to show (disabled IconButtons dim themselves).
        IconButton(enabled = !transcript.isNullOrBlank(), onClick = onOpenTranscript) { Icon(Icons.Default.Edit, "Show transcript") }
        IconButton(onClick = { player.playClip(audioPath) }) { Icon(Icons.Default.VolumeUp, "Play aside") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete aside") }
    }
    HorizontalDivider()
}

/** The full transcript of one note, with the recording and where in the episode it was made. */
@Composable
fun TranscriptScreen(repo: Repository, annotationId: Long, player: PlayerConnection) {
    val a by repo.annotation(annotationId).collectAsState(null)
    val note = a ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aside at ${formatTime(note.positionMs)}", style = MaterialTheme.typography.titleLarge)
                Text(
                    DateFormat.getMediumDateFormat(LocalContext.current).format(Date(note.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { player.playClip(note.audioPath) }) { Icon(Icons.Default.VolumeUp, "Play aside") }
        }
        HorizontalDivider()
        SelectionContainer {
            Text(
                note.transcript?.takeIf { it.isNotBlank() } ?: "No transcript available.",
                style = MaterialTheme.typography.bodyLarge,
            )
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
    val rows by repo.allAnnotations.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    if (rows.isEmpty()) {
        Text("No asides yet. Triple-press your headset button while an episode is playing.", Modifier.padding(16.dp))
        return
    }
    val byPodcast = rows.groupBy { it.podcastTitle }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                        Column(Modifier.padding(12.dp)) {
                            Text(head.episodeTitle, fontWeight = FontWeight.SemiBold)
                            Text(formatDate(head.episodePubDate), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(epRows, key = { "a-${it.id}" }) { r: AnnotationRow ->
                    AnnotationItem(
                        r.positionMs, r.transcript, r.audioPath, player,
                        onJump = { onOpenEpisode(episodeId) },
                        onOpenTranscript = { onOpenTranscript(r.id) },
                        onDelete = { scope.launch { repo.deleteAnnotation(r.id) } },
                    )
                }
            }
        }
    }
}
