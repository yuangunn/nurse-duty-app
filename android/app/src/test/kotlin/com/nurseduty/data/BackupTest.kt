package com.nurseduty.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupTest {
    private fun sample() = Backup(
        version = 1,
        profiles = listOf(DutyProfileEntity("p", "Day", "#4F86C6", isPreset = true, sortOrder = 0)),
        alarms = listOf(AlarmEntity("a", "p", "인계", 7, 0, 0, true, 0)),
        checklist = listOf(ChecklistItemEntity("c", "p", "활력징후 측정", false, 0)),
        checks = listOf(ChecklistCheckEntity("k", "c", 20260630, 0)),
        assignments = listOf(ShiftAssignmentEntity(20260630, "p", charge = true)),
        memos = listOf(QuickMemoEntity("m", "1001:01", "진통제 호소", false, 0)),
    )

    @Test
    fun backupRoundTrips() {
        val b = sample()
        val back = Backup.decode(Backup.encode(b))
        assertEquals(b, back)
        assertEquals("Day", back.profiles.single().name)
        assertEquals(20260630, back.assignments.single().dayKey)
    }

    @Test
    fun emptyOrForeignJsonFailsDecode() {
        // an empty object or an unrelated app's JSON must not decode into a wipe-everything backup
        assertFails { Backup.decode("{}") }
        assertFails { Backup.decode("""{"tasks":[{"id":1}]}""") }
    }

    @Test
    fun validRejectsEmptyProfilesAndWrongVersion() {
        assertTrue(Backup.valid(sample()))
        assertFalse(Backup.valid(sample().copy(profiles = emptyList())))
        assertFalse(Backup.valid(sample().copy(version = 2)))
    }

    @Test
    fun validRejectsOutOfRangeAlarmAndDayKey() {
        val base = sample()
        assertFalse(Backup.valid(base.copy(alarms = listOf(AlarmEntity("a", "p", "x", 24, 0, 0, true, 0)))))
        assertFalse(Backup.valid(base.copy(alarms = listOf(AlarmEntity("a", "p", "x", 7, 60, 0, true, 0)))))
        assertFalse(Backup.valid(base.copy(assignments = listOf(ShiftAssignmentEntity(20261301, "p")))))
        assertFalse(Backup.valid(base.copy(checks = listOf(ChecklistCheckEntity("k", "c", 20260732, 0)))))
    }
}
