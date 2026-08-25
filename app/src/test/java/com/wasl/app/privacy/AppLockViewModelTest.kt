package com.wasl.app.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockViewModelTest {
    @Test
    fun enabledColdStartBeginsLockedAndUnlockSuccessOpensSession() {
        val model = AppLockViewModel()

        model.initialize(enabledFromPreferences = true)

        assertTrue(model.enabled)
        assertTrue(model.locked)

        model.beginAuthentication(AppLockAuthPurpose.UNLOCK)
        assertEquals(AppLockAuthPurpose.UNLOCK, model.authenticationPurpose)

        val completed = model.authenticationSucceeded()

        assertEquals(AppLockAuthPurpose.UNLOCK, completed)
        assertFalse(model.locked)
        assertTrue(model.enabled)
    }

    @Test
    fun backgroundGracePeriodOnlyRelocksAfterConfiguredTimeout() {
        val model = unlockedEnabledModel()
        model.onBackground(nowElapsedRealtime = 1_000L)

        model.onForeground(
            enabledFromPreferences = true,
            timeoutMillis = 15_000L,
            nowElapsedRealtime = 10_000L,
        )
        assertFalse(model.locked)

        model.onBackground(nowElapsedRealtime = 20_000L)
        model.onForeground(
            enabledFromPreferences = true,
            timeoutMillis = 15_000L,
            nowElapsedRealtime = 35_000L,
        )

        assertTrue(model.locked)
    }

    @Test
    fun immediateTimeoutRelocksOnNextForeground() {
        val model = unlockedEnabledModel()
        model.onBackground(nowElapsedRealtime = 50L)

        model.onForeground(
            enabledFromPreferences = true,
            timeoutMillis = AppLockTimeout.IMMEDIATELY.durationMillis,
            nowElapsedRealtime = 51L,
        )

        assertTrue(model.locked)
    }

    @Test
    fun enablingRequiresSuccessfulAuthenticationAndErrorDoesNotEnable() {
        val model = AppLockViewModel()
        model.initialize(enabledFromPreferences = false)

        model.beginAuthentication(AppLockAuthPurpose.ENABLE)
        model.authenticationError("فشل التحقق")

        assertFalse(model.enabled)
        assertFalse(model.locked)
        assertEquals("فشل التحقق", model.message)

        model.beginAuthentication(AppLockAuthPurpose.ENABLE)
        val completed = model.authenticationSucceeded()

        assertEquals(AppLockAuthPurpose.ENABLE, completed)
        assertTrue(model.enabled)
        assertFalse(model.locked)
    }

    @Test
    fun disableAndRecoveryNeverDeleteOrLockTheSession() {
        val model = unlockedEnabledModel()
        model.lockNow()
        assertTrue(model.locked)

        model.disable()

        assertFalse(model.enabled)
        assertFalse(model.locked)
        assertEquals(AppLockAuthPurpose.NONE, model.authenticationPurpose)
    }

    @Test
    fun timeoutStoredValuesAreStableAndUnknownValueUsesSafeDefault() {
        assertEquals(
            AppLockTimeout.FIVE_MINUTES,
            AppLockTimeout.fromStoredValue("5_minutes"),
        )
        assertEquals(
            AppLockTimeout.FIFTEEN_SECONDS,
            AppLockTimeout.fromStoredValue("unknown"),
        )
    }

    private fun unlockedEnabledModel(): AppLockViewModel =
        AppLockViewModel().apply {
            initialize(enabledFromPreferences = false)
            beginAuthentication(AppLockAuthPurpose.ENABLE)
            authenticationSucceeded()
        }
}
