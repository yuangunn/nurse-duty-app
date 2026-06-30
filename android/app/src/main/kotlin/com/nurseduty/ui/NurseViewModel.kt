package com.nurseduty.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nurseduty.NurseApp
import com.nurseduty.data.AlarmEntity
import com.nurseduty.data.ChecklistItemEntity
import com.nurseduty.data.DutyProfileEntity
import com.nurseduty.data.QuickMemoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class NurseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NurseApp).repository

    private fun <T> Flow<List<T>>.ui() =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles = repo.profiles.ui()
    val alarms = repo.alarms.ui()
    val checklistItems = repo.checklistItems.ui()
    val checks = repo.checks.ui()
    val assignments = repo.assignments.ui()
    val memos = repo.memos.ui()

    fun assign(dayKey: Int, profileId: String) = go { repo.assignDuty(dayKey, profileId) }
    fun clearAssign(dayKey: Int) = go { repo.clearAssignment(dayKey) }
    fun toggleCheck(itemId: String, dayKey: Int) = go { repo.toggleCheck(itemId, dayKey) }

    fun saveProfile(id: String?, name: String, color: String) = go { repo.saveProfile(id, name, color) }
    fun archiveProfile(p: DutyProfileEntity) = go { repo.archiveProfile(p) }

    fun saveAlarm(a: AlarmEntity) = go { repo.saveAlarm(a) }
    fun deleteAlarm(a: AlarmEntity) = go { repo.deleteAlarm(a) }
    fun newAlarm(profileId: String, order: Int) =
        AlarmEntity(UUID.randomUUID().toString(), profileId, "", 7, 0, 0, true, order)

    fun saveChecklistItem(i: ChecklistItemEntity) = go { repo.saveChecklistItem(i) }
    fun archiveChecklistItem(i: ChecklistItemEntity) = go { repo.archiveChecklistItem(i) }
    fun newChecklistItem(profileId: String, text: String, order: Int) =
        ChecklistItemEntity(UUID.randomUUID().toString(), profileId, text, false, order)

    fun addMemo(bed: String, text: String) = go { repo.addMemo(bed, text) }
    fun setMemoDone(m: QuickMemoEntity, done: Boolean) = go { repo.setMemoDone(m, done) }
    fun deleteMemo(m: QuickMemoEntity) = go { repo.deleteMemo(m) }

    fun export(uri: Uri, resolver: ContentResolver, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val json = repo.exportBackup()
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }.isSuccess
        onDone(ok)
    }

    fun import(uri: Uri, resolver: ContentResolver, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val text = runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        onDone(text != null && repo.importBackup(text))
    }

    private fun go(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
