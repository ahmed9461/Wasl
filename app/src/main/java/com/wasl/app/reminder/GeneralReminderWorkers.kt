package com.wasl.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wasl.app.WaslApplication
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class GeneralReminderDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WaslApplication ?: return Result.failure()
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val reminder = application.generalReminderStore.getReminder(reminderId)
            ?: return Result.success()
        if (reminder.status == ReminderStatus.CANCELLED ||
            reminder.status == ReminderStatus.DELIVERED
        ) {
            return Result.success()
        }

        val now = Instant.now()
        if (!application.generalReminderNotificationPublisher.canNotify()) {
            application.generalReminderStore.markReminderBlockedByPermission(reminderId, now)
            return Result.success()
        }
        val account = application.repository.getAccount(reminder.debtId)
        if (account == null) {
            application.generalReminderStore.markReminderFailed(
                reminderId = reminderId,
                failureCode = FAILURE_MISSING_DEBT,
                updatedAt = now,
            )
            return Result.failure()
        }
        if (account.ledger.balance.isZero) {
            application.generalReminderStore.cancelReminder(reminderId, now)
            application.generalReminderScheduler.cancel(reminderId)
            return Result.success()
        }

        return try {
            application.generalReminderNotificationPublisher.publish(reminder, account)
            val repeatRule = reminder.repeatRule
            if (repeatRule == null) {
                application.generalReminderStore.markReminderDelivered(reminderId, now)
            } else {
                val nextTrigger = nextGeneralReminderTrigger(
                    currentTriggerAt = reminder.triggerAt,
                    zoneId = reminder.zoneId,
                    repeatRule = repeatRule,
                    now = now,
                )
                val updated = application.generalReminderStore.updateReminderSchedule(
                    reminderId = reminderId,
                    triggerAt = nextTrigger,
                    zoneId = reminder.zoneId,
                    updatedAt = now,
                )
                application.generalReminderScheduler.scheduleNext(updated)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            application.generalReminderStore.markReminderFailed(
                reminderId = reminderId,
                failureCode = FAILURE_NOTIFICATION_DELIVERY,
                updatedAt = now,
            )
            Result.retry()
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "general_reminder_id"
        private const val FAILURE_MISSING_DEBT = "MISSING_DEBT"
        private const val FAILURE_NOTIFICATION_DELIVERY = "NOTIFICATION_DELIVERY"
    }
}

class GeneralReminderRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WaslApplication ?: return Result.failure()
        val now = Instant.now()
        val currentZone = ZoneId.systemDefault()
        return try {
            application.generalReminderStore.getRecoverableReminders().forEach { stored ->
                val plan = planGeneralReminderRecovery(
                    stored = stored,
                    currentZone = currentZone,
                    now = now,
                    canNotify = application.generalReminderNotificationPublisher.canNotify(),
                )
                if (!plan.shouldSchedule) return@forEach
                val reminder = if (plan.shouldPersistScheduledState) {
                    application.generalReminderStore.updateReminderSchedule(
                        reminderId = stored.id,
                        triggerAt = plan.triggerAt,
                        zoneId = plan.zoneId,
                        updatedAt = now,
                    )
                } else {
                    stored
                }
                application.generalReminderScheduler.replace(reminder)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
