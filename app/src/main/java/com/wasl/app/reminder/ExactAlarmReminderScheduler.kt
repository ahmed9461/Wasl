package com.wasl.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import java.time.Clock
import java.time.Instant

class ExactAlarmPermissionRequiredException : IllegalStateException(
    "Exact alarm access is required for a strong alarm.",
)

internal class ExactAlarmReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: ReminderRecord) {
        require(reminder.scheduleType == ReminderScheduleType.EXACT_ALARM) {
            "Exact scheduler only accepts EXACT_ALARM reminders."
        }
        if (!reminder.triggerAt.isAfter(Instant.now(clock))) return
        if (!ExactAlarmAccess.canSchedule(appContext)) {
            throw ExactAlarmPermissionRequiredException()
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt.toEpochMilli(),
            pendingIntent(reminder.id),
        )
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(pendingIntent(reminderId))
    }

    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(appContext, StrongAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_STRONG_ALARM
            data = Uri.Builder()
                .scheme("wasl")
                .authority("strong-alarm")
                .appendPath(reminderId)
                .build()
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE_STRONG_ALARM = "com.wasl.app.action.FIRE_STRONG_ALARM"
        const val EXTRA_REMINDER_ID = "com.wasl.app.extra.STRONG_ALARM_REMINDER_ID"
    }
}

class HybridReminderScheduler(
    context: Context,
    clock: Clock = Clock.systemUTC(),
) : ReminderScheduler {
    private val workScheduler = WorkManagerReminderScheduler(context, clock)
    private val exactScheduler = ExactAlarmReminderScheduler(context, clock)

    override fun schedule(reminder: ReminderRecord) {
        when (reminder.scheduleType) {
            ReminderScheduleType.WORK -> workScheduler.schedule(reminder)
            ReminderScheduleType.EXACT_ALARM -> exactScheduler.schedule(reminder)
        }
    }

    override fun cancel(reminderId: String) {
        workScheduler.cancel(reminderId)
        exactScheduler.cancel(reminderId)
    }

    override fun requestRecovery() = workScheduler.requestRecovery()
}
