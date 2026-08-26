package com.wasl.app.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderTime {
    val defaultDueTime: LocalTime = LocalTime.of(9, 0)

    fun dueDateTrigger(
        dueDate: LocalDate,
        now: Instant,
        zoneId: ZoneId,
        time: LocalTime = defaultDueTime,
    ): Instant {
        require(!dueDate.isBefore(now.atZone(zoneId).toLocalDate())) {
            "Due date cannot be in the past."
        }
        val intended = dueDate.atTime(time).atZone(zoneId).toInstant()
        return if (intended.isAfter(now)) intended else now.plusSeconds(60)
    }

    fun rebaseToZone(
        triggerAt: Instant,
        sourceZone: ZoneId,
        targetZone: ZoneId,
        now: Instant,
    ): Instant {
        val civilTime = triggerAt.atZone(sourceZone).toLocalDateTime()
        val rebased = civilTime.atZone(targetZone).toInstant()
        return if (rebased.isAfter(now)) rebased else now.plusSeconds(60)
    }
}
