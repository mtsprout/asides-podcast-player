package com.asides.app

import com.asides.app.data.AnnotationRow
import com.asides.app.ui.AsidesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsidesTextTest {
    private fun row(id: Long, episode: Long, pos: Long, text: String?, ep: String = "Day 1: Genesis", pod: String = "Bible in a Year") =
        AnnotationRow(id, episode, pos, "", text, 0, ep, 0, 1, pod, null)

    @Test fun `every word must appear in the text, episode or podcast`() {
        val r = row(1, 1, 5000, "Forgiveness is a choice")
        assertTrue(AsidesText.matches(r, "forgiveness"))
        assertTrue(AsidesText.matches(r, "CHOICE genesis"))
        assertTrue(AsidesText.matches(r, "  "))
        assertFalse(AsidesText.matches(r, "forgiveness mercy"))
    }

    @Test fun `an aside with no transcript yet can still be found by its episode`() {
        assertTrue(AsidesText.matches(row(1, 1, 0, null), "bible"))
    }

    @Test fun `one aside reads as a short message`() {
        assertEquals(
            "Aside at 1:05 in \"Day 1: Genesis\" (Bible in a Year):\nGood point",
            AsidesText.plain(row(1, 1, 65_000, " Good point ")),
        )
    }

    @Test fun `markdown groups by podcast and episode with asides in time order`() {
        val md = AsidesText.markdown(listOf(row(2, 1, 90_000, "later"), row(1, 1, 30_000, "earlier"), row(3, 2, 0, null, ep = "Day 2")))
        val expected = """# Asides

## Bible in a Year

### Day 1: Genesis

- **0:30** earlier
- **1:30** later

### Day 2

- **0:00** (no transcript)
"""
        assertEquals(expected, md)
    }
}
