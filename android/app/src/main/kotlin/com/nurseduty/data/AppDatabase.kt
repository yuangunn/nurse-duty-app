package com.nurseduty.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    exportSchema = true,   // schemas/ is committed so future migrations can be written against history
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NurseDao

    companion object {
        /** v1 (7b, D/E/N/Off flat model) → v2 (kind/timeText + charge modifier). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE duty_profile ADD COLUMN kind TEXT NOT NULL DEFAULT 'Custom'")
                db.execSQL("ALTER TABLE duty_profile ADD COLUMN timeText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shift_assignment ADD COLUMN charge INTEGER NOT NULL DEFAULT 0")
                // map v1 preset names to kinds so hero gradients + charge rules survive the upgrade
                listOf("Day", "Evening", "Night", "Off").forEach {
                    db.execSQL("UPDATE duty_profile SET kind = '$it' WHERE name = '$it' AND isPreset = 1")
                }
            }
        }
    }
}
