package com.nurseduty.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Whole-store snapshot for JSON export/import (mirrors the iOS backup). */
@Serializable
data class Backup(
    val version: Int = 1,
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
    }
}
