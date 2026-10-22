package com.asides.app.data

import android.util.Log
import androidx.room.withTransaction
import com.asides.app.service.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.net.URLEncoder
import java.util.Locale

data class SearchResult(val title: String, val feedUrl: String, val artworkUrl: String?)

/** A podcast that gained episodes in a refresh. [titles] are newest first. */
data class NewEpisodes(val podcast: Podcast, val titles: List<String>, val firstEpisodeId: Long?)

/** What an export wrote. */
data class BackupSummary(val podcasts: Int, val asides: Int)

/** What a restore did. [settings] are the saved app settings, for the caller to apply. */
data class RestoreSummary(
    val podcastsAdded: Int,
    val asidesRestored: Int,
    val asidesAlreadyHere: Int,
    val asidesWithoutAudio: Int,
    val settings: BackupSettings,
)

/** A downloaded feed; [feed] is null when the server said nothing had changed. */
private class Fetched(val feed: ParsedFeed?, val etag: String?, val lastModified: String?)

class Repository(val db: AppDb, private val http: OkHttpClient, val annotationDir: File, val genreStore: GenreStore? = null, val queueStore: QueueStore? = null,
    val podcastSettings: PodcastSettingsStore? = null) {

    private companion object {
        const val TAG = "Asides"
        /** Don't re-check a feed more often than this unless the user asks (pull-to-refresh). */
        const val STALE_AFTER_MS = 30 * 60 * 1000L
        val SAFE_WAV = Regex("[0-9A-Za-z_.-]+\\.wav")
    }

    /** Whether to keep an aside's recording after it has been transcribed (the "Save audio files" setting). */
    var saveAudio: () -> Boolean = { false }

    val podcasts = db.podcasts().podcasts()
    fun podcast(id: Long) = db.podcasts().podcastFlow(id)
    fun episodes(podcastId: Long) = db.episodes().episodes(podcastId)
    fun episode(id: Long) = db.episodes().episodeFlow(id)
    fun annotation(id: Long) = db.annotations().flow(id)
    fun annotationsFor(episodeId: Long) = db.annotations().forEpisode(episodeId)
    val allAnnotations = db.annotations().allRows()
    /** The newest asides first, for the front page. */
    fun recentAsides(count: Int) = allAnnotations.map { rows -> rows.sortedByDescending { it.createdAt }.take(count) }
    /** Unplayed episodes of followed podcasts; the front page picks "up next" from these. */
    val unplayed = db.episodes().unplayedSubscribed()
    /** Per podcast, how many episodes a refresh found that haven't been opened yet. */
    val newCounts = db.episodes().newCounts().map { rows -> rows.associate { it.podcastId to it.count } }

    /**
     * Searches the Apple/iTunes podcast directory. With [genreId], only shows in that category: the
     * search endpoint can't filter by category itself, so a longer list is fetched and filtered here.
     */
    suspend fun search(term: String, genreId: Int? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search?media=podcast&limit=${if (genreId == null) 25 else 200}&term=" +
            URLEncoder.encode(term, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val feed = o.optString("feedUrl").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (genreId != null) {
                val ids = o.optJSONArray("genreIds")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).toIntOrNull() } }.orEmpty()
                if (!Discover.inGenre(ids, genreId)) return@mapNotNull null
            }
            // iTunes serves any size by changing the dimensions in the URL; 600px suits the player.
            val art = o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100") }.takeIf { it.isNotEmpty() }
            SearchResult(o.optString("collectionName"), feed, art)
        }.distinctBy { it.feedUrl }.take(50)
    }

    /** The followed podcasts as an OPML subscription list. */
    suspend fun exportOpml(): String = Opml.encode(db.podcasts().subscribedNow())

    data class OpmlResult(val added: Int, val alreadyFollowed: Int, val failed: Int)

    /**
     * Follows every podcast in [feeds] that isn't followed already, a few at a time. [onProgress] gets
     * (done, total) as each finishes. A feed that can't be reached is counted, not fatal.
     */
    suspend fun importOpml(feeds: List<OpmlFeed>, onProgress: (Int, Int) -> Unit = { _, _ -> }): OpmlResult = coroutineScope {
        val have = db.podcasts().subscribedNow().map { it.feedUrl.lowercase() }.toSet()
        val todo = feeds.filter { it.feedUrl.lowercase() !in have }
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val done = java.util.concurrent.atomic.AtomicInteger()
        val results = todo.map { f ->
            async {
                gate.withPermit {
                    val ok = try { subscribe(f.feedUrl); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        Log.w(TAG, "OPML import: couldn't follow ${f.feedUrl}: ${e.message}"); false
                    }
                    onProgress(done.incrementAndGet(), todo.size)
                    ok
                }
            }
        }.awaitAll()
        OpmlResult(results.count { it }, feeds.size - todo.size, results.count { !it })
    }

    private val topCache = HashMap<Int, List<SearchResult>>()

    /**
     * The iTunes podcast chart for the device's country (falling back to the US): the overall top
     * [limit], or the top in one category when [genreId] is given. The chart only lists ids, so a second
     * lookup call fetches each show's feed URL and artwork.
     */
    suspend fun topPodcasts(genreId: Int? = null, limit: Int = 10): List<SearchResult> = topCache[(genreId ?: 0) * 1000 + limit] ?: withContext(Dispatchers.IO) {
        fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
        val country = Locale.getDefault().country.lowercase().ifEmpty { "us" }
        val path = (genreId?.let { "genre=$it/" } ?: "") + "limit=$limit/json"
        val chart = try {
            get("https://itunes.apple.com/$country/rss/toppodcasts/$path")
        } catch (e: Exception) {
            if (country == "us") throw e
            get("https://itunes.apple.com/us/rss/toppodcasts/$path")
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
        }.distinctBy { it.feedUrl }.also { topCache[(genreId ?: 0) * 1000 + limit] = it }
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
            val feed = FeedParser.parse(resp.body!!.byteStream())
            genreStore?.put(feedUrl, feed.categories.mapNotNull(Discover::genreIdForName).toSet())
            Fetched(feed, resp.header("ETag"), resp.header("Last-Modified"))
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

    private val chapterCache = HashMap<String, List<Chapter>>()

    /** The episode's chapters from its feed's chapters file; empty if it has none or they can't be loaded. */
    suspend fun episodeChapters(feedUrl: String, audioUrl: String): List<Chapter> {
        val url = previewFeed(feedUrl).episodes.firstOrNull { it.audioUrl == audioUrl }?.chaptersUrl ?: return emptyList()
        chapterCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) emptyList() else Chapters.parse(resp.body!!.string())
                }
            } catch (e: java.io.IOException) {
                emptyList()
            }.also { if (it.isNotEmpty()) chapterCache[url] = it }
        }
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

    /** A podcast whose categories haven't been learned yet is checked even if it was checked recently. */
    private fun needsGenres(p: Podcast) = genreStore?.knows(p.feedUrl) == false

    private suspend fun refreshOne(p: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        // A feed whose categories aren't known yet is downloaded in full once, to learn them.
        val learning = genreStore?.knows(p.feedUrl) == false
        val fetched = if (learning) fetch(p.feedUrl) else fetch(p.feedUrl, p.etag, p.lastModified)
        val feed = fetched.feed
        if (feed == null) {
            Log.d(TAG, "${p.title}: feed unchanged (304), nothing downloaded")
            db.podcasts().markChecked(p.id, System.currentTimeMillis())
            emptyList()
        } else {
            applyFeed(p.id, feed, fetched, firstDownload = p.lastCheckedAt < 0).also {
                Log.d(TAG, "${p.title}: downloaded ${feed.episodes.size} episodes, ${it.size} new")
                if (it.isNotEmpty() && p.lastCheckedAt >= 0) onNewEpisodes(p, it)
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
            if (!force && !needsGenres(p) && System.currentTimeMillis() - p.lastCheckedAt < STALE_AFTER_MS) continue
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
        if (!force && !needsGenres(p) && System.currentTimeMillis() - p.lastCheckedAt < STALE_AFTER_MS) return@withLock
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

    /** The play queue, first entry plays next. */
    val queue: StateFlow<List<Long>> = queueStore?.ids ?: MutableStateFlow(emptyList())

    private val _queueDriven = MutableStateFlow(false)

    /** True while the episode playing came out of the queue; only then does the queue decide what's next. */
    val queueDriven: StateFlow<Boolean> = _queueDriven

    /** Called when an episode starts: [fromQueue] is whether it was waiting in the queue. */
    fun playbackStarted(episodeId: Long) {
        _queueDriven.value = episodeId in queue.value
        queueStore?.remove(episodeId)
    }

    /**
     * What should play after [episodeId] finishes, or null if nothing is left to hear. If that episode came
     * from the queue, the rest of the queue comes first; one started from a podcast's page follows its podcast.
     */
    suspend fun nextEpisode(episodeId: Long, newestFirst: Boolean): Episode? {
        if (_queueDriven.value) for (id in queue.value) {
            if (id == episodeId) continue
            db.episodes().episode(id)?.let { return it }
            queueStore?.remove(id) // its episode is gone
        }
        val current = db.episodes().episode(episodeId) ?: return null
        return NextEpisode.pick(db.episodes().episodesNow(current.podcastId), episodeId, newestFirst)
    }

    /** Puts episodes at the top of the queue, in the order given. */
    fun addToQueue(episodeIds: List<Long>) { queueStore?.addToTop(episodeIds) }
    fun removeFromQueue(episodeId: Long) { queueStore?.remove(episodeId) }
    fun moveInQueue(from: Int, to: Int) { queueStore?.move(from, to) }
    fun clearQueue() { queueStore?.clear() }

    /** The queued episodes with their podcasts, in play order. */
    suspend fun queuedEpisodes(ids: List<Long>): List<Pair<Episode, Podcast?>> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id -> db.episodes().episode(id)?.let { it to db.podcasts().podcast(it.podcastId) } }
    }

    /**
     * Saves an episode from a podcast you don't follow so it can be queued and played like any other:
     * the podcast is kept as an unsubscribed row (out of the Library, not refreshed) and the episode
     * is added to it. Returns the episode's id.
     */
    suspend fun saveEpisodeFrom(feedUrl: String, title: String, artworkUrl: String?, e: ParsedEpisode): Long? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val podcasts = db.podcasts()
            val podcastId = podcasts.idForFeed(feedUrl) ?: run {
                podcasts.insert(Podcast(title = title.ifEmpty { feedUrl }, feedUrl = feedUrl, artworkUrl = artworkUrl, subscribed = false))
                podcasts.idForFeed(feedUrl)!!
            }
            BackupMatch.episode(db.episodes().known(podcastId), e.guid, e.audioUrl)?.let { return@withTransaction it.id }
            db.episodes().insertAll(listOf(
                Episode(podcastId = podcastId, title = e.title, pubDate = e.pubDate, audioUrl = e.audioUrl, durationMs = e.durationMs, guid = e.guid),
            )).first().takeIf { it != -1L }
        }
    }

    suspend fun episodeNow(id: Long): Episode? = db.episodes().episode(id)

    /** The episode a play press should resume: the last one listened to, or the next one if it is finished. */
    suspend fun resumeEpisode(lastId: Long, newestFirst: Boolean): Episode? {
        val last = db.episodes().episode(lastId) ?: return null
        return NextEpisode.resume(db.episodes().episodesNow(last.podcastId), lastId, newestFirst)
    }

    suspend fun podcastNow(id: Long): Podcast? = db.podcasts().podcast(id)

    /**
     * Writes a backup to [output]: a zip holding backup.json (first, so a restore can read it before the
     * audio) and each aside's recording. Asides name their episode by feed and guid, not database id, so
     * the file can be restored on another phone.
     */
    suspend fun exportBackup(output: OutputStream, settings: BackupSettings, appVersion: String): BackupSummary =
        withContext(Dispatchers.IO) {
            val podcasts = db.podcasts().all()
            val podcastById = podcasts.associateBy { it.id }
            val episodes = db.episodes().withState().filter { it.podcastId in podcastById }
            val episodeById = episodes.associateBy { it.id }

            val asides = db.annotations().allNow().mapNotNull { a ->
                val ep = episodeById[a.episodeId] ?: return@mapNotNull null
                val feed = podcastById.getValue(ep.podcastId).feedUrl
                val hasAudio = a.audioPath.isNotEmpty() && File(a.audioPath).exists()
                a to BackupAside(feed, ep.guid, ep.audioUrl, a.positionMs, a.createdAt, a.transcript, if (hasAudio) "audio/${a.id}.wav" else null)
            }
            val data = BackupData(
                appVersion = appVersion,
                exportedAt = System.currentTimeMillis(),
                settings = settings,
                podcasts = podcasts.map { BackupPodcast(it.feedUrl, it.title, it.artworkUrl, it.subscribed, it.notifyNew) },
                episodes = episodes.map {
                    BackupEpisode(podcastById.getValue(it.podcastId).feedUrl, it.guid, it.audioUrl, it.title, it.pubDate,
                        it.durationMs, it.positionMs, it.played)
                },
                asides = asides.map { it.second },
                podcastSettings = podcasts.mapNotNull { p ->
                    val s = podcastSettings?.get(p.id)?.takeIf { it != PodcastSettings() } ?: return@mapNotNull null
                    BackupPodcastSettings(p.feedUrl, s.speed, s.skipIntroSec, s.autoDownload)
                },
                queue = queueStore?.ids?.value.orEmpty().mapNotNull { id ->
                    val ep = db.episodes().episode(id) ?: return@mapNotNull null
                    val feed = podcastById[ep.podcastId]?.feedUrl ?: return@mapNotNull null
                    BackupQueued(feed, ep.guid, ep.audioUrl, ep.title, ep.pubDate, ep.durationMs)
                },
            )
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(BackupFormat.encode(data).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for ((annotation, aside) in asides) {
                    val name = aside.audio ?: continue
                    zip.putNextEntry(ZipEntry(name))
                    File(annotation.audioPath).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            BackupSummary(podcasts.size, asides.size)
        }

    /**
     * Restores a backup made by [exportBackup]. It only ever adds: podcasts and episodes already here are
     * kept as they are (a played mark or saved position is filled in only where this phone has none),
     * and an aside that is already here is skipped, so restoring twice does no harm. Throws
     * [BackupException] for a file that isn't a usable backup.
     */
    suspend fun importBackup(input: InputStream): RestoreSummary = withContext(Dispatchers.IO) {
        val staging = File(annotationDir, ".import").apply { deleteRecursively(); mkdirs() }
        try {
            var json: String? = null
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) when {
                        entry.name == "backup.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                        // Only the file's own name is used, so a crafted path can't write elsewhere.
                        entry.name.startsWith("audio/") -> File(entry.name).name.takeIf { it.matches(SAFE_WAV) }?.let { safe ->
                            File(staging, safe).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            val data = BackupFormat.decode(json ?: throw BackupException("That file isn't an Asides backup."))
            restore(data, staging)
        } catch (e: java.util.zip.ZipException) {
            throw BackupException("That file isn't an Asides backup.")
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun restore(data: BackupData, staging: File): RestoreSummary = db.withTransaction {
        val podcastDao = db.podcasts()
        val episodeDao = db.episodes()
        val annotationDao = db.annotations()

        var podcastsAdded = 0
        val podcastIds = HashMap<String, Long>()
        for (p in data.podcasts) {
            val existing = podcastDao.idForFeed(p.feedUrl)
            val id = if (existing != null) {
                if (p.subscribed) podcastDao.setSubscribed(existing, true)
                existing
            } else {
                // lastCheckedAt = -1 marks "restored, never downloaded": the first refresh fills in the rest of
                // the episodes quietly instead of flagging the whole back catalogue as new.
                podcastDao.insert(Podcast(title = p.title, feedUrl = p.feedUrl, artworkUrl = p.artworkUrl,
                    subscribed = p.subscribed, notifyNew = p.notifyNew, lastCheckedAt = -1))
                podcastsAdded++
                podcastDao.idForFeed(p.feedUrl)!!
            }
            podcastIds[p.feedUrl] = id
        }

        val knownByPodcast = HashMap<Long, MutableList<KnownEpisode>>()
        suspend fun episodeId(feedUrl: String, guid: String?, audioUrl: String, title: String, pubDate: Long, durationMs: Long): Long? {
            val podcastId = podcastIds[feedUrl] ?: return null
            val known = knownByPodcast.getOrPut(podcastId) { episodeDao.known(podcastId).toMutableList() }
            BackupMatch.episode(known, guid, audioUrl)?.let { return it.id }
            val id = episodeDao.insertAll(listOf(
                Episode(podcastId = podcastId, title = title, pubDate = pubDate, audioUrl = audioUrl, durationMs = durationMs, guid = guid),
            )).first().takeIf { it != -1L } ?: return null
            known += KnownEpisode(id, guid, audioUrl, title, pubDate)
            return id
        }

        for (e in data.episodes) {
            val id = episodeId(e.feedUrl, e.guid, e.audioUrl, e.title, e.pubDate, e.durationMs) ?: continue
            val current = episodeDao.episode(id) ?: continue
            if (e.played && !current.played) episodeDao.setPlayed(id, true)
            if (!e.played && current.positionMs == 0L && e.positionMs > 0) episodeDao.savePosition(id, e.positionMs, e.durationMs)
        }

        // Per-podcast settings fill in only where this phone has none of its own.
        for (s in data.podcastSettings) {
            val id = podcastIds[s.feedUrl] ?: continue
            val store = podcastSettings ?: break
            if (store.get(id) != PodcastSettings()) continue
            store.setSpeed(id, s.speed)
            store.setSkipIntro(id, s.skipIntroSec)
            store.setAutoDownload(id, s.autoDownload)
        }
        // Queued episodes go after whatever is already queued here, in their saved order.
        queueStore?.append(data.queue.mapNotNull { episodeId(it.feedUrl, it.guid, it.audioUrl, it.title, it.pubDate, it.durationMs) })

        var restored = 0
        var alreadyHere = 0
        var withoutAudio = 0
        for (a in data.asides) {
            val info = data.episodes.firstOrNull {
                it.feedUrl == a.feedUrl && ((a.episodeGuid != null && it.guid == a.episodeGuid) || it.audioUrl == a.episodeAudioUrl)
            }
            val episodeId = episodeId(a.feedUrl, a.episodeGuid, a.episodeAudioUrl, info?.title ?: "Episode", info?.pubDate ?: 0, info?.durationMs ?: 0)
                ?: continue
            if (BackupMatch.alreadyHave(annotationDao.forEpisodeNow(episodeId), a.positionMs, a.createdAt)) {
                alreadyHere++
                continue
            }
            val source = a.audio?.let { File(staging, File(it).name) }?.takeIf { it.exists() }
            // An aside whose recording was deleted on purpose comes back as text only.
            if (source == null && a.transcript.isNullOrBlank()) {
                withoutAudio++
                continue
            }
            var path = ""
            if (source != null) {
                var destination = File(annotationDir, "note-${a.createdAt}.wav")
                var n = 1
                while (destination.exists()) destination = File(annotationDir, "note-${a.createdAt}-${n++}.wav")
                source.copyTo(destination)
                path = destination.absolutePath
            }
            annotationDao.insert(Annotation(episodeId = episodeId, positionMs = a.positionMs, audioPath = path,
                transcript = a.transcript, createdAt = a.createdAt))
            restored++
        }
        RestoreSummary(podcastsAdded, restored, alreadyHere, withoutAudio, data.settings)
    }

    suspend fun markSeen(episodeId: Long) = db.episodes().markSeen(episodeId)

    suspend fun setNotify(podcastId: Long, notify: Boolean) = db.podcasts().setNotify(podcastId, notify)

    /** Removes a podcast from the library but keeps its episodes and asides; subscribing again restores it. */
    suspend fun unsubscribe(feedUrl: String) {
        db.podcasts().idForFeed(feedUrl)?.let { db.podcasts().setSubscribed(it, false) }
    }

    suspend fun savePosition(episodeId: Long, positionMs: Long, durationMs: Long) =
        db.episodes().savePosition(episodeId, positionMs, durationMs)

    /** Told which episodes a refresh just found for a podcast (not the first download). */
    var onNewEpisodes: (Podcast, List<Episode>) -> Unit = { _, _ -> }

    /** Told which episodes were just marked played (the app uses it to clear their downloads). */
    var onPlayed: (Collection<Long>) -> Unit = {}

    suspend fun setPlayed(episodeId: Long, played: Boolean) {
        db.episodes().setPlayed(episodeId, played)
        if (played) onPlayed(listOf(episodeId))
    }

    suspend fun setPlayed(episodeIds: Collection<Long>, played: Boolean) {
        db.episodes().setPlayed(episodeIds, played)
        if (played) onPlayed(episodeIds)
    }

    /** Called when an episode plays to the end: mark it played and rewind it for next time. */
    suspend fun markFinished(episodeId: Long) {
        db.episodes().setPlayed(episodeId, true)
        db.episodes().savePosition(episodeId, 0, 0)
        onPlayed(listOf(episodeId))
    }

    /** Saves a corrected transcript. Only a missing transcript is ever re-transcribed, so edits are kept. */
    suspend fun updateTranscript(id: Long, text: String) = db.annotations().setTranscript(id, text.trim())

    suspend fun clearTranscripts() = db.annotations().clearTranscripts()

    private val transcribeLock = Mutex()

    /** Transcribes every annotation that has no transcript yet. Safe to call from anywhere, any time. */
    suspend fun transcribePending(transcriber: Transcriber) = transcribeLock.withLock {
        for (a in db.annotations().pending()) {
            if (a.audioPath.isEmpty()) { db.annotations().setTranscript(a.id, ""); continue }
            val file = File(a.audioPath)
            // Notes from the first build were m4a; the recognizer needs the raw PCM in newer WAV notes.
            if (!file.exists() || file.extension != "wav") { db.annotations().setTranscript(a.id, ""); continue }
            // null means the recognizer couldn't run at all; leave the rest for next time.
            val text = transcriber.transcribe(file) ?: break
            db.annotations().setTranscript(a.id, text)
            // By default the text is what's kept. If nothing was recognized the recording stays, so it isn't lost.
            if (text.isNotBlank() && !saveAudio()) deleteAnnotationAudio(a.id)
        }
    }

    suspend fun addAnnotation(episodeId: Long, positionMs: Long, audio: File): Long =
        db.annotations().insert(Annotation(episodeId = episodeId, positionMs = positionMs, audioPath = audio.absolutePath))

    /** Deletes the recording but keeps the aside and its transcript. */
    suspend fun deleteAnnotationAudio(id: Long) {
        db.annotations().get(id)?.let { if (it.audioPath.isNotEmpty()) File(it.audioPath).delete() }
        db.annotations().clearAudio(id)
    }

    suspend fun deleteAnnotation(id: Long) {
        db.annotations().get(id)?.let { if (it.audioPath.isNotEmpty()) File(it.audioPath).delete() }
        db.annotations().delete(id)
    }
}
