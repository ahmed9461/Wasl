package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralReminderPolicyTest {
    @Test
    fun dailyReminderPreservesCivilClockAcrossDst() {
        val zone = ZoneId.of("Europe/Berlin")
        val start = LocalDateTime.parse("2026-03-28T09:00:00").atZone(zone).toInstant()
        val next = nextGeneralReminderTrigger(
            currentTriggerAt = start,
            zoneId = zone,
            repeatRule = GeneralReminderRepeatRule(GeneralReminderFrequency.DAILY),
            now = Instant.parse("2026-03-28T12:00:00Z"),
        )

        val local = next.atZone(zone)
        assertEquals(29, local.dayOfMonth)
        assertEquals(9, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun monthlyReminderKeepsOriginalDayAfterShortMonth() {
        val zone = ZoneId.of("Asia/Aden")
        val rule = GeneralReminderRepeatRule(
            frequency = GeneralReminderFrequency.MONTHLY,
            monthlyDayOfMonth = 31,
        )
        val january = LocalDateTime.parse("2026-01-31T09:00:00").atZone(zone).toInstant()
        val february = nextGeneralReminderTrigger(
            currentTriggerAt = january,
            zoneId = zone,
            repeatRule = rule,
            now = january,
        )
        assertEquals(28, february.atZone(zone).dayOfMonth)

        val march = nextGeneralReminderTrigger(
            currentTriggerAt = february,
            zoneId = zone,
            repeatRule = rule,
            now = february,
        )
        assertEquals(31, march.atZone(zone).dayOfMonth)
        assertEquals(9, march.atZone(zone).hour)
    }

    @Test
    fun missedOneTimeReminderIsRecoveredImmediatelyWhenNotificationsReturn() {
        val stored = record(
            triggerAt = Instant.parse("2026-08-25T08:00:00Z"),
            repeatRule = null,
            status = ReminderStatus.BLOCKED_PERMISSION,
        )
        val now = Instant.parse("2026-08-25T10:00:00Z")
        val plan = planGeneralReminderRecovery(stored, now, canNotify = true)

        assertTrue(plan.shouldSchedule)
        assertTrue(plan.shouldPersistScheduledState)
        assertEquals(now, plan.triggerAt)
    }

    @Test
    fun blockedReminderIsNotScheduledWhileNotificationsRemainUnavailable() {
        val stored = record(
            triggerAt = Instant.parse("2026-08-26T08:00:00Z"),
            repeatRule = GeneralReminderRepeatRule(GeneralReminderFrequency.WEEKLY),
            status = ReminderStatus.BLOCKED_PERMISSION,
        )
        val plan = planGeneralReminderRecovery(
            stored = stored,
            now = Instant.parse("2026-08-25T10:00:00Z"),
            canNotify = false,
        )

        assertFalse(plan.shouldSchedule)
        assertFalse(plan.shouldPersistScheduledState)
    }

    private fun record(
        triggerAt: Instant,
        repeatRule: GeneralReminderRepeatRule?,
        status: ReminderStatus,
    ) = GeneralReminderRecord(
        id = "general-1",
        debtId = DebtId("debt-1"),
        triggerAt = triggerAt,
        zoneId = ZoneId.of("Asia/Aden"),
        repeatRule = repeatRule,
        status = status,
        createdAt = Instant.parse("2026-08-24T10:00:00Z"),
        updatedAt = Instant.parse("2026-08-24T10:00:00Z"),
    )
}
