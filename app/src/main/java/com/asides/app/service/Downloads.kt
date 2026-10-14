package com.asides.app.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.asides.app.data.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Episodes saved on the phone for offline listening. The system's DownloadManager does the transfer
 * (so it carries on with the app closed and shows its own progress notification); a finished episode
 * is a plain file, so whether an episode is downloaded needs no database column. [mediaItemFor]
 * plays the file when there is one.
 */
object Downloads {
    /** [done]: episodes on the phone. [progress]: episode id to percent (0 until the size is known). */
    data class State(val done: Set<Long> = emptySet(), val progress: Map<Long, Int> = emptyMap())

    private lateinit var app: Context
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun init(context: Context) {
        app = context.applicationContext
        refresh()
    }

    private fun dir(): File =
        (app.getExternalFilesDir("downloads") ?: File(app.filesDir, "downloads")).apply { mkdirs() }

    private fun finalFile(id: Long) = File(dir(), "ep$id.audio")
    private fun partFile(id: Long) = File(dir(), "ep$id.part")

    /** The saved copy of an episode, or null to stream it. */
    fun localFile(episodeId: Long): File? {
        if (!this::app.isInitialized) return null
        return finalFile(episodeId).takeIf { it.isFile && it.length() > 0 }
    }

    private val manager get() = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs get() = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    /** episode id -> DownloadManager id, for transfers that haven't finished. */
    private fun pending(): MutableMap<Long, Long> =
        prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Long)?.let { id to it } } }.toMap().toMutableMap()

    @Synchronized
    fun start(episode: Episode) {
        if (localFile(episode.id) != null || prefs.contains(episode.id.toString())) return
        partFile(episode.id).delete()
        val request = DownloadManager.Request(Uri.parse(episode.audioUrl))
            .setTitle(episode.title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, "downloads", "ep${episode.id}.part")
        val dmId = try {
            manager.enqueue(request)
        } catch (e: IllegalArgumentException) {
            return // not an http(s) address
        }
        prefs.edit().putLong(episode.id.toString(), dmId).apply()
        refresh()
    }

    /** Stops a transfer in progress, or deletes a finished download. */
    @Synchronized
    fun remove(episodeId: Long) {
        prefs.getLong(episodeId.toString(), -1L).takeIf { it >= 0 }?.let { manager.remove(it) }
        prefs.edit().remove(episodeId.toString()).apply()
        partFile(episodeId).delete()
        finalFile(episodeId).delete()
        refresh()
    }

    /** Deletes every finished download and cancels any in progress. */
    @Synchronized
    fun removeAll() {
        (pending().keys + state.value.done).forEach(::remove)
    }

    /** Deletes the downloads of episodes in [ids] only. */
    fun removeAll(ids: Collection<Long>) = ids.forEach(::remove)

    /** Bytes used by finished downloads. */
    fun sizeBytes(): Long = dir().listFiles().orEmpty().filter { it.name.endsWith(".audio") }.sumOf { it.length() }

    /** Looks at the transfers and the files on disk and publishes the result. Safe to call from any thread. */
    @Synchronized
    fun refresh() {
        val progress = mutableMapOf<Long, Int>()
        val finished = mutableListOf<Long>()
        for ((episodeId, dmId) in pending()) {
            val cursor = manager.query(DownloadManager.Query().setFilterById(dmId))
            var status = -1
            var soFar = 0L
            var total = 0L
            cursor.use {
                if (it.moveToFirst()) {
                    status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                }
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    partFile(episodeId).renameTo(finalFile(episodeId))
                    finished += episodeId
                }
                // Failed, or the system forgot it (cleared in the system's Downloads app).
                DownloadManager.STATUS_FAILED, -1 -> {
                    if (status == DownloadManager.STATUS_FAILED) manager.remove(dmId)
                    partFile(episodeId).delete()
                    finished += episodeId
                }
                else -> progress[episodeId] = if (total > 0) (soFar * 100 / total).toInt().coerceIn(0, 99) else 0
            }
        }
        if (finished.isNotEmpty()) prefs.edit().apply { finished.forEach { remove(it.toString()) } }.apply()
        val done = dir().listFiles().orEmpty()
            .filter { it.name.endsWith(".audio") && it.length() > 0 }
            .mapNotNull { it.name.removePrefix("ep").removeSuffix(".audio").toLongOrNull() }
            .toSet()
        _state.value = State(done, progress)
    }
}
