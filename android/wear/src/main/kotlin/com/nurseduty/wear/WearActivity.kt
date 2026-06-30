package com.nurseduty.wear

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nurseduty.domain.WearCommand
import com.nurseduty.domain.WearState
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

private const val MEMO_KEY = "memo"

@Composable
fun WearApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(WearState()) }

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

    fun send(cmd: WearCommand) = scope.launch {
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val payload = WearCommand.encode(cmd).toByteArray()
            nodes.forEach { Wearable.getMessageClient(context).sendMessage(it.id, "/command", payload).await() }
        }
    }

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
                ListHeader { Text(state.dutyName ?: "오늘 근무 없음") }
            }
            state.nextAlarm?.let { na -> item { Text("⏰ $na") } }
            items(state.checklist, key = { it.id }) { c ->
                ToggleChip(
                    checked = c.checked,
                    onCheckedChange = {
                        state = state.copy(checklist = state.checklist.map {
                            if (it.id == c.id) it.copy(checked = !it.checked) else it
                        })
                        send(WearCommand.ToggleCheck(c.id, state.dayKey))
                    },
                    label = { Text(c.text) },
                    toggleControl = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { captureMemo() },
                    label = { Text("빠른 메모") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
