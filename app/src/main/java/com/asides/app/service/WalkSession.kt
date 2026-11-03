package com.asides.app.service

import android.Manifest
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.asides.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Counts steps while an episode plays on headphones, using the phone's step-counter chip (which counts
 * on its own, at very little cost to the battery). Lives inside [PlaybackService] and is told when
 * playback starts, pauses and changes episode; [WalkTracker] decides what makes a walk.
 *
 * Everything here runs on the main thread. Saves go through one background thread in order, so an
 * older snapshot can never land after a newer one.
 */
class WalkSession(private val context: Context, private val repo: Repository) : SensorEventListener {
    private val tracker = WalkTracker()
    private val handler = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val stepSensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val audio = context.getSystemService(AudioManager::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var playing = false
    private var episodeId: Long? = null
    private var listening = false

    /**
     * The newest saved walk, read once when the service starts. If Android stopped the service during a
     * pause, playing again within the grace period carries on with that walk instead of starting another.
     */
    private var resumable: WalkSnapshot? = null

    private val periodicSave = object : Runnable {
        override fun run() {
            tracker.current?.let(::save)
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val expire = Runnable {
        tracker.expire(System.currentTimeMillis())?.let { finish(it, "paused too long") }
    }

    // Car audio connecting, or the headphones going, while a walk is being counted ends it; headphones
    // coming back while still playing starts one.
    private val routes = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = reconsider()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = reconsider()
    }

    init {
        audio?.registerAudioDeviceCallback(routes, handler)
        writes.launch {
            val latest = try { repo.latestWalk() } catch (e: Exception) { null }
            handler.post { if (tracker.current == null) resumable = latest }
        }
    }

    fun playbackChanged(isPlaying: Boolean, episode: Long?) {
        playing = isPlaying
        episodeId = episode
        val now = System.currentTimeMillis()
        if (!isPlaying) {
            if (!tracker.isCounting) return
            tracker.pause(now)
            stopCounting()
            tracker.current?.let(::save)
            handler.removeCallbacks(expire)
            handler.postDelayed(expire, WalkTracker.GRACE_MS + 1_000)
            Log.d(TAG, "paused: ${tracker.current?.steps} steps so far")
            return
        }
        reconsider()
    }

    fun episodeChanged(episode: Long?) {
        episodeId = episode
        tracker.episodeChanged(System.currentTimeMillis(), episode)
    }

    /** Starts or ends counting to match what's playing, and where. */
    private fun reconsider() {
        val now = System.currentTimeMillis()
        val why = when {
            !playing -> return
            !enabled(context) -> "walks are off"
            stepSensor == null -> "no step counter"
            !permitted(context) -> "no activity permission"
            else -> route()
        }
        if (why == null) {
            if (tracker.isCounting) return
            handler.removeCallbacks(expire)
            resumable?.let { if (tracker.restore(it, now)) Log.d(TAG, "carrying on with the walk from ${time(it.startedAt)}") }
            resumable = null
            tracker.play(now, episodeId)?.let { finish(it, "paused too long") }
            startCounting()
            Log.d(TAG, "counting (walk from ${tracker.current?.startedAt?.let(::time)})")
        } else if (tracker.current != null) {
            stopCounting()
            tracker.end(now)?.let { finish(it, why) }
        }
    }

    /** Null when the audio is going to headphones; otherwise why it doesn't count. */
    private fun route(): String? {
        val ui = context.getSystemService(UiModeManager::class.java)
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_CAR) return "car mode"
        val outputs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return "no audio outputs"
        var headphones = false
        for (d in outputs) when (d.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_BLE_HEADSET -> headphones = true
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> when (WalkRules.bluetoothKind(bluetoothClass(d))) {
                AudioKind.Car -> return "car audio"
                AudioKind.Speaker -> {}
                AudioKind.Headphones -> if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) headphones = true
            }
        }
        return if (headphones) null else "not on headphones"
    }

    /** The Bluetooth device class behind an audio output, when Android lets us see it. */
    private fun bluetoothClass(d: AudioDeviceInfo): Int? {
        if (Build.VERSION.SDK_INT < 28) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        val address = d.address
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
        return try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.bluetoothClass?.deviceClass
        } catch (e: SecurityException) {
            null
        }
    }

    private fun startCounting() {
        if (!listening) listening = sensors?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, handler) == true
        handler.removeCallbacks(periodicSave)
        handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
    }

    private fun stopCounting() {
        if (listening) sensors?.unregisterListener(this)
        listening = false
        handler.removeCallbacks(periodicSave)
    }

    override fun onSensorChanged(event: SensorEvent) {
        tracker.steps(event.values[0].toLong(), System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun save(w: WalkSnapshot) {
        if (w.steps < WalkTracker.MIN_STEPS) return
        writes.launch {
            try {
                repo.saveWalk(w)
            } catch (e: Exception) {
                Log.w(TAG, "couldn't save walk ${w.startedAt}: $e")
            }
        }
    }

    private fun finish(w: WalkSnapshot, why: String) {
        val kept = w.steps >= WalkTracker.MIN_STEPS
        // The pause timer stops while the phone sleeps, so this can run well after the walk really ended.
        Log.d(
            TAG,
            "walk ${time(w.startedAt)}–${time(w.endedAt)} ended ($why): ${w.steps} steps in ${w.activeMs / 60_000} min, " +
                if (kept) "kept" else "too short, dropped",
        )
        save(w)
    }

    /** The service is going away: keep what was walked. */
    fun release() {
        handler.removeCallbacks(expire)
        stopCounting()
        tracker.end(System.currentTimeMillis())?.let { finish(it, "service stopped") }
        audio?.unregisterAudioDeviceCallback(routes)
    }

    private fun time(ms: Long) = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ms))

    companion object {
        private const val TAG = "AsidesWalk"
        private const val SAVE_INTERVAL_MS = 60_000L
        /** Shared with [com.asides.app.ui.AppSettings], which owns the switch. */
        const val PREF = "walksEnabled"

        fun enabled(context: Context) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean(PREF, false)

        fun hasStepCounter(context: Context) =
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun permitted(context: Context) = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
}
