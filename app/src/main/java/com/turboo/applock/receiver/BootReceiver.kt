package com.turboo.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.turboo.applock.service.ForegroundPollService

/**
 * The accessibility service is restarted by the system on its own after a reboot, so
 * there is nothing to do for it here. The polling fallback is ours to restart.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ForegroundPollService.startIfNeeded(context)
        }
    }
}
