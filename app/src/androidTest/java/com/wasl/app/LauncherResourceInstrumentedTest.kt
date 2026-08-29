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
    fun manifestLauncherIconsResolveToInstalledResources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertNotEquals(0, appInfo.icon)
        assertEquals(R.mipmap.ic_launcher, appInfo.icon)
        assertNotNull(context.getDrawable(appInfo.icon))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            assertNotEquals(0, appInfo.roundIcon)
            assertEquals(R.mipmap.ic_launcher_round, appInfo.roundIcon)
            assertNotNull(context.getDrawable(appInfo.roundIcon))
        }
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
    fun launcherForegroundAndReferenceArtworkArePackaged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_legacy))

        val artId = context.resources.getIdentifier(
            "wasl_launcher_art",
            "drawable",
            context.packageName,
        )
        assertNotEquals(0, artId)
        assertNotNull(context.getDrawable(artId))
    }
}
