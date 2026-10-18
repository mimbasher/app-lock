package com.turboo.applock

import android.app.Application
import com.turboo.applock.data.LockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppLockApplication : Application() {

    val repository: LockRepository by lazy { LockRepository(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Keep the theme choice warm for the whole process, so the lock screen can
        // colour itself synchronously instead of flashing the wrong scheme.
        scope.launch {
            repository.themeMode.collect { repository.cachedThemeMode = it }
        }
    }

    companion object {
        lateinit var instance: AppLockApplication
            private set

        fun repository(): LockRepository = instance.repository
    }
}
