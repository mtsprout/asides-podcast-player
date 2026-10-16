package com.asides.app

import com.asides.app.data.StartPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class StartPositionTest {
    @Test fun aFreshEpisodeSkipsTheIntro() {
        assertEquals(30_000L, StartPosition.of(0, 1_800_000, 30))
    }

    @Test fun anEpisodeWithASavedPlaceStartsThere() {
        assertEquals(95_000L, StartPosition.of(95_000, 1_800_000, 30))
    }

    @Test fun noSkipSettingChangesNothing() {
        assertEquals(0L, StartPosition.of(0, 1_800_000, 0))
    }

    @Test fun neverSkipsPastTheEndOfAShortEpisode() {
        assertEquals(0L, StartPosition.of(0, 25_000, 30))
        assertEquals(30_000L, StartPosition.of(0, 0, 30)) // length unknown: trust the setting
    }
}
