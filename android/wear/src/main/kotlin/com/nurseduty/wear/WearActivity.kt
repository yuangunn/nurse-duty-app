package com.nurseduty.wear

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.input.RemoteInputIntentHelper
import com.nurseduty.wear.BuildConfig
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.PutDataMapRequest
import com.nurseduty.domain.DayKey
import com.nurseduty.domain.WearCommand
import com.nurseduty.domain.WearState
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: DEBUG preview mirrors iOS --watch-preview so the styled state
        // is verifiable without pairing a phone. Launch: am start ... -e preview evening
        val preview = if (BuildConfig.DEBUG) intent?.getStringExtra("preview")?.let(::previewState) else null
        setContent { WearApp(preview) }
    }
}

private const val MEMO_KEY = "memo"

private fun previewState(kind: String): WearState = WearState(
    dayKey = DayKey.from(LocalDate.now()), dutyName = kind.replaceFirstChar { it.uppercase() }, kind = kind.replaceFirstChar { it.uppercase() },
    colorHex = null, timeText = "14:00 – 22:00", charge = true, nextAlarm = "13:30", pendingMemos = 1,
    checklist = listOf(
        WearState.WearItem("charge:handover", "팀 배정·인수인계 확인", false),
        WearState.WearItem("a", "활력징후 측정", true),
        WearState.WearItem("b", "저녁 투약 확인", false),
        WearState.WearItem("c", "인계 준비", false),
    ),
)

@Composable
fun WearApp(preview: WearState? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(preview ?: WearState()) }

    // receive today-state pushed from the phone (latest-wins), plus an initial read
    DisposableEffect(Unit) {
        val dataClient = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { e ->
                if (e.dataItem.uri.path == "/today") {
                    DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let {
                        state = runCatching { WearState.decode(it) }.getOrDefault(state)
                    }
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        scope.launch {
            runCatching {
                val items = dataClient.dataItems.await()
                items.firstOrNull { it.uri.path == "/today" }?.let {
                    DataMapItem.fromDataItem(it).dataMap.getString("json")?.let { j ->
                        state = WearState.decode(j)
                    }
                }
                items.release()
            }
        }
        onDispose { dataClient.removeListener(listener) }
    }

    // DataItem queue: stored locally while disconnected, delivered on reconnect (phone deletes as ack)
    fun send(cmd: WearCommand) = scope.launch {
        runCatching {
            val req = PutDataMapRequest.create("/cmd/${UUID.randomUUID()}").apply {
                dataMap.putString("json", WearCommand.encode(cmd))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req).await()
        }
    }

    // ask for a fresh /today on open — heals a stale snapshot after midnight or long disconnect
    LaunchedEffect(Unit) { send(WearCommand.Sync) }

    val memoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(MEMO_KEY)?.toString()
        if (!text.isNullOrBlank()) send(WearCommand.AddMemo(UUID.randomUUID().toString(), "", text))
    }
    fun captureMemo() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val inputs = listOf(
            RemoteInput.Builder(MEMO_KEY).setLabel("메모").setAllowFreeFormInput(true).build(),
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, inputs)
        memoLauncher.launch(intent)
    }

    Scaffold {
        ScalingLazyColumn(Modifier.fillMaxWidth()) {
            item {
                val done = state.checklist.count { it.checked }
                val total = state.checklist.size
                Column(Modifier.fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(18.dp))
                    .background(WearStyle.gradient(state.kind)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        if (state.dutyName == null) "오늘 근무 없음" else "${state.dutyName} · ${WearStyle.ko(state.kind)}",
                        style = MaterialTheme.typography.title3, color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    if (state.charge) Text("👑 차지 · 팀 리더", style = MaterialTheme.typography.caption2, color = Color(0xFFFFE49B))
                    if (state.kind != "None") state.timeText?.let {
                        Text(it, style = MaterialTheme.typography.caption2, color = Color.White.copy(0.9f))
                    }
                    Row(Modifier.padding(top = 3.dp)) {
                        if (total > 0) Text("$done/$total 완료", style = MaterialTheme.typography.caption2, color = Color.White)
                        state.nextAlarm?.let { Spacer(Modifier.width(8.dp)); Text("⏰ $it", style = MaterialTheme.typography.caption3, color = Color.White.copy(0.9f)) }
                    }
                }
            }
            items(state.checklist, key = { it.id }) { c ->
                ToggleChip(
                    checked = c.checked,
                    onCheckedChange = {
                        val todayKey = DayKey.from(LocalDate.now())
                        if (state.dayKey != 0 && state.dayKey != todayKey) {
                            send(WearCommand.Sync)   // stale snapshot — refresh instead of writing to yesterday
                        } else {
                            val newChecked = !c.checked
                            state = state.copy(checklist = state.checklist.map {
                                if (it.id == c.id) it.copy(checked = newChecked) else it
                            })
                            send(WearCommand.SetCheck(c.id, todayKey, newChecked))
                        }
                    },
                    label = { Text(c.text, color = if (c.checked) WearStyle.Success else Color.White) },
                    toggleControl = { Text(if (c.checked) "✓" else "○", color = if (c.checked) WearStyle.Success else Color(0xFF9AA0AB)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { captureMemo() },
                    label = { Text("＋ 빠른 메모") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
