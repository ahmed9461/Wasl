package com.wasl.app.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wasl.domain.DebtId
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSnoozeSchedulerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager = WorkManager.getInstance(context)
    private val debtId = DebtId("snooze-test-debt")

    @AfterTest
    fun tearDown() {
        workManager.cancelUniqueWork(
            ReminderSnoozeScheduler.workName(debtId),
        ).result.get(10, TimeUnit.SECONDS)
        workManager.cancelAllWorkByTag(
            ReminderSnoozeScheduler.debtTag(debtId),
        ).result.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun repeatedSnoozeLeavesExactlyOneActiveOneShotWorker() {
        val scheduler = ReminderSnoozeScheduler(context)

        scheduler.snooze(debtId, Duration.ofHours(1))
        scheduler.snooze(debtId, Duration.ofHours(2))

        val work = workManager.getWorkInfosForUniqueWork(
            ReminderSnoozeScheduler.workName(debtId),
        ).get(10, TimeUnit.SECONDS)
        val active = work.filter { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED ||
                info.state == WorkInfo.State.RUNNING
        }
        assertEquals(1, active.size)
    }
}
