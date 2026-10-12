package com.asides.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Turns a recorded WAV note into text with Android's speech recognizer, feeding it the audio
 * from the file (Android 13+) rather than the microphone, so it works after the fact.
 */
class Transcriber(private val context: Context) {

    /** Returns the text, "" if no speech was recognized, or null if recognition couldn't run. */
    suspend fun transcribe(wav: File): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(TIMEOUT_MS) { recognize(wav) }
        }
    }

    private suspend fun recognize(wav: File): String? = suspendCancellableCoroutine { cont ->
        // The system default recognizer reads supplied audio reliably. The on-device one heard speech
        // but returned no text on the test phone, so it is only a fallback.
        val onDevice = Build.VERSION.SDK_INT >= 31 && !SpeechRecognizer.isRecognitionAvailable(context) &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        Log.d(TAG, "transcribing ${wav.name} (${wav.length()} bytes) with ${if (onDevice) "on-device" else "default"} recognizer")
        val recognizer = when {
            onDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> {
                Log.w(TAG, "no speech recognizer available")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val reader = pipe[0]
        val writer = pipe[1]

        recognizer.setRecognitionListener(object : RecognitionListener {
            // The final result can come back empty even when partial results had the text.
            private var lastPartial = ""

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    .orEmpty().ifBlank { lastPartial }
                Log.d(TAG, "transcript for ${wav.name}: '$text'")
                if (cont.isActive) cont.resume(text)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "recognition error $error for ${wav.name}")
                val noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (cont.isActive) cont.resume(if (lastPartial.isNotBlank()) lastPartial else if (noSpeech) "" else null)
            }

            private var rmsLogged = 0
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "  ready") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "  speech began") }
            override fun onRmsChanged(rmsdB: Float) { if (rmsLogged++ % 20 == 0) Log.d(TAG, "  rms $rmsdB") }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "  speech ended") }
            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!p.isNullOrBlank()) lastPartial = p
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Stream the PCM (everything after the 44-byte WAV header) into the pipe off the main thread.
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writer).use { out ->
                    FileInputStream(wav).use { input ->
                        input.skip(AnnotationRecorder.HEADER_SIZE.toLong())
                        input.copyTo(out)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "feeding audio failed", e)
            }
        }.start()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, reader)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AnnotationRecorder.RATE)
        recognizer.startListening(intent)

        cont.invokeOnCancellation {
            recognizer.destroy()
            reader.close()
        }
    }

    companion object {
        private const val TAG = "Asides"
        private const val TIMEOUT_MS = 60_000L
    }
}
