package com.turboo.applock.data

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory record of which locked apps the user has already authenticated for.
 *
 * Deliberately not persisted: a reboot or a process death should mean everything
 * is locked again. Read from the accessibility service on the main thread, written
 * from the lock screen, so the state is kept in concurrent/volatile fields.
 */
object SessionGate {

    private val unlockedAt = ConcurrentHashMap<String, Long>()

    /** Mirrors [LockRepository.relockDelayMillis]; 0 = re-lock the moment the user leaves. */
    @Volatile
    var relockDelayMillis: Long = 0L

    @Volatile
    private var lastForegroundPackage: String? = null

    /** True while a lock screen is up, so the watcher does not launch a second one. */
    @Volatile
    var lockScreenVisibleFor: String? = null

    fun markUnlocked(packageName: String) {
        unlockedAt[packageName] = SystemClock.elapsedRealtime()
    }

    fun isUnlocked(packageName: String): Boolean {
        val at = unlockedAt[packageName] ?: return false
        val delay = relockDelayMillis
        if (delay > 0L && SystemClock.elapsedRealtime() - at > delay) {
            unlockedAt.remove(packageName)
            return false
        }
        return true
    }

    /**
     * Called for every foreground change. When the user leaves an unlocked app and
     * no grace period is configured, that app's unlock is spent immediately.
     */
    fun onForegroundChanged(packageName: String) {
        val previous = lastForegroundPackage
        if (previous != null && previous != packageName && relockDelayMillis == 0L) {
            unlockedAt.remove(previous)
        }
        lastForegroundPackage = packageName
    }

    /** Screen off, or the watcher stopping: forget every unlock. */
    fun clearAll() {
        unlockedAt.clear()
        lastForegroundPackage = null
    }
}
