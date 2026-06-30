package com.nurseduty.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fleshed out in the scheduler step — posts the notification when an alarm fires. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

/** Re-arms the rolling alarm window after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
