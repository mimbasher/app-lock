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
import android.os.SystemClock
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

class LockGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private enum class Mode { ACCESSIBILITY, POLLING, DOWN }

    @Volatile
    private var lockedPackages: Set<String> = emptySet()

    private var lastSeenPackage: String? = null
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        startedAt = SystemClock.elapsedRealtime()

        runCatching { startForeground(NOTIFICATION_ID, buildNotification(Mode.ACCESSIBILITY)) }

        val repository = AppLockApplication.repository()
        scope.launch {
            repository.lockedPackages.collect {
                lockedPackages = it
                repository.cachedLockedPackages = it

                if (it.isEmpty()) stopSelf()
            }
        }
        scope.launch { repository.relockDelayMillis.collect { SessionGate.relockDelayMillis = it } }
        scope.launch { heartbeat() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        WatchdogJob.schedule(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        notificationManager().cancel(ALERT_ID)
    }

    private suspend fun heartbeat() {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var lastMode: Mode? = null

        while (scope.isActive) {
            val mode = currentMode()
            if (mode != lastMode) {
                onModeChanged(mode)
                lastMode = mode
            }

            if (mode == Mode.POLLING) {
                pollOnce(usage)
                delay(POLL_INTERVAL_MILLIS)
            } else {
                delay(HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }

    private fun currentMode(): Mode {
        if (AppWatchService.isRunning) return Mode.ACCESSIBILITY

        val binding = Permissions.isAccessibilityEnabled(this) &&
            SystemClock.elapsedRealtime() - startedAt < BIND_GRACE_MILLIS
        if (binding) return Mode.ACCESSIBILITY
        return if (Permissions.hasUsageAccess(this)) Mode.POLLING else Mode.DOWN
    }

    private fun onModeChanged(mode: Mode) {
        SessionGate.clearAll()
        lastSeenPackage = null

        notificationManager().notify(NOTIFICATION_ID, buildNotification(mode))
        if (mode == Mode.DOWN) raiseAlert() else notificationManager().cancel(ALERT_ID)
    }

    private fun pollOnce(usage: UsageStatsManager) {
        val now = System.currentTimeMillis()
        val current = latestForegroundPackage(usage, now - POLL_WINDOW_MILLIS, now)

        if (current != null && current != lastSeenPackage) {
            lastSeenPackage = current
            onForeground(current)
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

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun buildNotification(mode: Mode): Notification {
        ensureChannel(CHANNEL_ID, R.string.channel_name, NotificationManager.IMPORTANCE_LOW)

        val title = when (mode) {
            Mode.ACCESSIBILITY, Mode.POLLING -> R.string.notification_title
            Mode.DOWN -> R.string.notification_title_down
        }
        val text = when (mode) {
            Mode.ACCESSIBILITY -> R.string.notification_text
            Mode.POLLING -> R.string.notification_text_polling
            Mode.DOWN -> R.string.notification_text_down
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(title))
            .setContentText(getString(text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    private fun raiseAlert() {
        ensureChannel(CHANNEL_ALERT_ID, R.string.channel_alert_name, NotificationManager.IMPORTANCE_HIGH)

        val alert = Notification.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(getString(R.string.notification_title_down))
            .setContentText(getString(R.string.notification_text_down))
            .setStyle(
                Notification.BigTextStyle().bigText(getString(R.string.notification_text_down))
            )
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()

        notificationManager().notify(ALERT_ID, alert)
    }

    private fun ensureChannel(id: String, nameRes: Int, importance: Int) {
        val manager = notificationManager()
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                NotificationChannel(id, getString(nameRes), importance)
            )
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "app_watcher"
        private const val CHANNEL_ALERT_ID = "app_watcher_alert"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_ID = 2
        private const val POLL_INTERVAL_MILLIS = 300L
        private const val POLL_WINDOW_MILLIS = 2_000L

        private const val HEARTBEAT_INTERVAL_MILLIS = 5_000L

        private const val BIND_GRACE_MILLIS = 8_000L

        fun ensureRunning(context: Context, force: Boolean = false) {
            WatchdogJob.schedule(context)

            val known = AppLockApplication.repository().cachedLockedPackages
            if (!force && known != null && known.isEmpty()) return

            runCatching {
                context.startForegroundService(Intent(context, LockGuardService::class.java))
            }
        }
    }
}
