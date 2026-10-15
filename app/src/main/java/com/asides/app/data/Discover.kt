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

    /** Apple's subcategories of each top-level category (from its genres directory). */
    private val subgenres: Map<Int, Set<Int>> = mapOf(
        1301 to setOf(1306, 1402, 1405, 1406, 1459, 1482),
        1321 to setOf(1410, 1412, 1491, 1492, 1493, 1494),
        1303 to setOf(1495, 1496, 1497),
        1304 to setOf(1498, 1499, 1500, 1501),
        1483 to setOf(1484, 1485, 1486),
        1511 to setOf(),
        1512 to setOf(1513, 1514, 1515, 1516, 1517, 1518),
        1487 to setOf(),
        1305 to setOf(1519, 1520, 1521, 1522),
        1502 to setOf(1503, 1504, 1505, 1506, 1507, 1508, 1509, 1510),
        1310 to setOf(1523, 1524, 1525),
        1489 to setOf(1490, 1526, 1527, 1528, 1529, 1530, 1531),
        1314 to setOf(1438, 1439, 1440, 1441, 1444, 1463, 1532),
        1533 to setOf(1534, 1535, 1536, 1537, 1538, 1539, 1540, 1541, 1542),
        1324 to setOf(1302, 1320, 1443, 1543, 1544),
        1545 to setOf(1546, 1547, 1548, 1549, 1550, 1551, 1552, 1553, 1554, 1555, 1556, 1557, 1558, 1559, 1560),
        1309 to setOf(1561, 1562, 1563, 1564, 1565),
        1318 to setOf(),
        1488 to setOf(),
    )

    /** Whether a directory entry whose categories are [genreIds] belongs to the top-level category [genreId]. */
    fun inGenre(genreIds: Collection<Int>, genreId: Int): Boolean =
        genreId in genreIds || genreIds.any { it in subgenres[genreId].orEmpty() }

    /** The top-level category with this name as Apple spells it (any case), or null (also for "Government"). */
    fun genreIdForName(name: String): Int? = genres.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id

    /**
     * The category the user follows most, as (category, how many followed podcasts are in it), for
     * "Because you follow..." suggestions. Ties go to the category listed first; null if none is known.
     */
    fun topFollowedGenre(genresByFeed: Map<String, Set<Int>>, followedFeeds: Collection<String>): Pair<Genre, Int>? {
        val counts = genres.map { g -> g to followedFeeds.count { g.id in genresByFeed[it].orEmpty() } }
        return counts.filter { it.second > 0 }.maxByOrNull { it.second }
    }

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
     * The episode in [excludeId] (the one on the Continue listening card) is left out.
     */
    fun upNext(unplayed: List<Episode>, newestFirst: Boolean, limit: Int = 10, excludeId: Long = -1L): List<Episode> =
        unplayed.groupBy { it.podcastId }.values.mapNotNull { eps ->
            if (newestFirst) eps.maxByOrNull { it.pubDate } else eps.minByOrNull { it.pubDate }
        }.filter { it.id != excludeId }
            .sortedWith(compareByDescending<Episode> { it.isNew }.thenByDescending { it.pubDate }).take(limit)

    /** Chosen at app start, so the random categories hold still while the app is open and change next time. */
    val launchSeed: Long = System.nanoTime()

    /**
     * The categories to show a Top 5 for, in order: the ones the user picked (at most five, in list
     * order), then random others to make up five in all; with nothing picked, four random ones.
     */
    fun sections(picked: Set<Int>, seed: Long = launchSeed): List<Genre> {
        val rng = java.util.Random(seed)
        val pickedGenres = genres.filter { it.id in picked }
        // Past five picks, a different five each time the app opens (kept in list order).
        val chosen = if (pickedGenres.size <= 5) pickedGenres else pickedGenres.shuffled(rng).take(5).sortedBy { genres.indexOf(it) }
        val randomCount = if (chosen.isEmpty()) 4 else 5 - chosen.size
        val others = genres.filter { it !in chosen }.shuffled(rng)
        return chosen + others.take(randomCount)
    }
}
