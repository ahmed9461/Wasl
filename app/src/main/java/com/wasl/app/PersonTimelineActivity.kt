package com.wasl.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.DebtId
import com.wasl.domain.PersonId

class PersonTimelineActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val personIdValue = intent.getStringExtra(EXTRA_PERSON_ID)
        if (personIdValue.isNullOrBlank()) {
            finish()
            return
        }
        val application = application as WaslApplication
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                WaslTheme {
                    val timelineViewModel: PersonTimelineViewModel = viewModel(
                        factory = PersonTimelineViewModel.Factory(
                            personId = PersonId(personIdValue),
                            repository = application.repository,
                            promiseStore = application.paymentPromiseStore,
                            claimStore = application.paymentClaimStore,
                            attachmentStore = application.attachmentStore,
                        ),
                    )
                    val state by timelineViewModel.uiState.collectAsStateWithLifecycle()
                    PersonTimelineScreen(
                        state = state,
                        onBack = ::finish,
                        onRetry = timelineViewModel::retryLoad,
                        onOpenAccount = ::openAccount,
                    )
                }
            }
        }
    }

    private fun openAccount(debtId: DebtId) {
        startActivity(
            android.content.Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DEBT)
                .putExtra(MainActivity.EXTRA_DEBT_ID, debtId.value),
        )
    }

    companion object {
        const val EXTRA_PERSON_ID = "com.wasl.app.extra.PERSON_ID"
    }
}
