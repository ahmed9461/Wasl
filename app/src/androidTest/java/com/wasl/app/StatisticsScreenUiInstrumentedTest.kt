package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.ui.theme.WaslTheme
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsScreenUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statisticsExplainMethodAndShowObjectiveValues() {
        composeRule.setContent {
            WaslTheme {
                StatisticsScreen(
                    state = StatisticsUiState(
                        isLoading = false,
                        statistics = sampleStatistics(),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("objective-statistics-screen").assertIsDisplayed()
        composeRule.onNodeWithText("المسددة").assertIsDisplayed()
        composeRule.onNodeWithText("تم الوفاء بها").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-method-note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("بدون تقييم الأشخاص", substring = true).assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsStatisticsReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    StatisticsScreen(
                        state = StatisticsUiState(
                            isLoading = false,
                            statistics = sampleStatistics(),
                        ),
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("objective-statistics-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics-method-note").performScrollTo().assertIsDisplayed()
    }

    private fun sampleStatistics() = ObjectiveStatistics(
        totalAccounts = 7,
        settledAccounts = 4,
        openAccounts = 3,
        averageSettlementDays = 12.5,
        settledAccountsWithDueDate = 3,
        lateSettledAccounts = 1,
        averageLateDays = 2.0,
        keptPromises = 2,
        missedPromises = 1,
        pendingPromises = 1,
        cancelledPromises = 0,
    )
}
