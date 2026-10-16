package com.asides.app

import com.asides.app.data.Opml
import com.asides.app.data.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpmlTest {
    private fun parse(xml: String) = Opml.parse(xml.byteInputStream())

    @Test fun `reads feeds from nested folders and skips non-feed outlines`() {
        val feeds = parse(
            """<?xml version="1.0"?><opml version="2.0"><head/><body>
              <outline text="News"><outline type="rss" text="Daily" xmlUrl="https://a.example/feed.xml"/></outline>
              <outline text="Just a label"/>
              <outline type="rss" title="Cast &amp; Crew" xmlurl="http://b.example/rss"/>
              <outline type="rss" text="Odd" xmlUrl="ftp://c.example/x"/>
            </body></opml>""",
        )
        assertEquals(listOf("Daily" to "https://a.example/feed.xml", "Cast & Crew" to "http://b.example/rss"), feeds.map { it.title to it.feedUrl })
    }

    @Test fun `repeated feeds are listed once`() {
        val feeds = parse("""<opml><body><outline xmlUrl="https://a.example/f"/><outline xmlUrl="HTTPS://A.EXAMPLE/f"/></body></opml>""")
        assertEquals(1, feeds.size)
    }

    @Test fun `a file that is not opml is refused`() {
        assertThrows(IllegalArgumentException::class.java) { parse("<rss><channel/></rss>") }
        assertThrows(IllegalArgumentException::class.java) { parse("not xml at all") }
    }

    @Test fun `a doctype with an external entity is not followed`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE opml [<!ENTITY x SYSTEM "file:///etc/passwd">]><opml><body><outline text="&x;" xmlUrl="https://a.example/f"/></body></opml>"""
        // Either refused outright or parsed without reading the file; never the file's contents.
        val result = runCatching { parse(xml) }
        result.getOrNull()?.forEach { assertEquals(false, it.title.contains("root:")) }
    }

    @Test fun `export escapes text and round trips`() {
        val podcasts = listOf(Podcast(id = 1, title = "Q&A \"Live\" <1>", feedUrl = "https://a.example/f?x=1&y=2", artworkUrl = null))
        val feeds = parse(Opml.encode(podcasts))
        assertEquals(listOf("Q&A \"Live\" <1>" to "https://a.example/f?x=1&y=2"), feeds.map { it.title to it.feedUrl })
    }
}
