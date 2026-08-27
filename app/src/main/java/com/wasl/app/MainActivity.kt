package com.wasl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.wasl.app.privacy.AppLockAuthPurpose
import com.wasl.app.privacy.AppLockViewModel
import com.wasl.app.reminder.ReminderNotificationActions
import com.wasl.domain.DebtId

class MainActivity : FragmentActivity() {
    private val requestedDebtId = mutableStateOf<String?>(null)
    private val requestedPaymentIntent = mutableStateOf<String?>(null)
    private lateinit var appLockViewModel: AppLockViewModel
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val waslApplication = application as WaslApplication
        appLockViewModel = ViewModelProvider(this)[AppLockViewModel::class.java]
        appLockViewModel.initialize(waslApplication.privacyPreferences.appLockEnabled)
        biometricPrompt = BiometricPrompt(
            this,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(result)
                    val completedPurpose = appLockViewModel.authenticationSucceeded()
                    if (completedPurpose == AppLockAuthPurpose.ENABLE) {
                        waslApplication.privacyPreferences.appLockEnabled = true
                    }
                    applySecureScreenPreference()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    appLockViewModel.authenticationError(
                        authenticationErrorMessage(errorCode, errString),
                    )
                }
            },
        )

        readNavigationIntent(intent)
        enableEdgeToEdge()
        applySecureScreenPreference()

        setContent {
            var installmentsOpen by remember { mutableStateOf(false) }
            var settingsOpen by remember { mutableStateOf(false) }
            var documentsOpen by remember { mutableStateOf(false) }
            var documentsDebtId by remember { mutableStateOf<DebtId?>(null) }
            var securityOpen by remember { mutableStateOf(false) }

            val appLocked = appLockViewModel.locked
            val appLockEnabled = appLockViewModel.enabled
            val appLockMessage = appLockViewModel.message
            val authenticationAvailable = isAppLockAuthenticationAvailable()

            val blockedContentModifier = if (appLocked) {
                Modifier
                    .clearAndSetSemantics { }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed }) break
                            }
                        }
                    }
            } else {
                Modifier
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blockedContentModifier),
                ) {
                    CompositionLocalProvider(
                        LocalOpenPersonTimeline provides { personId ->
                            startActivity(
                                Intent(this@MainActivity, PersonTimelineActivity::class.java)
                                    .putExtra(PersonTimelineActivity.EXTRA_PERSON_ID, personId.value),
                            )
                        },
                        LocalOpenInstallmentsHub provides {
                            settingsOpen = false
                            documentsOpen = false
                            documentsDebtId = null
                            securityOpen = false
                            installmentsOpen = true
                        },
                        LocalOpenSettingsHub provides {
                            installmentsOpen = false
                            documentsOpen = false
                            documentsDebtId = null
                            securityOpen = false
                            settingsOpen = true
                        },
                        LocalOpenAccountDocuments provides { debtId ->
                            installmentsOpen = false
                            settingsOpen = false
                            securityOpen = false
                            documentsDebtId = debtId
                            documentsOpen = true
                        },
                    ) {
                        WaslApp(
                            repository = waslApplication.repository,
                            reminderScheduler = waslApplication.reminderScheduler,
                            paymentReceiptService = waslApplication.paymentReceiptService,
                            paymentPromiseStore = waslApplication.paymentPromiseStore,
                            requestedDebtId = requestedDebtId.value,
                            requestedPaymentIntent = requestedPaymentIntent.value,
                            onRequestedDebtHandled = {
                                requestedDebtId.value = null
                                requestedPaymentIntent.value = null
                            },
                        )
                    }

                    if (!installmentsOpen && !settingsOpen && !documentsOpen && !securityOpen) {
                        OutlinedButton(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 88.dp),
                            onClick = {
                                startActivity(
                                    Intent(this@MainActivity, NaturalEntryActivity::class.java),
                                )
                            },
                        ) {
                            Text("إدخال ذكي")
                        }
                    }

                    if (installmentsOpen) {
                        InstallmentsHubRoute(
                            repository = waslApplication.repository,
                            store = waslApplication.installmentPlanStore,
                            onBack = { installmentsOpen = false },
                            onOpenAccount = { debtId ->
                                installmentsOpen = false
                                requestedDebtId.value = debtId.value
                                requestedPaymentIntent.value = null
                            },
                        )
                    }

                    if (settingsOpen) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SettingsHubRoute(
                                backupService = waslApplication.backupService,
                                privacyPreferences = waslApplication.privacyPreferences,
                                onBack = { settingsOpen = false },
                                onOpenDocuments = {
                                    settingsOpen = false
                                    documentsDebtId = null
                                    documentsOpen = true
                                },
                                onOpenStatistics = {
                                    startActivity(
                                        Intent(this@MainActivity, StatisticsActivity::class.java),
                                    )
                                },
                                onRestored = {
                                    runCatching { waslApplication.reminderScheduler.requestRecovery() }
                                    runCatching { waslApplication.generalReminderService.requestRecovery() }
                                },
                                onSecureScreenChanged = ::applySecureScreenPreference,
                            )
                            SecuritySettingsEntryButton(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp),
                                onClick = {
                                    settingsOpen = false
                                    securityOpen = true
                                },
                            )
                        }
                    }

                    if (documentsOpen) {
                        DocumentsHubRoute(
                            repository = waslApplication.repository,
                            documentService = waslApplication.paymentReceiptService,
                            initialDebtId = documentsDebtId,
                            onBack = {
                                val returnToSettings = documentsDebtId == null
                                documentsOpen = false
                                documentsDebtId = null
                                if (returnToSettings) settingsOpen = true
                            },
                        )
                    }

                    if (securityOpen) {
                        SecurityHubRoute(
                            appLockEnabled = appLockEnabled,
                            appLockTimeout = waslApplication.privacyPreferences.appLockTimeout,
                            authenticationAvailable = authenticationAvailable,
                            statusMessage = appLockMessage,
                            onBack = {
                                appLockViewModel.clearMessage()
                                securityOpen = false
                                settingsOpen = true
                            },
                            onAppLockEnabledChange = { enabled ->
                                if (enabled) {
                                    requestAppLockAuthentication(AppLockAuthPurpose.ENABLE)
                                } else {
                                    disableAppLock()
                                }
                            },
                            onAppLockTimeoutChange = { timeout ->
                                waslApplication.privacyPreferences.appLockTimeout = timeout
                            },
                            onLockNow = {
                                appLockViewModel.lockNow()
                                applySecureScreenPreference()
                            },
                        )
                    }
                }

                if (appLocked) {
                    AppLockScreen(
                        authenticationAvailable = authenticationAvailable,
                        message = appLockMessage,
                        onUnlock = {
                            requestAppLockAuthentication(AppLockAuthPurpose.UNLOCK)
                        },
                        onDisableUnavailableLock = ::disableAppLock,
                    )
                }
            }
        }

        window.decorView.post {
            requestInitialNotificationPermissionIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val waslApplication = application as WaslApplication
        val preferences = waslApplication.privacyPreferences
        appLockViewModel.onForeground(
            enabledFromPreferences = preferences.appLockEnabled,
            timeoutMillis = preferences.appLockTimeout.durationMillis,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
        )
        applySecureScreenPreference()
        if (
            appLockViewModel.locked &&
            isAppLockAuthenticationAvailable() &&
            !appLockViewModel.authenticationInProgress
        ) {
            requestAppLockAuthentication(AppLockAuthPurpose.UNLOCK)
        }
        if (waslApplication.reminderNotificationPublisher.canNotify()) {
            runCatching { waslApplication.reminderScheduler.requestRecovery() }
        }
        if (waslApplication.generalReminderNotificationPublisher.canNotify()) {
            runCatching { waslApplication.generalReminderService.requestRecovery() }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appLockViewModel.onBackground(SystemClock.elapsedRealtime())
        }
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) return
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) return
        val waslApplication = application as? WaslApplication ?: return
        runCatching { waslApplication.reminderScheduler.requestRecovery() }
        runCatching { waslApplication.generalReminderService.requestRecovery() }
    }

    private fun requestInitialNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val preferences = getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)) return
        preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true).apply()
        runCatching {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION,
            )
        }.onFailure {
            preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false).apply()
        }
    }

    private fun readNavigationIntent(intent: Intent) {
        val notificationTag = intent.getStringExtra(
            ReminderNotificationActions.EXTRA_NOTIFICATION_TAG,
        )
        val notificationId = if (intent.hasExtra(ReminderNotificationActions.EXTRA_NOTIFICATION_ID)) {
            intent.getIntExtra(ReminderNotificationActions.EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        } else {
            Int.MIN_VALUE
        }
        if (notificationTag != null && notificationId != Int.MIN_VALUE) {
            NotificationManagerCompat.from(this).cancel(notificationTag, notificationId)
        }

        requestedDebtId.value = intent.getStringExtra(EXTRA_DEBT_ID)
        requestedPaymentIntent.value = intent
            .getStringExtra(ReminderNotificationActions.EXTRA_PAYMENT_INTENT)
            ?.takeIf { value ->
                value == ReminderNotificationActions.PAYMENT_INTENT_PARTIAL ||
                    value == ReminderNotificationActions.PAYMENT_INTENT_FULL
            }
    }

    private fun requestAppLockAuthentication(purpose: AppLockAuthPurpose) {
        if (appLockViewModel.authenticationInProgress) return
        val availability = appLockAuthenticationStatus()
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            appLockViewModel.authenticationError(
                authenticationUnavailableMessage(availability),
            )
            return
        }

        appLockViewModel.beginAuthentication(purpose)
        val title = if (purpose == AppLockAuthPurpose.ENABLE) {
            "تفعيل قفل وَصل"
        } else {
            "فتح وَصل"
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("استخدم البصمة أو قفل الجهاز")
            .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun disableAppLock() {
        val preferences = (application as WaslApplication).privacyPreferences
        preferences.appLockEnabled = false
        appLockViewModel.disable()
        applySecureScreenPreference()
    }

    private fun appLockAuthenticationStatus(): Int =
        BiometricManager.from(this).canAuthenticate(APP_LOCK_AUTHENTICATORS)

    private fun isAppLockAuthenticationAvailable(): Boolean =
        appLockAuthenticationStatus() == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticationUnavailableMessage(status: Int): String = when (status) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "لا توجد بصمة أو وسيلة قفل جهاز مسجلة. فعّلها من إعدادات Android أولًا."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "هذا الجهاز لا يوفّر عتاد مصادقة متوافقًا."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "المصادقة النظامية غير متاحة مؤقتًا. حاول مرة أخرى."
        else ->
            "تعذر استخدام البصمة أو قفل الجهاز الآن."
    }

    private fun authenticationErrorMessage(
        errorCode: Int,
        errorText: CharSequence,
    ): String = when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
            "ألغيت المصادقة. يبقى وَصل مقفلًا حتى نجاح التحقق."
        BiometricPrompt.ERROR_LOCKOUT ->
            "تم إيقاف المحاولات مؤقتًا بعد محاولات غير ناجحة. حاول لاحقًا أو استخدم قفل الجهاز إذا ظهر كخيار."
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            "المصادقة الحيوية مقفلة حاليًا. افتح الجهاز بوسيلة القفل الأساسية ثم حاول مجددًا."
        else -> errorText.toString().takeIf { it.isNotBlank() }
            ?: "تعذرت المصادقة."
    }

    private fun applySecureScreenPreference() {
        val preferences = (application as WaslApplication).privacyPreferences
        if (preferences.secureScreen || preferences.appLockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        private val APP_LOCK_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        private const val PERMISSION_PREFERENCES = "wasl-runtime-permissions"
        private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notification-permission-prompted"
        private const val REQUEST_NOTIFICATION_PERMISSION = 4001

        const val ACTION_OPEN_DEBT = "com.wasl.app.action.OPEN_DEBT"
        const val EXTRA_DEBT_ID = "com.wasl.app.extra.DEBT_ID"
    }
}
