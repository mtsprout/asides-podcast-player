package com.asides.app.data

import android.content.SharedPreferences

/** Settings that belong to one podcast. [speed] null means "use my normal speed". */
data class PodcastSettings(
    val speed: Float? = null,
    val skipIntroSec: Int = 0,
    val autoDownload: Boolean = false,
)

object StartPosition {
    const val MAX_SKIP_SEC = 180

    /**
     * Where an episode should start. An episode with a saved place starts there. A fresh one (at the
     * very start) skips [skipIntroSec] seconds, unless that would leave next to nothing to hear.
     */
    fun of(positionMs: Long, durationMs: Long, skipIntroSec: Int): Long {
        if (positionMs > 0 || skipIntroSec <= 0) return positionMs
        val skip = skipIntroSec * 1000L
        return if (durationMs > 0 && skip >= durationMs - 10_000) 0L else skip
    }
}

/**
 * Per-podcast settings, in their own small preferences file (not the database, so there is nothing
 * to migrate). Keyed by the podcast's id.
 */
class PodcastSettingsStore(private val prefs: SharedPreferences) {
    fun get(podcastId: Long) = PodcastSettings(
        speed = if (prefs.contains("speed.$podcastId")) prefs.getFloat("speed.$podcastId", 1f) else null,
        skipIntroSec = prefs.getInt("skip.$podcastId", 0),
        autoDownload = prefs.getBoolean("dl.$podcastId", false),
    )

    fun setSpeed(podcastId: Long, speed: Float?) = prefs.edit().apply {
        if (speed == null) remove("speed.$podcastId") else putFloat("speed.$podcastId", speed)
    }.apply()

    fun setSkipIntro(podcastId: Long, seconds: Int) =
        prefs.edit().putInt("skip.$podcastId", seconds.coerceIn(0, StartPosition.MAX_SKIP_SEC)).apply()

    fun setAutoDownload(podcastId: Long, on: Boolean) = prefs.edit().putBoolean("dl.$podcastId", on).apply()

    /** Where [episode] should start playing: its saved place, or past the intro if it is fresh. */
    fun startPositionMs(episode: Episode) =
        StartPosition.of(episode.positionMs, episode.durationMs, get(episode.podcastId).skipIntroSec)
}
