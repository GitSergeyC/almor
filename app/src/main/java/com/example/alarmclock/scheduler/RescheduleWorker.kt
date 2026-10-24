package com.example.alarmclock.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Страховка от агрессивных прошивок (Xiaomi, Huawei, Oppo и т.п.),
 * которые могут вычистить зарегистрированный AlarmManager при убийстве
 * процесса. Раз в 30 минут просто перепланируем ближайший будильник.
 *
 * Не заменяет AlarmManager, а дублирует его.
 */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: AlarmScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            scheduler.rescheduleAll()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "alarm_reschedule_guard"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RescheduleWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
