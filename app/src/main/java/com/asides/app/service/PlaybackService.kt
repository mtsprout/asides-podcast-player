package com.asides.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSessionService
import com.asides.app.AsidesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the player and the media session. Because this is a foreground service it keeps running
 * with the screen off, and the session receives headset button events there.
 *
 * Headset gestures (play/pause button):
 *  - 1 press: play/pause, 2 presses: skip forward 30s
 *  - 3 presses: start a voice annotation (pauses playback and records)
 *  - any press while recording: stop and save the annotation, then resume
 */
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var recorder: AnnotationRecorder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val counter = PressCounter(PRESS_WINDOW_MS)

    private var pendingNote: PendingNote? = null
    private var resolvePress: Runnable? = null
    private var gestureAt = -STRAY_MS

    private data class PendingNote(val episodeId: Long, val positionMs: Long, val wasPlaying: Boolean)

    private val savePosition = object : Runnable {
        override fun run() {
            persistPosition()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val autoStop = Runnable { finishAnnotation() }

    override fun onCreate() {
        super.onCreate()
        val app = application as AsidesApp
        val repo = app.repo
        recorder = AnnotationRecorder(repo.annotationDir)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(30_000)
            .setSeekBackIncrementMs(15_000)
            .build()
        // Restore the speed chosen last time.
        player.setPlaybackSpeed(getSharedPreferences("prefs", MODE_PRIVATE).getFloat("speed", 1f))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                // "Sleep at the end of the episode" means stop here, not carry on to the next one.
                val sleepAtEnd = prefs.getBoolean(SleepTimer.PREF_END_OF_EPISODE, false)
                clearSleepTimer()
                val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                scope.launch {
                    repo.markFinished(id)
                    if (!sleepAtEnd && prefs.getBoolean(Autoplay.PREF, true)) playNextAfter(id, prefs.getBoolean("newestFirst", false))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) handler.post(savePosition) else {
                    handler.removeCallbacks(savePosition)
                    persistPosition()
                }
            }
        })

        clearSleepTimer()
        scope.launch { repo.transcribePending(app.transcriber) }

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY)).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == SleepTimer.COMMAND) {
                        setSleepTimer(args.getInt(SleepTimer.ARG_MINUTES))
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent,
                ): Boolean {
                    val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        ?: return false
                    Log.d(TAG, "media button: ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action}")
                    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
                    val now = SystemClock.elapsedRealtime()
                    return when (event.keyCode) {
                        // Many earbuds send "previous" for a triple-tap. Left to the default it would
                        // restart the episode, so treat it as the annotation gesture instead.
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            gestureAt = now
                            resolvePress?.let(handler::removeCallbacks)
                            resolvePress = null
                            counter.reset()
                            if (recorder.isRecording) finishAnnotation() else startAnnotation()
                            true
                        }
                        // ...and "next" for a double-tap.
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            gestureAt = now
                            if (recorder.isRecording) finishAnnotation() else player.seekForward()
                            true
                        }
                        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> when {
                            // Earbuds follow a multi-tap with a stray play/pause; swallow it.
                            now - gestureAt < STRAY_MS -> true
                            // While recording, any press means "stop". Headsets send play or pause
                            // here depending on what they think the state is.
                            recorder.isRecording -> { finishAnnotation(); true }
                            // Wired headsets send one key for every press, so count them for a triple-press.
                            event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                                event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { onButtonPress(); true }
                            // Bluetooth headsets send explicit play or pause: the default handling is right.
                            else -> false
                        }
                        else -> false
                    }
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun onButtonPress() {
        resolvePress?.let(handler::removeCallbacks)
        resolvePress = null

        if (recorder.isRecording) {
            counter.reset()
            finishAnnotation()
            return
        }

        val n = counter.press(System.currentTimeMillis())
        if (n >= 3) {
            counter.reset()
            startAnnotation()
            return
        }
        // Wait out the window to see if more presses follow before acting on 1 or 2.
        val r = Runnable {
            resolvePress = null
            counter.reset()
            when (n) {
                1 -> if (player.isPlaying) player.pause() else player.play()
                2 -> player.seekForward()
            }
        }
        resolvePress = r
        handler.postDelayed(r, PRESS_WINDOW_MS)
    }

    private fun startAnnotation() {
        val episodeId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val note = PendingNote(episodeId, player.currentPosition, player.isPlaying)
        player.pause()
        if (recorder.start() == null) {
            // Couldn't open the mic (e.g. permission missing): put things back as they were.
            if (note.wasPlaying) player.play()
            return
        }
        pendingNote = note
        cue(ToneGenerator.TONE_PROP_BEEP, longArrayOf(0, 80))
        handler.postDelayed(autoStop, MAX_NOTE_MS)
    }

    private fun finishAnnotation() {
        handler.removeCallbacks(autoStop)
        val note = pendingNote ?: return
        pendingNote = null
        val file = recorder.stop()
        cue(ToneGenerator.TONE_PROP_ACK, longArrayOf(0, 40, 60, 40))
        if (file != null) {
            val repo = (application as AsidesApp).repo
            scope.launch {
                repo.addAnnotation(note.episodeId, note.positionMs, file)
                repo.transcribePending((application as AsidesApp).transcriber)
            }
        }
        // Resume a few seconds back so the listener regains context.
        if (note.wasPlaying) {
            player.seekTo((note.positionMs - REWIND_MS).coerceAtLeast(0))
            player.play()
        }
    }

    private fun cue(tone: Int, vibration: LongArray) {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(tone, 150)
        } catch (_: RuntimeException) {
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(vibration, -1))
    }

    private val sleepFired = Runnable {
        player.pause()
        clearSleepTimer()
    }

    /** [minutes] > 0 pauses after that long, -1 pauses at the end of the episode, 0 cancels. */
    private fun setSleepTimer(minutes: Int) {
        clearSleepTimer()
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE).edit()
        when {
            minutes > 0 -> {
                handler.postDelayed(sleepFired, minutes * 60_000L)
                prefs.putLong(SleepTimer.PREF_AT, System.currentTimeMillis() + minutes * 60_000L)
            }
            minutes < 0 -> {
                player.pauseAtEndOfMediaItems = true
                prefs.putBoolean(SleepTimer.PREF_END_OF_EPISODE, true)
            }
        }
        prefs.apply()
    }

    private fun clearSleepTimer() {
        handler.removeCallbacks(sleepFired)
        player.pauseAtEndOfMediaItems = false
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .remove(SleepTimer.PREF_AT).remove(SleepTimer.PREF_END_OF_EPISODE).apply()
    }

    /** Starts the next unplayed episode after [finishedId], if there is one. Runs off the main thread. */
    private suspend fun playNextAfter(finishedId: Long, newestFirst: Boolean) {
        val repo = (application as AsidesApp).repo
        val next = repo.nextEpisode(finishedId, newestFirst) ?: return
        val item = mediaItemFor(next, repo.podcastNow(next.podcastId))
        repo.markSeen(next.id)
        Log.d(TAG, "autoplay: ${next.title}")
        withContext(Dispatchers.Main) {
            // Only if nothing else has been started in the meantime.
            if (player.currentMediaItem?.mediaId?.toLongOrNull() == finishedId && player.playbackState == Player.STATE_ENDED) {
                player.setMediaItem(item, next.positionMs)
                player.prepare()
                player.play()
            }
        }
    }

    private fun persistPosition() {
        // At the end the position is reset by markFinished; don't overwrite it with the final timestamp.
        if (player.playbackState == Player.STATE_ENDED) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val repo = (application as AsidesApp).repo
        scope.launch { repo.savePosition(id, pos, dur) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recorder.stop()
        persistPosition()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Asides"
        const val PRESS_WINDOW_MS = 500L
        const val STRAY_MS = 1_200L
        const val MAX_NOTE_MS = 60_000L
        const val SAVE_INTERVAL_MS = 10_000L
        const val REWIND_MS = 3_000L
    }
}
