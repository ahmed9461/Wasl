package com.wasl.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            -> {
                WorkManagerReminderScheduler(context).requestRecovery()
                WorkManagerGeneralReminderScheduler(context).requestRecovery()
            }

            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                WorkManagerReminderScheduler(context).requestRecovery()
        }
    }
}
