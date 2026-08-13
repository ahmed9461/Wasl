package com.wasl.app.data

import java.time.Instant
import java.time.ZoneId

interface ReminderStore {
    suspend fun getReminder(reminderId: String): ReminderRecord?

    suspend fun getRecoverableReminders(): List<ReminderRecord>

    suspend fun updateReminderSchedule(
        reminderId: String,
        triggerAt: Instant,
        zoneId: ZoneId,
        updatedAt: Instant,
    )

    suspend fun markReminderDelivered(reminderId: String, deliveredAt: Instant)

    suspend fun markReminderBlockedByPermission(reminderId: String, updatedAt: Instant)

    suspend fun markReminderCancelled(reminderId: String, updatedAt: Instant)

    suspend fun markReminderFailed(
        reminderId: String,
        failureCode: String,
        updatedAt: Instant,
    )
}
