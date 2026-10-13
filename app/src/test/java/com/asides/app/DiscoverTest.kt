package com.asides.app

import com.asides.app.data.Discover
import com.asides.app.data.Episode
import com.asides.app.data.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverTest {
    @Test fun `a short transcript is shown whole`() {
        assertEquals("came back to this", Discover.snippet("came back to this"))
    }

    @Test fun `a long transcript is cut on a word near 30 characters`() {
        val s = Discover.snippet("we need to come back to this episode and check this")
        assertEquals("we need to come back to this…", s)
        assert(s.length <= 31)
    }

    @Test fun `a missing transcript says it is being transcribed`() {
        assertEquals("Transcribing…", Discover.snippet(null))
        assertEquals("Transcribing…", Discover.snippet("   "))
    }

    @Test fun `interests round-trip and unknown ids are dropped`() {
        val ids = setOf(1488, 1303)
        assertEquals(ids, Discover.parseInterests(Discover.formatInterests(ids)))
        assertEquals(setOf(1488), Discover.parseInterests("1488, 9, junk,"))
        assertEquals(emptySet<Int>(), Discover.parseInterests(null))
    }

    private fun ep(id: Long, podcast: Long, date: Long, new: Boolean = false) =
        Episode(id = id, podcastId = podcast, title = "e$id", pubDate = date, audioUrl = "u$id", isNew = new)

    @Test fun `up next is one episode per podcast in list order`() {
        val unplayed = listOf(ep(1, 1, 100), ep(2, 1, 200), ep(3, 2, 50), ep(4, 2, 60))
        assertEquals(setOf(1L, 3L), Discover.upNext(unplayed, newestFirst = false).map { it.id }.toSet())
        assertEquals(setOf(2L, 4L), Discover.upNext(unplayed, newestFirst = true).map { it.id }.toSet())
    }

    @Test fun `new episodes come first in up next`() {
        val unplayed = listOf(ep(1, 1, 900), ep(2, 2, 100, new = true))
        assertEquals(listOf(2L, 1L), Discover.upNext(unplayed, newestFirst = false).map { it.id })
    }

    @Test fun `for you mixes the charts and skips what is followed`() {
        fun r(n: String) = SearchResult(n, "f$n", null)
        val out = Discover.forYou(listOf(listOf(r("a"), r("b")), listOf(r("c"), r("a"), r("d"))), followed = setOf("fb"), limit = 10)
        assertEquals(listOf("a", "c", "d"), out.map { it.title })
    }
}
