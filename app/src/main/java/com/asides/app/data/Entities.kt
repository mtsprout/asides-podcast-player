package com.asides.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcast", indices = [Index(value = ["feedUrl"], unique = true)])
data class Podcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val feedUrl: String,
    val artworkUrl: String?,
)

@Entity(
    tableName = "episode",
    foreignKeys = [ForeignKey(Podcast::class, ["id"], ["podcastId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("podcastId"), Index(value = ["podcastId", "audioUrl"], unique = true)],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long,
    val title: String,
    val pubDate: Long,
    val audioUrl: String,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val played: Boolean = false,
)

@Entity(
    tableName = "annotation",
    foreignKeys = [ForeignKey(Episode::class, ["id"], ["episodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("episodeId")],
)
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    /** Playback position in the episode when the annotation was started. */
    val positionMs: Long,
    val audioPath: String,
    /** Null until transcription completes. */
    val transcript: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** An annotation with the episode and podcast it belongs to, for the global list. */
data class AnnotationRow(
    val id: Long,
    val episodeId: Long,
    val positionMs: Long,
    val audioPath: String,
    val transcript: String?,
    val createdAt: Long,
    val episodeTitle: String,
    val episodePubDate: Long,
    val podcastId: Long,
    val podcastTitle: String,
    val podcastArtworkUrl: String?,
)
