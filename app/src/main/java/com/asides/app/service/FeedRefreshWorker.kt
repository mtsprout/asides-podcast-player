package com.asides.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.asides.app.AsidesApp
import java.util.concurrent.TimeUnit

/** Checks every subscribed podcast for new episodes in the background, and notifies where asked to. */
class FeedRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as AsidesApp).repo
        val found = repo.refreshAll()
        // Only podcasts the user switched notifications on for. Episodes found while the app is open
        // are shown with "new" markers instead, so there is nothing to notify about.
        Notifier.notifyNewEpisodes(applicationContext, found.filter { it.podcast.notifyNew })
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "feed-refresh"

        /** Runs about every 4 hours, only with a network connection. Android chooses the exact moments. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
