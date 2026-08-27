package com.wasl.app.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

internal enum class AppLockAuthPurpose {
    NONE,
    UNLOCK,
    ENABLE,
}

internal class AppLockViewModel(
    private val session: AppLockSession = AppLockSession(),
) : ViewModel() {
    val enabled: Boolean
        get() = session.enabled

    val locked: Boolean
        get() = session.locked

    val message: String?
        get() = session.message

    val authenticationPurpose: AppLockAuthPurpose
        get() = session.authenticationPurpose

    val authenticationInProgress: Boolean
        get() = session.authenticationInProgress

    fun initialize(enabledFromPreferences: Boolean) =
        session.initialize(enabledFromPreferences)

    fun onForeground(
        enabledFromPreferences: Boolean,
        timeoutMillis: Long,
        nowElapsedRealtime: Long,
    ) = session.onForeground(
        enabledFromPreferences = enabledFromPreferences,
        timeoutMillis = timeoutMillis,
        nowElapsedRealtime = nowElapsedRealtime,
    )

    fun onBackground(nowElapsedRealtime: Long) =
        session.onBackground(nowElapsedRealtime)

    fun beginAuthentication(purpose: AppLockAuthPurpose) =
        session.beginAuthentication(purpose)

    fun authenticationSucceeded(): AppLockAuthPurpose =
        session.authenticationSucceeded()

    fun authenticationError(message: String) =
        session.authenticationError(message)

    fun disable() = session.disable()

    fun lockNow() = session.lockNow()

    fun clearMessage() = session.clearMessage()

    class Factory(
        private val session: AppLockSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppLockViewModel::class.java)) {
                "Unsupported ViewModel: ${modelClass.name}"
            }
            return AppLockViewModel(session) as T
        }
    }
}
