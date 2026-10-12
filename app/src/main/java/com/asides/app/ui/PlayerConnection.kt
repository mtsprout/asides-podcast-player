package com.asides.app.ui

import android.content.ComponentName
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.asides.app.service.Autoplay
import com.asides.app.service.SleepTimer
import com.asides.app.service.mediaItemFor
import com.google.common.util.concurrent.MoreExecutors
import com.asides.app.data.Episode
import com.asides.app.data.Podcast

/** UI-side handle on the playback service's player, plus a tiny player for annotation clips. */
class PlayerConnection(private val context: Context) {
    private var controller: MediaController? = null
    private var clip: MediaPlayer? = null

    var isPlaying by mutableStateOf(false)
        private set
    var currentEpisodeId by mutableLongStateOf(-1L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** Whether the next unplayed episode starts by itself when one finishes. The service reads the same setting. */
    var autoplay by mutableStateOf(context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean(Autoplay.PREF, true))
        private set

    /** Re-reads settings that a restore may have changed. */
    fun reloadSettings() {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        autoplay = prefs.getBoolean(Autoplay.PREF, true)
        changeSpeed(prefs.getFloat("speed", 1f))
    }

    fun toggleAutoplay() {
        autoplay = !autoplay
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean(Autoplay.PREF, autoplay).apply()
    }

    /** What the sleep timer is doing, for display: "12 min", "End of episode", or null when off. */
    var sleepLabel by mutableStateOf<String?>(null)
        private set

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, com.asides.app.service.PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also {
                it.addListener(listener)
                sync(it)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        stopClip()
        controller?.release()
        controller = null
    }

    /** Called periodically by the UI to refresh the position slider and sleep timer. */
    fun poll() {
        controller?.let(::sync)
        refreshSleep()
    }

    private fun refreshSleep() {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val at = prefs.getLong(SleepTimer.PREF_AT, 0L)
        sleepLabel = when {
            prefs.getBoolean(SleepTimer.PREF_END_OF_EPISODE, false) -> "End of episode"
            at > System.currentTimeMillis() -> "${(at - System.currentTimeMillis() + 59_999) / 60_000} min"
            else -> null
        }
    }

    /** Sets the sleep timer: [minutes] > 0, -1 for the end of the episode, 0 to turn it off. */
    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY),
            Bundle().apply { putInt(SleepTimer.ARG_MINUTES, minutes) },
        )
        // The service records it; give it a moment, then show what it stored.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refreshSleep() }, 300)
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        currentEpisodeId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        positionMs = p.currentPosition
        durationMs = p.duration.takeIf { it > 0 } ?: 0L
        speed = p.playbackParameters.speed
    }

    fun play(episode: Episode, podcast: Podcast? = null) {
        val c = controller ?: return
        if (currentEpisodeId != episode.id) {
            c.setMediaItem(mediaItemFor(episode, podcast), episode.positionMs)
            c.prepare()
        }
        c.play()
    }

    /** Sets the playback speed, kept within [MIN_SPEED], [MAX_SPEED] in 0.1 steps, and remembers it. */
    fun changeSpeed(value: Float) {
        val v = (Math.round(value.coerceIn(MIN_SPEED, MAX_SPEED) * 10) / 10f)
        controller?.setPlaybackSpeed(v)
        speed = v
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putFloat("speed", v).apply()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        controller?.let(::sync)
    }

    fun skip(deltaMs: Long) = seekTo((controller?.currentPosition ?: 0) + deltaMs)

    /** Plays a recorded annotation, pausing the episode first. */
    fun playClip(path: String) {
        controller?.pause()
        stopClip()
        clip = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { stopClip() }
            prepare()
            start()
        }
    }

    private fun stopClip() {
        clip?.release()
        clip = null
    }
}
