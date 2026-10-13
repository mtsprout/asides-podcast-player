package com.asides.app

import com.asides.app.service.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewTest {
    @Test fun `starts ten seconds before the aside`() {
        assertEquals(50_000L, Review.startPosition(60_000))
    }

    @Test fun `never starts before the beginning`() {
        assertEquals(0L, Review.startPosition(4_000))
        assertEquals(0L, Review.startPosition(0))
    }

    @Test fun `the first review remembers where the listener was`() {
        val info = Review.begin(null, currentId = 31, currentPositionMs = 760_000, currentTitle = "Day 31", targetEpisodeId = 27)!!
        assertEquals(31L, info.returnEpisodeId)
        assertEquals(760_000L, info.returnPositionMs)
        assertEquals("Day 31", info.returnTitle)
        assertEquals(27L, info.reviewEpisodeId)
    }

    @Test fun `a second review keeps the original place to return to`() {
        val first = Review.begin(null, 31, 760_000, "Day 31", 27)
        // Now playing episode 27, at some position; reviewing an aside in episode 25.
        val second = Review.begin(first, 27, 90_000, "Day 27", 25)!!
        assertEquals(31L, second.returnEpisodeId)
        assertEquals(760_000L, second.returnPositionMs)
        assertEquals(25L, second.reviewEpisodeId)
    }

    @Test fun `with nothing loaded there is nothing to return to`() {
        assertNull(Review.begin(null, null, 0, "", 27))
    }
}
