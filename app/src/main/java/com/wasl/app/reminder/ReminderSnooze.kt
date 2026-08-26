package com.wasl.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wasl.app.WaslApplication
import com.wasl.domain.DebtId
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class ReminderNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderNotificationActions.ACTION_SNOOZE) return
        val debtId = intent.getStringExtra(ReminderNotificationActions.EXTRA_SNOOZE_DEBT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return

        ReminderSnoozeScheduler(context).snooze(DebtId(debtId))

        val notificationTag = intent.getStringExtra(
            ReminderNotificationActions.EXTRA_NOTIFICATION_TAG,
        )
        val notificationId = intent.getIntExtra(
            ReminderNotificationActions.EXTRA_NOTIFICATION_ID,
            Int.MIN_VALUE,
        )
        if (notificationTag != null && notificationId != Int.MIN_VALUE) {
            NotificationManagerCompat.from(context).cancel(notificationTag, notificationId)
        }
    }
}

class ReminderSnoozeScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun snooze(
        debtId: DebtId,
        delay: Duration = DEFAULT_DELAY,
    ) {
        require(!delay.isNegative && !delay.isZero) { "Snooze delay must be positive." }
        val delayMillis = delay.toMillis()
        val input = Data.Builder()
            .putString(ReminderSnoozeWorker.KEY_DEBT_ID, debtId.value)
            .build()
        val work = OneTimeWorkRequestBuilder<ReminderSnoozeWorker>()
            .setInputData(input)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .addTag(debtTag(debtId))
            .build()
        workManager.enqueueUniqueWork(
            workName(debtId),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    companion object {
        val DEFAULT_DELAY: Duration = Duration.ofHours(1)
        const val WORK_TAG = "wasl-reminder-snooze"

        fun workName(debtId: DebtId): String = "wasl:reminder-snooze:${debtId.value}"
        fun debtTag(debtId: DebtId): String = "wasl:reminder-snooze-debt:${debtId.value}"
    }
}

class ReminderSnoozeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? WaslApplication ?: return Result.failure()
        val debtId = inputData.getString(KEY_DEBT_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let(::DebtId)
            ?: return Result.failure()
        val account = application.repository.getAccount(debtId) ?: return Result.success()
        if (account.ledger.balance.isZero) return Result.success()
        if (!application.reminderNotificationPublisher.canNotify()) return Result.success()

        return try {
            application.reminderNotificationPublisher.publishSnoozedAccount(account)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_DEBT_ID = "snoozed_debt_id"
    }
}
