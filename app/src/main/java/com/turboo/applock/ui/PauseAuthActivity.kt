package com.turboo.applock.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.turboo.applock.AppLockApplication
import com.turboo.applock.R
import com.turboo.applock.data.PauseState
import com.turboo.applock.service.LockGuardService
import com.turboo.applock.service.LockTileService

/**
 * Proves who is holding the phone before the Quick Settings tile is allowed to
 * suspend locking.
 *
 * Separate from [LockScreenActivity] on purpose: that one is `singleInstance`, so
 * routing a pause through it would have a tile tap and a live gate arriving at the
 * same instance, each able to overwrite what the other is asking for.
 */
class PauseAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        setContent {
            LockScreen(
                appLabel = getString(R.string.pause_title),
                appIcon = null,
                themeMode = AppLockApplication.repository().cachedThemeMode,
                statusText = resources.getQuantityString(
                    R.plurals.pause_status,
                    PauseState.PAUSE_MINUTES,
                    PauseState.PAUSE_MINUTES,
                ),
                actionText = getString(R.string.pause_confirm),
                onUnlockClick = ::promptForAuth,
            )
        }

        promptForAuth()
    }

    override fun onStop() {
        super.onStop()
        // Nothing is being protected by this screen, so there is no reason to leave
        // it sitting behind whatever the user went to instead.
        if (!isFinishing) finish()
    }

    private fun promptForAuth() {
        promptForUnlock(
            title = getString(R.string.pause_title),
            subtitle = getString(R.string.pause_subtitle),
            onSuccess = ::pauseAndDismiss,
            onCancelled = ::finish,
            // No biometric and no screen lock: the gate itself opens in that case
            // too, so refusing here would be the one place the app is stricter
            // than the lock it defers to.
            onUnavailable = ::pauseAndDismiss,
        )
    }

    private fun pauseAndDismiss() {
        PauseState.pause()
        LockGuardService.refresh(applicationContext)
        LockTileService.requestUpdate(applicationContext)
        finish()
        overridePendingTransitionCompat()
    }

    @Suppress("DEPRECATION")
    private fun overridePendingTransitionCompat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0)
        }
    }
}
