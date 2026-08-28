package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.ReminderStatus
import com.wasl.app.data.UpsertGeneralReminderCommand
import com.wasl.domain.DebtId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class GeneralReminderServiceTest {
    @Test
    fun saveReportsPendingAndRequestsRecoveryWhenPlatformSchedulingFails() = runTest {
        val reminder = record()
        val store = FakeStore(reminder)
        val scheduler = FakeScheduler(failReplace = true)
        val service = GeneralReminderService(
            store = store,
            scheduler = scheduler,
            clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC),
        )

        val result = service.save(command(reminder))

        assertTrue(store.upsertCalled)
        assertTrue(result.platformSyncPending)
        assertTrue(scheduler.recoveryRequested)
    }

    @Test
    fun saveReportsSynchronizedWhenPlatformSchedulingSucceeds() = runTest {
        val reminder = record()
        val store = FakeStore(reminder)
        val scheduler = FakeScheduler(failReplace = false)
        val service = GeneralReminderService(store, scheduler)

        val result = service.save(command(reminder))

        assertTrue(store.upsertCalled)
        assertTrue(scheduler.replaceCalled)
        assertFalse(result.platformSyncPending)
        assertFalse(scheduler.recoveryRequested)
    }

    private fun record() = GeneralReminderRecord(
        id = "general-1",
        debtId = DebtId("debt-1"),
        triggerAt = Instant.parse("2026-08-26T06:00:00Z"),
        zoneId = ZoneId.of("Asia/Aden"),
        status = ReminderStatus.SCHEDULED,
        createdAt = Instant.parse("2026-08-25T10:00:00Z"),
        updatedAt = Instant.parse("2026-08-25T10:00:00Z"),
    )

    private fun command(reminder: GeneralReminderRecord) = UpsertGeneralReminderCommand(
        reminderId = reminder.id,
        debtId = reminder.debtId,
        triggerAt = reminder.triggerAt,
        zoneId = reminder.zoneId,
        repeatRule = reminder.repeatRule,
        updatedAt = reminder.updatedAt,
    )

    private class FakeStore(
        private val record: GeneralReminderRecord,
    ) : GeneralReminderStore {
        var upsertCalled = false

        override fun observeReminderForDebt(debtId: DebtId): Flow<GeneralReminderRecord?> =
            flowOf(record)

        override suspend fun getReminder(reminderId: String): GeneralReminderRecord? = record

        override suspend fun getReminderForDebt(debtId: DebtId): GeneralReminderRecord? = record

        override suspend fun getRecoverableReminders(): List<GeneralReminderRecord> = listOf(record)

        override suspend fun upsertReminder(
            command: UpsertGeneralReminderCommand,
        ): GeneralReminderRecord {
            upsertCalled = true
            return record
        }

        override suspend fun updateReminderSchedule(
            reminderId: String,
            triggerAt: Instant,
            zoneId: ZoneId,
            updatedAt: Instant,
        ): GeneralReminderRecord = record

        override suspend fun markReminderDelivered(
            reminderId: String,
            deliveredAt: Instant,
        ): GeneralReminderRecord = record

        override suspend fun markReminderBlockedByPermission(
            reminderId: String,
            updatedAt: Instant,
        ): GeneralReminderRecord = record

        override suspend fun markReminderFailed(
            reminderId: String,
            failureCode: String,
            updatedAt: Instant,
        ): GeneralReminderRecord = record

        override suspend fun cancelReminder(
            reminderId: String,
            updatedAt: Instant,
        ): GeneralReminderRecord = record
    }

    private class FakeScheduler(
        private val failReplace: Boolean,
    ) : GeneralReminderScheduler {
        var replaceCalled = false
        var recoveryRequested = false

        override fun replace(reminder: GeneralReminderRecord) {
            replaceCalled = true
            if (failReplace) error("scheduler unavailable")
        }

        override fun scheduleNext(reminder: GeneralReminderRecord) = Unit

        override fun cancel(reminderId: String) = Unit

        override fun requestRecovery() {
            recoveryRequested = true
        }
    }
}
