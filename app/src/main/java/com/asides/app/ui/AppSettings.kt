package com.asides.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asides.app.data.BackupSettings

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

    fun init(context: Context) {
        prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        listDensity = ListDensity.from(prefs.getString("listDensity", null))
        introSeen = prefs.getBoolean("introSeen", false)
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
    )

    fun apply(settings: BackupSettings) {
        prefs.edit()
            .putBoolean("autoplay", settings.autoplay)
            .putFloat("speed", settings.speed)
            .putBoolean("newestFirst", settings.newestFirst)
            .putBoolean("hidePlayed", settings.hidePlayed)
            .apply()
        chooseListDensity(ListDensity.from(settings.listDensity))
    }
}
