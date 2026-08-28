package com.wasl.app.reminder

import com.wasl.app.data.GeneralReminderRecord

interface GeneralReminderScheduler {
    fun replace(reminder: GeneralReminderRecord)

    fun scheduleNext(reminder: GeneralReminderRecord)

    fun cancel(reminderId: String)

    fun requestRecovery()
}
