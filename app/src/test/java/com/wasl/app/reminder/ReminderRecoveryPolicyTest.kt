package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderType
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderRecoveryPolicyTest {
    private val now = Instant.parse("2026-08-14T08:00:00Z")

    @Test
    fun blockedReminderStaysBlockedUntilNotificationsAreAvailable() {
        val plan = planReminderRecovery(
            stored = reminder(ReminderStatus.BLOCKED_PERMISSION),
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = false,
        )

        assertFalse(plan.shouldSchedule)
        assertFalse(plan.shouldPersistScheduledState)
    }

    @Test
    fun blockedOrFailedReminderReturnsToScheduledBeforeRetry() {
        listOf(ReminderStatus.BLOCKED_PERMISSION, ReminderStatus.FAILED).forEach { status ->
            val plan = planReminderRecovery(
                stored = reminder(status),
                currentZone = ZoneOffset.UTC,
                now = now,
                canNotify = true,
            )

            assertTrue(plan.shouldSchedule)
            assertTrue(plan.shouldPersistScheduledState)
            assertEquals(ZoneOffset.UTC, plan.zoneId)
        }
    }

    @Test
    fun alreadyScheduledReminderDoesNotNeedAStatusWrite() {
        val plan = planReminderRecovery(
            stored = reminder(ReminderStatus.SCHEDULED, lastFailureCode = null),
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
        )

        assertTrue(plan.shouldSchedule)
        assertFalse(plan.shouldPersistScheduledState)
    }

    @Test
    fun timezoneChangeRebasesCivilTimeAndPersistsTheNewZone() {
        val stored = reminder(ReminderStatus.SCHEDULED).copy(
            triggerAt = Instant.parse("2026-08-15T06:00:00Z"),
            zoneId = ZoneId.of("Asia/Riyadh"),
        )
        val plan = planReminderRecovery(
            stored = stored,
            currentZone = ZoneId.of("Europe/London"),
            now = now,
            canNotify = true,
        )

        assertEquals(Instant.parse("2026-08-15T08:00:00Z"), plan.triggerAt)
        assertEquals(ZoneId.of("Europe/London"), plan.zoneId)
        assertTrue(plan.shouldPersistScheduledState)
    }

    @Test
    fun strongAlarmNeedsExactAccessAndMustStillBeInTheFuture() {
        val futureAlarm = reminder(ReminderStatus.SCHEDULED).copy(
            type = ReminderType.STRONG_ALARM,
            scheduleType = ReminderScheduleType.EXACT_ALARM,
        )
        val blocked = planReminderRecovery(
            stored = futureAlarm,
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = false,
        )
        assertFalse(blocked.shouldSchedule)

        val allowed = planReminderRecovery(
            stored = futureAlarm,
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = true,
        )
        assertTrue(allowed.shouldSchedule)

        val past = planReminderRecovery(
            stored = futureAlarm.copy(triggerAt = now.minusSeconds(1)),
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = true,
        )
        assertFalse(past.shouldSchedule)
    }

    private fun reminder(
        status: ReminderStatus,
        lastFailureCode: String? = "TEST_FAILURE",
    ) = ReminderRecord(
        id = "reminder-1",
        debtId = DebtId("debt-1"),
        triggerAt = Instant.parse("2026-08-15T09:00:00Z"),
        zoneId = ZoneOffset.UTC,
        status = status,
        lastFailureCode = lastFailureCode,
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(30),
    )
}
