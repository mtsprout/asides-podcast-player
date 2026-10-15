package com.asides.app

import com.asides.app.data.Discover
import com.asides.app.data.Episode
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

    @Test fun `up next leaves out the continue listening episode`() {
        val unplayed = listOf(ep(1, 1, 100), ep(3, 2, 50))
        assertEquals(listOf(3L), Discover.upNext(unplayed, newestFirst = false, excludeId = 1).map { it.id })
    }

    @Test fun `a show is in a category by its own id or a subcategory`() {
        assertEquals(true, Discover.inGenre(listOf(1533, 26), 1533))
        assertEquals(true, Discover.inGenre(listOf(1535, 26), 1533))   // a Science subcategory
        assertEquals(false, Discover.inGenre(listOf(1324, 26), 1533))
        assertEquals(true, Discover.inGenre(listOf(1487), 1487))       // History has no subcategories
    }

    @Test fun `category names map to ids in any case`() {
        assertEquals(1314, Discover.genreIdForName("Religion & Spirituality"))
        assertEquals(1487, Discover.genreIdForName(" history "))
        assertEquals(null, Discover.genreIdForName("Government"))
    }

    @Test fun `the followed category with most podcasts wins`() {
        val byFeed = mapOf("a" to setOf(1314), "b" to setOf(1314, 1324), "c" to setOf(1324), "d" to setOf(1314))
        // Among the followed a, b, c: religion (1314) has a and b, society (1324) has b and c. A tie goes to the earlier listed.
        val (g, n) = Discover.topFollowedGenre(byFeed, listOf("a", "b", "c"))!!
        assertEquals(1314 to 2, g.id to n)
        val (g2, n2) = Discover.topFollowedGenre(byFeed, listOf("b", "c"))!!
        assertEquals(1324 to 2, g2.id to n2)
    }

    @Test fun `no known categories means no suggestions`() {
        assertEquals(null, Discover.topFollowedGenre(emptyMap(), listOf("a")))
    }

    private val ids = Discover.genres.map { it.id }

    @Test fun `with nothing picked there are four random categories`() {
        val s = Discover.sections(emptySet(), seed = 1)
        assertEquals(4, s.size)
        assertEquals(4, s.map { it.id }.toSet().size)
    }

    @Test fun `picked categories come first and random ones fill up to five`() {
        val s = Discover.sections(setOf(ids[2]), seed = 7)
        assertEquals(5, s.size)
        assertEquals(ids[2], s.first().id)
        assertEquals(5, s.map { it.id }.toSet().size)
        val two = Discover.sections(setOf(ids[2], ids[5]), seed = 7)
        assertEquals(listOf(ids[2], ids[5]), two.take(2).map { it.id })
        assertEquals(5, two.size)
    }

    @Test fun `more than five picked shows a random five of them`() {
        val picked = ids.take(8).toSet()
        val a = Discover.sections(picked, seed = 3).map { it.id }
        assertEquals(5, a.size)
        assert(a.all { it in picked })
        assertEquals(a, a.sortedBy { ids.indexOf(it) })
        val differs = (1L..20L).map { Discover.sections(picked, seed = it).map { g -> g.id } }.toSet().size > 1
        assert(differs)
    }

    @Test fun `the random picks are the same for the same seed`() {
        assertEquals(Discover.sections(emptySet(), seed = 42), Discover.sections(emptySet(), seed = 42))
    }
}
