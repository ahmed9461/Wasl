package com.wasl.app.privacy

internal class AppForegroundTracker(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) {
    private var startedActivities = 0

    fun activityStarted() {
        val enteringForeground = startedActivities == 0
        startedActivities += 1
        if (enteringForeground) onForeground()
    }

    fun activityStopped(isChangingConfigurations: Boolean) {
        if (startedActivities == 0) return
        startedActivities -= 1
        if (startedActivities == 0 && !isChangingConfigurations) {
            onBackground()
        }
    }
}
