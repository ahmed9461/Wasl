package com.wasl.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val requestedDebtId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDebtId.value = intent.getStringExtra(EXTRA_DEBT_ID)
        enableEdgeToEdge()
        setContent {
            val application = application as WaslApplication
            WaslApp(
                repository = application.repository,
                reminderScheduler = application.reminderScheduler,
                requestedDebtId = requestedDebtId.value,
                onRequestedDebtHandled = { requestedDebtId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDebtId.value = intent.getStringExtra(EXTRA_DEBT_ID)
    }

    override fun onResume() {
        super.onResume()
        val application = application as WaslApplication
        if (application.reminderNotificationPublisher.canNotify()) {
            application.reminderScheduler.requestRecovery()
        }
    }

    companion object {
        const val ACTION_OPEN_DEBT = "com.wasl.app.action.OPEN_DEBT"
        const val EXTRA_DEBT_ID = "com.wasl.app.extra.DEBT_ID"
    }
}
