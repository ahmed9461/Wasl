package com.wasl.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.ui.theme.WaslTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentsAdaptiveUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStacksHeaderAndDualActionsWithoutLosingClicks() {
        var primaryClicks = 0
        var secondaryClicks = 0

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DocumentsHubHeader(
                            isAccountScoped = true,
                            onBack = {},
                        )
                        DocumentDualActionButtons(
                            testTagPrefix = "documents-test",
                            primaryLabel = "فتح PDF",
                            secondaryLabel = "مشاركة",
                            primaryFilled = true,
                            onPrimary = { primaryClicks += 1 },
                            onSecondary = { secondaryClicks += 1 },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("documents-header-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("documents-test-actions-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("documents-test-primary").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("documents-test-secondary").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, primaryClicks)
            assertEquals(1, secondaryClicks)
        }
    }
}
