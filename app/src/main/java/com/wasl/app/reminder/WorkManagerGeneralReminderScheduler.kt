package com.wasl.app.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wasl.app.data.GeneralReminderRecord
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class WorkManagerGeneralReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : GeneralReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun replace(reminder: GeneralReminderRecord) {
        cancel(reminder.id)
        enqueue(reminder)
    }

    override fun scheduleNext(reminder: GeneralReminderRecord) {
        enqueue(reminder)
    }

    override fun cancel(reminderId: String) {
        workManager.cancelAllWorkByTag(reminderTag(reminderId))
    }

    override fun requestRecovery() {
        val work = OneTimeWorkRequestBuilder<GeneralReminderRecoveryWorker>()
            .addTag(RECOVERY_TAG)
            .build()
        workManager.enqueueUniqueWork(
            RECOVERY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    private fun enqueue(reminder: GeneralReminderRecord) {
        val now = Instant.now(clock)
        val delayMillis = Duration.between(now, reminder.triggerAt)
            .toMillis()
            .coerceAtLeast(0L)
        val input = Data.Builder()
            .putString(GeneralReminderDeliveryWorker.KEY_REMINDER_ID, reminder.id)
            .build()
        val work = OneTimeWorkRequestBuilder<GeneralReminderDeliveryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(DELIVERY_TAG)
            .addTag(reminderTag(reminder.id))
            .build()
        workManager.enqueueUniqueWork(
            deliveryWorkName(reminder),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    companion object {
        const val DELIVERY_TAG = "wasl-general-reminder-delivery"
        const val RECOVERY_TAG = "wasl-general-reminder-recovery"
        const val RECOVERY_WORK_NAME = "wasl:general-reminder-recovery"

        fun reminderTag(reminderId: String): String =
            "wasl:general-reminder-plan:$reminderId"

        fun deliveryWorkName(reminder: GeneralReminderRecord): String =
            "wasl:general-reminder:${reminder.id}:${reminder.triggerAt.toEpochMilli()}"
    }
}
