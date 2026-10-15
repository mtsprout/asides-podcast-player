package com.asides.app.data

/** Chooses which episode plays after the current one finishes. */
object NextEpisode {
    /**
     * The next unplayed episode after [currentId], in the same order the podcast's page lists them:
     * oldest first by default, or newest first. Episodes already played are skipped, which makes
     * catching up work naturally: mark a block as played, play the last one you haven't heard, and
     * playback carries on from there. Returns null at the end of the list.
     *
     * [all] is in the order the database returns them (newest first); the sort below is the same stable
     * sort the podcast page applies, so "next" always matches what the list shows.
     */
    fun pick(all: List<Episode>, currentId: Long, newestFirst: Boolean): Episode? {
        val ordered = if (newestFirst) all.sortedByDescending { it.pubDate } else all.sortedBy { it.pubDate }
        val i = ordered.indexOfFirst { it.id == currentId }
        if (i < 0) return null
        return ordered.drop(i + 1).firstOrNull { !it.played }
    }

    /**
     * Where a play press should pick up after the player was shut down. Normally the episode last
     * listened to. But if that one is finished (played, and back at the start), the listener has moved
     * on, so go to the next unplayed episode, the same one autoplay would have chosen.
     */
    fun resume(all: List<Episode>, lastId: Long, newestFirst: Boolean): Episode? {
        val last = all.firstOrNull { it.id == lastId } ?: return null
        if (!last.played || last.positionMs > 0) return last
        return pick(all, lastId, newestFirst) ?: last
    }
}
