package com.wasl.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.backup.BackupCreated
import com.wasl.app.backup.BackupRestored
import com.wasl.app.backup.BackupService
import com.wasl.app.privacy.PrivacyPreferences
import com.wasl.app.ui.theme.WaslTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var privacyPreferences: PrivacyPreferences

    @BeforeTest
    fun setUp() {
        privacyPreferences = PrivacyPreferences(context)
        privacyPreferences.hideSensitiveNotifications = false
        privacyPreferences.secureScreen = false
    }

    @AfterTest
    fun tearDown() {
        privacyPreferences.hideSensitiveNotifications = false
        privacyPreferences.secureScreen = false
    }

    @Test
    fun largeFontStacksSettingsHeaderAndPrivacyControls() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    SettingsHubRoute(
                        backupService = unusedBackupService,
                        privacyPreferences = privacyPreferences,
                        onBack = {},
                        onOpenDocuments = {},
                        onOpenStatistics = {},
                        onRestored = {},
                        onSecureScreenChanged = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-header-stacked").assertIsDisplayed()
        composeRule.onNodeWithTag("privacy-hide-notification-details-row-stacked")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("privacy-hide-notification-details")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(privacyPreferences.hideSensitiveNotifications)
        }
        composeRule.onNodeWithTag("privacy-secure-screen-row-stacked")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("privacy-secure-screen").assertIsDisplayed()
    }

    private val unusedBackupService = object : BackupService {
        override suspend fun create(password: CharArray): BackupCreated = error("Not used")

        override suspend fun restore(
            backupBytes: ByteArray,
            password: CharArray,
        ): BackupRestored = error("Not used")
    }
}
