package com.asides.app.data

import android.util.Log
import androidx.room.withTransaction
import com.asides.app.service.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

data class SearchResult(val title: String, val feedUrl: String, val artworkUrl: String?)

/** A podcast that gained episodes in a refresh. [titles] are newest first. */
data class NewEpisodes(val podcast: Podcast, val titles: List<String>, val firstEpisodeId: Long?)

/** A downloaded feed; [feed] is null when the server said nothing had changed. */
private class Fetched(val feed: ParsedFeed?, val etag: String?, val lastModified: String?)

class Repository(val db: AppDb, private val http: OkHttpClient, val annotationDir: File) {

    private companion object {
        const val TAG = "Asides"
        /** Don't re-check a feed more often than this unless the user asks (pull-to-refresh). */
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }

    val podcasts = db.podcasts().podcasts()
    fun podcast(id: Long) = db.podcasts().podcastFlow(id)
    fun episodes(podcastId: Long) = db.episodes().episodes(podcastId)
    fun episode(id: Long) = db.episodes().episodeFlow(id)
    fun annotation(id: Long) = db.annotations().flow(id)
    fun annotationsFor(episodeId: Long) = db.annotations().forEpisode(episodeId)
    val allAnnotations = db.annotations().allRows()
    /** Per podcast, how many episodes a refresh found that haven't been opened yet. */
    val newCounts = db.episodes().newCounts().map { rows -> rows.associate { it.podcastId to it.count } }

    /** Searches the Apple/iTunes podcast directory. */
    suspend fun search(term: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search?media=podcast&limit=25&term=" +
            URLEncoder.encode(term, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val feed = o.optString("feedUrl").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // iTunes serves any size by changing the dimensions in the URL; 600px suits the player.
            val art = o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100") }.takeIf { it.isNotEmpty() }
            SearchResult(o.optString("collectionName"), feed, art)
        }.distinctBy { it.feedUrl }
    }

    private var topCache: List<SearchResult>? = null

    /**
     * The iTunes top-10 podcast chart for the device's country (falling back to the US). The chart
     * only lists ids, so a second lookup call fetches each show's feed URL and artwork.
     */
    suspend fun topPodcasts(): List<SearchResult> = topCache ?: withContext(Dispatchers.IO) {
        fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
        val country = Locale.getDefault().country.lowercase().ifEmpty { "us" }
        val chart = try {
            get("https://itunes.apple.com/$country/rss/toppodcasts/limit=10/json")
        } catch (e: Exception) {
            if (country == "us") throw e
            get("https://itunes.apple.com/us/rss/toppodcasts/limit=10/json")
        }
        val entries = JSONObject(chart).getJSONObject("feed").getJSONArray("entry")
        val ids = (0 until entries.length()).map {
            entries.getJSONObject(it).getJSONObject("id").getJSONObject("attributes").getString("im:id")
        }
        val results = JSONObject(get("https://itunes.apple.com/lookup?entity=podcast&id=${ids.joinToString(",")}"))
            .getJSONArray("results")
        val byId = (0 until results.length()).map { results.getJSONObject(it) }.associateBy { it.optString("collectionId") }
        ids.mapNotNull { id ->
            val o = byId[id] ?: return@mapNotNull null
            val feed = o.optString("feedUrl").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val art = o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100") }.takeIf { it.isNotEmpty() }
            SearchResult(o.optString("collectionName"), feed, art)
        }.distinctBy { it.feedUrl }.also { topCache = it }
    }

