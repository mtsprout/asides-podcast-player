package com.asides.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcast WHERE subscribed = 1 ORDER BY title COLLATE NOCASE")
    fun podcasts(): Flow<List<Podcast>>

    @Query("UPDATE podcast SET subscribed = :subscribed WHERE id = :id")
    suspend fun setSubscribed(id: Long, subscribed: Boolean)

    @Query("SELECT * FROM podcast WHERE subscribed = 1")
    suspend fun subscribedNow(): List<Podcast>

    @Query("SELECT * FROM podcast WHERE id = :id")
    suspend fun podcast(id: Long): Podcast?

    @Query("UPDATE podcast SET notifyNew = :notify WHERE id = :id")
    suspend fun setNotify(id: Long, notify: Boolean)

    @Query("UPDATE podcast SET lastCheckedAt = :at WHERE id = :id")
    suspend fun markChecked(id: Long, at: Long)

    /** Test aid: forget the cache validators and check time, and switch notifications on. */
    @Query("UPDATE podcast SET etag = NULL, lastModified = NULL, lastCheckedAt = 0, notifyNew = 1 WHERE id = :id")
    suspend fun resetForRefreshTest(id: Long)

    @Query("UPDATE podcast SET title = :title, artworkUrl = :artworkUrl, etag = :etag, lastModified = :lastModified, lastCheckedAt = :at WHERE id = :id")
    suspend fun updateAfterFetch(id: Long, title: String, artworkUrl: String?, etag: String?, lastModified: String?, at: Long)

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

    /** Returns the new row id for each episode, or -1 where it was skipped as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>): List<Long>

    @Query("SELECT id, guid, audioUrl, title, pubDate FROM episode WHERE podcastId = :podcastId")
    suspend fun known(podcastId: Long): List<KnownEpisode>

    @Query("SELECT COUNT(*) FROM episode WHERE podcastId = :podcastId")
    suspend fun count(podcastId: Long): Int

    /** Test aid: drop a podcast's newest episodes so a refresh will find them again as new. */
    @Query("DELETE FROM episode WHERE id IN (SELECT id FROM episode WHERE podcastId = :podcastId ORDER BY pubDate DESC LIMIT :count)")
    suspend fun deleteNewest(podcastId: Long, count: Int)

    @Query("UPDATE episode SET guid = :guid, audioUrl = :audioUrl WHERE id = :id")
    suspend fun updateIdentity(id: Long, guid: String?, audioUrl: String)

    @Query("SELECT podcastId, COUNT(*) AS count FROM episode WHERE isNew = 1 GROUP BY podcastId")
    fun newCounts(): Flow<List<NewCount>>

    @Query("UPDATE episode SET isNew = 0 WHERE id = :id")
    suspend fun markSeen(id: Long)

    // Playing or marking an episode counts as having seen it.
    @Query("UPDATE episode SET played = :played, isNew = 0 WHERE id = :id")
    suspend fun setPlayed(id: Long, played: Boolean)

    @Query("UPDATE episode SET played = :played, isNew = 0 WHERE id IN (:ids)")
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

@Database(entities = [Podcast::class, Episode::class, Annotation::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun episodes(): EpisodeDao
    abstract fun annotations(): AnnotationDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "asides.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** Adds what feed refreshing needs: cache validators, episode ids, a "new" flag and notify setting. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE podcast ADD COLUMN lastModified TEXT")
                db.execSQL("ALTER TABLE podcast ADD COLUMN lastCheckedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE podcast ADD COLUMN notifyNew INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episode ADD COLUMN guid TEXT")
                db.execSQL("ALTER TABLE episode ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_episode_podcastId_guid ON episode (podcastId, guid)")
            }
        }

        /** Adds the subscribed flag; every podcast that already exists stays subscribed. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast ADD COLUMN subscribed INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
