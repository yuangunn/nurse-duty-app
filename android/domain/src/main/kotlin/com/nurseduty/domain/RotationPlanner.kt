package com.nurseduty.domain

import java.time.LocalDate

/** Pure expansion of a repeating shift pattern over a date range (rotation bulk-fill). */
object RotationPlanner {
    /**
     * Returns dayKey → pattern element for start..end, cycling the pattern per calendar day.
     * The cycle index advances on skipped (already-assigned, !overwrite) days too, so the
     * rotation rhythm stays aligned to dates rather than compressing around existing entries.
     */
    fun plan(
        start: LocalDate,
        end: LocalDate,
        pattern: List<String>,
        existingDayKeys: Set<Int> = emptySet(),
        overwrite: Boolean = true,
    ): List<Pair<Int, String>> {
        if (pattern.isEmpty() || start.isAfter(end)) return emptyList()
        val out = mutableListOf<Pair<Int, String>>()
        var d = start
        var i = 0
        while (!d.isAfter(end)) {
            val dk = DayKey.from(d)
            if (overwrite || dk !in existingDayKeys) out += dk to pattern[i % pattern.size]
            d = d.plusDays(1)
            i++
        }
        return out
    }
}
