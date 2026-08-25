package com.wasl.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wasl.app.WaslApplication
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.ReminderType
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StrongAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExactAlarmReminderScheduler.ACTION_FIRE_STRONG_ALARM) return
        val reminderId = intent.getStringExtra(ExactAlarmReminderScheduler.EXTRA_REMINDER_ID)
            ?: return
        val application = context.applicationContext as? WaslApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deliver(application, reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(application: WaslApplication, reminderId: String) {
        val reminder = application.reminderStore.getReminder(reminderId) ?: return
        if (reminder.type != ReminderType.STRONG_ALARM ||
            reminder.scheduleType != ReminderScheduleType.EXACT_ALARM ||
            reminder.status == ReminderStatus.CANCELLED ||
            reminder.status == ReminderStatus.DELIVERED
        ) {
            return
        }
        val now = Instant.now()
        val account = application.repository.getAccount(reminder.debtId)
        if (account == null || account.ledger.balance.isZero) {
            application.reminderStore.markReminderCancelled(reminderId, now)
            application.reminderScheduler.cancel(reminderId)
            return
        }
        if (!application.reminderNotificationPublisher.canNotify()) {
            application.reminderStore.markReminderBlockedByPermission(reminderId, now)
            return
        }

        try {
            val published = application.reminderNotificationPublisher.publishStrongAlarm(
                reminder = reminder,
                account = account,
            )
            if (published) {
                application.reminderStore.markReminderDelivered(reminderId, now)
            } else {
                application.reminderStore.markReminderBlockedByPermission(reminderId, now)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            application.reminderStore.markReminderFailed(
                reminderId = reminderId,
                failureCode = "STRONG_ALARM_DELIVERY",
                updatedAt = now,
            )
        }
    }
}
