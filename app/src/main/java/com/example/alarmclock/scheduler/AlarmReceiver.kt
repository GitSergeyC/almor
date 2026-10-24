package com.example.alarmclock.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
 * Точка входа срабатывания. Работает в жёстком лимите ~10 секунд,
 * поэтому делает минимум: берёт WakeLock, открывает событие в журнале
 * и передаёт управление foreground-сервису.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        if (alarmId <= 0) return

        Log.i(TAG, "Срабатывание будильника $alarmId (snooze=$isSnooze)")

        // Удерживаем CPU, пока сервис не встанет на ноги.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmclock:receiver")
        wl.acquire(30_000L)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventId = repository.openEvent(alarmId)
                val start = Intent(context, RingingService::class.java).apply {
                    action = RingingService.ACTION_START
                    putExtra(RingingService.EXTRA_ALARM_ID, alarmId)
                    putExtra(RingingService.EXTRA_EVENT_ID, eventId)
                    putExtra(RingingService.EXTRA_IS_SNOOZE, isSnooze)
                }
                ContextCompat.startForegroundService(context, start)
                // Сразу планируем следующее срабатывание.
                scheduler.rescheduleAll()
            } catch (t: Throwable) {
                Log.e(TAG, "Не удалось запустить звонок", t)
            } finally {
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_FIRE = "com.example.alarmclock.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }
}
