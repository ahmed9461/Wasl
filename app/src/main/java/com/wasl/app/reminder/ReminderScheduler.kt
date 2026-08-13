package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord

interface ReminderScheduler {
    fun schedule(reminder: ReminderRecord)

    fun requestRecovery()
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderRecord) = Unit

    override fun requestRecovery() = Unit
}
