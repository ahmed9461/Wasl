package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderScheduleType
import com.wasl.app.data.ReminderStatus
import java.time.Instant
import java.time.ZoneId

internal data class ReminderRecoveryPlan(
    val shouldSchedule: Boolean,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val shouldPersistScheduledState: Boolean,
)

internal fun planReminderRecovery(
    stored: ReminderRecord,
    currentZone: ZoneId,
    now: Instant,
    canNotify: Boolean,
    canScheduleExactAlarms: Boolean = true,
): ReminderRecoveryPlan {
    if (stored.scheduleType == ReminderScheduleType.EXACT_ALARM) {
        val rebased = if (stored.zoneId != currentZone) {
            stored.triggerAt.atZone(stored.zoneId)
                .toLocalDateTime()
                .atZone(currentZone)
                .toInstant()
        } else {
            stored.triggerAt
        }
        val canSchedule = canNotify && canScheduleExactAlarms && rebased.isAfter(now)
        return ReminderRecoveryPlan(
            shouldSchedule = canSchedule,
            triggerAt = rebased,
            zoneId = currentZone,
            shouldPersistScheduledState = canSchedule && (
                stored.zoneId != currentZone ||
                    stored.status != ReminderStatus.SCHEDULED ||
                    stored.lastFailureCode != null
                ),
        )
    }

    if (stored.status == ReminderStatus.BLOCKED_PERMISSION && !canNotify) {
        return ReminderRecoveryPlan(
            shouldSchedule = false,
            triggerAt = stored.triggerAt,
            zoneId = stored.zoneId,
            shouldPersistScheduledState = false,
        )
    }

    val triggerAt = if (stored.zoneId != currentZone) {
        ReminderTime.rebaseToZone(
            triggerAt = stored.triggerAt,
            sourceZone = stored.zoneId,
            targetZone = currentZone,
            now = now,
        )
    } else {
        stored.triggerAt
    }
    return ReminderRecoveryPlan(
        shouldSchedule = true,
        triggerAt = triggerAt,
        zoneId = currentZone,
        shouldPersistScheduledState = stored.zoneId != currentZone ||
            stored.status != ReminderStatus.SCHEDULED ||
            stored.lastFailureCode != null,
    )
}
