package com.asides.app

import com.asides.app.data.Episode
import com.asides.app.data.NextEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    // Days 1..5, dated 1000..5000. The database hands them over newest first.
    private fun series(played: Set<Int> = emptySet()) = (5 downTo 1).map {
        Episode(id = it.toLong(), podcastId = 1, title = "Day $it", pubDate = it * 1000L, audioUrl = "u$it", played = it in played)
    }

    @Test fun oldestFirstPlaysTheNextNewerEpisode() {
        assertEquals("Day 3", NextEpisode.pick(series(), currentId = 2, newestFirst = false)?.title)
    }

    @Test fun newestFirstPlaysTheNextOlderEpisode() {
        assertEquals("Day 3", NextEpisode.pick(series(), currentId = 4, newestFirst = true)?.title)
    }

    @Test fun playedEpisodesAreSkipped() {
        // Caught up through Day 4: after finishing Day 2, the next one to hear is Day 5.
        assertEquals("Day 5", NextEpisode.pick(series(played = setOf(3, 4)), currentId = 2, newestFirst = false)?.title)
    }

    @Test fun theLastEpisodeHasNoNext() {
        assertNull(NextEpisode.pick(series(), currentId = 5, newestFirst = false))
        assertNull(NextEpisode.pick(series(), currentId = 1, newestFirst = true))
    }

    @Test fun nothingUnplayedAfterMeansNoNext() {
        assertNull(NextEpisode.pick(series(played = setOf(4, 5)), currentId = 3, newestFirst = false))
    }

    @Test fun anUnknownEpisodeHasNoNext() {
        assertNull(NextEpisode.pick(series(), currentId = 99, newestFirst = false))
    }

    @Test fun episodesWithTheSameDateKeepTheOrderThePageShows() {
        // Two episodes on one day, as the database returns them (id 11 listed before id 10).
        val all = listOf(
            Episode(id = 11, podcastId = 1, title = "B", pubDate = 5000, audioUrl = "b"),
            Episode(id = 10, podcastId = 1, title = "A", pubDate = 5000, audioUrl = "a"),
            Episode(id = 9, podcastId = 1, title = "Earlier", pubDate = 1000, audioUrl = "e"),
        )
        // Oldest first: Earlier, then the stable sort keeps B before A.
        assertEquals("B", NextEpisode.pick(all, currentId = 9, newestFirst = false)?.title)
        assertEquals("A", NextEpisode.pick(all, currentId = 11, newestFirst = false)?.title)
    }

    @Test fun resumeStaysOnAnEpisodeInProgress() {
        val eps = series().map { if (it.id == 2L) it.copy(positionMs = 5000) else it }
        assertEquals("Day 2", NextEpisode.resume(eps, 2, newestFirst = false)?.title)
    }

    @Test fun resumeMovesPastAFinishedEpisode() {
        // Days 1 and 2 finished (played, back at the start); Day 3 is next.
        assertEquals("Day 3", NextEpisode.resume(series(played = setOf(1, 2)), 1, newestFirst = false)?.title)
    }

    @Test fun resumeKeepsAReplayedPlayedEpisode() {
        val eps = series(played = setOf(2)).map { if (it.id == 2L) it.copy(positionMs = 9000) else it }
        assertEquals("Day 2", NextEpisode.resume(eps, 2, newestFirst = false)?.title)
    }

    @Test fun resumeFallsBackToTheFinishedEpisodeAtTheEnd() {
        assertEquals("Day 5", NextEpisode.resume(series(played = setOf(5)), 5, newestFirst = false)?.title)
    }
}
