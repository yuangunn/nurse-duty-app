package com.nurseduty.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RotationTest {
    private val d1 = LocalDate.of(2026, 7, 1)

    @Test
    fun cyclesPatternAcrossRange() {
        val plan = RotationPlanner.plan(d1, LocalDate.of(2026, 7, 7), listOf("D", "D", "E", "N", "O"))
        assertEquals(7, plan.size)
        assertEquals(listOf("D", "D", "E", "N", "O", "D", "D"), plan.map { it.second })
        assertEquals(20260701, plan.first().first)
        assertEquals(20260707, plan.last().first)
    }

    @Test
    fun skipKeepsRhythmAlignedToDates() {
        // 7/2 already assigned + no overwrite → skipped, but 7/3 still gets index 2 ("E")
        val plan = RotationPlanner.plan(
            d1, LocalDate.of(2026, 7, 3), listOf("D", "M", "E"),
            existingDayKeys = setOf(20260702), overwrite = false,
        )
        assertEquals(listOf(20260701 to "D", 20260703 to "E"), plan)
    }

    @Test
    fun emptyPatternOrInvertedRangeIsEmpty() {
        assertEquals(emptyList(), RotationPlanner.plan(d1, d1.plusDays(3), emptyList()))
        assertEquals(emptyList(), RotationPlanner.plan(d1, d1.minusDays(1), listOf("D")))
    }
}
