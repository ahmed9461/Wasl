package com.wasl.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val requestedDebtId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDebtId.value = intent.getStringExtra(EXTRA_DEBT_ID)
        enableEdgeToEdge()
        applySecureScreenPreference()
        setContent {
            val waslApplication = application as WaslApplication
            var installmentsOpen by remember { mutableStateOf(false) }
            var settingsOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalOpenInstallmentsHub provides {
                        settingsOpen = false
                        installmentsOpen = true
                    },
                    LocalOpenSettingsHub provides {
                        installmentsOpen = false
                        settingsOpen = true
                    },
                ) {
                    WaslApp(
                        repository = waslApplication.repository,
                        reminderScheduler = waslApplication.reminderScheduler,
                        paymentReceiptService = waslApplication.paymentReceiptService,
                        paymentPromiseStore = waslApplication.paymentPromiseStore,
                        requestedDebtId = requestedDebtId.value,
                        onRequestedDebtHandled = { requestedDebtId.value = null },
                    )
                }
                if (installmentsOpen) {
                    InstallmentsHubRoute(
                        repository = waslApplication.repository,
                        store = waslApplication.installmentPlanStore,
                        onBack = { installmentsOpen = false },
                        onOpenAccount = { debtId ->
                            installmentsOpen = false
                            requestedDebtId.value = debtId.value
                        },
                    )
                }
                if (settingsOpen) {
                    SettingsHubRoute(
                        backupService = waslApplication.backupService,
                        privacyPreferences = waslApplication.privacyPreferences,
                        onBack = { settingsOpen = false },
                        onRestored = {
                            waslApplication.reminderScheduler.requestRecovery()
                        },
                        onSecureScreenChanged = ::applySecureScreenPreference,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDebtId.value = intent.getStringExtra(EXTRA_DEBT_ID)
    }

    override fun onResume() {
        super.onResume()
        val waslApplication = application as WaslApplication
        applySecureScreenPreference()
        if (waslApplication.reminderNotificationPublisher.canNotify()) {
            waslApplication.reminderScheduler.requestRecovery()
        }
    }

    private fun applySecureScreenPreference() {
        val preferences = (application as WaslApplication).privacyPreferences
        if (preferences.secureScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        const val ACTION_OPEN_DEBT = "com.wasl.app.action.OPEN_DEBT"
        const val EXTRA_DEBT_ID = "com.wasl.app.extra.DEBT_ID"
    }
}
