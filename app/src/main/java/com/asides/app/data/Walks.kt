package com.asides.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Steps, playing time and number of walks over some period. */
data class WalkTotals(val steps: Long = 0, val activeMs: Long = 0, val walks: Int = 0) {
    operator fun plus(w: Walk) = WalkTotals(steps + w.steps, activeMs + w.activeMs, walks + 1)
}

data class WalkSummary(val today: WalkTotals, val week: WalkTotals, val month: WalkTotals, val all: WalkTotals)

/** Where the stride used for distances comes from. */
enum class StrideSource { Measured, Height, Average }

data class Stride(val cm: Double, val source: StrideSource)

enum class DistanceUnit(val key: String, val label: String) {
    Auto("auto", "Automatic"), Km("km", "Kilometres"), Mi("mi", "Miles");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/**
 * Walk arithmetic, kept free of Android so it can be tested. A walk belongs to the day (week, month) it
 * started in. Distance is steps times stride; nothing measures it directly.
 */
object Walks {
    /** A typical adult stride, used until a height or a measured walk gives a better one. */
    const val AVERAGE_STRIDE_CM = 76.2
    /** Stride is roughly this fraction of height. */
    const val STRIDE_PER_HEIGHT = 0.414
    /** Strides outside this range are a typo or a mis-measured walk, not a person. */
    val PLAUSIBLE_STRIDE_CM = 30.0..150.0

    fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun summarize(walks: List<Walk>, now: Long, zone: ZoneId, firstDayOfWeek: DayOfWeek): WalkSummary {
        val today = date(now, zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val month = YearMonth.from(today)
        var d = WalkTotals(); var w = WalkTotals(); var m = WalkTotals(); var all = WalkTotals()
        for (walk in walks) {
            val day = date(walk.id, zone)
            all += walk
            if (YearMonth.from(day) == month) m += walk
            if (!day.isBefore(weekStart) && !day.isAfter(today)) w += walk
            if (day == today) d += walk
        }
        return WalkSummary(d, w, m, all)
    }

    /** A measured stride wins, then one estimated from height, then the average. Zero means "not set". */
    fun stride(measuredCm: Double, heightCm: Int): Stride = when {
        measuredCm > 0 -> Stride(measuredCm, StrideSource.Measured)
        heightCm > 0 -> Stride(heightCm * STRIDE_PER_HEIGHT, StrideSource.Height)
        else -> Stride(AVERAGE_STRIDE_CM, StrideSource.Average)
    }

    fun distanceMetres(steps: Long, strideCm: Double) = steps * strideCm / 100.0

    /** The stride that makes [steps] cover [metres]; null if that isn't a believable stride. */
    fun strideFrom(steps: Long, metres: Double): Double? {
        if (steps <= 0 || metres <= 0) return null
        return (metres * 100.0 / steps).takeIf { it in PLAUSIBLE_STRIDE_CM }
    }

    /** Miles where people walk in miles; kilometres everywhere else. */
    fun resolve(unit: DistanceUnit, locale: Locale): DistanceUnit = when (unit) {
        DistanceUnit.Auto -> if (locale.country in MILE_COUNTRIES) DistanceUnit.Mi else DistanceUnit.Km
        else -> unit
    }

    private val MILE_COUNTRIES = setOf("US", "GB", "LR", "MM")

    fun metresPer(unit: DistanceUnit) = if (unit == DistanceUnit.Mi) 1609.344 else 1000.0

    /** "2.4 km", "1.5 mi". [unit] must already be resolved. */
    fun formatDistance(metres: Double, unit: DistanceUnit): String =
        String.format(Locale.getDefault(), "%.1f %s", metres / metresPer(unit), if (unit == DistanceUnit.Mi) "mi" else "km")

    /**
     * A height as typed. Metric: centimetres ("178"). Imperial: feet and inches ("5'10", "5' 10\"", "5 10")
     * or inches alone ("70"). Null if it isn't a believable height.
     */
    fun parseHeightCm(text: String, imperial: Boolean): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val cm = if (!imperial) t.toDoubleOrNull() else {
            val parts = t.split(Regex("[^0-9.]+")).filter { it.isNotEmpty() }.map { it.toDoubleOrNull() ?: return null }
            when (parts.size) {
                1 -> if (t.contains('\'')) parts[0] * 30.48 else parts[0] * 2.54
                2 -> parts[0] * 30.48 + parts[1] * 2.54
                else -> null
            }
        } ?: return null
        return cm.roundToInt().takeIf { it in 100..250 }
    }

    /** A height for showing back: "178 cm" or "5′10″". */
    fun formatHeight(cm: Int, imperial: Boolean): String {
        if (!imperial) return "$cm cm"
        val inches = (cm / 2.54).roundToInt()
        return "${inches / 12}′${inches % 12}″"
    }
}
