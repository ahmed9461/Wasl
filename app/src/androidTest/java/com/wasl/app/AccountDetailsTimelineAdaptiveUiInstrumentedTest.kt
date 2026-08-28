package com.wasl.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDetailsTimelineAdaptiveUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStacksTimelineHeaderMoneyAndMetadataRows() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    Column {
                        PaymentTimelineStatusHeader(isReversed = false)
                        AdaptiveDetailMoneyRow(
                            label = "المبلغ",
                            money = Money(123_450L, CurrencyCode.SAR),
                        )
                        AdaptiveMetadataRow(
                            label = "وقت السداد",
                            value = ltrIsolate("28/08/2026 - 03:20"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("payment-timeline-header-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-detail-money-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("account-metadata-row-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun largeFontStacksDualActionsAndKeepsBothClickable() {
        val firstClicks = mutableStateOf(0)
        val secondClicks = mutableStateOf(0)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    AccountTimelineDualActions(
                        firstLabel = "مشاركة",
                        secondLabel = "فتح PDF",
                        firstTag = "test-first-action",
                        secondTag = "test-second-action",
                        onFirst = { firstClicks.value += 1 },
                        onSecond = { secondClicks.value += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account-timeline-actions-stacked", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("test-first-action", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("test-second-action", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, firstClicks.value)
            assertEquals(1, secondClicks.value)
        }
    }
}
