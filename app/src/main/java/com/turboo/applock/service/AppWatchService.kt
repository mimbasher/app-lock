package com.turboo.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.turboo.applock.AppLockApplication
import com.turboo.applock.data.PauseState
import com.turboo.applock.data.SessionGate
import com.turboo.applock.ui.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppWatchService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val selfPackage: String by lazy { applicationContext.packageName }

    @Volatile
    private var lockedPackages: Set<String> = emptySet()

    @Volatile
    private var launchablePackages: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                SessionGate.clearAll()
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scope.launch { refreshLaunchablePackages() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        LockGuardService.ensureRunning(this)

        val repository = AppLockApplication.repository()
        scope.launch {
            repository.lockedPackages.collect { lockedPackages = it }
        }
        scope.launch {
            repository.relockDelayMillis.collect { SessionGate.relockDelayMillis = it }
        }
        scope.launch { refreshLaunchablePackages() }

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == selfPackage) return
        if (packageName !in launchablePackages && packageName !in lockedPackages) return

        SessionGate.onForegroundChanged(packageName)

        // Paused from the Quick Settings tile. Foreground changes are still followed
        // so the session rules stay honest, but nothing is gated until it expires.
        if (PauseState.isPaused) return

        if (packageName !in lockedPackages) {
            SessionGate.lockScreenVisibleFor = null
            return
        }
        if (SessionGate.isUnlocked(packageName)) return
        if (SessionGate.lockScreenVisibleFor == packageName) return

        SessionGate.lockScreenVisibleFor = packageName
        startActivity(
            Intent(this, LockScreenActivity::class.java).apply {
                putExtra(LockScreenActivity.EXTRA_PACKAGE, packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        SessionGate.clearAll()

        LockGuardService.ensureRunning(applicationContext)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        scope.cancel()
        return super.onUnbind(intent)
    }

    private suspend fun refreshLaunchablePackages() {
        launchablePackages = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
