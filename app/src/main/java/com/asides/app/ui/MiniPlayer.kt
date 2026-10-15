package com.asides.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.asides.app.service.Review
import kotlinx.coroutines.delay

/**
 * A slim bar above the tabs with what is loaded in the player, so it can be paused or resumed from
 * any screen. Tap it to open the episode. Hidden when nothing is loaded, during an aside review (the
 * review bar has the controls then) and on the episode's own screen.
 */
@Composable
fun MiniPlayer(player: PlayerConnection, onOpen: (Long) -> Unit) {
    if (player.currentEpisodeId <= 0 || Review.info != null) return
    val playing = player.isPlaying
    LaunchedEffect(playing) { while (playing) { player.poll(); delay(1000) } }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(player.currentEpisodeId) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    player.artworkUrl, null,
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(player.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(player.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { player.togglePlay() }) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
                }
            }
            if (player.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
