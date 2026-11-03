package com.asides.app

import android.bluetooth.BluetoothClass
import com.asides.app.service.AudioKind
import com.asides.app.service.EpisodeSteps
import com.asides.app.service.WalkRules
import com.asides.app.service.WalkTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkTrackerTest {
    private val min = 60_000L

    @Test fun firstReadingIsOnlyAStartingPoint() {
        val t = WalkTracker()
        t.play(0, 1)
        t.steps(5_000, 1_000)
        t.steps(5_120, 61_000)
        assertEquals(120L, t.current!!.steps)
        assertEquals(61_000L, t.current!!.activeMs)
    }

    @Test fun stepsWhilePausedDontCount() {
        val t = WalkTracker()
        t.play(0, 1)
        t.steps(100, 0)
        t.steps(200, min)
        t.pause(min)
        t.steps(900, 2 * min) // ignored: paused
        t.play(3 * min, 1)
        t.steps(1_000, 3 * min) // new starting point
        t.steps(1_050, 4 * min)
        assertEquals(150L, t.current!!.steps)
        assertEquals(2 * min, t.current!!.activeMs)
    }

    @Test fun shortPauseKeepsTheSameWalk() {
        val t = WalkTracker()
        t.play(0, 1)
        t.pause(10 * min)
        assertNull(t.expire(14 * min))
        assertNull(t.play(14 * min, 1))
        assertEquals(0L, t.current!!.startedAt)
    }

    @Test fun longPauseEndsTheWalk() {
        val t = WalkTracker()
        t.play(0, 1)
        t.steps(0, 0); t.steps(500, 10 * min)
        t.pause(10 * min)
        val ended = t.expire(16 * min)
        assertNotNull(ended)
        assertEquals(500L, ended!!.steps)
        assertEquals(10 * min, ended.endedAt)
        assertNull(t.current)
    }

    @Test fun playingAfterALongPauseClosesTheOldWalkAndStartsANewOne() {
        val t = WalkTracker()
        t.play(0, 1)
        t.pause(10 * min)
        val closed = t.play(30 * min, 2)
        assertEquals(0L, closed!!.startedAt)
        assertEquals(30 * min, t.current!!.startedAt)
    }

    @Test fun stepsAreCreditedToTheEpisodePlaying() {
        val t = WalkTracker()
        t.play(0, 1)
        t.steps(0, 0)
        t.steps(300, 5 * min)
        t.episodeChanged(5 * min, 2)
        t.steps(500, 8 * min)
        val w = t.current!!
        assertEquals(500L, w.steps)
        assertEquals(EpisodeSteps(300, 5 * min), w.episodes[1])
        assertEquals(EpisodeSteps(200, 3 * min), w.episodes[2])
    }

    @Test fun aRestartedPhoneCounterStartsOverWithoutLosingTheWalk() {
        val t = WalkTracker()
        t.play(0, 1)
        t.steps(9_000, 0)
        t.steps(9_200, min)
        t.steps(10, 2 * min) // counter went down: phone restarted
        t.steps(60, 3 * min)
        assertEquals(250L, t.current!!.steps)
    }

    @Test fun endClosesImmediately() {
        val t = WalkTracker()
        t.play(0, 1)
        assertTrue(t.isCounting)
        val ended = t.end(2 * min)
        assertEquals(2 * min, ended!!.activeMs)
        assertFalse(t.isCounting)
        assertNull(t.current)
        assertNull(t.end(3 * min))
    }

    @Test fun aSavedWalkCarriesOnAfterARestart() {
        val saved = com.asides.app.service.WalkSnapshot(0, 10 * min, 1_000, 10 * min, mapOf(1L to EpisodeSteps(1_000, 10 * min)))
        val t = WalkTracker()
        assertTrue(t.restore(saved, 12 * min))
        t.play(12 * min, 1)
        t.steps(50, 12 * min); t.steps(250, 14 * min)
        val w = t.current!!
        assertEquals(0L, w.startedAt)
        assertEquals(1_200L, w.steps)
        assertEquals(EpisodeSteps(1_200, 12 * min), w.episodes[1])
    }

    @Test fun aSavedWalkFromTooLongAgoIsLeftAlone() {
        val saved = com.asides.app.service.WalkSnapshot(0, 10 * min, 1_000, 10 * min, emptyMap())
        val t = WalkTracker()
        assertFalse(t.restore(saved, 20 * min))
        assertNull(t.current)
    }

    @Test fun bluetoothKinds() {
        assertEquals(AudioKind.Car, WalkRules.bluetoothKind(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
        assertEquals(AudioKind.Speaker, WalkRules.bluetoothKind(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER))
        assertEquals(AudioKind.Headphones, WalkRules.bluetoothKind(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET))
        assertEquals(AudioKind.Headphones, WalkRules.bluetoothKind(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES))
        assertEquals(AudioKind.Headphones, WalkRules.bluetoothKind(null))
    }
}
