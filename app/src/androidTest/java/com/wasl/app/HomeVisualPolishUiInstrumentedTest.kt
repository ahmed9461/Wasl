package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.ui.theme.WaslTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeVisualPolishUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStacksHeroAndKeepsAccountCountVisible() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                WaslTheme {
                    HomeHeroCard(accountCount = 7)
                }
            }
        }

        composeRule.onNodeWithTag("home-hero").assertIsDisplayed()
        composeRule.onNodeWithTag("home-hero-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("home-hero-account-count")
            .assertIsDisplayed()
            .assertTextEquals("7")
    }

    @Test
    fun largeFontStacksSectionCountWithoutHidingIt() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                WaslTheme {
                    HomeSectionHeader(
                        title = "الحسابات",
                        subtitle = "7 حسابات محفوظة",
                        count = 7,
                        tagPrefix = "visual-test-accounts",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("visual-test-accounts-heading-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("visual-test-accounts-count")
            .assertIsDisplayed()
            .assertTextEquals("7")
    }

    @Test
    fun entryOptionsStayDistinctAndClickableAtLargeFont() {
        var individualClicks = 0
        var groupClicks = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                WaslTheme {
                    androidx.compose.foundation.layout.Column {
                        CreateEntryOption(
                            title = "حساب فردي",
                            description = "دين أو حق مع شخص واحد",
                            primary = true,
                            testTag = "visual-entry-individual",
                            onClick = { individualClicks += 1 },
                        )
                        CreateEntryOption(
                            title = "عملية جماعية",
                            description = "عملية واحدة بحصص موزعة على شخصين أو أكثر",
                            primary = false,
                            testTag = "visual-entry-group",
                            onClick = { groupClicks += 1 },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("visual-entry-individual").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("visual-entry-group").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, individualClicks)
            assertEquals(1, groupClicks)
        }
    }
}
