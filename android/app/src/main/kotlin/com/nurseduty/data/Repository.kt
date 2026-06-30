package com.nurseduty.data

import com.nurseduty.alarm.AlarmScheduler
import com.nurseduty.domain.AlarmSpec
import com.nurseduty.domain.DutyProfile
import com.nurseduty.domain.ShiftAssignment
import java.util.UUID

class Repository(private val dao: NurseDao, private val scheduler: AlarmScheduler) {

    val profiles = dao.profiles()
    val alarms = dao.alarms()
    val checklistItems = dao.checklistItems()
    val checks = dao.checks()
    val assignments = dao.assignments()
    val memos = dao.memos()

    // ---- assignments ----
    suspend fun assignDuty(dayKey: Int, profileId: String) {
        dao.upsertAssignment(ShiftAssignmentEntity(dayKey, profileId))
        rescheduleNow()
    }

    suspend fun clearAssignment(dayKey: Int) {
        dao.deleteAssignment(dayKey)
        rescheduleNow()
    }

    // ---- checklist state ----
    suspend fun toggleCheck(itemId: String, dayKey: Int) {
        val existing = dao.check(itemId, dayKey)
        if (existing != null) dao.deleteCheck(existing)
        else dao.insertCheck(ChecklistCheckEntity(UUID.randomUUID().toString(), itemId, dayKey, System.currentTimeMillis()))
    }

    // ---- profiles / alarms / checklist templates ----
    suspend fun saveProfile(id: String?, name: String, colorHex: String) {
        val existing = id?.let { pid -> dao.allProfilesOnce().firstOrNull { it.id == pid } }
        if (existing != null) {
            dao.upsertProfile(existing.copy(name = name, colorHex = colorHex))
        } else {
            val order = (dao.allProfilesOnce().maxOfOrNull { it.sortOrder } ?: -1) + 1
            dao.upsertProfile(DutyProfileEntity(UUID.randomUUID().toString(), name, colorHex,
                sortOrder = order, createdAt = System.currentTimeMillis()))
        }
        rescheduleNow()
    }

    suspend fun archiveProfile(p: DutyProfileEntity) {
        dao.upsertProfile(p.copy(isArchived = true))   // soft-delete keeps history
        rescheduleNow()
    }

    suspend fun saveAlarm(a: AlarmEntity) { dao.upsertAlarm(a); rescheduleNow() }
    suspend fun deleteAlarm(a: AlarmEntity) { dao.deleteAlarm(a); rescheduleNow() }
    suspend fun newAlarmId() = UUID.randomUUID().toString()

    suspend fun saveChecklistItem(i: ChecklistItemEntity) = dao.upsertChecklistItem(i)
    suspend fun archiveChecklistItem(i: ChecklistItemEntity) = dao.upsertChecklistItem(i.copy(isArchived = true))
    suspend fun newChecklistId() = UUID.randomUUID().toString()

    // ---- memos ----
    suspend fun addMemo(bedTag: String, text: String) =
        dao.upsertMemo(QuickMemoEntity(UUID.randomUUID().toString(), bedTag, text, false, System.currentTimeMillis()))
    suspend fun setMemoDone(m: QuickMemoEntity, done: Boolean) = dao.upsertMemo(m.copy(isDone = done))
    suspend fun deleteMemo(m: QuickMemoEntity) = dao.deleteMemo(m)

    // ---- scheduling ----
    suspend fun rescheduleNow() {
        val profiles = dao.allProfilesOnce()
        val alarmsByProfile = dao.allAlarmsOnce().groupBy { it.dutyProfileId }
        val byId = profiles.associate { p ->
            p.id to DutyProfile(
                id = p.id, name = p.name, colorHex = p.colorHex, isArchived = p.isArchived,
                alarms = (alarmsByProfile[p.id] ?: emptyList()).map {
                    AlarmSpec(it.id, it.label, it.hour, it.minute, it.dayOffset, it.enabled, it.sortOrder)
                },
            )
        }
        val assigns = dao.allAssignmentsOnce().map { ShiftAssignment(it.dayKey, it.dutyProfileId) }
        scheduler.reschedule(assigns, byId)
    }

    suspend fun seedPresetsIfEmpty() {
        if (dao.profileCount() > 0) return
        val now = System.currentTimeMillis()
        data class P(val name: String, val color: String,
                     val alarms: List<Triple<String, Pair<Int, Int>, Int>>, val checklist: List<String>)
        val presets = listOf(
            P("Day", "#4F86C6", listOf(Triple("기상", 6 to 0, 0), Triple("인계", 7 to 0, 0)),
                listOf("활력징후 측정", "오전 투약 확인", "인계 준비")),
            P("Evening", "#E8A33D", listOf(Triple("출근 준비", 14 to 0, 0), Triple("인계", 15 to 0, 0)),
                listOf("활력징후 측정", "저녁 투약 확인", "인계 준비")),
            P("Night", "#3B4A6B", listOf(Triple("출근 준비", 21 to 0, 0), Triple("인계", 22 to 0, 0), Triple("아침 인계", 6 to 0, 1)),
                listOf("활력징후 측정", "야간 투약 확인", "낙상 위험 점검", "아침 인계 준비")),
            P("Off", "#9AA0A6", emptyList(), emptyList()),
            P("Charge", "#C0504D", listOf(Triple("인계", 7 to 0, 0), Triple("팀 브리핑", 8 to 0, 0)),
                listOf("인력 배치 확인", "중환자 파악", "입퇴원 조율", "팀 인계")),
        )
        presets.forEachIndexed { i, preset ->
            val pid = UUID.randomUUID().toString()
            dao.upsertProfile(DutyProfileEntity(pid, preset.name, preset.color, isPreset = true, sortOrder = i, createdAt = now))
            preset.alarms.forEachIndexed { j, (label, hm, off) ->
                dao.upsertAlarm(AlarmEntity(UUID.randomUUID().toString(), pid, label, hm.first, hm.second, off, true, j))
            }
            preset.checklist.forEachIndexed { j, text ->
                dao.upsertChecklistItem(ChecklistItemEntity(UUID.randomUUID().toString(), pid, text, false, j))
            }
        }
    }
}
