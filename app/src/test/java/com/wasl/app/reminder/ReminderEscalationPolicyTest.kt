package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderEscalationPolicyTest {
    private val zone = ZoneId.of("Asia/Aden")

    @Test
    fun schedulesDayBeforeDueDateAndOverdueFollowUps() {
        val dueTrigger = Instant.parse("2026-08-30T06:00:00Z")
        val reminder = reminder(dueTrigger)
        val now = Instant.parse("2026-08-28T00:00:00Z")

        val schedules = ReminderEscalationPolicy.schedules(reminder, now)
            .associateBy { it.occurrence }

        assertEquals(
            Instant.parse("2026-08-29T06:00:00Z"),
            schedules.getValue(ReminderOccurrence.UPCOMING_DAY_BEFORE).triggerAt,
        )
        assertEquals(
            Instant.parse("2026-08-30T06:00:00Z"),
            schedules.getValue(ReminderOccurrence.DUE_DATE).triggerAt,
        )
        assertEquals(
            Instant.parse("2026-09-01T06:00:00Z"),
            schedules.getValue(ReminderOccurrence.OVERDUE_TWO_DAYS).triggerAt,
        )
        assertEquals(
            Instant.parse("2026-09-06T06:00:00Z"),
            schedules.getValue(ReminderOccurrence.OVERDUE_WEEKLY).triggerAt,
        )
    }

    @Test
    fun pastOneTimeOccurrencesAreNotRescheduledButWeeklyMovesForward() {
        val reminder = reminder(Instant.parse("2026-08-01T06:00:00Z"))
        val now = Instant.parse("2026-08-20T12:00:00Z")

        assertNull(
            ReminderEscalationPolicy.nextTrigger(
                reminder,
                ReminderOccurrence.UPCOMING_DAY_BEFORE,
                now,
            ),
        )
        assertNull(
            ReminderEscalationPolicy.nextTrigger(
                reminder,
                ReminderOccurrence.DUE_DATE,
                now,
            ),
        )
        assertNull(
            ReminderEscalationPolicy.nextTrigger(
                reminder,
                ReminderOccurrence.OVERDUE_TWO_DAYS,
                now,
            ),
        )
        assertEquals(
            Instant.parse("2026-08-22T06:00:00Z"),
            ReminderEscalationPolicy.nextTrigger(
                reminder,
                ReminderOccurrence.OVERDUE_WEEKLY,
                now,
            ),
        )
    }

    @Test
    fun civilTimeIsPreservedAcrossDaylightSavingChanges() {
        val berlin = ZoneId.of("Europe/Berlin")
        val dueLocal = java.time.LocalDate.of(2026, 10, 26)
            .atTime(9, 0)
            .atZone(berlin)
            .toInstant()
        val reminder = ReminderRecord(
            id = "reminder-dst",
            debtId = DebtId("debt-dst"),
            triggerAt = dueLocal,
            zoneId = berlin,
            status = ReminderStatus.SCHEDULED,
            createdAt = Instant.parse("2026-10-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-10-20T00:00:00Z"),
        )

        val dayBefore = ReminderEscalationPolicy.nextTrigger(
            reminder = reminder,
            occurrence = ReminderOccurrence.UPCOMING_DAY_BEFORE,
            now = Instant.parse("2026-10-20T00:00:00Z"),
        )

        assertEquals(
            java.time.LocalTime.of(9, 0),
            requireNotNull(dayBefore).atZone(berlin).toLocalTime(),
        )
    }

    private fun reminder(triggerAt: Instant): ReminderRecord = ReminderRecord(
        id = "reminder-1",
        debtId = DebtId("debt-1"),
        triggerAt = triggerAt,
        zoneId = zone,
        status = ReminderStatus.SCHEDULED,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )
}
