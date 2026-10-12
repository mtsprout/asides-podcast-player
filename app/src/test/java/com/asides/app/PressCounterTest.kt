package com.asides.app

import com.asides.app.data.FeedParser
import com.asides.app.service.PressCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class PressCounterTest {
    @Test fun rapidPressesBuildARun() {
        val c = PressCounter(500)
        assertEquals(1, c.press(0))
        assertEquals(2, c.press(300))
        assertEquals(3, c.press(600))
    }

    @Test fun slowPressStartsOver() {
        val c = PressCounter(500)
        c.press(0)
        c.press(300)
        assertEquals(1, c.press(1000))
    }

    @Test fun resetClearsRun() {
        val c = PressCounter(500)
        c.press(0)
        c.reset()
        assertEquals(1, c.press(100))
    }

    @Test fun durations() {
        assertEquals(3_723_000L, FeedParser.parseDuration("1:02:03"))
        assertEquals(125_000L, FeedParser.parseDuration("02:05"))
        assertEquals(90_000L, FeedParser.parseDuration("90"))
        assertEquals(0L, FeedParser.parseDuration("abc"))
    }
}
