package com.asides.app.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/** A finished DownloadManager transfer: move the file into place so the episode shows as downloaded. */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        Downloads.init(context)
    }
}

/**
 * When a car's Bluetooth audio (not a headset, earbuds or speaker) connects and the user has asked for it
 * in Settings, press "play" on the playback service, which resumes the last episode where it was left.
 */
class BluetoothResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) return
        if (!isCar(intent)) return
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(RESUME_PREF, false)) return
        if (prefs.getLong(PlaybackService.LAST_EPISODE_PREF, -1L) < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        // Give the audio route a moment to switch to the new device before sound starts.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val press = Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClass(app, PlaybackService::class.java)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                ContextCompat.startForegroundService(app, press)
            } catch (e: Exception) {
                // Android may refuse to start playback from the background; nothing else to do.
                Log.w("BluetoothResume", "Couldn't resume: $e")
            } finally {
                pending.finish()
            }
        }, 1500)
    }

    /** Only a device that identifies itself as car audio counts; headsets and speakers must not auto-start. */
    private fun isCar(intent: Intent): Boolean {
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return try {
            device?.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val RESUME_PREF = "resumeOnBluetooth"
    }
}
