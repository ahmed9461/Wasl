package com.wasl.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtState
import com.wasl.domain.Money
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDetailsAdaptiveUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStacksHeroBadgesAndFinancialMetrics() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    Column {
                        AccountHeroBadges(
                            receivable = true,
                            state = DebtState.PARTIALLY_PAID,
                        )
                        AccountFinancialMetrics(
                            originalAmount = Money(250_000L, CurrencyCode.SAR),
                            paidAmount = Money(50_000L, CurrencyCode.SAR),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("account-summary-badges-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-summary-metrics-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun largeFontStacksTimelineHeading() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    AccountTimelineHeading()
                }
            }
        }

        composeRule.onNodeWithTag("account-timeline-heading-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
