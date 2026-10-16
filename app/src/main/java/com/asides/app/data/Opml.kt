package com.asides.app.data

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** One podcast listed in an OPML file. */
data class OpmlFeed(val title: String, val feedUrl: String)

/** OPML is the list-of-subscriptions file every podcast app can export and import. */
object Opml {
    /** At most this many bytes are read from a file, so a wrong pick can't exhaust memory. */
    const val MAX_BYTES = 5 * 1024 * 1024

    fun encode(podcasts: List<Podcast>, appVersion: String = ""): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n  <head>\n    <title>Asides subscriptions</title>\n  </head>\n  <body>\n")
        podcasts.forEach {
            append("    <outline type=\"rss\" text=\"").append(escape(it.title)).append("\" title=\"").append(escape(it.title))
                .append("\" xmlUrl=\"").append(escape(it.feedUrl)).append("\"/>\n")
        }
        append("  </body>\n</opml>\n")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * Every outline with an `xmlUrl` that is an http(s) address, wherever it sits (folders are ignored),
     * without repeats. Throws [IllegalArgumentException] for a file that isn't OPML.
     */
    fun parse(input: InputStream): List<OpmlFeed> {
        val factory = DocumentBuilderFactory.newInstance()
        // A feed list never needs a DOCTYPE or external entities; refuse them where the parser allows.
        for ((feature, value) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) {
            try { factory.setFeature(feature, value) } catch (_: Exception) { }
        }
        factory.isExpandEntityReferences = false
        val doc = try {
            factory.newDocumentBuilder().parse(input.readNBytesCapped())
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't an OPML subscription list.")
        }
        if (!doc.documentElement.tagName.equals("opml", ignoreCase = true)) {
            throw IllegalArgumentException("That file isn't an OPML subscription list.")
        }
        val outlines = doc.getElementsByTagName("outline")
        val found = LinkedHashMap<String, OpmlFeed>()
        for (i in 0 until outlines.length) {
            val e = outlines.item(i) as? Element ?: continue
            val url = attr(e, "xmlUrl")?.trim().orEmpty()
            if (!(url.startsWith("http://", true) || url.startsWith("https://", true))) continue
            val title = (attr(e, "title") ?: attr(e, "text")).orEmpty().trim().ifEmpty { url }
            found.putIfAbsent(url.lowercase(), OpmlFeed(title, url))
        }
        return found.values.toList()
    }

    /** Attribute names vary in case between apps ("xmlUrl", "xmlurl"). */
    private fun attr(e: Element, name: String): String? {
        val attrs = e.attributes
        for (i in 0 until attrs.length) if (attrs.item(i).nodeName.equals(name, ignoreCase = true)) return attrs.item(i).nodeValue
        return null
    }

    private fun InputStream.readNBytesCapped(): java.io.ByteArrayInputStream {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            require(total <= MAX_BYTES) { "too big" }
            out.write(buf, 0, n)
        }
        return java.io.ByteArrayInputStream(out.toByteArray())
    }
}
