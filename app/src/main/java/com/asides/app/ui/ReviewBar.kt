package com.asides.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asides.app.service.Review

/**
 * Shown on every screen while an aside is being heard in context: says where Return will go, and offers
 * it. Return puts the listener back where they were, paused.
 */
@Composable
fun ReviewBar(player: PlayerConnection) {
    val review = Review.info ?: return
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reviewing an aside", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Return to ${formatTime(review.returnPositionMs)} in ${review.returnTitle.ifBlank { "where you were" }}",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { player.returnFromReview() }) { Text("Return") }
        }
    }
}
