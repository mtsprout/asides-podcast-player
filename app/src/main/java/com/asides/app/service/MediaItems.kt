package com.asides.app.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asides.app.data.Episode
import com.asides.app.data.Podcast

/** The player's description of an episode: its id (so asides know where they belong), audio and artwork. */
fun mediaItemFor(episode: Episode, podcast: Podcast?): MediaItem =
    MediaItem.Builder()
        .setMediaId(episode.id.toString())
        .setUri(episode.audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast?.title)
                .setArtworkUri(podcast?.artworkUrl?.let(Uri::parse))
                .build(),
        )
        .build()
