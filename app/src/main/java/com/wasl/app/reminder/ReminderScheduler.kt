package com.wasl.app.reminder

import com.wasl.app.data.ReminderRecord

interface ReminderScheduler {
    fun schedule(reminder: ReminderRecord)

    fun cancel(reminderId: String)

    fun requestRecovery()
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderRecord) = Unit

    override fun cancel(reminderId: String) = Unit

    override fun requestRecovery() = Unit
}
