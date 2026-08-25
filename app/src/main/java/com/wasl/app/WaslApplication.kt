package com.wasl.app

import android.app.Application
import com.wasl.app.data.InstallmentAwareWaslRepository
import com.wasl.app.data.InstallmentPlanStore
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.ReminderStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.RoomInstallmentPlanStore
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AndroidPaymentReceiptService
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.reminder.ReminderNotificationPublisher
import com.wasl.app.reminder.HybridReminderScheduler
import com.wasl.app.reminder.ReminderScheduler

class WaslApplication : Application() {
    private val database: WaslDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WaslDatabase.create(this)
    }

    private val roomRepository: RoomWaslRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomWaslRepository(database)
    }

    private val roomInstallmentPlanStore: RoomInstallmentPlanStore by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        RoomInstallmentPlanStore(database, roomRepository)
    }

    private val installmentAwareRepository: InstallmentAwareWaslRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        InstallmentAwareWaslRepository(
            waslRepository = roomRepository,
            installmentPlanStore = roomInstallmentPlanStore,
        )
    }

    val repository: WaslRepository
        get() = installmentAwareRepository

    val reminderStore: ReminderStore
        get() = roomRepository

    val paymentPromiseStore: PaymentPromiseStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomPaymentPromiseStore(database)
    }

    val installmentPlanStore: InstallmentPlanStore
        get() = installmentAwareRepository

    val reminderScheduler: ReminderScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HybridReminderScheduler(this)
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
