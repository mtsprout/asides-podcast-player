package com.asides.app.data

import com.asides.app.service.Transcriber
import kotlinx.coroutines.Dispatchers
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

class Repository(val db: AppDb, private val http: OkHttpClient, val annotationDir: File) {

    val podcasts = db.podcasts().podcasts()
    fun podcast(id: Long) = db.podcasts().podcastFlow(id)
    fun episodes(podcastId: Long) = db.episodes().episodes(podcastId)
    fun episode(id: Long) = db.episodes().episodeFlow(id)
    fun annotation(id: Long) = db.annotations().flow(id)
    fun annotationsFor(episodeId: Long) = db.annotations().forEpisode(episodeId)
    val allAnnotations = db.annotations().allRows()

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

    private fun fetchFeed(feedUrl: String): ParsedFeed =
        http.newCall(Request.Builder().url(feedUrl).build()).execute().use { resp ->
            check(resp.isSuccessful) { "Feed returned HTTP ${resp.code}" }
            FeedParser.parse(resp.body!!.byteStream())
        }

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
        val feed = fetchFeed(feedUrl)
        val dao = db.podcasts()
        dao.insert(Podcast(title = feed.title.ifEmpty { feedUrl }, feedUrl = feedUrl, artworkUrl = feed.artworkUrl))
        val id = dao.idForFeed(feedUrl)!!
        // Re-subscribing refreshes the title and artwork of an existing podcast.
        dao.updateInfo(id, feed.title.ifEmpty { feedUrl }, feed.artworkUrl)
        dao.setSubscribed(id, true)
        db.episodes().insertAll(feed.episodes.map {
            Episode(podcastId = id, title = it.title, pubDate = it.pubDate, audioUrl = it.audioUrl, durationMs = it.durationMs)
        })
        id
    }

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
