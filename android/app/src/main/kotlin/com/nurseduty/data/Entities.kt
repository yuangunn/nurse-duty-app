package com.nurseduty.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// kind drives the hero sky gradient + letter/short label: Day/Mid/Evening/Night/Off/Custom
@Serializable
@Entity(tableName = "duty_profile")
data class DutyProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val kind: String = "Custom",
    val timeText: String = "",
    val isPreset: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

@Serializable
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

@Serializable
@Entity(tableName = "checklist_item", indices = [Index("dutyProfileId")])
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val dutyProfileId: String,
    val text: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
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

// dayKey PK = one duty per day. charge is a per-assignment modifier (팀 리더 역할).
@Serializable
@Entity(tableName = "shift_assignment")
data class ShiftAssignmentEntity(
    @PrimaryKey val dayKey: Int,
    val dutyProfileId: String,
    val charge: Boolean = false,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "quick_memo")
data class QuickMemoEntity(
    @PrimaryKey val id: String,
    val bedTag: String,
    val text: String,
    val isDone: Boolean = false,
    val createdAt: Long = 0,
)
