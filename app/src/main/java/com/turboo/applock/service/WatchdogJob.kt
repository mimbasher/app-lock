package com.turboo.applock.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        LockGuardService.ensureRunning(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 1001

        private val INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(15)

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

            if (scheduler.getPendingJob(JOB_ID) != null) return

            runCatching {
                scheduler.schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJob::class.java))
                        .setPeriodic(INTERVAL_MILLIS)
                        .setPersisted(true)
                        .build()
                )
            }
        }
    }
}
