package com.wasl.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wasl.app.ui.theme.WaslTheme

class StatisticsActivity : ProtectedWaslActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as WaslApplication
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                WaslTheme {
                    val statisticsViewModel: StatisticsViewModel = viewModel(
                        factory = StatisticsViewModel.Factory(
                            repository = application.repository,
                            promiseStore = application.paymentPromiseStore,
                        ),
                    )
                    val state by statisticsViewModel.uiState.collectAsStateWithLifecycle()
                    StatisticsScreen(
                        state = state,
                        onBack = ::finish,
                        onRetry = statisticsViewModel::retry,
                    )
                }
            }
        }
    }
}
