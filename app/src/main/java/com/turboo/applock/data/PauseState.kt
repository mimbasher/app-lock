package com.turboo.applock.data

import android.os.SystemClock

/**
 * How long locking is suspended for, after the Quick Settings tile has authenticated.
 *
 * Deliberately not persisted, for the same reason as [SessionGate]: if the process
 * dies while locking is paused, it comes back locking. The failure mode of forgetting
 * a pause is an unexpected prompt; the failure mode of remembering one across a kill
 * is a locked app opening freely, which is the thing this app exists to prevent.
 *
 * Written from the tile and the pause screen, read from both watchers, so the
 * deadline is volatile.
 */
object PauseState {

    const val PAUSE_MINUTES = 15

    private const val PAUSE_MILLIS = PAUSE_MINUTES * 60 * 1000L

    @Volatile
    private var pausedUntilElapsed = 0L

    val isPaused: Boolean
        get() = remainingMillis > 0L

    val remainingMillis: Long
        get() = (pausedUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Rounded up, so a pause with 30 seconds left still reads "1 min" rather than "0". */
    val remainingMinutes: Int
        get() = ((remainingMillis + 59_999L) / 60_000L).toInt()

    fun pause() {
        pausedUntilElapsed = SystemClock.elapsedRealtime() + PAUSE_MILLIS
    }

    fun resume() {
        pausedUntilElapsed = 0L
    }
}
