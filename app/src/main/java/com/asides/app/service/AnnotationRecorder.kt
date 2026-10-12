package com.asides.app.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records a single voice note as 16 kHz mono 16-bit PCM in a WAV file. Raw PCM (rather than AAC)
 * lets the speech recognizer read the note back later without decoding it first.
 */
class AnnotationRecorder(private val dir: File) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var file: File? = null
    @Volatile private var running = false

    val isRecording get() = running

    /** Starts recording; returns the target file, or null if the mic could not be opened. */
    fun start(): File? {
        if (running) return file
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4,
            )
        } catch (e: Exception) {
            return null
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return null
        }
        val out = File(dir, "note-${System.currentTimeMillis()}.wav")
        val raf = RandomAccessFile(out, "rw")
        raf.write(header(0))
        try {
            r.startRecording()
        } catch (e: IllegalStateException) {
            r.release(); raf.close(); out.delete()
            return null
        }
        record = r
        file = out
        running = true
        thread = Thread {
            val buf = ByteArray(minBuf)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) raf.write(buf, 0, n) else if (n < 0) break
            }
            // Patch the sizes into the header now that the length is known.
            val data = (raf.length() - HEADER_SIZE).toInt()
            raf.seek(0)
            raf.write(header(data))
            raf.close()
        }.also { it.start() }
        return out
    }

    /** Stops recording; returns the finished file, or null if nothing usable was captured. */
    fun stop(): File? {
        val r = record ?: return null
        val out = file
        running = false
        thread?.join()
        try { r.stop() } catch (_: IllegalStateException) {}
        r.release()
        record = null
        thread = null
        file = null
        // Under half a second is an accidental press.
        if (out == null || out.length() - HEADER_SIZE < RATE * 2 / 2) {
            out?.delete()
            return null
        }
        return out
    }

    private fun header(dataLen: Int): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }.array()

    companion object {
        const val RATE = 16000
        const val HEADER_SIZE = 44
    }
}
