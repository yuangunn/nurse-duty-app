package com.nurseduty.data

import com.nurseduty.domain.DayKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Whole-store snapshot for JSON export/import (mirrors the iOS backup). */
@Serializable
data class Backup(
    val version: Int,   // required on purpose: an unrelated/empty JSON must fail decode, not wipe the store
    val profiles: List<DutyProfileEntity> = emptyList(),
    val alarms: List<AlarmEntity> = emptyList(),
    val checklist: List<ChecklistItemEntity> = emptyList(),
    val checks: List<ChecklistCheckEntity> = emptyList(),
    val assignments: List<ShiftAssignmentEntity> = emptyList(),
    val memos: List<QuickMemoEntity> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        fun encode(b: Backup): String = json.encodeToString(b)
        fun decode(s: String): Backup = json.decodeFromString(s)

        /** Rejects backups that would wipe the store or crash the alarm planner after import. */
        fun valid(b: Backup): Boolean =
            b.version == 1 &&
                b.profiles.isNotEmpty() &&
                b.alarms.all { it.hour in 0..23 && it.minute in 0..59 && it.dayOffset in -1..1 } &&
                (b.assignments.asSequence().map { it.dayKey } + b.checks.asSequence().map { it.dayKey })
                    .all { runCatching { DayKey.toLocalDate(it) }.isSuccess }
    }
}
