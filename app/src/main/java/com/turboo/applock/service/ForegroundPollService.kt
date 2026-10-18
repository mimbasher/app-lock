package com.turboo.applock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.turboo.applock.AppLockApplication
import com.turboo.applock.Permissions
import com.turboo.applock.R
import com.turboo.applock.data.SessionGate
import com.turboo.applock.ui.LockScreenActivity
import com.turboo.applock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fallback watcher for when the accessibility service is unavailable — it polls
 * [UsageStatsManager] instead, which needs only the "usage access" toggle.
 *
 * Strictly worse than [AppWatchService]: the locked app is briefly visible before the
 * lock screen lands, and the loop costs battery. Used only when nothing better exists.
 */
class ForegroundPollService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lockedPackages: Set<String> = emptySet()

    private var lastSeenPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        val repository = AppLockApplication.repository()
        scope.launch { repository.lockedPackages.collect { lockedPackages = it } }
        scope.launch { repository.relockDelayMillis.collect { SessionGate.relockDelayMillis = it } }
        scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun pollLoop() {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val current = latestForegroundPackage(usage, now - POLL_WINDOW_MILLIS, now)

            if (current != null && current != lastSeenPackage) {
                lastSeenPackage = current
                onForeground(current)
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun latestForegroundPackage(
        usage: UsageStatsManager,
        from: Long,
        to: Long,
    ): String? {
        val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

        val events = usage.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resumed) latest = event.packageName
        }
        return latest
    }

    private fun onForeground(packageName: String) {
        if (packageName == applicationContext.packageName) return

        SessionGate.onForegroundChanged(packageName)

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

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "app_watcher"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MILLIS = 300L
        private const val POLL_WINDOW_MILLIS = 2_000L

        /** Only worth running when the accessibility route is off but usage access is on. */
        fun startIfNeeded(context: Context) {
            if (AppWatchService.isRunning) return
            if (!Permissions.hasUsageAccess(context)) return
            context.startForegroundService(Intent(context, ForegroundPollService::class.java))
        }
    }
}
