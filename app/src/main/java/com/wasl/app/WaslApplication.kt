package com.wasl.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.wasl.app.backup.AndroidBackupService
import com.wasl.app.backup.BackupService
import com.wasl.app.data.AttachmentStore
import com.wasl.app.data.GeneralReminderStore
import com.wasl.app.data.InstallmentAwareWaslRepository
import com.wasl.app.data.InstallmentPlanStore
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.ReminderStore
import com.wasl.app.data.UnavailableAttachmentStore
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.RoomAccountDocumentStore
import com.wasl.app.data.local.RoomAdvancedSearchStore
import com.wasl.app.data.local.RoomAttachmentStore
import com.wasl.app.data.local.RoomGeneralReminderStore
import com.wasl.app.data.local.RoomInstallmentPlanStore
import com.wasl.app.data.local.RoomPaymentClaimStore
import com.wasl.app.data.local.RoomPaymentPromiseStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.app.document.AccountDocumentService
import com.wasl.app.document.AndroidAccountDocumentService
import com.wasl.app.document.AndroidPaymentReceiptService
import com.wasl.app.document.PaymentReceiptService
import com.wasl.app.privacy.AppForegroundTracker
import com.wasl.app.privacy.AppLockSession
import com.wasl.app.privacy.PrivacyPreferences
import com.wasl.app.reminder.GeneralReminderNotificationPublisher
import com.wasl.app.reminder.GeneralReminderScheduler
import com.wasl.app.reminder.GeneralReminderService
import com.wasl.app.reminder.HybridReminderScheduler
import com.wasl.app.reminder.ReminderNotificationPublisher
import com.wasl.app.reminder.ReminderScheduler
import com.wasl.app.reminder.WorkManagerGeneralReminderScheduler

class WaslApplication : Application() {
    private val database: WaslDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WaslDatabase.create(this)
    }

    private val roomRepository: RoomWaslRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomWaslRepository(database)
    }

    private val roomAdvancedSearchStore: RoomAdvancedSearchStore by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        RoomAdvancedSearchStore(database)
    }

    private val roomInstallmentPlanStore: RoomInstallmentPlanStore by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        RoomInstallmentPlanStore(database, roomRepository)
    }

    private val roomPaymentClaimStore: RoomPaymentClaimStore by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        RoomPaymentClaimStore(database)
    }

    private val installmentAwareRepository: InstallmentAwareWaslRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        InstallmentAwareWaslRepository(
            waslRepository = roomRepository,
            installmentPlanStore = roomInstallmentPlanStore,
            advancedSearchStore = roomAdvancedSearchStore,
            paymentClaimStore = roomPaymentClaimStore,
        )
    }

    val repository: WaslRepository
        get() = installmentAwareRepository

    val reminderStore: ReminderStore
        get() = roomRepository

    val generalReminderStore: GeneralReminderStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomGeneralReminderStore(database)
    }

    val paymentPromiseStore: PaymentPromiseStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomPaymentPromiseStore(database)
    }

    val paymentClaimStore: PaymentClaimStore
        get() = installmentAwareRepository

    val installmentPlanStore: InstallmentPlanStore
        get() = installmentAwareRepository

    val attachmentStore: AttachmentStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomAttachmentStore(this, database)
    }

    val reminderScheduler: ReminderScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HybridReminderScheduler(this)
    }

    val generalReminderScheduler: GeneralReminderScheduler by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        WorkManagerGeneralReminderScheduler(this)
    }

    val generalReminderService: GeneralReminderService by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        GeneralReminderService(
            store = generalReminderStore,
            scheduler = generalReminderScheduler,
        )
    }

    private val accountDocumentStore: RoomAccountDocumentStore by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        RoomAccountDocumentStore(
            database = database,
            repository = installmentAwareRepository,
        )
    }

    val accountDocumentService: AccountDocumentService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAccountDocumentService(
            context = this,
            store = accountDocumentStore,
        )
    }

    val paymentReceiptService: PaymentReceiptService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidPaymentReceiptService(
            context = this,
            store = roomRepository,
            accountDocumentService = accountDocumentService,
        )
    }

    val privacyPreferences: PrivacyPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PrivacyPreferences(this)
    }

    internal val appLockSession = AppLockSession()

    val backupService: BackupService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidBackupService(
            context = this,
            database = database,
        )
    }

    val reminderNotificationPublisher: ReminderNotificationPublisher by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        ReminderNotificationPublisher(this)
    }

    val generalReminderNotificationPublisher: GeneralReminderNotificationPublisher by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        GeneralReminderNotificationPublisher(this)
    }

    override fun onCreate() {
        super.onCreate()
        appLockSession.initialize(privacyPreferences.appLockEnabled)
        registerAppLockLifecycle()
        UnavailableAttachmentStore.install(attachmentStore)
        reminderNotificationPublisher.ensureChannels()
        generalReminderNotificationPublisher.ensureChannel()
        reminderScheduler.requestRecovery()
        generalReminderScheduler.requestRecovery()
    }

    private fun registerAppLockLifecycle() {
        val tracker = AppForegroundTracker(
            onForeground = {
                appLockSession.onForeground(
                    enabledFromPreferences = privacyPreferences.appLockEnabled,
                    timeoutMillis = privacyPreferences.appLockTimeout.durationMillis,
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                )
            },
            onBackground = {
                appLockSession.onBackground(SystemClock.elapsedRealtime())
            },
        )
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    tracker.activityStarted()
                }

                override fun onActivityStopped(activity: Activity) {
                    tracker.activityStopped(activity.isChangingConfigurations)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
