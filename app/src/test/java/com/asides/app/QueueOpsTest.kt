package com.asides.app

import com.asides.app.data.QueueOps
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOpsTest {
    @Test fun addedEpisodesGoToTheTopInTheOrderGiven() {
        assertEquals(listOf(7L, 8L, 1L, 2L), QueueOps.addToTop(listOf(1, 2), listOf(7, 8)))
    }

    @Test fun anEpisodeAlreadyQueuedMovesUpInsteadOfRepeating() {
        assertEquals(listOf(3L, 1L, 2L), QueueOps.addToTop(listOf(1, 2, 3), listOf(3)))
        assertEquals(listOf(2L, 1L), QueueOps.addToTop(listOf(1, 2), listOf(2, 2)))
    }

    @Test fun appendGoesToTheEndSkippingRepeats() {
        assertEquals(listOf(1L, 2L, 3L, 4L), QueueOps.append(listOf(1L, 2L), listOf(2L, 3L, 3L, 4L)))
    }

    @Test fun moveShiftsOneEntry() {
        val q = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(1L, 3L, 2L, 4L), QueueOps.move(q, 2, 1))
        assertEquals(listOf(2L, 3L, 4L, 1L), QueueOps.move(q, 0, 3))
    }

    @Test fun moveOutOfRangeChangesNothing() {
        val q = listOf(1L, 2L)
        assertEquals(q, QueueOps.move(q, 0, 2))
        assertEquals(q, QueueOps.move(q, -1, 0))
        assertEquals(q, QueueOps.move(q, 1, 1))
    }

    @Test fun saveAndLoadRoundTrip() {
        val q = listOf(5L, 9L, 2L)
        assertEquals(q, QueueOps.decode(QueueOps.encode(q)))
        assertEquals(emptyList<Long>(), QueueOps.decode(null))
        assertEquals(listOf(1L, 2L), QueueOps.decode("1,x,2,,1"))
    }
}
