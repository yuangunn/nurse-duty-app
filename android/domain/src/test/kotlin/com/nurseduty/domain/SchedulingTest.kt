package com.nurseduty.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulingTest {

    // Night shift's 06:00 handover (dayOffset = 1) written on Jun 30 must fire Jul 1 06:00.
    @Test
    fun nightShiftAlarmFiresNextMorning() {
        val fire = AlarmScheduling.fireDateTime(LocalDate.of(2026, 6, 30), 6, 0, 1)
        assertEquals(LocalDateTime.of(2026, 7, 1, 6, 0), fire)
    }

    @Test
    fun sameDayAlarmFiresSameDay() {
        val fire = AlarmScheduling.fireDateTime(LocalDate.of(2026, 6, 30), 7, 0, 0)
        assertEquals(LocalDateTime.of(2026, 6, 30, 7, 0), fire)
    }

    @Test
    fun dayKeyRoundTrips() {
        assertEquals(20260630, DayKey.from(LocalDate.of(2026, 6, 30)))
        assertEquals(LocalDate.of(2026, 7, 1), DayKey.toLocalDate(20260701))
    }

    private fun dayProfile() = DutyProfile(
        id = "day", name = "Day", colorHex = "#4F86C6",
        alarms = listOf(
            AlarmSpec("a", "기상", 6, 0),
            AlarmSpec("b", "인계", 7, 0),
            AlarmSpec("c", "점심 라운딩", 12, 0),
        ),
    )

    // Acceptance check: a full month of alarms is capped to the budget and kept soonest-first.
    @Test
    fun planCapsAtBudgetAndSortsSoonestFirst() {
        val profile = dayProfile()
        val byId = mapOf(profile.id to profile)
        val start = LocalDate.of(2026, 6, 1)
        val assignments = (0 until 30).map { ShiftAssignment(DayKey.from(start.plusDays(it.toLong())), profile.id) }

        val plan = AlarmPlanner.plan(assignments, byId, start.atStartOfDay(), windowDays = 28, budget = 50)

        assertEquals(50, plan.size)                       // 90 possible -> capped
        assertEquals(plan.sortedBy { it.fireAt }, plan)   // soonest-first
        assertEquals(50, plan.map { it.id }.toSet().size) // unique ids
    }

    @Test
    fun planSkipsArchivedProfiles() {
        val archived = DutyProfile("day", "Day", "#4F86C6", isArchived = true,
            alarms = listOf(AlarmSpec("a", "인계", 23, 0)))
        val byId = mapOf(archived.id to archived)
        val now = LocalDateTime.of(2026, 6, 30, 22, 0)
        val plan = AlarmPlanner.plan(
            listOf(ShiftAssignment(20260630, archived.id)), byId, now, windowDays = 7, budget = 50,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun planPlacesNightHandoverNextMorning() {
        val night = DutyProfile("n", "Night", "#3B4A6B",
            alarms = listOf(AlarmSpec("h", "아침 인계", 6, 0, dayOffset = 1)))
        val byId = mapOf(night.id to night)
        val plan = AlarmPlanner.plan(
            listOf(ShiftAssignment(20260630, night.id)), byId,
            LocalDateTime.of(2026, 6, 30, 9, 0), windowDays = 7, budget = 50,
        )
        assertEquals(1, plan.size)
        assertEquals(LocalDateTime.of(2026, 7, 1, 6, 0), plan[0].fireAt)
    }

    @Test
    fun reconcileReemitsDesiredAndCancelsStale() {
        fun pa(id: String) = PlannedAlarm(id, LocalDateTime.of(2026, 1, 1, 0, 0), "", "")
        val (schedule, cancel) = AlarmPlanner.reconcile(setOf("A", "B", "C"), listOf(pa("B"), pa("C"), pa("D")))
        assertEquals(setOf("B", "C", "D"), schedule.map { it.id }.toSet())
        assertEquals(listOf("A"), cancel)
    }
}
