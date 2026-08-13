package com.wasl.app.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
        val delayMillis = Duration.between(Instant.now(clock), reminder.triggerAt)
            .toMillis()
            .coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<ReminderDeliveryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderDeliveryWorker.KEY_REMINDER_ID, reminder.id)
                    .build(),
            )
            .addTag(DELIVERY_TAG)
            .build()
        workManager.enqueueUniqueWork(
            deliveryWorkName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            work,
        )
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

        fun deliveryWorkName(reminderId: String): String = "wasl:reminder:$reminderId"
    }
}
