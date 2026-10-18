package com.turboo.applock.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.turboo.applock.data.InstalledApps
import com.turboo.applock.AppLockApplication
import com.turboo.applock.data.SessionGate

/**
 * Full-screen gate that covers a locked app until the user proves who they are.
 *
 * It asks for a fingerprint/face *or* the phone's own PIN, pattern or password in a
 * single prompt — the system handles both, so this app never sees or stores a secret.
 */
class LockScreenActivity : FragmentActivity() {

    private lateinit var targetPackage: String
    private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (targetPackage.isEmpty()) {
            finish()
            return
        }

        // Keep the locked app's name out of screenshots and the recents preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = denyAndLeave()
        })

        val label = InstalledApps.labelFor(this, targetPackage)
        val icon = runCatching {
            packageManager.getApplicationIcon(targetPackage).toBitmap(144, 144)
        }.getOrNull()

        setContent {
            LockScreen(
                appLabel = label,
                appIcon = icon,
                // Read from the warm cache: this screen must paint immediately.
                themeMode = AppLockApplication.repository().cachedThemeMode,
                onUnlockClick = ::promptForAuth,
            )
        }

        promptForAuth()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleInstance: a second lock request reuses this instance.
        intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() }?.let { targetPackage = it }
    }

    override fun onStop() {
        super.onStop()
        // Never leave an un-authenticated lock screen lying around over a locked app.
        if (!authenticated && !isFinishing) denyAndLeave()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (SessionGate.lockScreenVisibleFor == targetPackage) {
            SessionGate.lockScreenVisibleFor = null
        }
    }

    private fun promptForAuth() {
        promptForUnlock(
            title = getString(
                com.turboo.applock.R.string.unlock_title,
                InstalledApps.labelFor(this, targetPackage),
            ),
            subtitle = getString(com.turboo.applock.R.string.unlock_subtitle),
            onSuccess = ::unlockAndDismiss,
            onCancelled = ::denyAndLeave,
            onUnavailable = ::unlockAndDismiss,
        )
    }

    private fun unlockAndDismiss() {
        authenticated = true
        SessionGate.markUnlocked(targetPackage)
        SessionGate.lockScreenVisibleFor = null
        finish()
        overridePendingTransitionCompat()
    }

    /** Send the user home so the locked app is never left exposed behind us. */
    private fun denyAndLeave() {
        SessionGate.lockScreenVisibleFor = null
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
        overridePendingTransitionCompat()
    }

    @Suppress("DEPRECATION")
    private fun overridePendingTransitionCompat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
    }
}
