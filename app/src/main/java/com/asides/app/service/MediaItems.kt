package com.asides.app.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asides.app.data.Episode
import com.asides.app.data.Podcast

const val EXTRA_PODCAST_ID = "podcastId"

/** The player's description of an episode: its id (so asides know where they belong), audio and artwork. */
fun mediaItemFor(episode: Episode, podcast: Podcast?): MediaItem =
    MediaItem.Builder()
        .setMediaId(episode.id.toString())
        // A downloaded copy plays offline; otherwise the episode streams.
        .setUri(Downloads.localFile(episode.id)?.let(Uri::fromFile) ?: Uri.parse(episode.audioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast?.title)
                .setArtworkUri(podcast?.artworkUrl?.let(Uri::parse))
                // So the player service knows whose settings (speed) apply when this episode starts.
                .setExtras(Bundle().apply { putLong(EXTRA_PODCAST_ID, episode.podcastId) })
                .build(),
        )
        .build()
