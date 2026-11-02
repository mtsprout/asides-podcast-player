package com.asides.app.service

import android.bluetooth.BluetoothClass

/** Steps and listening time one episode got during a walk. */
data class EpisodeSteps(val steps: Long = 0, val activeMs: Long = 0)

/**
 * A walk as it stands. [startedAt] (epoch ms) also identifies it; [endedAt] is the last moment it was
 * playing, so a walk saved part-way through is always complete up to there.
 */
data class WalkSnapshot(
    val startedAt: Long,
    val endedAt: Long,
    val steps: Long,
    val activeMs: Long,
    val episodes: Map<Long, EpisodeSteps>,
)

/**
 * Turns playback events and step-counter readings into walks. Steps count only while playing; a pause
 * of up to [graceMs] (a crossing, a chat) keeps the same walk going, a longer one ends it.
 *
 * The step counter reports a running total since the phone started, so each reading is compared with
 * the one before. Every time playback starts the first reading is only a starting point: steps taken
 * while paused don't count. A total that goes down means the phone restarted; it becomes the new start.
 *
 * No Android calls here (only constants), so it can be unit-tested; [WalkSession] feeds it.
 */
class WalkTracker(private val graceMs: Long = GRACE_MS) {
    private class Building(val startedAt: Long) {
        var endedAt = startedAt
        var steps = 0L
        var activeMs = 0L
        val episodes = mutableMapOf<Long, EpisodeSteps>()

        fun credit(episodeId: Long, steps: Long, ms: Long) {
            val e = episodes[episodeId] ?: EpisodeSteps()
            episodes[episodeId] = EpisodeSteps(e.steps + steps, e.activeMs + ms)
        }

        fun snapshot() = WalkSnapshot(startedAt, endedAt, steps, activeMs, episodes.toMap())
    }

    private var walk: Building? = null
    private var lastCounter: Long? = null
    private var episodeId: Long? = null
    private var activeSince = 0L

    /** True while a walk is being counted (playing on headphones). */
    var isCounting = false
        private set

    /** The walk in progress (counting or paused within the grace period), if any. */
    val current: WalkSnapshot? get() = walk?.snapshot()

    /**
     * Playback started on headphones. Returns the previous walk if this start ended it, because the
     * pause before it was longer than the grace period; the caller saves it.
     */
    fun play(now: Long, episode: Long?): WalkSnapshot? {
        if (isCounting) { episodeChanged(now, episode); return null }
        var closed: WalkSnapshot? = null
        walk?.let { if (now - it.endedAt > graceMs) { closed = it.snapshot(); walk = null } }
        if (walk == null) walk = Building(now)
        isCounting = true
        activeSince = now
        lastCounter = null
        episodeId = episode
        return closed
    }

    /** A step-counter reading: [counter] is the phone's running total. */
    fun steps(counter: Long, now: Long) {
        val w = walk ?: return
        if (!isCounting) return
        val last = lastCounter
        lastCounter = counter
        if (last != null && counter >= last) {
            val delta = counter - last
            w.steps += delta
            episodeId?.let { w.credit(it, delta, 0) }
        }
        accrue(w, now)
    }

    /** A different episode started playing; what follows counts toward it. */
    fun episodeChanged(now: Long, episode: Long?) {
        walk?.takeIf { isCounting }?.let { accrue(it, now) }
        episodeId = episode
    }

    /** Playback paused. The walk stays open for the grace period. */
    fun pause(now: Long) {
        val w = walk ?: return
        if (!isCounting) return
        accrue(w, now)
        isCounting = false
        lastCounter = null
    }

    /** Ends a paused walk once the grace period has passed; returns it, or null if it isn't over yet. */
    fun expire(now: Long): WalkSnapshot? {
        val w = walk ?: return null
        if (isCounting || now - w.endedAt <= graceMs) return null
        walk = null
        return w.snapshot()
    }

    /** Ends the walk now (car audio connected, headphones gone, the service stopping). */
    fun end(now: Long): WalkSnapshot? {
        pause(now)
        val w = walk ?: return null
        walk = null
        return w.snapshot()
    }

    private fun accrue(w: Building, now: Long) {
        val ms = (now - activeSince).coerceAtLeast(0)
        w.activeMs += ms
        episodeId?.let { w.credit(it, 0, ms) }
        activeSince = now
        w.endedAt = maxOf(w.endedAt, now)
    }

    companion object {
        /** A pause up to this long keeps the same walk going. */
        const val GRACE_MS = 5 * 60_000L
        /** Shorter walks (listening at a desk, a trip to the kitchen) aren't kept. */
        const val MIN_STEPS = 100L
    }
}

/** What kind of Bluetooth audio device is playing, as far as walks care. */
enum class AudioKind { Headphones, Speaker, Car }

object WalkRules {
    /**
     * A Bluetooth device's kind from its class. Unknown or unusual classes count as headphones: plenty
     * of earbuds report something odd, and the step counter itself ignores driving.
     */
    fun bluetoothKind(deviceClass: Int?): AudioKind = when (deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> AudioKind.Car
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
        BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
        BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING -> AudioKind.Speaker
        else -> AudioKind.Headphones
    }
}
