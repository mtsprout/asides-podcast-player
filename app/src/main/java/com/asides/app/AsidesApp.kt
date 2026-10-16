package com.asides.app

import android.app.Application
import com.asides.app.data.AppDb
import com.asides.app.data.GenreStore
import com.asides.app.data.PodcastSettingsStore
import com.asides.app.data.QueueStore
import com.asides.app.data.Repository
import com.asides.app.service.Downloads
import com.asides.app.service.FeedRefreshWorker
import com.asides.app.ui.AppSettings
import com.asides.app.service.Notifier
import com.asides.app.service.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

class AsidesApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var transcriber: Transcriber
        private set
    lateinit var podcastSettings: PodcastSettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val dir = File(filesDir, "annotations").apply { mkdirs() }
        transcriber = Transcriber(this)
        repo = Repository(AppDb.create(this), OkHttpClient(), dir, GenreStore(getSharedPreferences("genres", MODE_PRIVATE)),
            QueueStore(getSharedPreferences("queue", MODE_PRIVATE)))
        podcastSettings = PodcastSettingsStore(getSharedPreferences("podcast_settings", MODE_PRIVATE))
        AppSettings.init(this)
        Downloads.init(this)
        repo.saveAudio = { AppSettings.saveAudio }
        repo.onPlayed = { ids ->
            if (AppSettings.autoDeleteDownloads) {
                CoroutineScope(Dispatchers.IO).launch { ids.forEach(Downloads::remove) }
            }
        }
        // Podcasts set to download new episodes: fetch the newest few, on Wi-Fi only.
        repo.onNewEpisodes = { podcast, added ->
            if (podcastSettings.get(podcast.id).autoDownload) {
                added.sortedByDescending { it.pubDate }.take(AUTO_DOWNLOAD_MAX).forEach { Downloads.start(it, wifiOnly = true) }
            }
        }
        Notifier.createChannel(this)
        FeedRefreshWorker.schedule(this)
    }
}

/** The most episodes one refresh downloads on its own for a podcast. */
private const val AUTO_DOWNLOAD_MAX = 3
