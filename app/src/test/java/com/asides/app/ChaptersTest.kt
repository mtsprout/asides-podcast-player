package com.asides.app

import com.asides.app.data.Chapter
import com.asides.app.data.Chapters
import org.junit.Assert.assertEquals
import org.junit.Test

class ChaptersTest {
    @Test fun readsSortsAndDropsHiddenOrNamelessEntries() {
        val json = """{"version":"1.2.0","chapters":[
            {"startTime":90.5,"title":"Second"},
            {"startTime":0,"title":"Intro"},
            {"startTime":30,"title":"Ad","toc":false},
            {"startTime":40,"title":" "},
            {"title":"No start"}
        ]}"""
        assertEquals(listOf(Chapter(0, "Intro"), Chapter(90_500, "Second")), Chapters.parse(json))
    }

    @Test fun garbageGivesNoChapters() {
        assertEquals(emptyList<Chapter>(), Chapters.parse("not json"))
        assertEquals(emptyList<Chapter>(), Chapters.parse("""{"hello":1}"""))
    }

    @Test fun indexAtFindsTheCurrentChapter() {
        val c = listOf(Chapter(1000, "A"), Chapter(5000, "B"))
        assertEquals(-1, Chapters.indexAt(c, 500))
        assertEquals(0, Chapters.indexAt(c, 1000))
        assertEquals(1, Chapters.indexAt(c, 99_000))
    }
}
