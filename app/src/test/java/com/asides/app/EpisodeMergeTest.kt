package com.asides.app

import com.asides.app.data.EpisodeMerge
import com.asides.app.data.EpisodeUpdate
import com.asides.app.data.KnownEpisode
import com.asides.app.data.ParsedEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeMergeTest {
    private fun known(id: Long, guid: String?, url: String, title: String = "T$id", date: Long = id * 1000) =
        KnownEpisode(id, guid, url, title, date)

    private fun parsed(guid: String?, url: String, title: String = "T", date: Long = 0) =
        ParsedEpisode(title, date, url, 0, guid)

    @Test fun anEpisodeWeHaveIsNotNew() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, "g1", "https://a/1.mp3")),
            listOf(parsed("g1", "https://a/1.mp3")),
        )
        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test fun anUnseenEpisodeIsInserted() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, "g1", "https://a/1.mp3")),
            listOf(parsed("g2", "https://a/2.mp3", "Two"), parsed("g1", "https://a/1.mp3")),
        )
        assertEquals(listOf("Two"), plan.inserts.map { it.title })
    }

    @Test fun aChangedAudioUrlIsAnUpdateNotADuplicate() {
        // Feeds often change tracking tags on the URL; the guid says it's the same episode.
        val plan = EpisodeMerge.plan(
            listOf(known(1, "g1", "https://a/1.mp3?t=old")),
            listOf(parsed("g1", "https://a/1.mp3?t=new")),
        )
        assertTrue(plan.inserts.isEmpty())
        assertEquals(listOf(EpisodeUpdate(1, "g1", "https://a/1.mp3?t=new")), plan.updates)
    }

    @Test fun episodesSavedWithoutAGuidAreMatchedByUrlAndGivenOne() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, null, "https://a/1.mp3")),
            listOf(parsed("g1", "https://a/1.mp3")),
        )
        assertTrue(plan.inserts.isEmpty())
        assertEquals(listOf(EpisodeUpdate(1, "g1", "https://a/1.mp3")), plan.updates)
    }

    @Test fun anOldEpisodeWithAChangedUrlIsStillMatchedByTitleAndDate() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, null, "https://a/old.mp3", title = "Day 1", date = 5000)),
            listOf(parsed("g1", "https://a/new.mp3", title = "Day 1", date = 5000)),
        )
        assertTrue("must not be treated as new", plan.inserts.isEmpty())
        assertEquals(listOf(EpisodeUpdate(1, "g1", "https://a/new.mp3")), plan.updates)
    }

    @Test fun feedsWithoutGuidsMatchOnUrl() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, null, "https://a/1.mp3")),
            listOf(parsed(null, "https://a/1.mp3"), parsed(null, "https://a/2.mp3", "Two")),
        )
        assertEquals(listOf("Two"), plan.inserts.map { it.title })
        assertTrue(plan.updates.isEmpty())
    }

    @Test fun anEpisodeListedTwiceIsInsertedOnce() {
        val plan = EpisodeMerge.plan(
            emptyList(),
            listOf(parsed("g9", "https://a/9.mp3", "Nine"), parsed("g9", "https://a/9.mp3", "Nine")),
        )
        assertEquals(1, plan.inserts.size)
    }

    @Test fun anExistingEpisodeIsOnlyMatchedOnce() {
        // Two feed items that both look like episode 1 must not both rewrite it.
        val plan = EpisodeMerge.plan(
            listOf(known(1, "g1", "https://a/1.mp3")),
            listOf(parsed("g1", "https://a/1.mp3"), parsed(null, "https://a/1.mp3")),
        )
        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test fun aUrlAlreadyUsedByAnotherEpisodeIsNotCopied() {
        val plan = EpisodeMerge.plan(
            listOf(known(1, "g1", "https://a/1.mp3"), known(2, "g2", "https://a/2.mp3")),
            listOf(parsed("g1", "https://a/2.mp3")),
        )
        // g1 now claims episode 2's URL: leave its URL alone rather than break the unique index.
        assertTrue(plan.updates.none { it.audioUrl == "https://a/2.mp3" && it.id == 1L })
    }
}
