package com.wasl.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            WorkManagerReminderScheduler(context).requestRecovery()
        }
    }
}
