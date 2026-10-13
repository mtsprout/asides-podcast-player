package com.asides.app

import android.app.Application
import com.asides.app.data.AppDb
import com.asides.app.data.Repository
import com.asides.app.service.FeedRefreshWorker
import com.asides.app.ui.AppSettings
import com.asides.app.service.Notifier
import com.asides.app.service.Transcriber
import okhttp3.OkHttpClient
import java.io.File

class AsidesApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var transcriber: Transcriber
        private set

    override fun onCreate() {
        super.onCreate()
        val dir = File(filesDir, "annotations").apply { mkdirs() }
        transcriber = Transcriber(this)
        repo = Repository(AppDb.create(this), OkHttpClient(), dir)
        AppSettings.init(this)
        repo.saveAudio = { AppSettings.saveAudio }
        Notifier.createChannel(this)
        FeedRefreshWorker.schedule(this)
    }
}
