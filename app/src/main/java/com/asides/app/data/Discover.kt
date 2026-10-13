package com.asides.app.data

/** An interest the user can browse by: one of Apple's top-level podcast categories. */
data class Genre(val id: Int, val name: String)

/** Pure helpers for the front page, kept apart from the screens so they can be unit tested. */
object Discover {
    /** Apple's top-level podcast categories (ids checked against the live chart endpoint). */
    val genres = listOf(
        Genre(1303, "Comedy"), Genre(1489, "News"), Genre(1488, "True Crime"), Genre(1487, "History"),
        Genre(1533, "Science"), Genre(1318, "Technology"), Genre(1314, "Religion & Spirituality"),
        Genre(1324, "Society & Culture"), Genre(1512, "Health & Fitness"), Genre(1321, "Business"),
        Genre(1304, "Education"), Genre(1545, "Sports"), Genre(1310, "Music"), Genre(1301, "Arts"),
        Genre(1483, "Fiction"), Genre(1305, "Kids & Family"), Genre(1309, "TV & Film"), Genre(1502, "Leisure"),
    )

    fun genreName(id: Int): String = genres.firstOrNull { it.id == id }?.name ?: "Top podcasts"

    /** "1303,1488" -> {1303, 1488}; anything unreadable or unknown is dropped. */
    fun parseInterests(raw: String?): Set<Int> {
        val known = genres.map { it.id }.toSet()
        return raw.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in known }.toSet()
    }

    fun formatInterests(ids: Set<Int>): String = genres.map { it.id }.filter { it in ids }.joinToString(",")

    /** The start of a transcript, cut at [max] characters on a word boundary, with an ellipsis if cut. */
    fun snippet(text: String?, max: Int = 30): String {
        val t = text?.trim().orEmpty().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return "Transcribing…"
        if (t.length <= max) return t
        val cut = t.take(max)
        val atWord = cut.lastIndexOf(' ').takeIf { it >= max / 2 } ?: max
        return cut.take(atWord).trimEnd(',', '.', ';', ':', ' ') + "…"
    }

    /**
     * What to listen to next: for each podcast, its next unplayed episode in the order its page lists
     * them; episodes a refresh found ("new") come first, then the most recently published.
     */
    fun upNext(unplayed: List<Episode>, newestFirst: Boolean, limit: Int = 10): List<Episode> =
        unplayed.groupBy { it.podcastId }.values.mapNotNull { eps ->
            if (newestFirst) eps.maxByOrNull { it.pubDate } else eps.minByOrNull { it.pubDate }
        }.sortedWith(compareByDescending<Episode> { it.isNew }.thenByDescending { it.pubDate }).take(limit)

    /**
     * Interleaves charts so that each picked interest shows up near the top: first of each, then
     * second of each, and so on; podcasts already followed or seen twice are dropped.
     */
    fun forYou(charts: List<List<SearchResult>>, followed: Set<String>, limit: Int = 12): List<SearchResult> {
        val out = LinkedHashMap<String, SearchResult>()
        var i = 0
        while (out.size < limit && charts.any { i < it.size }) {
            charts.forEach { c -> c.getOrNull(i)?.takeIf { it.feedUrl !in followed }?.let { out.putIfAbsent(it.feedUrl, it) } }
            i++
        }
        return out.values.take(limit)
    }
}
