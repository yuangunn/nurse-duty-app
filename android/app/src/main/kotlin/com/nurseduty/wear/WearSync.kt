package com.nurseduty.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nurseduty.NurseApp
import com.nurseduty.domain.WearCommand
import com.nurseduty.domain.WearState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
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

/** Receives commands the watch sends (toggle check / add memo) and applies them to the store. */
class PhoneWearService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/command") return
        val cmd = runCatching { WearCommand.decode(String(event.data)) }.getOrNull() ?: return
        val app = applicationContext as NurseApp
        // Synchronous on purpose: the service (and its wakeful process) may die right after this
        // returns, so a fire-and-forget launch could drop the watch's command. Already off-main.
        runBlocking { runCatching { app.repository.applyWearCommand(cmd) } }
    }
}
