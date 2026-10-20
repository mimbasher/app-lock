package com.turboo.applock.update

import android.content.Context

/**
 * Play build: there is no self-update check.
 *
 * Play distributes updates itself, and an app that fetches its own APK breaks the
 * Device and Network Abuse policy. Returning null keeps the banner and the view
 * model's launch check inert without either of them knowing which flavour they
 * are in. The real implementation is in src/github.
 */
@Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
object UpdateChecker {
    suspend fun check(context: Context): AvailableUpdate? = null
}
