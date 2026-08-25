package com.wasl.app.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wasl.app.data.ReminderRecord
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(reminder: ReminderRecord) {
        val now = Instant.now(clock)
        val planned = ReminderEscalationPolicy.schedules(reminder, now)
            .associateBy { it.occurrence }

        ReminderOccurrence.entries.forEach { occurrence ->
            val schedule = planned[occurrence]
            if (schedule == null) {
                workManager.cancelUniqueWork(occurrenceWorkName(reminder.id, occurrence))
                return@forEach
            }

            val delayMillis = Duration.between(now, schedule.triggerAt)
                .toMillis()
                .coerceAtLeast(0L)
            val input = Data.Builder()
                .putString(ReminderDeliveryWorker.KEY_REMINDER_ID, reminder.id)
                .putString(ReminderDeliveryWorker.KEY_OCCURRENCE, occurrence.wireValue)
                .build()

            if (occurrence.isPeriodic) {
                val repeatDays = requireNotNull(occurrence.repeatEveryDays)
                val work = PeriodicWorkRequestBuilder<ReminderDeliveryWorker>(
                    repeatDays,
                    TimeUnit.DAYS,
                )
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .addTag(DELIVERY_TAG)
                    .addTag(reminderTag(reminder.id))
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    occurrenceWorkName(reminder.id, occurrence),
                    ExistingPeriodicWorkPolicy.UPDATE,
                    work,
                )
            } else {
                val work = OneTimeWorkRequestBuilder<ReminderDeliveryWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .addTag(DELIVERY_TAG)
                    .addTag(reminderTag(reminder.id))
                    .build()
                workManager.enqueueUniqueWork(
                    occurrenceWorkName(reminder.id, occurrence),
                    ExistingWorkPolicy.REPLACE,
                    work,
                )
            }
        }
    }

    override fun cancel(reminderId: String) {
        workManager.cancelAllWorkByTag(reminderTag(reminderId))
        ReminderOccurrence.entries.forEach { occurrence ->
            workManager.cancelUniqueWork(occurrenceWorkName(reminderId, occurrence))
        }
    }

    override fun requestRecovery() {
        val work = OneTimeWorkRequestBuilder<ReminderRecoveryWorker>()
            .addTag(RECOVERY_TAG)
            .build()
        workManager.enqueueUniqueWork(
            RECOVERY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    companion object {
        const val DELIVERY_TAG = "wasl-reminder-delivery"
        const val RECOVERY_TAG = "wasl-reminder-recovery"
        const val RECOVERY_WORK_NAME = "wasl:reminder-recovery"

        fun deliveryWorkName(reminderId: String): String =
            occurrenceWorkName(reminderId, ReminderOccurrence.DUE_DATE)

        fun occurrenceWorkName(
            reminderId: String,
            occurrence: ReminderOccurrence,
        ): String = if (occurrence == ReminderOccurrence.DUE_DATE) {
            "wasl:reminder:$reminderId"
        } else {
            "wasl:reminder:$reminderId:${occurrence.wireValue.lowercase()}"
        }

        fun reminderTag(reminderId: String): String = "wasl:reminder-plan:$reminderId"
    }
}
