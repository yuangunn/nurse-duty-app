package com.nurseduty.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupTest {
    @Test
    fun backupRoundTrips() {
        val b = Backup(
            profiles = listOf(DutyProfileEntity("p", "Day", "#4F86C6", isPreset = true, sortOrder = 0)),
            alarms = listOf(AlarmEntity("a", "p", "인계", 7, 0, 0, true, 0)),
            checklist = listOf(ChecklistItemEntity("c", "p", "활력징후 측정", false, 0)),
            checks = listOf(ChecklistCheckEntity("k", "c", 20260630, 0)),
            assignments = listOf(ShiftAssignmentEntity(20260630, "p", null)),
            memos = listOf(QuickMemoEntity("m", "1001:01", "진통제 호소", false, 0)),
        )
        val back = Backup.decode(Backup.encode(b))
        assertEquals(b, back)
        assertEquals("Day", back.profiles.single().name)
        assertEquals(20260630, back.assignments.single().dayKey)
    }
}
