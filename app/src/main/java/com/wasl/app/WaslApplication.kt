package com.wasl.app

import android.app.Application
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.ReminderStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.reminder.ReminderNotificationPublisher
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.reminder.WorkManagerReminderScheduler
import com.wasl.app.document.AndroidPaymentReceiptService
import com.wasl.app.document.PaymentReceiptService

class WaslApplication : Application() {
    private val database: WaslDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WaslDatabase.create(this)
    }

    private val roomRepository: RoomWaslRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomWaslRepository(database)
    }

    val repository: WaslRepository
        get() = roomRepository

    val reminderStore: ReminderStore
        get() = roomRepository

    val reminderScheduler: ReminderScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManagerReminderScheduler(this)
    }

    val paymentReceiptService: PaymentReceiptService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidPaymentReceiptService(
            context = this,
            store = roomRepository,
        )
    }

    val reminderNotificationPublisher: ReminderNotificationPublisher by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        ReminderNotificationPublisher(this)
    }

    override fun onCreate() {
        super.onCreate()
        reminderNotificationPublisher.ensureChannels()
        reminderScheduler.requestRecovery()
    }
}
