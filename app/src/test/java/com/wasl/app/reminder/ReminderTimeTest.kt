package com.wasl.app.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReminderTimeTest {
    @Test
    fun futureDueDateUsesNineAmInTheSelectedCivilZone() {
        val trigger = ReminderTime.dueDateTrigger(
            dueDate = LocalDate.parse("2026-08-14"),
            now = Instant.parse("2026-08-13T20:00:00Z"),
            zoneId = ZoneId.of("Asia/Riyadh"),
        )

        assertEquals(Instant.parse("2026-08-14T06:00:00Z"), trigger)
    }

    @Test
    fun todayAfterNineSchedulesOneMinuteFromNow() {
        val now = Instant.parse("2026-08-13T10:00:00Z")
        val trigger = ReminderTime.dueDateTrigger(
            dueDate = LocalDate.parse("2026-08-13"),
            now = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(now.plusSeconds(60), trigger)
    }

    @Test
    fun zoneRebasePreservesCivilClockTime() {
        val trigger = ReminderTime.rebaseToZone(
            triggerAt = Instant.parse("2026-08-14T06:00:00Z"),
            sourceZone = ZoneId.of("Asia/Riyadh"),
            targetZone = ZoneId.of("Europe/London"),
            now = Instant.parse("2026-08-13T00:00:00Z"),
        )

        assertEquals(Instant.parse("2026-08-14T08:00:00Z"), trigger)
    }

    @Test
    fun pastDueDateIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ReminderTime.dueDateTrigger(
                dueDate = LocalDate.parse("2026-08-12"),
                now = Instant.parse("2026-08-13T00:00:00Z"),
                zoneId = ZoneId.of("UTC"),
            )
        }
    }
}
