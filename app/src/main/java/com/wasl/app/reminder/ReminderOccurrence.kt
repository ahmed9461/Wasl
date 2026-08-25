package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord
import java.time.Instant

enum class ReminderOccurrence(
    val wireValue: String,
    val offsetDays: Long,
    val repeatEveryDays: Long? = null,
) {
    UPCOMING_DAY_BEFORE(
        wireValue = "UPCOMING_DAY_BEFORE",
        offsetDays = -1L,
    ),
    DUE_DATE(
        wireValue = "DUE_DATE",
        offsetDays = 0L,
    ),
    OVERDUE_TWO_DAYS(
        wireValue = "OVERDUE_TWO_DAYS",
        offsetDays = 2L,
    ),
    OVERDUE_WEEKLY(
        wireValue = "OVERDUE_WEEKLY",
        offsetDays = 7L,
        repeatEveryDays = 7L,
    ),
    ;

    val isPeriodic: Boolean
        get() = repeatEveryDays != null

    companion object {
        fun fromWireValue(value: String?): ReminderOccurrence =
            entries.firstOrNull { it.wireValue == value } ?: DUE_DATE
    }
}

data class ReminderOccurrenceSchedule(
    val occurrence: ReminderOccurrence,
    val triggerAt: Instant,
)

object ReminderEscalationPolicy {
    fun schedules(
        reminder: ReminderRecord,
        now: Instant,
    ): List<ReminderOccurrenceSchedule> = buildList {
        ReminderOccurrence.entries.forEach { occurrence ->
            nextTrigger(reminder, occurrence, now)?.let { triggerAt ->
                add(ReminderOccurrenceSchedule(occurrence, triggerAt))
            }
        }
    }

    fun nextTrigger(
        reminder: ReminderRecord,
        occurrence: ReminderOccurrence,
        now: Instant,
    ): Instant? {
        val dueCivilTime = reminder.triggerAt.atZone(reminder.zoneId).toLocalDateTime()
        var candidate = dueCivilTime
            .plusDays(occurrence.offsetDays)
            .atZone(reminder.zoneId)
            .toInstant()

        val repeatDays = occurrence.repeatEveryDays
        if (repeatDays == null) {
            return candidate.takeIf { it.isAfter(now) }
        }

        while (!candidate.isAfter(now)) {
            candidate = candidate
                .atZone(reminder.zoneId)
                .toLocalDateTime()
                .plusDays(repeatDays)
                .atZone(reminder.zoneId)
                .toInstant()
        }
        return candidate
    }
}
