package com.asides.app.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.asides.app.MainActivity
import com.asides.app.R
import com.asides.app.data.NewEpisodes

/** Posts "new episodes" notifications for the podcasts the user has switched them on for. */
object Notifier {
    private const val CHANNEL_ID = "new_episodes"

    /** Intent extra naming the screen a notification should open, e.g. "episode/42". */
    const val EXTRA_ROUTE = "route"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a podcast you've turned notifications on for has a new episode"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** One notification per podcast. Does nothing if notifications are turned off for the app. */
    @SuppressLint("MissingPermission") // checked just below
    fun notifyNewEpisodes(context: Context, found: List<NewEpisodes>) {
        val manager = NotificationManagerCompat.from(context)
        if (found.isEmpty() || !manager.areNotificationsEnabled()) return

        for (n in found) {
            // One new episode opens it directly; several open the podcast's list.
            val route = if (n.firstEpisodeId != null) "episode/${n.firstEpisodeId}" else "podcast/${n.podcast.id}"
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ROUTE, route)
            val tap = PendingIntent.getActivity(
                context, n.podcast.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (n.titles.size == 1) n.titles.first()
            else "${n.titles.size} new episodes · latest: ${n.titles.first()}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(n.podcast.title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            // The podcast id is the notification id, so a newer one replaces the older for the same show.
            manager.notify(n.podcast.id.toInt(), notification)
        }
    }
}
