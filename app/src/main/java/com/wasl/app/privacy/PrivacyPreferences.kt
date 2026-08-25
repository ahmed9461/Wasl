package com.wasl.app.privacy

import android.content.Context

class PrivacyPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var hideSensitiveNotifications: Boolean
        get() = preferences.getBoolean(KEY_HIDE_SENSITIVE_NOTIFICATIONS, false)
        set(value) {
            preferences.edit().putBoolean(KEY_HIDE_SENSITIVE_NOTIFICATIONS, value).apply()
        }

    var secureScreen: Boolean
        get() = preferences.getBoolean(KEY_SECURE_SCREEN, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SECURE_SCREEN, value).apply()
        }

    companion object {
        private const val PREFERENCES_NAME = "wasl_privacy"
        private const val KEY_HIDE_SENSITIVE_NOTIFICATIONS = "hide_sensitive_notifications"
        private const val KEY_SECURE_SCREEN = "secure_screen"
    }
}
