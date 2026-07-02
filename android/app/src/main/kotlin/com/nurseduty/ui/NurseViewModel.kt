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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.util.UUID

data class WeatherUi(val isLive: Boolean, val loc: String, val temp: String, val icon: String)

class NurseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NurseApp).repository

    private val _weather = MutableStateFlow(WeatherUi(false, "서울", "—", "sun"))
    val weather: StateFlow<WeatherUi> = _weather.asStateFlow()

    private var weatherFetchedAt = 0L

    init { refreshWeather() }

    /** Re-fetches at most every 30 min; failures don't advance the clock so the next resume retries. */
    fun refreshWeather() {
        if (System.currentTimeMillis() - weatherFetchedAt < 30 * 60_000) return
        // Seoul default; geolocation ("현위치") is a later add. ponytail: no permission dance for v1.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://api.open-meteo.com/v1/forecast?latitude=37.5665&longitude=126.9780&current=temperature_2m,weather_code")
                    .openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                val body = conn.getInputStream().bufferedReader().use { it.readText() }
                val cur = JSONObject(body).getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                _weather.value = WeatherUi(true, "서울", "$temp°", weatherKind(cur.getInt("weather_code")))
                weatherFetchedAt = System.currentTimeMillis()
            }
        }
    }

    private fun weatherKind(code: Int) = when (code) {
        0 -> "sun"; 1, 2 -> "cloudsun"; 3 -> "cloud"; 45, 48 -> "fog"
        in 51..67, in 80..82 -> "rain"; in 71..77 -> "snow"; in 95..99 -> "storm"; else -> "sun"
    }

    private fun <T> Flow<List<T>>.ui() =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles = repo.profiles.ui()
    val alarms = repo.alarms.ui()
    val checklistItems = repo.checklistItems.ui()
    val checks = repo.checks.ui()
    val assignments = repo.assignments.ui()
    val memos = repo.memos.ui()

    fun assign(dayKey: Int, profileId: String, charge: Boolean = false) = go { repo.assignDuty(dayKey, profileId, charge) }
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
            // "wt" truncates: some SAF providers keep the old tail on plain "w", corrupting the file.
            // A null stream must report failure, not silently "succeed" having written nothing.
            val out = resolver.openOutputStream(uri, "wt") ?: error("no output stream")
            out.use { it.write(json.toByteArray()) }
        }.isSuccess
        onDone(ok)
    }

    fun import(uri: Uri, resolver: ContentResolver, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val text = runCatching { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        onDone(text != null && repo.importBackup(text))
    }

    private fun go(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
