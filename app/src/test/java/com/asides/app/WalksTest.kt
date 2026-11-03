package com.asides.app

import com.asides.app.data.DistanceUnit
import com.asides.app.data.StrideSource
import com.asides.app.data.Walk
import com.asides.app.data.Walks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class WalksTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun walk(start: Long, steps: Long) = Walk(start, start + 30 * 60_000, steps, 30 * 60_000)

    @Test fun totalsByDayWeekMonth() {
        // Tuesday 3 Nov 2026, weeks starting Sunday (1 Nov).
        val now = at(2026, 11, 3, 9)
        val walks = listOf(
            walk(at(2026, 11, 3, 5, 15), 3_000),  // today
            walk(at(2026, 11, 3, 0, 5), 100),     // today, just after midnight
            walk(at(2026, 11, 2, 23, 55), 200),   // yesterday, this week
            walk(at(2026, 11, 1, 6), 400),        // Sunday: this week
            walk(at(2026, 10, 31, 6), 800),       // Saturday: last week, last month
        )
        val s = Walks.summarize(walks, now, zone, DayOfWeek.SUNDAY)
        assertEquals(3_100L, s.today.steps)
        assertEquals(2, s.today.walks)
        assertEquals(3_700L, s.week.steps)
        assertEquals(3_700L, s.month.steps)
        assertEquals(4_500L, s.all.steps)
        assertEquals(5, s.all.walks)
        // With Monday-start weeks, Sunday's walk is last week.
        assertEquals(3_300L, Walks.summarize(walks, now, zone, DayOfWeek.MONDAY).week.steps)
    }

    @Test fun strideChoice() {
        assertEquals(StrideSource.Measured, Walks.stride(80.0, 180).source)
        assertEquals(80.0, Walks.stride(80.0, 180).cm, 0.001)
        assertEquals(74.52, Walks.stride(0.0, 180).cm, 0.001)
        assertEquals(StrideSource.Average, Walks.stride(0.0, 0).source)
    }

    @Test fun calibration() {
        // 3,000 steps over a measured 2.25 km: 75 cm.
        assertEquals(75.0, Walks.strideFrom(3_000, 2_250.0)!!, 0.001)
        assertNull(Walks.strideFrom(3_000, 20_000.0)) // 6.7 m strides: a typo
        assertNull(Walks.strideFrom(0, 1_000.0))
        assertEquals(2_250.0, Walks.distanceMetres(3_000, 75.0), 0.001)
    }

    @Test fun units() {
        assertEquals(DistanceUnit.Mi, Walks.resolve(DistanceUnit.Auto, Locale.US))
        assertEquals(DistanceUnit.Km, Walks.resolve(DistanceUnit.Auto, Locale.GERMANY))
        assertEquals(DistanceUnit.Km, Walks.resolve(DistanceUnit.Km, Locale.US))
        Locale.setDefault(Locale.US)
        assertEquals("1.5 mi", Walks.formatDistance(2_414.0, DistanceUnit.Mi))
        assertEquals("2.4 km", Walks.formatDistance(2_414.0, DistanceUnit.Km))
    }

    @Test fun heights() {
        assertEquals(178, Walks.parseHeightCm("5'10", imperial = true))
        assertEquals(178, Walks.parseHeightCm("5' 10\"", imperial = true))
        assertEquals(178, Walks.parseHeightCm("5 10", imperial = true))
        assertEquals(178, Walks.parseHeightCm("70", imperial = true))
        assertEquals(183, Walks.parseHeightCm("6'", imperial = true))
        assertEquals(178, Walks.parseHeightCm("178", imperial = false))
        assertNull(Walks.parseHeightCm("5", imperial = false))
        assertNull(Walks.parseHeightCm("tall", imperial = true))
        assertEquals("5′10″", Walks.formatHeight(178, imperial = true))
        assertEquals("178 cm", Walks.formatHeight(178, imperial = false))
    }
}
