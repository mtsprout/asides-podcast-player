package com.asides.app.data

/** What the database already knows about an episode, enough to recognize it in a fresh copy of the feed. */
data class KnownEpisode(val id: Long, val guid: String?, val audioUrl: String, val title: String, val pubDate: Long)

/** Rewrites an existing episode's identity: its feed id and audio URL as the feed now reports them. */
data class EpisodeUpdate(val id: Long, val guid: String?, val audioUrl: String)

/** What a refresh should do: [inserts] are genuinely new episodes, [updates] fix up ones we already have. */
data class MergePlan(val inserts: List<ParsedEpisode>, val updates: List<EpisodeUpdate>)

/**
 * Decides which episodes in a freshly downloaded feed are new. Episodes are recognized by the id the
 * feed gives them (its guid), then by audio URL, then by title and date. Several feeds change the
 * tracking tags on their audio URLs between downloads, so matching on the URL alone would show the same
 * episode twice. Episodes saved by older versions have no guid; they are matched another way and
 * given one.
 */
object EpisodeMerge {
    fun plan(known: List<KnownEpisode>, parsed: List<ParsedEpisode>): MergePlan {
        val byGuid = known.filter { it.guid != null }.associateBy { it.guid!! }
        val byUrl = known.associateBy { it.audioUrl }
        val byTitleAndDate = known.associateBy { it.title to it.pubDate }

        val matched = HashSet<Long>()
        val queuedInserts = HashSet<String>()
        val inserts = ArrayList<ParsedEpisode>()
        val updates = ArrayList<EpisodeUpdate>()

        for (p in parsed) {
            val match = p.guid?.let { byGuid[it] } ?: byUrl[p.audioUrl] ?: byTitleAndDate[p.title to p.pubDate]
            if (match != null) {
                if (!matched.add(match.id)) continue // the feed lists the same episode twice
                val urlTakenByAnother = byUrl[p.audioUrl]?.let { it.id != match.id } == true
                val newGuid = p.guid != null && p.guid != match.guid
                val newUrl = p.audioUrl != match.audioUrl && !urlTakenByAnother
                if (newGuid || newUrl) {
                    updates += EpisodeUpdate(match.id, p.guid ?: match.guid, if (newUrl) p.audioUrl else match.audioUrl)
                }
            } else if (queuedInserts.add(p.guid ?: p.audioUrl)) {
                inserts += p
            }
        }
        return MergePlan(inserts, updates)
    }
}
