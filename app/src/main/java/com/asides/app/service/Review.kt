package com.asides.app.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Reviewing an aside": a detour into an old episode to hear a note in context, without losing the
 * place in the episode you were really listening to. While it lasts, nothing about the reviewed
 * episode is saved (no position, no played mark), and when it ends or you tap Return you are back
 * where you left off, paused. The state lives in memory only: if the app is killed mid-review, the
 * place you left was already saved, so you simply find yourself back there.
 */
object Review {
    /** Where to go back to, and which episode is being reviewed. */
    data class Info(
        val returnEpisodeId: Long,
        val returnPositionMs: Long,
        val returnTitle: String,
        val reviewEpisodeId: Long,
    )

    /** Read by the screens and written by the playback service (they share a process). */
    var info by mutableStateOf<Info?>(null)

    /** Start a little before the aside so there is time to react, as it was made after a triple-tap. */
    const val REWIND_MS = 10_000L

    fun startPosition(asideMs: Long): Long = (asideMs - REWIND_MS).coerceAtLeast(0)

    /**
     * What a review of [targetEpisodeId] should remember. The first review records where the listener
     * is now; reviewing a second aside keeps that original place, so Return always means "back to my
     * flow". Null when nothing is loaded, in which case there is nothing to return to and the aside
     * just plays.
     */
    fun begin(existing: Info?, currentId: Long?, currentPositionMs: Long, currentTitle: String, targetEpisodeId: Long): Info? =
        when {
            existing != null -> existing.copy(reviewEpisodeId = targetEpisodeId)
            currentId == null -> null
            else -> Info(currentId, currentPositionMs, currentTitle, targetEpisodeId)
        }
}
