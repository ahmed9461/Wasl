package com.wasl.app

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherResourceInstrumentedTest {
    @Test
    fun manifestAndRoundLauncherResourcesResolve() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertNotEquals(0, appInfo.icon)
        assertEquals(R.mipmap.ic_launcher, appInfo.icon)
        assertNotNull(context.getDrawable(appInfo.icon))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
    }

    @Test
    fun adaptiveAndRoundLauncherIconsResolveAsAdaptiveIcons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcher = context.getDrawable(R.mipmap.ic_launcher)
        val round = context.getDrawable(R.mipmap.ic_launcher_round)

        assertTrue(launcher is AdaptiveIconDrawable)
        assertTrue(round is AdaptiveIconDrawable)

        launcher as AdaptiveIconDrawable
        round as AdaptiveIconDrawable
        assertNotNull(launcher.background)
        assertNotNull(launcher.foreground)
        assertNotNull(round.background)
        assertNotNull(round.foreground)
    }

    @Test
    fun themedLauncherIconsExposeMonochromeLayerOnAndroid13Plus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcher = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable

        assertNotNull(launcher.monochrome)
        assertNotNull(round.monochrome)
    }

    @Test
    fun launcherForegroundAndLegacyResourcesArePackaged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_legacy))
    }
}
