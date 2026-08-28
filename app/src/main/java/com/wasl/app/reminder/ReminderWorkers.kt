package com.wasl.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wasl.app.WaslApplication
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class ReminderDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WaslApplication ?: return Result.failure()
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val occurrence = ReminderOccurrence.fromWireValue(inputData.getString(KEY_OCCURRENCE))
        val reminder = application.reminderStore.getReminder(reminderId) ?: return Result.success()
        if (reminder.status == ReminderStatus.DELIVERED ||
            reminder.status == ReminderStatus.CANCELLED
        ) {
            return Result.success()
        }

        val now = Instant.now()
        if (!application.reminderNotificationPublisher.canNotify()) {
            application.reminderStore.markReminderBlockedByPermission(reminderId, now)
            return Result.success()
        }
        val account = application.repository.getAccount(reminder.debtId)
        if (account == null) {
            application.reminderStore.markReminderFailed(
                reminderId,
                FAILURE_MISSING_DEBT,
                now,
            )
            return Result.failure()
        }
        if (account.ledger.balance.isZero) {
            application.reminderStore.markReminderCancelled(reminderId, now)
            application.reminderScheduler.cancel(reminderId)
            return Result.success()
        }

        return try {
            application.reminderNotificationPublisher.publish(reminder, account, occurrence)
            application.reminderStore.updateReminderSchedule(
                reminderId = reminderId,
                triggerAt = reminder.triggerAt,
                zoneId = reminder.zoneId,
                updatedAt = now,
            )
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            application.reminderStore.markReminderFailed(
                reminderId,
                FAILURE_NOTIFICATION_DELIVERY,
                now,
            )
            Result.retry()
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_OCCURRENCE = "reminder_occurrence"
        private const val FAILURE_MISSING_DEBT = "MISSING_DEBT"
        private const val FAILURE_NOTIFICATION_DELIVERY = "NOTIFICATION_DELIVERY"
    }
}

class ReminderRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WaslApplication ?: return Result.failure()
        val now = Instant.now()
        val currentZone = ZoneId.systemDefault()
        return try {
            application.reminderStore.getRecoverableReminders().forEach { stored ->
                val plan = planReminderRecovery(
                    stored = stored,
                    currentZone = currentZone,
                    now = now,
                    canNotify = application.reminderNotificationPublisher.canNotify(),
                    canScheduleExactAlarms = ExactAlarmAccess.canSchedule(applicationContext),
                )
                if (!plan.shouldSchedule) return@forEach
                if (plan.shouldPersistScheduledState) {
                    application.reminderStore.updateReminderSchedule(
                        reminderId = stored.id,
                        triggerAt = plan.triggerAt,
                        zoneId = plan.zoneId,
                        updatedAt = now,
                    )
                }
                val reminder = stored.copy(
                    triggerAt = plan.triggerAt,
                    zoneId = plan.zoneId,
                    status = ReminderStatus.SCHEDULED,
                    lastFailureCode = null,
                    updatedAt = if (plan.shouldPersistScheduledState) now else stored.updatedAt,
                )
                application.reminderScheduler.schedule(reminder)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
