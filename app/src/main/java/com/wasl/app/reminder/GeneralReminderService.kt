package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.UpsertGeneralReminderCommand
import java.time.Clock
import java.time.Instant

class GeneralReminderService(
    private val store: GeneralReminderStore,
    private val scheduler: GeneralReminderScheduler,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun save(command: UpsertGeneralReminderCommand): GeneralReminderRecord {
        val reminder = store.upsertReminder(command)
        scheduler.replace(reminder)
        return reminder
    }

    suspend fun cancel(reminderId: String): GeneralReminderRecord {
        val cancelled = store.cancelReminder(
            reminderId = reminderId,
            updatedAt = Instant.now(clock),
        )
        scheduler.cancel(reminderId)
        return cancelled
    }

    fun requestRecovery() {
        scheduler.requestRecovery()
    }
}
