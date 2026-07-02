package com.nurseduty.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nurseduty.NurseApp
import kotlinx.coroutines.launch

/** Posts the notification when a scheduled alarm fires, then pulls the rolling window forward. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra("rearm", false)) {   // re-arm beacon: re-plan only, no notification
            val title = intent.getStringExtra("title") ?: "근무 알림"
            val body = intent.getStringExtra("body") ?: ""
            Notifications.ensureChannel(context)
            val notification = NotificationCompat.Builder(context, Notifications.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify((intent.getStringExtra("id") ?: title).hashCode(), notification)
        }

        // self-chain: re-arm so alarms beyond the rolling window get scheduled even if the app
        // is never opened for >window days.
        val pending = goAsync()
        val app = context.applicationContext as NurseApp
        app.scope.launch { try { runCatching { app.repository.rescheduleNow() } } finally { pending.finish() } }
    }
}

/** Re-arms the rolling alarm window after a reboot (pending alarms are cleared on boot). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as NurseApp
        app.scope.launch {
            try { runCatching { app.repository.rescheduleNow() } } finally { pending.finish() }
        }
    }
}
