package com.nurseduty.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.nurseduty.domain.AlarmPlanner
import com.nurseduty.domain.DutyProfile
import com.nurseduty.domain.PlannedAlarm
import com.nurseduty.domain.ShiftAssignment
import java.time.LocalDateTime
import java.time.ZoneId

object Notifications {
    const val CHANNEL = "duty_alarms"
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "근무 알람", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}

/**
 * AlarmManager-backed rolling scheduler. Android has no 64-pending cap, but a budgeted rolling
 * window keeps the exact-alarm count small; we re-arm on app open + boot. SharedPreferences holds
 * the set of currently-scheduled ids (Android can't enumerate alarms) so reconcile can cancel stale.
 */
class AlarmScheduler(
    private val context: Context,
    private val prefs: SharedPreferences,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun reschedule(assignments: List<ShiftAssignment>, profilesById: Map<String, DutyProfile>) {
        val desired = AlarmPlanner.plan(assignments, profilesById, LocalDateTime.now(), windowDays = 14, budget = 50)
        val pending = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        val (schedule, cancel) = AlarmPlanner.reconcile(pending, desired)
        cancel.forEach { runCatching { cancelById(it) } }
        // record only ids actually armed, so a throw mid-batch can't leave prefs claiming more
        // than what's scheduled (next reschedule re-tries the un-armed ones, which stay in desired).
        val armed = mutableSetOf<String>()
        schedule.forEach { p -> if (runCatching { scheduleOne(p) }.isSuccess) armed.add(p.id) }
        prefs.edit().putStringSet(KEY, armed).apply()
    }

    private fun scheduleOne(p: PlannedAlarm) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", p.id); putExtra("title", p.title); putExtra("body", p.body)
        }
        // ponytail: requestCode = id.hashCode() — collisions theoretically possible, fine at this scale.
        val pi = PendingIntent.getBroadcast(
            context, p.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = p.fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)        // inexact fallback
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelById(id: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }

    private companion object { const val KEY = "scheduled" }
}
