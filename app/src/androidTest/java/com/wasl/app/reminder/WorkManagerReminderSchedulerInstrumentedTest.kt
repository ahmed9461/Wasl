package com.wasl.app.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerReminderSchedulerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager = WorkManager.getInstance(context)
    private val reminderId = "scheduler-test-reminder"

    @AfterTest
    fun tearDown() {
        workManager.cancelUniqueWork(
            WorkManagerReminderScheduler.deliveryWorkName(reminderId),
        ).result.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun repeatedSchedulingLeavesExactlyOneActiveDelivery() {
        val now = Instant.parse("2026-08-13T00:00:00Z")
        val scheduler = WorkManagerReminderScheduler(
            context = context,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val reminder = ReminderRecord(
            id = reminderId,
            debtId = DebtId("debt-test"),
            triggerAt = now.plusSeconds(3_600),
            zoneId = ZoneOffset.UTC,
            status = ReminderStatus.SCHEDULED,
            createdAt = now,
            updatedAt = now,
        )

        scheduler.schedule(reminder)
        scheduler.schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(
            WorkManagerReminderScheduler.deliveryWorkName(reminderId),
        ).get(10, TimeUnit.SECONDS)
        val active = work.filter { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED ||
                info.state == WorkInfo.State.RUNNING
        }
        assertEquals(1, active.size)
    }
}
