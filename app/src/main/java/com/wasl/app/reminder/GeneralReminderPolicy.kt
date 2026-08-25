package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderFrequency
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderRepeatRule
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

fun nextGeneralReminderTrigger(
    currentTriggerAt: Instant,
    zoneId: ZoneId,
    repeatRule: GeneralReminderRepeatRule,
    now: Instant,
): Instant {
    var local = currentTriggerAt.atZone(zoneId).toLocalDateTime()
    var candidate: Instant
    do {
        local = advanceGeneralReminder(local, repeatRule)
        candidate = local.atZone(zoneId).toInstant()
    } while (!candidate.isAfter(now))
    return candidate
}

private fun advanceGeneralReminder(
    current: LocalDateTime,
    repeatRule: GeneralReminderRepeatRule,
): LocalDateTime = when (repeatRule.frequency) {
    GeneralReminderFrequency.DAILY -> current.plusDays(1)
    GeneralReminderFrequency.WEEKLY -> current.plusWeeks(1)
    GeneralReminderFrequency.MONTHLY -> {
        val anchorDay = requireNotNull(repeatRule.monthlyDayOfMonth)
        val targetMonth = YearMonth.from(current).plusMonths(1)
        val targetDay = anchorDay.coerceAtMost(targetMonth.lengthOfMonth())
        LocalDateTime.of(targetMonth.atDay(targetDay), current.toLocalTime())
    }
}

data class GeneralReminderRecoveryPlan(
    val shouldSchedule: Boolean,
    val shouldPersistScheduledState: Boolean,
    val triggerAt: Instant,
    val zoneId: ZoneId,
)

fun planGeneralReminderRecovery(
    stored: GeneralReminderRecord,
    now: Instant,
    canNotify: Boolean,
): GeneralReminderRecoveryPlan {
    if (stored.status == ReminderStatus.CANCELLED ||
        stored.status == ReminderStatus.DELIVERED ||
        !canNotify
    ) {
        return GeneralReminderRecoveryPlan(
            shouldSchedule = false,
            shouldPersistScheduledState = false,
            triggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
        )
    }

    val plannedTrigger = when {
        stored.triggerAt.isAfter(now) -> stored.triggerAt
        stored.repeatRule != null -> nextGeneralReminderTrigger(
            currentTriggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
            repeatRule = stored.repeatRule,
            now = now,
        )
        else -> now
    }
    return GeneralReminderRecoveryPlan(
        shouldSchedule = true,
        shouldPersistScheduledState = stored.status != ReminderStatus.SCHEDULED ||
            plannedTrigger != stored.triggerAt,
        triggerAt = plannedTrigger,
        zoneId = stored.zoneId,
    )
}
