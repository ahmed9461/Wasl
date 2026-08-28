package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.UpsertGeneralReminderCommand
import java.time.Clock
import java.time.Instant

data class GeneralReminderMutationResult(
    val reminder: GeneralReminderRecord,
    val platformSyncPending: Boolean,
)

class GeneralReminderService(
    private val store: GeneralReminderStore,
    private val scheduler: GeneralReminderScheduler,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun save(command: UpsertGeneralReminderCommand): GeneralReminderMutationResult {
        val reminder = store.upsertReminder(command)
        val platformSyncPending = runCatching {
            scheduler.replace(reminder)
        }.onFailure {
            runCatching { scheduler.requestRecovery() }
        }.isFailure
        return GeneralReminderMutationResult(
            reminder = reminder,
            platformSyncPending = platformSyncPending,
        )
    }

    suspend fun cancel(reminderId: String): GeneralReminderMutationResult {
        val cancelled = store.cancelReminder(
            reminderId = reminderId,
            updatedAt = Instant.now(clock),
        )
        val platformSyncPending = runCatching {
            scheduler.cancel(reminderId)
        }.onFailure {
            runCatching { scheduler.requestRecovery() }
        }.isFailure
        return GeneralReminderMutationResult(
            reminder = cancelled,
            platformSyncPending = platformSyncPending,
        )
    }

    fun requestRecovery() {
        scheduler.requestRecovery()
    }
}
