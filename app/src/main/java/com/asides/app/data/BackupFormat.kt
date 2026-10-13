package com.asides.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The app settings that travel with a backup. */
data class BackupSettings(
    val autoplay: Boolean,
    val speed: Float,
    val newestFirst: Boolean,
    val hidePlayed: Boolean,
    val listDensity: String,
    /** Genre ids chosen as interests, comma separated; empty if none. */
    val interests: String = "",
)

data class BackupPodcast(
    val feedUrl: String,
    val title: String,
    val artworkUrl: String?,
    val subscribed: Boolean,
    val notifyNew: Boolean,
)

/** An episode worth remembering: one that is played, partly played, or has asides on it. */
data class BackupEpisode(
    val feedUrl: String,
    val guid: String?,
    val audioUrl: String,
    val title: String,
    val pubDate: Long,
    val durationMs: Long,
    val positionMs: Long,
    val played: Boolean,
)

/** One aside. Episodes are named by feed and guid/audio URL rather than database ids, so a backup can be restored anywhere. */
data class BackupAside(
    val feedUrl: String,
    val episodeGuid: String?,
    val episodeAudioUrl: String,
    val positionMs: Long,
    val createdAt: Long,
    val transcript: String?,
    /** The recording's name inside the backup file, or null if the audio was missing when it was made. */
    val audio: String?,
)

data class BackupData(
    val appVersion: String,
    val exportedAt: Long,
    val settings: BackupSettings,
    val podcasts: List<BackupPodcast>,
    val episodes: List<BackupEpisode>,
    val asides: List<BackupAside>,
)

/** Raised for a file that isn't a usable Asides backup; the message is safe to show to the user. */
class BackupException(message: String) : Exception(message)

/** Reads and writes the JSON inside a backup file. */
object BackupFormat {
    /** Bumped only when a change would stop older apps reading a file. */
    const val VERSION = 1

    fun encode(data: BackupData): String = JSONObject().apply {
        put("app", "Asides")
        put("format", VERSION)
        put("appVersion", data.appVersion)
        put("exportedAt", data.exportedAt)
        put("settings", JSONObject().apply {
            put("autoplay", data.settings.autoplay)
            put("speed", data.settings.speed.toDouble())
            put("newestFirst", data.settings.newestFirst)
            put("hidePlayed", data.settings.hidePlayed)
            put("listDensity", data.settings.listDensity)
            put("interests", data.settings.interests)
        })
        put("podcasts", JSONArray(data.podcasts.map {
            JSONObject().put("feedUrl", it.feedUrl).put("title", it.title).put("artworkUrl", it.artworkUrl ?: JSONObject.NULL)
                .put("subscribed", it.subscribed).put("notifyNew", it.notifyNew)
        }))
        put("episodes", JSONArray(data.episodes.map {
            JSONObject().put("feedUrl", it.feedUrl).put("guid", it.guid ?: JSONObject.NULL).put("audioUrl", it.audioUrl)
                .put("title", it.title).put("pubDate", it.pubDate).put("durationMs", it.durationMs)
                .put("positionMs", it.positionMs).put("played", it.played)
        }))
        put("asides", JSONArray(data.asides.map {
            JSONObject().put("feedUrl", it.feedUrl).put("episodeGuid", it.episodeGuid ?: JSONObject.NULL)
                .put("episodeAudioUrl", it.episodeAudioUrl).put("positionMs", it.positionMs)
                .put("createdAt", it.createdAt).put("transcript", it.transcript ?: JSONObject.NULL)
                .put("audio", it.audio ?: JSONObject.NULL)
        }))
    }.toString(2)

    fun decode(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupException("That file isn't an Asides backup.")
        }
        if (root.optString("app") != "Asides") throw BackupException("That file isn't an Asides backup.")
        val format = root.optInt("format", -1)
        if (format < 1) throw BackupException("That file isn't an Asides backup.")
        if (format > VERSION) throw BackupException("This backup was made by a newer version of Asides. Update the app, then try again.")
        try {
            val s = root.getJSONObject("settings")
            return BackupData(
                appVersion = root.optString("appVersion"),
                exportedAt = root.optLong("exportedAt"),
                settings = BackupSettings(
                    autoplay = s.optBoolean("autoplay", true),
                    speed = s.optDouble("speed", 1.0).toFloat(),
                    newestFirst = s.optBoolean("newestFirst", false),
                    hidePlayed = s.optBoolean("hidePlayed", false),
                    listDensity = s.optString("listDensity", "compact"),
                    interests = s.optString("interests", ""),
                ),
                podcasts = root.getJSONArray("podcasts").objects().map {
                    BackupPodcast(it.getString("feedUrl"), it.getString("title"), it.optStringOrNull("artworkUrl"),
                        it.optBoolean("subscribed", true), it.optBoolean("notifyNew", false))
                },
                episodes = root.getJSONArray("episodes").objects().map {
                    BackupEpisode(it.getString("feedUrl"), it.optStringOrNull("guid"), it.getString("audioUrl"), it.getString("title"),
                        it.optLong("pubDate"), it.optLong("durationMs"), it.optLong("positionMs"), it.optBoolean("played"))
                },
                asides = root.getJSONArray("asides").objects().map {
                    BackupAside(it.getString("feedUrl"), it.optStringOrNull("episodeGuid"), it.getString("episodeAudioUrl"),
                        it.optLong("positionMs"), it.optLong("createdAt"), it.optStringOrNull("transcript"), it.optStringOrNull("audio"))
                },
            )
        } catch (e: JSONException) {
            throw BackupException("That backup file is damaged and can't be read.")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
}

/** Finds an episode we already have for one named in a backup: by guid first, then by audio URL. */
object BackupMatch {
    fun episode(known: List<KnownEpisode>, guid: String?, audioUrl: String): KnownEpisode? =
        guid?.let { g -> known.firstOrNull { it.guid == g } } ?: known.firstOrNull { it.audioUrl == audioUrl }

    /** Whether an aside from a backup is already present (same place in the episode, made at the same moment). */
    fun alreadyHave(existing: List<Annotation>, positionMs: Long, createdAt: Long): Boolean =
        existing.any { it.positionMs == positionMs && it.createdAt == createdAt }
}
