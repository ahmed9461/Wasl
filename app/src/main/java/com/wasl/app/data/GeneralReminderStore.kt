package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GeneralReminderStore {
    fun observeReminderForDebt(debtId: DebtId): Flow<GeneralReminderRecord?>

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

object UnavailableGeneralReminderStore : GeneralReminderStore {
    override fun observeReminderForDebt(debtId: DebtId): Flow<GeneralReminderRecord?> = flowOf(null)

    override suspend fun getReminder(reminderId: String): GeneralReminderRecord? = null

    override suspend fun getReminderForDebt(debtId: DebtId): GeneralReminderRecord? = null

    override suspend fun getRecoverableReminders(): List<GeneralReminderRecord> = emptyList()

    override suspend fun upsertReminder(command: UpsertGeneralReminderCommand): GeneralReminderRecord =
        error("General reminder storage is unavailable.")

    override suspend fun updateReminderSchedule(
        reminderId: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    ): GeneralReminderRecord = error("General reminder storage is unavailable.")

    override suspend fun markReminderDelivered(
        reminderId: String,
        deliveredAt: Instant,
    ): GeneralReminderRecord = error("General reminder storage is unavailable.")

    override suspend fun markReminderBlockedByPermission(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord = error("General reminder storage is unavailable.")

    override suspend fun markReminderFailed(
        reminderId: String,
        failureCode: String,
        updatedAt: Instant,
    ): GeneralReminderRecord = error("General reminder storage is unavailable.")

    override suspend fun cancelReminder(
        reminderId: String,
        updatedAt: Instant,
    ): GeneralReminderRecord = error("General reminder storage is unavailable.")
}
