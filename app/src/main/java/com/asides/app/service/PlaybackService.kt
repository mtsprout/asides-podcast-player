package com.asides.app.service

import android.app.PendingIntent
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
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.asides.app.MainActivity
import androidx.media3.session.MediaNotification
import com.asides.app.R
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.session.MediaSessionService
import com.asides.app.AsidesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The playback notification, laid out like the lock-screen card: start, -10, play/pause, +30, next
 * episode. (The session lists the custom actions as: -10, +30, start, next.) Collapsed, it shows the
 * middle three.
 */
@OptIn(UnstableApi::class)
private class AsidesNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        if (customLayout.size != 4) return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
        val playPause = CommandButton.Builder(if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY)
            .setDisplayName(if (showPauseButton) "Pause" else "Play")
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()
        return ImmutableList.of(customLayout[2], customLayout[0], playPause, customLayout[1], customLayout[3])
    }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        val defaults = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        return if (mediaButtons.size == 5) intArrayOf(1, 2, 3) else defaults
    }
}

/**
 * The player as the media session shows it to the system: without the standard "previous" and "next"
 * commands, so the lock-screen card uses those slots for our own buttons instead.
 */
private class WithoutSkipCommands(player: Player) : ForwardingPlayer(player) {
    private val hidden = intArrayOf(
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().removeAll(*hidden).build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
}

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

    /**
     * True from the end of an episode until autoplay has started the next one. Android only lets a
     * service become foreground again while the app is visible, so with the screen off, letting go at
     * the end of an episode meant the next one could not take over. Media3 would stop being foreground
     * when playback ends; this keeps it so through the handoff.
     */
    @Volatile private var holdForeground = false

