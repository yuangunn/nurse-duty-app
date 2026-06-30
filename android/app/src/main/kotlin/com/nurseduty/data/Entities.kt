package com.nurseduty.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "duty_profile")
data class DutyProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val isPreset: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

@Entity(tableName = "alarm", indices = [Index("dutyProfileId")])
data class AlarmEntity(
    @PrimaryKey val id: String,
    val dutyProfileId: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val dayOffset: Int = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(tableName = "checklist_item", indices = [Index("dutyProfileId")])
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val dutyProfileId: String,
    val text: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

// presence == checked; unique per (item, day) so yesterday's checks don't bleed.
@Entity(
    tableName = "checklist_check",
    indices = [Index(value = ["checklistItemId", "dayKey"], unique = true)],
)
data class ChecklistCheckEntity(
    @PrimaryKey val id: String,
    val checklistItemId: String,
    val dayKey: Int,
    val checkedAt: Long = 0,
)

// dayKey as the primary key enforces one-duty-per-day (upsert = REPLACE).
@Entity(tableName = "shift_assignment")
data class ShiftAssignmentEntity(
    @PrimaryKey val dayKey: Int,
    val dutyProfileId: String,
    val note: String? = null,
)

@Entity(tableName = "quick_memo")
data class QuickMemoEntity(
    @PrimaryKey val id: String,
    val bedTag: String,
    val text: String,
    val isDone: Boolean = false,
    val createdAt: Long = 0,
)
