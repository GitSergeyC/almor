package com.example.alarmclock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.alarmclock.scheduler.AlarmScheduler
import com.example.alarmclock.scheduler.RescheduleWorker
import com.example.alarmclock.scheduler.UpcomingNotification
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AlarmApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: AlarmScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        UpcomingNotification.ensureChannel(this)
        RescheduleWorker.enqueuePeriodic(this)
        // Синхронизируем расписание на старте: страховка на случай,
        // если BOOT_COMPLETED был убит прошивкой.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { scheduler.rescheduleAll() }
        }
    }
}
