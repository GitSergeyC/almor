package com.example.alarmclock.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarmclock.data.repo.AlarmRepository
import com.example.alarmclock.ringing.RingingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Перезагрузка, обновление приложения, смена времени/часового пояса.
 *
 * Здесь же живёт ключевая защита: если в журнале есть НЕЗАКРЫТОЕ событие
 * за последние 30 минут, значит устройство было перезагружено во время
 * звонка (единственный способ обойти защиту). Поднимаем звонок заново
 * и ужесточаем задание.
 */
@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var repository: AlarmRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Системное событие: $action")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                    action == Intent.ACTION_LOCKED_BOOT_COMPLETED

                if (isBoot) {
                    restoreInterruptedAlarm(context)
                }
                scheduler.rescheduleAll()
                RescheduleWorker.enqueuePeriodic(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Ошибка обработки системного события", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun restoreInterruptedAlarm(context: Context) {
        val window = System.currentTimeMillis() - RESTORE_WINDOW_MS
        val unfinished = repository.unfinishedEventsSince(window)
        val event = unfinished.firstOrNull() ?: run {
            // Старые незакрытые события просто закрываем, чтобы не копились.
            repository.closeAllStaleEvents()
            return
        }

        Log.w(TAG, "Обнаружена перезагрузка во время звонка, событие ${event.id}")
        repository.updateEvent(event.copy(rebootDetected = true, escalated = true))

        val restart = Intent(context, RingingService::class.java).apply {
            this.action = RingingService.ACTION_START
            putExtra(RingingService.EXTRA_ALARM_ID, event.alarmId)
            putExtra(RingingService.EXTRA_EVENT_ID, event.id)
            putExtra(RingingService.EXTRA_ESCALATED, true)
        }
        ContextCompat.startForegroundService(context, restart)
    }

    companion object {
        private const val TAG = "SystemEventReceiver"
        /** Окно, в котором перезагрузка считается попыткой обойти будильник. */
        private const val RESTORE_WINDOW_MS = 30 * 60 * 1000L
    }
}
