package com.wasl.app.privacy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockSessionSharingTest {
    @Test
    fun multipleViewModelsShareOneApplicationLockSession() {
        val session = AppLockSession()
        val first = AppLockViewModel(session)
        val second = AppLockViewModel(session)

        first.initialize(enabledFromPreferences = false)
        first.beginAuthentication(AppLockAuthPurpose.ENABLE)
        first.authenticationSucceeded()

        assertTrue(second.enabled)
        assertFalse(second.locked)

        first.onBackground(nowElapsedRealtime = 100L)
        second.onForeground(
            enabledFromPreferences = true,
            timeoutMillis = 0L,
            nowElapsedRealtime = 101L,
        )

        assertTrue(first.locked)
        assertTrue(second.locked)
    }
}