    /**
     * Downloads and parses a feed. Given the validators from the last download, the server can answer
     * "not modified", which skips the (sometimes multi-megabyte) transfer and the parse.
     */
    private fun fetch(feedUrl: String, etag: String? = null, lastModified: String? = null): Fetched {
        val request = Request.Builder().url(feedUrl).apply {
            etag?.let { header("If-None-Match", it) }
            lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        return http.newCall(request).execute().use { resp ->
            if (resp.code == 304) return@use Fetched(null, etag, lastModified)
            check(resp.isSuccessful) { "Feed returned HTTP ${resp.code}" }
            Fetched(FeedParser.parse(resp.body!!.byteStream()), resp.header("ETag"), resp.header("Last-Modified"))
        }
    }

    private fun fetchFeed(feedUrl: String): ParsedFeed = fetch(feedUrl).feed!!

    private val previewCache = HashMap<String, ParsedFeed>()

    /** Reads a feed without subscribing, for the preview page. */
    suspend fun previewFeed(feedUrl: String): ParsedFeed = previewCache[feedUrl] ?: withContext(Dispatchers.IO) {
        fetchFeed(feedUrl).also { previewCache[feedUrl] = it }
    }

    /** The plain-text show notes for one episode, read from its (cached) feed; null if there are none. */
    suspend fun episodeDescription(feedUrl: String, audioUrl: String): String? {
        val html = previewFeed(feedUrl).episodes.firstOrNull { it.audioUrl == audioUrl }?.descriptionHtml
        return html?.let { FeedParser.plainText(it) }?.ifBlank { null }
    }

    /** Subscribes to a feed (or refreshes it if already subscribed). Returns the podcast id. */
    suspend fun subscribe(feedUrl: String): Long = withContext(Dispatchers.IO) {
        val fetched = fetch(feedUrl)
        val feed = fetched.feed!!
        val dao = db.podcasts()
        dao.insert(Podcast(title = feed.title.ifEmpty { feedUrl }, feedUrl = feedUrl, artworkUrl = feed.artworkUrl))
        val id = dao.idForFeed(feedUrl)!!
        dao.setSubscribed(id, true)
        // The very first download isn't "new": flagging a show's whole back catalogue would be noise.
        applyFeed(id, feed, fetched, firstDownload = db.episodes().count(id) == 0)
        id
    }

    /**
     * Stores a downloaded feed: genuinely new episodes are added (flagged "new" unless this is the first
     * download) and episodes we already have are matched and fixed up rather than duplicated.
     * Returns the episodes that were added.
     */
    private suspend fun applyFeed(podcastId: Long, feed: ParsedFeed, fetched: Fetched, firstDownload: Boolean): List<Episode> =
        db.withTransaction {
            val episodes = db.episodes()
            val plan = EpisodeMerge.plan(episodes.known(podcastId), feed.episodes)
            val rows = plan.inserts.map {
                Episode(
                    podcastId = podcastId, title = it.title, pubDate = it.pubDate, audioUrl = it.audioUrl,
                    durationMs = it.durationMs, guid = it.guid, isNew = !firstDownload,
                )
            }
            val ids = episodes.insertAll(rows)
            plan.updates.forEach { episodes.updateIdentity(it.id, it.guid, it.audioUrl) }
            val existing = db.podcasts().podcast(podcastId)
            db.podcasts().updateAfterFetch(
                podcastId,
                title = feed.title.ifEmpty { existing?.title.orEmpty() },
                artworkUrl = feed.artworkUrl ?: existing?.artworkUrl,
                etag = fetched.etag, lastModified = fetched.lastModified, at = System.currentTimeMillis(),
            )
            rows.zip(ids).filter { it.second != -1L }.map { (row, id) -> row.copy(id = id) }
        }

    private val refreshLock = Mutex()

    private suspend fun refreshOne(p: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val fetched = fetch(p.feedUrl, p.etag, p.lastModified)
        val feed = fetched.feed
        if (feed == null) {
            Log.d(TAG, "${p.title}: feed unchanged (304), nothing downloaded")
            db.podcasts().markChecked(p.id, System.currentTimeMillis())
            emptyList()
        } else {
            applyFeed(p.id, feed, fetched, firstDownload = false).also {
                Log.d(TAG, "${p.title}: downloaded ${feed.episodes.size} episodes, ${it.size} new")
            }
        }
    }

    private fun summarize(p: Podcast, added: List<Episode>): NewEpisodes {
        val newestFirst = added.sortedByDescending { it.pubDate }
        return NewEpisodes(p, newestFirst.map { it.title }, newestFirst.singleOrNull()?.id)
    }

    /**
     * Re-checks every subscribed podcast for new episodes. Unless [force], podcasts checked in the last
     * half hour are skipped. Returns the podcasts that gained episodes. A feed that can't be reached
     * (offline, broken) is skipped quietly; the next refresh tries again.
     */
    suspend fun refreshAll(force: Boolean = false): List<NewEpisodes> = refreshLock.withLock {
        val found = ArrayList<NewEpisodes>()
        for (p in db.podcasts().subscribedNow()) {
            if (!force && System.currentTimeMillis() - p.lastCheckedAt < STALE_AFTER_MS) continue
            val added = try {
                refreshOne(p)
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed for ${p.title}: ${e.message}")
                continue
            }
            if (added.isNotEmpty()) found += summarize(p, added)
        }
        found
    }

    /** Re-checks one podcast, for pull-to-refresh on its page and when it is opened. */
    suspend fun refreshPodcast(podcastId: Long, force: Boolean = false) = refreshLock.withLock {
        val p = db.podcasts().podcast(podcastId) ?: return@withLock
        if (!force && System.currentTimeMillis() - p.lastCheckedAt < STALE_AFTER_MS) return@withLock
        try { refreshOne(p) } catch (e: Exception) { Log.w(TAG, "refresh failed for ${p.title}: ${e.message}") }
    }

    /**
     * Test aid for debug builds: forgets each podcast's newest [count] episodes and its cache
     * validators and switches notifications on, so the next refresh finds them as new.
     */
    suspend fun simulateNewEpisodes(count: Int) {
        for (p in db.podcasts().subscribedNow()) {
            db.episodes().deleteNewest(p.id, count)
            db.podcasts().resetForRefreshTest(p.id)
        }
    }

    suspend fun markSeen(episodeId: Long) = db.episodes().markSeen(episodeId)

    suspend fun setNotify(podcastId: Long, notify: Boolean) = db.podcasts().setNotify(podcastId, notify)

    /** Removes a podcast from the library but keeps its episodes and asides; subscribing again restores it. */
    suspend fun unsubscribe(feedUrl: String) {
        db.podcasts().idForFeed(feedUrl)?.let { db.podcasts().setSubscribed(it, false) }
    }

    suspend fun savePosition(episodeId: Long, positionMs: Long, durationMs: Long) =
        db.episodes().savePosition(episodeId, positionMs, durationMs)

    suspend fun setPlayed(episodeId: Long, played: Boolean) = db.episodes().setPlayed(episodeId, played)

    suspend fun setPlayed(episodeIds: Collection<Long>, played: Boolean) = db.episodes().setPlayed(episodeIds, played)

    /** Called when an episode plays to the end: mark it played and rewind it for next time. */
    suspend fun markFinished(episodeId: Long) {
        db.episodes().setPlayed(episodeId, true)
        db.episodes().savePosition(episodeId, 0, 0)
    }

    suspend fun clearTranscripts() = db.annotations().clearTranscripts()

    private val transcribeLock = Mutex()

    /** Transcribes every annotation that has no transcript yet. Safe to call from anywhere, any time. */
    suspend fun transcribePending(transcriber: Transcriber) = transcribeLock.withLock {
        for (a in db.annotations().pending()) {
            val file = File(a.audioPath)
            // Notes from the first build were m4a; the recognizer needs the raw PCM in newer WAV notes.
            if (!file.exists() || file.extension != "wav") { db.annotations().setTranscript(a.id, ""); continue }
            // null means the recognizer couldn't run at all; leave the rest for next time.
            val text = transcriber.transcribe(file) ?: break
            db.annotations().setTranscript(a.id, text)
        }
    }

    suspend fun addAnnotation(episodeId: Long, positionMs: Long, audio: File): Long =
        db.annotations().insert(Annotation(episodeId = episodeId, positionMs = positionMs, audioPath = audio.absolutePath))

    suspend fun deleteAnnotation(id: Long) {
        db.annotations().get(id)?.let { File(it.audioPath).delete() }
        db.annotations().delete(id)
    }
}
