package com.asides.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.asides.app.data.Episode
import com.asides.app.data.Podcast
import com.asides.app.data.QueueOps
import com.asides.app.data.Repository
import kotlinx.coroutines.launch

/**
 * The play queue: what plays next, top first, before the usual next-unplayed-episode rule takes over.
 * Tap an episode to play it now (it leaves the queue); drag the handle to reorder; the cross removes.
 */
@Composable
fun QueueScreen(repo: Repository, player: PlayerConnection) {
    val ids by repo.queue.collectAsState()
    val rows by produceState<List<Pair<Episode, Podcast?>>>(emptyList(), ids) { value = repo.queuedEpisodes(ids) }
    val scope = rememberCoroutineScope()

    // While a row is being dragged the list reorders locally; the queue itself is changed once, on release.
    var shown by remember { mutableStateOf(rows) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var staleOffset by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows) { if (dragId == null) shown = rows }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Play queue", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (ids.isNotEmpty()) TextButton(onClick = { repo.clearQueue() }) { Text("Clear") }
        }
        if (rows.isEmpty()) {
            Text(
                "Nothing is queued. Select episodes on a podcast's page and tap the queue icon to line them up. " +
                    "When the queue is empty, playback carries on with the next unplayed episode as usual.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(shown, key = { _, r -> r.first.id }) { _, (e, p) ->
                val dragging = dragId == e.id
                Box(
                    (if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }.alpha(0.92f)
                    else Modifier.animateItem())
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    repo.markSeen(e.id)
                                    player.play(e, p)
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Artwork(p?.artworkUrl, 48.dp)
                            Column(Modifier.weight(1f).padding(vertical = EpisodeListStyle.rowPadding, horizontal = 8.dp)) {
                                Text(e.title, style = EpisodeListStyle.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(p?.title, formatDate(e.pubDate)).joinToString(" · "),
                                    style = EpisodeListStyle.meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                Modifier.size(40.dp).pointerInput(e.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragId = e.id
                                            dragOffset = 0f
                                            staleOffset = Int.MIN_VALUE
                                            dragFrom = shown.indexOfFirst { it.first.id == e.id }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val info = listState.layoutInfo.visibleItemsInfo
                                            val cur = info.firstOrNull { it.key == e.id } ?: return@detectDragGestures
                                            // Just after a swap the layout is out of date until it settles.
                                            if (cur.offset == staleOffset) return@detectDragGestures
                                            val centre = cur.offset + cur.size / 2 + dragOffset
                                            val target = info.firstOrNull {
                                                it.key != e.id && centre >= it.offset && centre <= it.offset + it.size
                                            } ?: return@detectDragGestures
                                            val from = shown.indexOfFirst { it.first.id == e.id }
                                            val to = shown.indexOfFirst { it.first.id == target.key }
                                            if (from < 0 || to < 0) return@detectDragGestures
                                            shown = QueueOps.move(shown, from, to)
                                            // Keep the row under the finger while the others slide into place.
                                            dragOffset += cur.offset - target.offset
                                            staleOffset = cur.offset
                                        },
                                        onDragEnd = {
                                            val to = shown.indexOfFirst { it.first.id == e.id }
                                            if (dragFrom >= 0 && to >= 0 && to != dragFrom) repo.moveInQueue(dragFrom, to)
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            shown = rows
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                    )
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.DragHandle, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(modifier = Modifier.size(40.dp), onClick = { repo.removeFromQueue(e.id) }) {
                                Icon(Icons.Default.Close, "Remove from queue")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
