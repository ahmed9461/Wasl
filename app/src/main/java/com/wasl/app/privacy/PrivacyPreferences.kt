package com.wasl.app.privacy

import android.content.Context

enum class AppLockTimeout(
    val storedValue: String,
    val durationMillis: Long,
    val label: String,
) {
    IMMEDIATELY("immediately", 0L, "فورًا"),
    FIFTEEN_SECONDS("15_seconds", 15_000L, "بعد 15 ثانية"),
    ONE_MINUTE("1_minute", 60_000L, "بعد دقيقة"),
    FIVE_MINUTES("5_minutes", 5 * 60_000L, "بعد 5 دقائق");

    companion object {
        fun fromStoredValue(value: String?): AppLockTimeout =
            entries.firstOrNull { it.storedValue == value } ?: FIFTEEN_SECONDS
    }
}

enum class AppAppearance(
    val storedValue: String,
    val label: String,
) {
    SYSTEM("system", "تلقائي"),
    DARK("dark", "داكن"),
    LIGHT("light", "فاتح");

    companion object {
        fun fromStoredValue(value: String?): AppAppearance =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

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

    var appLockEnabled: Boolean
        get() = preferences.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()
        }

    var appLockTimeout: AppLockTimeout
        get() = AppLockTimeout.fromStoredValue(
            preferences.getString(KEY_APP_LOCK_TIMEOUT, null),
        )
        set(value) {
            preferences.edit().putString(KEY_APP_LOCK_TIMEOUT, value.storedValue).apply()
        }

    var appearance: AppAppearance
        get() = AppAppearance.fromStoredValue(preferences.getString(KEY_APPEARANCE, null))
        set(value) {
            preferences.edit().putString(KEY_APPEARANCE, value.storedValue).apply()
        }

    companion object {
        private const val PREFERENCES_NAME = "wasl_privacy"
        private const val KEY_HIDE_SENSITIVE_NOTIFICATIONS = "hide_sensitive_notifications"
        private const val KEY_SECURE_SCREEN = "secure_screen"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
        private const val KEY_APPEARANCE = "appearance"
    }
}