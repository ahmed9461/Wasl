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
    currentZone: ZoneId,
    now: Instant,
    canNotify: Boolean,
): GeneralReminderRecoveryPlan {
    if (stored.status == ReminderStatus.CANCELLED ||
        stored.status == ReminderStatus.DELIVERED
    ) {
        return GeneralReminderRecoveryPlan(
            shouldSchedule = false,
            shouldPersistScheduledState = false,
            triggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
        )
    }
    if (stored.status == ReminderStatus.BLOCKED_PERMISSION && !canNotify) {
        return GeneralReminderRecoveryPlan(
            shouldSchedule = false,
            shouldPersistScheduledState = false,
            triggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
        )
    }

    val rebasedTrigger = if (stored.zoneId == currentZone) {
        stored.triggerAt
    } else {
        stored.triggerAt.atZone(stored.zoneId)
            .toLocalDateTime()
            .atZone(currentZone)
            .toInstant()
    }
    val plannedTrigger = when {
        rebasedTrigger.isAfter(now) -> rebasedTrigger
        stored.repeatRule != null -> nextGeneralReminderTrigger(
            currentTriggerAt = rebasedTrigger,
            zoneId = currentZone,
            repeatRule = stored.repeatRule,
            now = now,
        )
        else -> now
    }
    return GeneralReminderRecoveryPlan(
        shouldSchedule = true,
        shouldPersistScheduledState = stored.status != ReminderStatus.SCHEDULED ||
            stored.lastFailureCode != null ||
            stored.zoneId != currentZone ||
            plannedTrigger != stored.triggerAt,
        triggerAt = plannedTrigger,
        zoneId = currentZone,
    )
}
