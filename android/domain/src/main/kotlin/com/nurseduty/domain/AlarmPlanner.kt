package com.nurseduty.domain

import java.time.LocalDateTime

/**
 * Rolling-window alarm planner — ported from iOS. Android has no 64-pending cap, but a budgeted
 * rolling window is still the right shape (fewer AlarmManager exact alarms, re-armed on app open).
 * Archived profiles never schedule; reconcile re-emits all desired (cancel+reschedule by id is
 * idempotent) and removes ids no longer desired.
 */
object AlarmPlanner {

    fun plan(
        assignments: List<ShiftAssignment>,
        profilesById: Map<String, DutyProfile>,
        from: LocalDateTime,
        windowDays: Int,
        budget: Int,
    ): List<PlannedAlarm> {
        val windowEnd = from.plusDays(windowDays.toLong())
        val planned = mutableListOf<PlannedAlarm>()
        for (assignment in assignments) {
            val profile = profilesById[assignment.dutyProfileId] ?: continue
            if (profile.isArchived) continue
            val shiftDay = DayKey.toLocalDate(assignment.dayKey)
            for (alarm in profile.alarms) {
                if (!alarm.enabled) continue
                val fireAt = AlarmScheduling.fireDateTime(shiftDay, alarm.hour, alarm.minute, alarm.dayOffset)
                if (fireAt.isAfter(from) && !fireAt.isAfter(windowEnd)) {
                    planned.add(
                        PlannedAlarm(
                            id = AlarmScheduling.notificationId(shiftDay, profile.id, alarm.id),
                            fireAt = fireAt,
                            title = alarm.label,
                            body = profile.name + " 근무",
                        ),
                    )
                }
            }
        }
        return planned.sortedBy { it.fireAt }.take(budget)
    }

    /** Returns (toSchedule, toCancel). Re-emit all desired; cancel ids no longer desired. */
    fun reconcile(pending: Set<String>, desired: List<PlannedAlarm>): Pair<List<PlannedAlarm>, List<String>> {
        val desiredIds = desired.map { it.id }.toSet()
        val cancel = (pending - desiredIds).sorted()
        return desired to cancel
    }
}
