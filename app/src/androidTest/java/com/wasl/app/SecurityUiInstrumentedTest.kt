package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.privacy.AppLockTimeout
import com.wasl.app.ui.theme.WaslTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun securityHubSupportsLargeFontDarkThemeAndAccessibleTimeoutRows() {
        var selectedTimeout = AppLockTimeout.FIFTEEN_SECONDS
        var enabledChange: Boolean? = null

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 2f,
                ),
            ) {
                WaslTheme(darkTheme = true) {
                    SecurityHubRoute(
                        appLockEnabled = true,
                        appLockTimeout = selectedTimeout,
                        authenticationAvailable = true,
                        statusMessage = null,
                        onBack = {},
                        onAppLockEnabledChange = { enabledChange = it },
                        onAppLockTimeoutChange = { selectedTimeout = it },
                        onLockNow = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("الأمان وقفل وَصل").assertIsDisplayed()
        scrollToTag("app-lock-timeout-5_minutes")
        composeRule.onNodeWithTag("app-lock-timeout-5_minutes").performClick()
        composeRule.runOnIdle {
            assertEquals(AppLockTimeout.FIVE_MINUTES, selectedTimeout)
        }

        scrollToTag("app-lock-enabled")
        composeRule.onNodeWithTag("app-lock-enabled").performClick()
        composeRule.runOnIdle {
            assertEquals(false, enabledChange)
        }
    }

    @Test
    fun lockedScreenDoesNotOfferUnlockWhenSystemAuthenticationIsUnavailable() {
        var recoveryRequests = 0

        composeRule.setContent {
            WaslTheme(darkTheme = false) {
                AppLockScreen(
                    authenticationAvailable = false,
                    message = "تعذر استخدام المصادقة.",
                    onUnlock = {},
                    onDisableUnavailableLock = { recoveryRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("app-lock-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("app-lock-unlock").assertDoesNotExist()
        composeRule.onNodeWithTag("app-lock-recovery-disable").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, recoveryRequests)
        }
    }

    @Test
    fun securityEntryButtonExposesAVisibleArabicAction() {
        var clicks = 0

        composeRule.setContent {
            WaslTheme {
                SecuritySettingsEntryButton(onClick = { clicks += 1 })
            }
        }

        composeRule.onNodeWithTag("open-security-hub").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }
}
