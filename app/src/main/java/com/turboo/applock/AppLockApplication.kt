package com.turboo.applock

import android.app.Application
import com.turboo.applock.data.LockRepository

class AppLockApplication : Application() {

    val repository: LockRepository by lazy { LockRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AppLockApplication
            private set

        fun repository(): LockRepository = instance.repository
    }
}
