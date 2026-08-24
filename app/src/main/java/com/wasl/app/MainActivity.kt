package com.wasl.app

import android.content.Intent
import android.os.Bundle
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
        setContent {
            val waslApplication = application as WaslApplication
            var installmentsOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalOpenInstallmentsHub provides { installmentsOpen = true },
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
        if (waslApplication.reminderNotificationPublisher.canNotify()) {
            waslApplication.reminderScheduler.requestRecovery()
        }
    }

    companion object {
        const val ACTION_OPEN_DEBT = "com.wasl.app.action.OPEN_DEBT"
        const val EXTRA_DEBT_ID = "com.wasl.app.extra.DEBT_ID"
    }
}
