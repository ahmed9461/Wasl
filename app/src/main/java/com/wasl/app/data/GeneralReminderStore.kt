package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId

interface GeneralReminderStore {
    suspend fun getReminder(reminderId: String): GeneralReminderRecord?

    suspend fun getReminderForDebt(debtId: DebtId): GeneralReminderRecord?

    suspend fun getRecoverableReminders(): List<GeneralReminderRecord>

    suspend fun upsertReminder(command: UpsertGeneralReminderCommand): GeneralReminderRecord

    suspend fun updateReminderSchedule(
        reminderId: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ): GeneralReminderRecord

    suspend fun markReminderDelivered(
        reminderId: String,
        deliveredAt: Instant,
    ): GeneralReminderRecord

    suspend fun markReminderBlockedByPermission(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord

    suspend fun markReminderFailed(
        reminderId: String,
        failureCode: String,
        updatedAt: Instant,
    ): GeneralReminderRecord

    suspend fun cancelReminder(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord
}
