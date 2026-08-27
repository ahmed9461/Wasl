package com.wasl.app.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class AppForegroundTrackerTest {
    @Test
    fun internalActivityTransitionDoesNotCountAsLeavingTheApp() {
        var foregroundCalls = 0
        var backgroundCalls = 0
        val tracker = AppForegroundTracker(
            onForeground = { foregroundCalls += 1 },
            onBackground = { backgroundCalls += 1 },
        )

        tracker.activityStarted()
        tracker.activityStarted()
        tracker.activityStopped(isChangingConfigurations = false)

        assertEquals(1, foregroundCalls)
        assertEquals(0, backgroundCalls)

        tracker.activityStopped(isChangingConfigurations = false)
        assertEquals(1, backgroundCalls)
    }

    @Test
    fun configurationChangeDoesNotStartTheBackgroundTimeout() {
        var backgroundCalls = 0
        val tracker = AppForegroundTracker(
            onForeground = {},
            onBackground = { backgroundCalls += 1 },
        )

        tracker.activityStarted()
        tracker.activityStopped(isChangingConfigurations = true)
        tracker.activityStarted()

        assertEquals(0, backgroundCalls)
    }
}
