package com.asides.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asides.app.data.Episode
import com.asides.app.data.Podcast
import com.asides.app.data.Repository
import kotlinx.coroutines.launch

/**
 * The play queue: what plays next, top first, before the usual next-unplayed-episode rule takes over.
 * Tap an episode to play it now (it leaves the queue); the arrows reorder; the cross removes.
 */
@Composable
fun QueueScreen(repo: Repository, player: PlayerConnection) {
    val ids by repo.queue.collectAsState()
    val rows by produceState<List<Pair<Episode, Podcast?>>>(emptyList(), ids) { value = repo.queuedEpisodes(ids) }
    val scope = rememberCoroutineScope()

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
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, r -> r.first.id }) { i, (e, p) ->
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
                    IconButton(enabled = i > 0, modifier = Modifier.size(40.dp), onClick = { repo.moveInQueue(i, i - 1) }) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up")
                    }
                    IconButton(enabled = i < rows.lastIndex, modifier = Modifier.size(40.dp), onClick = { repo.moveInQueue(i, i + 1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down")
                    }
                    IconButton(modifier = Modifier.size(40.dp), onClick = { repo.removeFromQueue(e.id) }) {
                        Icon(Icons.Default.Close, "Remove from queue")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
