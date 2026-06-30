package com.nurseduty.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.nurseduty.alarm.AlarmScheduler
import com.nurseduty.domain.AlarmPlanner
import com.nurseduty.domain.AlarmSpec
import com.nurseduty.domain.DayKey
import com.nurseduty.domain.DutyProfile
import com.nurseduty.domain.ShiftAssignment
import com.nurseduty.domain.WearCommand
import com.nurseduty.domain.WearState
import com.nurseduty.wear.WearSync
import com.nurseduty.widget.NurseWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class TodayWidget(
    val dutyName: String?, val colorHex: String?,
    val done: Int, val total: Int, val pendingMemos: Int, val nextAlarm: String?,
)

class Repository(
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val appContext: Context,
) {
    private val dao = db.dao()
    private val rescheduleMutex = Mutex()
    private val wearSync = WearSync(appContext)

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
        refreshWidget()
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

    suspend fun saveChecklistItem(i: ChecklistItemEntity) { dao.upsertChecklistItem(i); refreshWidget() }
    suspend fun archiveChecklistItem(i: ChecklistItemEntity) { dao.upsertChecklistItem(i.copy(isArchived = true)); refreshWidget() }
    suspend fun newChecklistId() = UUID.randomUUID().toString()

    // ---- memos ----
    suspend fun addMemo(bedTag: String, text: String) {
        dao.upsertMemo(QuickMemoEntity(UUID.randomUUID().toString(), bedTag, text, false, System.currentTimeMillis()))
        refreshWidget()
    }
    suspend fun setMemoDone(m: QuickMemoEntity, done: Boolean) { dao.upsertMemo(m.copy(isDone = done)); refreshWidget() }
    suspend fun deleteMemo(m: QuickMemoEntity) { dao.deleteMemo(m); refreshWidget() }

    // ---- scheduling + widget ----
    private suspend fun domainData(): Pair<List<ShiftAssignment>, Map<String, DutyProfile>> {
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
        return assigns to byId
    }

    // serialized so two rapid mutations can't interleave snapshots and re-arm a just-removed alarm
    suspend fun rescheduleNow() = rescheduleMutex.withLock {
        val (assigns, byId) = domainData()
        scheduler.reschedule(assigns, byId)
        refreshWidget()
    }

    suspend fun refreshWidget() {
        runCatching { NurseWidget().updateAll(appContext) }
        runCatching { wearSync.push(wearState()) }   // also mirror to the watch
    }

    suspend fun wearState(today: LocalDate = LocalDate.now()): WearState {
        val todayKey = DayKey.from(today)
        val profile = dao.assignment(todayKey)?.let { a -> dao.allProfilesOnce().firstOrNull { it.id == a.dutyProfileId } }
        val items = if (profile != null) {
            dao.allChecklistOnce().filter { it.dutyProfileId == profile.id && !it.isArchived }.sortedBy { it.sortOrder }
        } else emptyList()
        val checked = dao.allChecksOnce().filter { it.dayKey == todayKey }.map { it.checklistItemId }.toSet()
        val (assigns, byId) = domainData()
        val next = AlarmPlanner.plan(assigns, byId, LocalDateTime.now(), windowDays = 2, budget = 5).firstOrNull()
        return WearState(
            dayKey = todayKey, dutyName = profile?.name, colorHex = profile?.colorHex,
            nextAlarm = next?.let { "${it.title} %02d:%02d".format(it.fireAt.hour, it.fireAt.minute) },
            pendingMemos = dao.allMemosOnce().count { !it.isDone },
            checklist = items.map { WearState.WearItem(it.id, it.text, checked.contains(it.id)) },
        )
    }

    suspend fun applyWearCommand(cmd: WearCommand) {
        when (cmd) {
            is WearCommand.ToggleCheck -> toggleCheck(cmd.itemId, cmd.dayKey)
            is WearCommand.AddMemo -> {
                // idempotent: REPLACE on the watch-generated id so a re-delivered command can't duplicate
                dao.upsertMemo(QuickMemoEntity(cmd.id, cmd.bedTag, cmd.text, false, System.currentTimeMillis()))
                refreshWidget()
            }
        }
    }

    suspend fun todaySnapshot(today: LocalDate = LocalDate.now()): TodayWidget {
        val todayKey = DayKey.from(today)
        val profile = dao.assignment(todayKey)?.let { a -> dao.allProfilesOnce().firstOrNull { it.id == a.dutyProfileId } }
        val items = if (profile != null) dao.allChecklistOnce().filter { it.dutyProfileId == profile.id && !it.isArchived } else emptyList()
        val checkedToday = dao.allChecksOnce().filter { it.dayKey == todayKey }.map { it.checklistItemId }.toSet()
        val (assigns, byId) = domainData()
        val next = AlarmPlanner.plan(assigns, byId, LocalDateTime.now(), windowDays = 2, budget = 5).firstOrNull()
        return TodayWidget(
            dutyName = profile?.name, colorHex = profile?.colorHex,
            done = items.count { checkedToday.contains(it.id) }, total = items.size,
            pendingMemos = dao.allMemosOnce().count { !it.isDone },
            nextAlarm = next?.let { "${it.title} %02d:%02d".format(it.fireAt.hour, it.fireAt.minute) },
        )
    }

    // ---- backup ----
    suspend fun exportBackup(): String = Backup.encode(
        Backup(
            version = 1,
            profiles = dao.allProfilesOnce(),
            alarms = dao.allAlarmsOnce(),
            checklist = dao.allChecklistOnce(),
            checks = dao.allChecksOnce(),
            assignments = dao.allAssignmentsOnce(),
            memos = dao.allMemosOnce(),
        ),
    )

    suspend fun importBackup(jsonStr: String): Boolean {
        val b = runCatching { Backup.decode(jsonStr) }.getOrNull() ?: return false
        // Atomic: a process kill / I/O error / constraint-violating row rolls back instead of
        // leaving the store wiped with a partial restore (no data loss).
        val ok = runCatching {
            db.withTransaction {
                dao.clearChecks(); dao.clearAssignments(); dao.clearMemos()
                dao.clearAlarms(); dao.clearChecklist(); dao.clearProfiles()
                b.profiles.forEach { dao.upsertProfile(it) }
                b.alarms.forEach { dao.upsertAlarm(it) }
                b.checklist.forEach { dao.upsertChecklistItem(it) }
                b.checks.forEach { dao.insertCheck(it) }
                b.assignments.forEach { dao.upsertAssignment(it) }
                b.memos.forEach { dao.upsertMemo(it) }
            }
        }.isSuccess
        if (ok) rescheduleNow()
        return ok
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
