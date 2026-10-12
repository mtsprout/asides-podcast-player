package com.asides.app.service

/**
 * Counts rapid presses of a headset button. A press arriving within [windowMs] of the previous
 * one extends the run; otherwise a new run starts at 1.
 */
class PressCounter(private val windowMs: Long = 500) {
    private var count = 0
    private var lastMs = Long.MIN_VALUE

    /** Registers a press at [nowMs] and returns the length of the current run. */
    fun press(nowMs: Long): Int {
        count = if (lastMs != Long.MIN_VALUE && nowMs - lastMs <= windowMs) count + 1 else 1
        lastMs = nowMs
        return count
    }

    fun reset() {
        count = 0
        lastMs = Long.MIN_VALUE
    }
}