    /** True while the service itself swaps the episode, so the listener doesn't mistake it for the user's. */
    private var ourOwnChange = false
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
            .setSeekBackIncrementMs(10_000)
            .build()
        // Restore the speed chosen last time.
        player.setPlaybackSpeed(getSharedPreferences("prefs", MODE_PRIVATE).getFloat("speed", 1f))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) { holdForeground = false; return }
                // A reviewed episode finishing means "back to where I was", not "on to the next one".
                if (Review.info != null) { returnFromReview(); return }
                val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                // "Sleep at the end of the episode" means stop here, not carry on to the next one.
                val sleepAtEnd = prefs.getBoolean(SleepTimer.PREF_END_OF_EPISODE, false)
                holdForeground = !sleepAtEnd && prefs.getBoolean(Autoplay.PREF, true)
                clearSleepTimer()
                val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                scope.launch {
                    repo.markFinished(id)
                    if (!sleepAtEnd && prefs.getBoolean(Autoplay.PREF, true)) playNextAfter(id, prefs.getBoolean("newestFirst", false))
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Remember the episode the moment it is loaded (autoplay included), not only at the next save,
                // so a play press after Android stops this service resumes the right one.
                if (Review.info == null) mediaItem?.mediaId?.toLongOrNull()?.let {
                    getSharedPreferences("prefs", MODE_PRIVATE).edit().putLong(LAST_EPISODE_PREF, it).commit()
                    // Whatever starts playing leaves the queue: it is no longer "up next".
                    repo.removeFromQueue(it)
                }
                // Something else was started (not by us): the review is over, and there is no going back.
                val review = Review.info ?: return
                if (!ourOwnChange && mediaItem?.mediaId?.toLongOrNull() != review.reviewEpisodeId) Review.info = null
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) handler.post(savePosition) else {
                    handler.removeCallbacks(savePosition)
                    persistPosition()
                    // The speech service is flaky and a failed transcript is only retried when asked, so
                    // catch up on any aside still waiting whenever playback pauses (but not mid-recording).
                    if (pendingNote == null && !recorder.isRecording) {
                        scope.launch { (application as AsidesApp).repo.transcribePending((application as AsidesApp).transcriber) }
                    }
                }
            }
        })

        clearSleepTimer()
        scope.launch { repo.transcribePending(app.transcriber) }

        // The lock-screen card is built from the session's playback state. Android puts the standard
        // previous/next actions beside play/pause, and fills the remaining slots from the custom actions
        // in order: first the left-of-centre slot, then right-of-centre, then the two outer slots.
        // With previous/next withdrawn (see [WithoutSkipCommands]) the card therefore reads
        //     start  -10  play/pause  +30  next episode
        // when the custom actions are listed as: -10, +30, start, next.
        fun button(icon: Int, name: String, command: String) = CommandButton.Builder(icon)
            .setDisplayName(name)
            .setSessionCommand(SessionCommand(command, Bundle.EMPTY))
            .build()
        val buttons = ImmutableList.of(
            button(CommandButton.ICON_SKIP_BACK_10, "Back 10 seconds", PlayerCommands.BACK_10),
            button(CommandButton.ICON_SKIP_FORWARD_30, "Forward 30 seconds", PlayerCommands.FORWARD_30),
            button(CommandButton.ICON_PREVIOUS, "Back to the beginning", PlayerCommands.RESTART),
            button(CommandButton.ICON_NEXT, "Next episode", PlayerCommands.NEXT_EPISODE),
        )

        setMediaNotificationProvider(AsidesNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_notification) })

        @Suppress("DEPRECATION") // the ordered list is what the system card reads
        session = MediaSession.Builder(this, WithoutSkipCommands(player))
            .setCustomLayout(buttons)
            // Tapping the notification (or Samsung's status-bar chip) opens the app.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.BACK_10, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.FORWARD_30, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.RESTART, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.NEXT_EPISODE, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.START_REVIEW, Bundle.EMPTY))
                        .add(SessionCommand(PlayerCommands.END_REVIEW, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }

                // The system asks for this when a Bluetooth device (the truck) or the media controls press
                // play after this service was stopped: pick up the last episode where it was left.
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    scope.launch {
                        val repo = (application as AsidesApp).repo
                        val lastId = getSharedPreferences("prefs", MODE_PRIVATE).getLong(LAST_EPISODE_PREF, -1L)
                        val newestFirst = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("newestFirst", false)
                        val episode = repo.resumeEpisode(lastId, newestFirst)
                        Log.d(TAG, "resumption: last $lastId, resuming ${episode?.id} at ${episode?.positionMs}")
                        if (episode == null) {
                            result.setException(UnsupportedOperationException("Nothing to resume"))
                        } else {
                            val item = mediaItemFor(episode, repo.podcastNow(episode.podcastId))
                            result.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, episode.positionMs))
                        }
                    }
                    return result
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        SleepTimer.COMMAND -> setSleepTimer(args.getInt(SleepTimer.ARG_MINUTES))
                        PlayerCommands.BACK_10 -> player.seekBack()
                        PlayerCommands.FORWARD_30 -> player.seekForward()
                        PlayerCommands.RESTART -> player.seekTo(0)
                        PlayerCommands.NEXT_EPISODE -> skipToNextEpisode()
                        PlayerCommands.START_REVIEW ->
                            startReview(args.getLong(PlayerCommands.ARG_EPISODE_ID), args.getLong(PlayerCommands.ARG_POSITION_MS))
                        PlayerCommands.END_REVIEW -> returnFromReview()
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
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

    /** The "next episode" button: keep our place in this episode, then start the next unplayed one. */
    private fun skipToNextEpisode() {
        if (Review.info != null) { returnFromReview(); return }
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val newestFirst = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("newestFirst", false)
        scope.launch {
            (application as AsidesApp).repo.savePosition(id, position, duration)
            playNextAfter(id, newestFirst, onlyIfEnded = false)
        }
    }

    /**
     * Starts the next unplayed episode after [finishedId], if there is one. Runs off the main thread.
     * With [onlyIfEnded] it does nothing unless that episode has actually finished (autoplay).
     */
    private suspend fun playNextAfter(finishedId: Long, newestFirst: Boolean, onlyIfEnded: Boolean = true) {
        val repo = (application as AsidesApp).repo
        val next = repo.nextEpisode(finishedId, newestFirst)
        if (next == null) { releaseForegroundHold(); return }
        val item = mediaItemFor(next, repo.podcastNow(next.podcastId))
        repo.markSeen(next.id)
        Log.d(TAG, "autoplay: ${next.title}")
        withContext(Dispatchers.Main) {
            // Only if nothing else has been started in the meantime.
            val stillThere = player.currentMediaItem?.mediaId?.toLongOrNull() == finishedId
            if (stillThere && (!onlyIfEnded || player.playbackState == Player.STATE_ENDED)) {
                player.setMediaItem(item, next.positionMs)
                player.prepare()
                player.play()
            } else releaseForegroundHold()
        }
    }

    /** Nothing is going to play next after all: let the service stop being foreground, as it normally would. */
    private suspend fun releaseForegroundHold() = withContext(Dispatchers.Main) {
        if (!holdForeground) return@withContext
        holdForeground = false
        if (player.playbackState == Player.STATE_ENDED) ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || holdForeground)
    }

    /**
     * Plays the aside at [asideMs] in [episodeId] (starting a little earlier), remembering where the
     * listener was so [returnFromReview] can put them back.
     */
    private fun startReview(episodeId: Long, asideMs: Long) {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val currentPosition = player.currentPosition
        val currentDuration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val currentTitle = player.mediaMetadata.title?.toString().orEmpty()
        val start = Review.startPosition(asideMs)
        scope.launch {
            val repo = (application as AsidesApp).repo
            val episode = repo.episodeNow(episodeId) ?: return@launch
            val item = mediaItemFor(episode, repo.podcastNow(episode.podcastId))
            // Keep the place being left, exactly (not only as of the last periodic save).
            if (Review.info == null && currentId != null) repo.savePosition(currentId, currentPosition, currentDuration)
            // With nothing loaded (the app was just opened), the place to return to is the last episode
            // listened to, at its saved position.
            var placeId = currentId
            var placePosition = currentPosition
            var placeTitle = currentTitle
            if (currentId == null && Review.info == null) {
                val lastId = getSharedPreferences("prefs", MODE_PRIVATE).getLong(LAST_EPISODE_PREF, -1L)
                repo.episodeNow(lastId)?.let { placeId = it.id; placePosition = it.positionMs; placeTitle = it.title }
            }
            withContext(Dispatchers.Main) {
                val plan = Review.begin(Review.info, placeId, placePosition, placeTitle, episodeId)
                ourOwnChange = true
                try {
                    Review.info = plan
                    if (player.currentMediaItem?.mediaId?.toLongOrNull() == episodeId) {
                        player.seekTo(start)
                    } else {
                        player.setMediaItem(item, start)
                        player.prepare()
                    }
                    player.play()
                } finally {
                    ourOwnChange = false
                }
            }
        }
    }

    /** Ends a review: back to the episode and spot the listener left, paused. */
    private fun returnFromReview() {
        val review = Review.info ?: return
        scope.launch {
            val repo = (application as AsidesApp).repo
            val episode = repo.episodeNow(review.returnEpisodeId)
            val item = episode?.let { mediaItemFor(it, repo.podcastNow(it.podcastId)) }
            withContext(Dispatchers.Main) {
                ourOwnChange = true
                try {
                    player.playWhenReady = false
                    if (item != null) {
                        if (player.currentMediaItem?.mediaId?.toLongOrNull() == review.returnEpisodeId) {
                            player.seekTo(review.returnPositionMs)
                        } else {
                            player.setMediaItem(item, review.returnPositionMs)
                            player.prepare()
                        }
                    }
                } finally {
                    // Cleared last, so the pause above isn't saved as the reviewed episode's position.
                    Review.info = null
                    ourOwnChange = false
                }
            }
        }
    }

    private fun persistPosition() {
        // A review changes nothing about the episode being reviewed.
        if (Review.info != null) return
        // At the end the position is reset by markFinished; don't overwrite it with the final timestamp.
        if (player.playbackState == Player.STATE_ENDED) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val repo = (application as AsidesApp).repo
        // Remembered so a review can find the listener's place even when nothing is loaded in the player.
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putLong(LAST_EPISODE_PREF, id).apply()
        scope.launch { repo.savePosition(id, pos, dur) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Paused with an episode loaded: stay alive, so the notification (and the status-bar chip) stay
        // and the headset or the truck can resume. Only stop when there is nothing to come back to.
        if (player.mediaItemCount == 0) stopSelf()
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

    companion object {
        private const val TAG = "Asides"
        /** The episode the listener was last in, for finding their place after the app was closed. */
        const val LAST_EPISODE_PREF = "lastEpisodeId"
        private const val PRESS_WINDOW_MS = 500L
        private const val STRAY_MS = 1_200L
        private const val MAX_NOTE_MS = 60_000L
        private const val SAVE_INTERVAL_MS = 10_000L
        private const val REWIND_MS = 3_000L
    }
}
