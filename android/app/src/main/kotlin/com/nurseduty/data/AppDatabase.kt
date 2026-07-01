package com.nurseduty.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DutyProfileEntity::class,
        AlarmEntity::class,
        ChecklistItemEntity::class,
        ChecklistCheckEntity::class,
        ShiftAssignmentEntity::class,
        QuickMemoEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NurseDao
}
