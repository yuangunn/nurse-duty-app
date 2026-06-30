package com.nurseduty.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pure derivation of when an alarm fires: (shift day + dayOffset) at the alarm's local hour:minute.
 * This is the heart of night-shift-crosses-midnight handling. LocalDateTime keeps it wall-clock.
 */
object AlarmScheduling {
    fun fireDateTime(shiftDay: LocalDate, hour: Int, minute: Int, dayOffset: Int): LocalDateTime =
        shiftDay.plusDays(dayOffset.toLong()).atTime(hour, minute)

    /** Deterministic id so the scheduler can cancel/reschedule idempotently by id. */
    fun notificationId(shiftDay: LocalDate, dutyProfileId: String, alarmId: String): String =
        "%04d-%02d-%02d#%s#%s".format(
            shiftDay.year, shiftDay.monthValue, shiftDay.dayOfMonth, dutyProfileId, alarmId,
        )
}
