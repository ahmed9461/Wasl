package com.wasl.app.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AppLockSession {
    private var initialized = false
    private var authenticated = false
    private var backgroundedAtElapsedRealtime: Long? = null

    var enabled by mutableStateOf(false)
        private set

    var locked by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var authenticationPurpose: AppLockAuthPurpose = AppLockAuthPurpose.NONE
        private set

    val authenticationInProgress: Boolean
        get() = authenticationPurpose != AppLockAuthPurpose.NONE

    fun initialize(enabledFromPreferences: Boolean) {
        if (initialized) return
        initialized = true
        enabled = enabledFromPreferences
        authenticated = !enabledFromPreferences
        locked = enabledFromPreferences
    }

    fun onForeground(
        enabledFromPreferences: Boolean,
        timeoutMillis: Long,
        nowElapsedRealtime: Long,
    ) {
        if (!initialized) initialize(enabledFromPreferences)

        if (enabled != enabledFromPreferences) {
            enabled = enabledFromPreferences
            authenticated = !enabledFromPreferences
            locked = enabledFromPreferences
            backgroundedAtElapsedRealtime = null
        }

        if (!enabled) {
            authenticated = true
            locked = false
            backgroundedAtElapsedRealtime = null
            return
        }

        val backgroundedAt = backgroundedAtElapsedRealtime
        if (!authenticated) {
            locked = true
        } else if (
            backgroundedAt != null &&
            nowElapsedRealtime - backgroundedAt >= timeoutMillis.coerceAtLeast(0L)
        ) {
            authenticated = false
            locked = true
        } else {
            locked = false
        }
        backgroundedAtElapsedRealtime = null
    }

    fun onBackground(nowElapsedRealtime: Long) {
        if (enabled && authenticated) {
            backgroundedAtElapsedRealtime = nowElapsedRealtime
        }
    }

    fun beginAuthentication(purpose: AppLockAuthPurpose) {
        require(purpose != AppLockAuthPurpose.NONE)
        authenticationPurpose = purpose
        message = null
    }

    fun authenticationSucceeded(): AppLockAuthPurpose {
        val completedPurpose = authenticationPurpose
        authenticationPurpose = AppLockAuthPurpose.NONE
        if (completedPurpose == AppLockAuthPurpose.ENABLE) {
            enabled = true
        }
        authenticated = true
        locked = false
        backgroundedAtElapsedRealtime = null
        message = null
        return completedPurpose
    }

    fun authenticationError(message: String) {
        authenticationPurpose = AppLockAuthPurpose.NONE
        this.message = message
        if (enabled && !authenticated) {
            locked = true
        }
    }

    fun disable() {
        enabled = false
        authenticated = true
        locked = false
        authenticationPurpose = AppLockAuthPurpose.NONE
        backgroundedAtElapsedRealtime = null
        message = null
    }

    fun lockNow() {
        if (!enabled) return
        authenticated = false
        locked = true
        backgroundedAtElapsedRealtime = null
        message = null
        authenticationPurpose = AppLockAuthPurpose.NONE
    }

    fun clearMessage() {
        message = null
    }
}
