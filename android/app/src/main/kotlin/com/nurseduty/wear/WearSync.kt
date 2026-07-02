package com.nurseduty.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nurseduty.NurseApp
import com.nurseduty.domain.WearCommand
import com.nurseduty.domain.WearState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Phone side of the watch sync: push today-state to the watch (Data Layer, latest-wins). */
class WearSync(private val context: Context) {
    suspend fun push(state: WearState) {
        runCatching {
            val req = PutDataMapRequest.create("/today").apply {
                dataMap.putString("json", WearState.encode(state))
                dataMap.putLong("ts", System.currentTimeMillis())   // force a fresh DataItem each time
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req).await()
        }
    }
}

/** Receives commands the watch sends and applies them to the store. */
class PhoneWearService : WearableListenerService() {
    // legacy path (MessageClient) — old watch builds; drops silently when disconnected
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/command") return
        val cmd = runCatching { WearCommand.decode(String(event.data)) }.getOrNull() ?: return
        val app = applicationContext as NurseApp
        // Synchronous on purpose: the service (and its wakeful process) may die right after this
        // returns, so a fire-and-forget launch could drop the watch's command. Already off-main.
        runBlocking { runCatching { app.repository.applyWearCommand(cmd) } }
    }

    // command queue (DataItems under /cmd/*) — the Data Layer stores them while disconnected and
    // delivers on reconnect; we apply, then delete the item as the ack. Commands are idempotent.
    override fun onDataChanged(events: DataEventBuffer) {
        val app = applicationContext as NurseApp
        val client = Wearable.getDataClient(this)
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith("/cmd/") == true }
            .forEach { e ->
                val json = DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json") ?: return@forEach
                val cmd = runCatching { WearCommand.decode(json) }.getOrNull() ?: return@forEach
                runBlocking {
                    runCatching { app.repository.applyWearCommand(cmd) }
                    runCatching { client.deleteDataItems(e.dataItem.uri).await() }
                }
            }
    }
}
