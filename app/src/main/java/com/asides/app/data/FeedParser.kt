package com.asides.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedEpisode(
    val title: String,
    val pubDate: Long,
    val audioUrl: String,
    val durationMs: Long,
    /** The feed's own id for this episode, when it provides one. */
    val guid: String? = null,
    /** Raw show notes, usually HTML; convert with [FeedParser.plainText] when displaying. */
    val descriptionHtml: String = "",
    /** Link to a Podcasting 2.0 chapters file (`<podcast:chapters url=...>`), when the episode has one. */
    val chaptersUrl: String? = null,
)

data class ParsedFeed(
    val title: String,
    val artworkUrl: String?,
    val episodes: List<ParsedEpisode>,
    val description: String = "",
    val author: String = "",
    /** The feed's top-level iTunes category names, e.g. "Religion & Spirituality". */
    val categories: List<String> = emptyList(),
)

/** Minimal RSS 2.0 / iTunes podcast feed parser. */
object FeedParser {
    fun parse(input: InputStream): ParsedFeed {
        val p = Xml.newPullParser()
        p.setInput(input, null)

        var feedTitle = ""
        var description = ""
        var summary = ""
        var author = ""
        var inImage = false
        val categories = ArrayList<String>()
        var categoryDepth = 0
        var artwork: String? = null
        val episodes = ArrayList<ParsedEpisode>()

        var inItem = false
        var title = ""
        var pub = 0L
        var audio: String? = null
        var duration = 0L
        var itemDesc = ""
        var itemGuid: String? = null
        var itemChapters: String? = null
        var itemEncoded = ""
        var itemSummary = ""

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    // Xml.newPullParser() is namespace-aware: the name has no prefix ("image" for both
                    // <image> and <itunes:image>), so the namespace tells them apart.
                    val ns = p.namespace.orEmpty()
                    val plain = ns.isEmpty()
                    val itunes = ns.contains("itunes.com")
                    when (p.name) {
                        "category" -> if (!inItem) {
                            // Subcategories are nested inside their top-level category; only that one is wanted.
                            if (categoryDepth == 0) p.getAttributeValue(null, "text")?.trim()?.takeIf { it.isNotEmpty() }?.let { categories += it }
                            categoryDepth++
                        }
                        "item" -> {
                            inItem = true; title = ""; pub = 0L; audio = null; duration = 0L
                            itemDesc = ""; itemEncoded = ""; itemSummary = ""; itemGuid = null; itemChapters = null
                        }
                        "title" -> if (plain) {
                            val t = p.nextText().trim()
                            if (inItem) title = t else if (!inImage && feedTitle.isEmpty()) feedTitle = t
                        }
                        "description" -> if (plain) {
                            if (inItem) itemDesc = p.nextText() else if (!inImage) description = p.nextText()
                        }
                        "summary" -> if (itunes) {
                            if (inItem) itemSummary = p.nextText() else summary = p.nextText()
                        }
                        "encoded" -> if (inItem && ns.contains("purl.org/rss/1.0/modules/content")) itemEncoded = p.nextText()
                        "guid" -> if (inItem && plain) itemGuid = p.nextText().trim().ifEmpty { null }
                        // Only the plain-text forms: <atom:author> has child elements and nextText() would throw on it.
                        "author" -> if (!inItem && author.isEmpty() && (plain || itunes)) author = p.nextText().trim()
                        "pubDate" -> if (inItem) pub = parseDate(p.nextText())
                        "enclosure" -> if (inItem) audio = p.getAttributeValue(null, "url")
                        "chapters" -> if (inItem && ns.contains("podcastindex", ignoreCase = true)) itemChapters = p.getAttributeValue(null, "url")?.trim()?.ifEmpty { null }
                        "duration" -> if (inItem) duration = parseDuration(p.nextText())
                        "image" -> if (!inItem) {
                            if (plain) inImage = true
                            // <itunes:image href="..."/> is preferred over <image><url>...</url></image>.
                            p.getAttributeValue(null, "href")?.let { artwork = it }
                        }
                        "url" -> if (!inItem && artwork == null && plain) artwork = p.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "category" -> if (categoryDepth > 0) categoryDepth--
                    "image" -> if (p.namespace.orEmpty().isEmpty()) inImage = false
                    "item" -> {
                        inItem = false
                        audio?.let {
                            episodes += ParsedEpisode(
                                title, pub, it, duration, itemGuid,
                                itemEncoded.ifBlank { itemDesc }.ifBlank { itemSummary },
                                itemChapters,
                            )
                        }
                    }
                }
            }
            event = p.next()
        }
        return ParsedFeed(feedTitle, artwork, episodes, plainText(description.ifBlank { summary }), author, categories)
    }

    /** Feed descriptions are often HTML; show them as plain text. */
    internal fun plainText(html: String): String =
        androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString().trim()

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
    )

    internal fun parseDate(s: String): Long {
        val text = s.trim()
        for (f in dateFormats) {
            try {
                return SimpleDateFormat(f, Locale.US).parse(text)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    /** "HH:MM:SS", "MM:SS" or plain seconds. */
    internal fun parseDuration(s: String): Long {
        val parts = s.trim().split(":").map { it.toDoubleOrNull() ?: return 0L }
        val seconds = parts.fold(0.0) { acc, v -> acc * 60 + v }
        return (seconds * 1000).toLong()
    }
}
