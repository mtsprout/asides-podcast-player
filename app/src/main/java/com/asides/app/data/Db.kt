package com.asides.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcast ORDER BY title COLLATE NOCASE")
    fun podcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcast WHERE id = :id")
    suspend fun podcast(id: Long): Podcast?

    @Query("SELECT * FROM podcast WHERE id = :id")
    fun podcastFlow(id: Long): Flow<Podcast?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(p: Podcast): Long

    @Query("UPDATE podcast SET title = :title, artworkUrl = :artworkUrl WHERE id = :id")
    suspend fun updateInfo(id: Long, title: String, artworkUrl: String?)

    @Query("SELECT id FROM podcast WHERE feedUrl = :feedUrl")
    suspend fun idForFeed(feedUrl: String): Long?

    @Query("DELETE FROM podcast WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episode WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun episodes(podcastId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun episode(id: Long): Episode?

    @Query("SELECT * FROM episode WHERE id = :id")
    fun episodeFlow(id: Long): Flow<Episode?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>)

    @Query("UPDATE episode SET played = :played WHERE id = :id")
    suspend fun setPlayed(id: Long, played: Boolean)

    @Query("UPDATE episode SET played = :played WHERE id IN (:ids)")
    suspend fun setPlayed(ids: Collection<Long>, played: Boolean)

    @Query("UPDATE episode SET positionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END WHERE id = :id")
    suspend fun savePosition(id: Long, positionMs: Long, durationMs: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotation WHERE episodeId = :episodeId ORDER BY positionMs")
    fun forEpisode(episodeId: Long): Flow<List<Annotation>>

    @Query(
        """
        SELECT a.id, a.episodeId, a.positionMs, a.audioPath, a.transcript, a.createdAt,
               e.title AS episodeTitle, e.pubDate AS episodePubDate,
               p.id AS podcastId, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl
        FROM annotation a
        JOIN episode e ON e.id = a.episodeId
        JOIN podcast p ON p.id = e.podcastId
        ORDER BY p.title COLLATE NOCASE, e.pubDate ASC, a.positionMs
        """
    )
    fun allRows(): Flow<List<AnnotationRow>>

    @Insert
    suspend fun insert(a: Annotation): Long

    @Query("UPDATE annotation SET transcript = :transcript WHERE id = :id")
    suspend fun setTranscript(id: Long, transcript: String)

    @Query("SELECT * FROM annotation WHERE id = :id")
    suspend fun get(id: Long): Annotation?

    @Query("SELECT * FROM annotation WHERE id = :id")
    fun flow(id: Long): Flow<Annotation?>

    @Query("UPDATE annotation SET transcript = NULL")
    suspend fun clearTranscripts()

    @Query("SELECT * FROM annotation WHERE transcript IS NULL ORDER BY id")
    suspend fun pending(): List<Annotation>

    @Query("DELETE FROM annotation WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Podcast::class, Episode::class, Annotation::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun episodes(): EpisodeDao
    abstract fun annotations(): AnnotationDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "asides.db").build()
    }
}
