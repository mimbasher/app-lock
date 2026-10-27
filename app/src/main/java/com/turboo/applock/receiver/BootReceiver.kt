package com.turboo.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.turboo.applock.service.LockGuardService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> LockGuardService.ensureRunning(context)
        }
    }
}
