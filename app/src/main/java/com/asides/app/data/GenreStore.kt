package com.asides.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The categories each feed says it belongs to, learned when its feed is read. Kept in a small
 * preferences file rather than the database (it can always be re-learned from the feeds, and it needs
 * no migration). A feed with no recognised category is stored empty, so it isn't looked up again.
 */
class GenreStore(private val prefs: SharedPreferences) {
    private val _all = MutableStateFlow(
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to Discover.parseInterests(it) } }.toMap(),
    )
    /** Feed address to its top-level category ids. */
    val all: StateFlow<Map<String, Set<Int>>> = _all

    fun knows(feedUrl: String) = feedUrl in _all.value

    fun put(feedUrl: String, ids: Set<Int>) {
        if (_all.value[feedUrl] == ids) return
        prefs.edit().putString(feedUrl, Discover.formatInterests(ids)).apply()
        _all.value = _all.value + (feedUrl to ids)
    }
}
