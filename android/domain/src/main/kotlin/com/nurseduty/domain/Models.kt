package com.nurseduty.domain

import java.time.LocalDateTime

/** Pure domain models for the scheduling logic (Room entities in the app module map to these). */

data class AlarmSpec(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val dayOffset: Int = 0,   // 0 = same day as shift, +1 = next morning (night shift handover)
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class DutyProfile(
    val id: String,
    val name: String,
    val colorHex: String,
    val isArchived: Boolean = false,
    val alarms: List<AlarmSpec> = emptyList(),
)

data class ShiftAssignment(
    val dayKey: Int,            // yyyymmdd of the shift start day
    val dutyProfileId: String,
)

data class PlannedAlarm(
    val id: String,            // "YYYY-MM-DD#dutyProfileId#alarmId"
    val fireAt: LocalDateTime,
    val title: String,
    val body: String,
)
