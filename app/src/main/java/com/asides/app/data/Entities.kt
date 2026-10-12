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
    /** False once unsubscribed. The row stays so episodes and asides recorded on it are kept. */
    @ColumnInfo(defaultValue = "1") val subscribed: Boolean = true,
    /** HTTP cache validators from the last download, so an unchanged feed can be skipped cheaply. */
    val etag: String? = null,
    val lastModified: String? = null,
    /** When the feed was last checked for new episodes (epoch ms); 0 if never. */
    @ColumnInfo(defaultValue = "0") val lastCheckedAt: Long = 0,
    /** Whether a refresh that finds new episodes should post a notification. */
    @ColumnInfo(defaultValue = "0") val notifyNew: Boolean = false,
)

@Entity(
    tableName = "episode",
    foreignKeys = [ForeignKey(Podcast::class, ["id"], ["podcastId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("podcastId"), Index(value = ["podcastId", "audioUrl"], unique = true), Index(value = ["podcastId", "guid"])],
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
    /** The id the feed gives this episode; null for episodes saved before it was recorded. */
    val guid: String? = null,
    /** Found by a refresh and not yet opened. Episodes from the first download are never "new". */
    @ColumnInfo(defaultValue = "0") val isNew: Boolean = false,
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

/** How many episodes of a podcast a refresh found that haven't been opened yet. */
data class NewCount(val podcastId: Long, val count: Int)

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
