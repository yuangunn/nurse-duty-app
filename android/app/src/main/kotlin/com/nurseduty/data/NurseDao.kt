package com.nurseduty.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NurseDao {
    // profiles
    @Query("SELECT * FROM duty_profile ORDER BY sortOrder")
    fun profiles(): Flow<List<DutyProfileEntity>>

    @Query("SELECT * FROM duty_profile")
    suspend fun allProfilesOnce(): List<DutyProfileEntity>

    @Upsert suspend fun upsertProfile(p: DutyProfileEntity)

    // alarms
    @Query("SELECT * FROM alarm ORDER BY sortOrder")
    fun alarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm")
    suspend fun allAlarmsOnce(): List<AlarmEntity>

    @Upsert suspend fun upsertAlarm(a: AlarmEntity)
    @Delete suspend fun deleteAlarm(a: AlarmEntity)

    // checklist
    @Query("SELECT * FROM checklist_item ORDER BY sortOrder")
    fun checklistItems(): Flow<List<ChecklistItemEntity>>

    @Upsert suspend fun upsertChecklistItem(i: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_check")
    fun checks(): Flow<List<ChecklistCheckEntity>>

    @Query("SELECT * FROM checklist_check WHERE checklistItemId = :itemId AND dayKey = :dayKey LIMIT 1")
    suspend fun check(itemId: String, dayKey: Int): ChecklistCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCheck(c: ChecklistCheckEntity)
    @Delete suspend fun deleteCheck(c: ChecklistCheckEntity)

    // assignments
    @Query("SELECT * FROM shift_assignment")
    fun assignments(): Flow<List<ShiftAssignmentEntity>>

    @Query("SELECT * FROM shift_assignment")
    suspend fun allAssignmentsOnce(): List<ShiftAssignmentEntity>

    @Query("SELECT * FROM shift_assignment WHERE dayKey = :dayKey LIMIT 1")
    suspend fun assignment(dayKey: Int): ShiftAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAssignment(a: ShiftAssignmentEntity)

    @Query("DELETE FROM shift_assignment WHERE dayKey = :dayKey")
    suspend fun deleteAssignment(dayKey: Int)

    // memos
    @Query("SELECT * FROM quick_memo ORDER BY createdAt DESC")
    fun memos(): Flow<List<QuickMemoEntity>>

    @Upsert suspend fun upsertMemo(m: QuickMemoEntity)
    @Delete suspend fun deleteMemo(m: QuickMemoEntity)

    @Query("SELECT COUNT(*) FROM duty_profile")
    suspend fun profileCount(): Int
}
