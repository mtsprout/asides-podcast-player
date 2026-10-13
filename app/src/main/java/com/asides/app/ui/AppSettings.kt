package com.asides.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asides.app.data.BackupSettings
import com.asides.app.data.Discover

/** How roomy the episode lists are, like Gmail's compact / regular / large. */
enum class ListDensity(val key: String, val label: String) {
    Compact("compact", "Compact"),
    Regular("regular", "Regular"),
    Large("large", "Large");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Compact
    }
}

/**
 * Settings the user can change, kept in the "prefs" file. The ones the screens react to are Compose
 * state, so changing them updates the screen straight away.
 */
object AppSettings {
    private lateinit var prefs: SharedPreferences

    var listDensity by mutableStateOf(ListDensity.Compact)
        private set

    /** Whether the user has dismissed the welcome note on the home page. */
    var introSeen by mutableStateOf(true)
        private set

    /** Keep each aside's recording after it is transcribed. Off by default: recordings take a lot of space. */
    var saveAudio by mutableStateOf(false)
        private set

    /** Genre ids the user follows as interests (see [Discover]); drives the "For you" suggestions. */
    var interests by mutableStateOf(emptySet<Int>())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        listDensity = ListDensity.from(prefs.getString("listDensity", null))
        introSeen = prefs.getBoolean("introSeen", false)
        saveAudio = prefs.getBoolean("saveAudio", false)
        interests = Discover.parseInterests(prefs.getString("interests", null))
    }

    fun toggleInterest(genreId: Int) {
        interests = if (genreId in interests) interests - genreId else interests + genreId
        prefs.edit().putString("interests", Discover.formatInterests(interests)).apply()
    }

    fun chooseSaveAudio(save: Boolean) {
        saveAudio = save
        prefs.edit().putBoolean("saveAudio", save).apply()
    }

    fun chooseListDensity(density: ListDensity) {
        listDensity = density
        prefs.edit().putString("listDensity", density.key).apply()
    }

    fun markIntroSeen(seen: Boolean) {
        introSeen = seen
        prefs.edit().putBoolean("introSeen", seen).apply()
    }

    /** The settings that travel with a backup. */
    fun snapshot() = BackupSettings(
        autoplay = prefs.getBoolean("autoplay", true),
        speed = prefs.getFloat("speed", 1f),
        newestFirst = prefs.getBoolean("newestFirst", false),
        hidePlayed = prefs.getBoolean("hidePlayed", false),
        listDensity = listDensity.key,
        interests = Discover.formatInterests(interests),
    )

    fun apply(settings: BackupSettings) {
        prefs.edit()
            .putBoolean("autoplay", settings.autoplay)
            .putFloat("speed", settings.speed)
            .putBoolean("newestFirst", settings.newestFirst)
            .putBoolean("hidePlayed", settings.hidePlayed)
            .apply()
        chooseListDensity(ListDensity.from(settings.listDensity))
        // A restore adds interests, like everything else it brings back; it never removes any.
        Discover.parseInterests(settings.interests).forEach { if (it !in interests) toggleInterest(it) }
    }
}
