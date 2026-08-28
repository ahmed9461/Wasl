package com.wasl.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity

abstract class ProtectedWaslActivity : FragmentActivity() {
    private val waslApplication: WaslApplication
        get() = application as WaslApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecureScreenPreference()
        redirectToUnlockIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        applySecureScreenPreference()
        redirectToUnlockIfNeeded()
    }

    private fun redirectToUnlockIfNeeded() {
        if (!waslApplication.appLockSession.locked || isFinishing) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
        finish()
    }

    private fun applySecureScreenPreference() {
        val preferences = waslApplication.privacyPreferences
        if (preferences.secureScreen || preferences.appLockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
