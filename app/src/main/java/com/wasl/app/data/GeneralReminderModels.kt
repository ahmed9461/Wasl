package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId

enum class GeneralReminderFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class GeneralReminderRepeatRule(
    val frequency: GeneralReminderFrequency,
    val monthlyDayOfMonth: Int? = null,
) {
    init {
        when (frequency) {
            GeneralReminderFrequency.MONTHLY -> require(
                monthlyDayOfMonth != null && monthlyDayOfMonth in 1..31,
            ) {
                "A monthly reminder requires an anchor day from 1 to 31."
            }
            GeneralReminderFrequency.DAILY,
            GeneralReminderFrequency.WEEKLY,
            -> require(monthlyDayOfMonth == null) {
                "Only monthly reminders may carry a day-of-month anchor."
            }
        }
    }

    fun toStorageValue(): String = when (frequency) {
        GeneralReminderFrequency.DAILY -> "DAILY"
        GeneralReminderFrequency.WEEKLY -> "WEEKLY"
        GeneralReminderFrequency.MONTHLY -> "MONTHLY:${requireNotNull(monthlyDayOfMonth)}"
    }

    companion object {
        fun forTrigger(
            frequency: GeneralReminderFrequency,
            triggerAt: Instant,
            zoneId: ZoneId,
        ): GeneralReminderRepeatRule = when (frequency) {
            GeneralReminderFrequency.MONTHLY -> GeneralReminderRepeatRule(
                frequency = frequency,
                monthlyDayOfMonth = triggerAt.atZone(zoneId).dayOfMonth,
            )
            GeneralReminderFrequency.DAILY,
            GeneralReminderFrequency.WEEKLY,
            -> GeneralReminderRepeatRule(frequency)
        }

        fun fromStorageValue(value: String?): GeneralReminderRepeatRule? {
            if (value == null) return null
            return when {
                value == "DAILY" -> GeneralReminderRepeatRule(GeneralReminderFrequency.DAILY)
                value == "WEEKLY" -> GeneralReminderRepeatRule(GeneralReminderFrequency.WEEKLY)
                value.startsWith("MONTHLY:") -> {
                    val day = value.substringAfter(':').toIntOrNull()
                        ?: error("Invalid monthly reminder rule: $value")
                    GeneralReminderRepeatRule(
                        frequency = GeneralReminderFrequency.MONTHLY,
                        monthlyDayOfMonth = day,
                    )
                }
                else -> error("Unsupported general reminder repeat rule: $value")
            }
        }
    }
}

data class GeneralReminderRecord(
    val id: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val repeatRule: GeneralReminderRepeatRule? = null,
    val status: ReminderStatus,
    val lastFailureCode: String? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "General reminder ID cannot be blank." }
    }
}

data class UpsertGeneralReminderCommand(
    val reminderId: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val repeatRule: GeneralReminderRepeatRule? = null,
    val updatedAt: Instant,
) {
    init {
        require(reminderId.isNotBlank()) { "General reminder ID cannot be blank." }
        require(triggerAt.isAfter(updatedAt)) {
            "A general reminder must be scheduled in the future."
        }
    }
}
