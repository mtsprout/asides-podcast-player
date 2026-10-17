package com.asides.app.data

import org.json.JSONObject

/** One chapter of an episode: where it starts and what it is called. */
data class Chapter(val startMs: Long, val title: String)

/**
 * Chapters as publishers supply them: a Podcasting 2.0 JSON file linked from the feed
 * (`{"chapters":[{"startTime":0,"title":"Intro"},...]}`, start times in seconds).
 */
object Chapters {
    /** Reads the JSON; anything unusable yields an empty list rather than an error. */
    fun parse(json: String): List<Chapter> = try {
        val array = JSONObject(json).optJSONArray("chapters")
        (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val c = array!!.optJSONObject(i) ?: return@mapNotNull null
            // toc=false marks entries meant to be hidden from a chapter list (ad slots, images only).
            if (!c.optBoolean("toc", true)) return@mapNotNull null
            val title = c.optString("title").trim()
            if (title.isEmpty() || !c.has("startTime")) return@mapNotNull null
            Chapter((c.optDouble("startTime") * 1000).toLong().coerceAtLeast(0), title)
        }.sortedBy { it.startMs }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    /** The index of the chapter playing at [positionMs], or -1 before the first one starts. */
    fun indexAt(chapters: List<Chapter>, positionMs: Long): Int = chapters.indexOfLast { it.startMs <= positionMs }
}
