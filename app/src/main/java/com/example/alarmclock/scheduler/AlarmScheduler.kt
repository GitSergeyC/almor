package com.example.alarmclock.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarmclock.data.db.AlarmDao
import com.example.alarmclock.data.db.toDomain
import com.example.alarmclock.domain.AlarmWithGroup
import com.example.alarmclock.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class AlarmTrigger(
    val alarm: AlarmWithGroup,
    val at: ZonedDateTime
)

/**
 * Регистрирует в системе ТОЛЬКО ближайший будильник, а не все сразу.
 * После срабатывания расписание пересчитывается заново. Это экономит
 * системные слоты и делает поведение предсказуемым при отладке.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Пересчитать всё расписание с нуля. Безопасно вызывать многократно. */
    suspend fun rescheduleAll() {
        val next = computeNext()
        if (next == null) {
            cancelPending()
            UpcomingNotification.hide(context)
            Log.i(TAG, "Активных будильников нет, расписание очищено")
            return
        }
        schedule(next)
        UpcomingNotification.show(context, next)
    }

    /** Ближайшее срабатывание среди всех включённых будильников. */
    suspend fun computeNext(now: ZonedDateTime = ZonedDateTime.now()): AlarmTrigger? {
        return alarmDao.getAll()
            .map { it.toDomain() }
            .mapNotNull { awg ->
                NextTriggerCalculator
                    .next(awg.alarm, awg.effectivelyEnabled, now)
                    ?.let { AlarmTrigger(awg, it) }
            }
            .minByOrNull { it.at }
    }

    private fun schedule(trigger: AlarmTrigger) {
        val triggerMillis = trigger.at.toInstant().toEpochMilli()
        val operation = firePendingIntent(trigger.alarm.alarm.id, triggerMillis)

        if (!canScheduleExact()) {
            // Деградируем, но не молча: пользователь увидит предупреждение в UI.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
            Log.w(TAG, "Нет права на точные будильники, поставлен неточный")
            return
        }

        // setAlarmClock - единственный API, который не откладывается в Doze
        // и показывает системную иконку будильника в статус-баре.
        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
            operation
        )
        Log.i(TAG, "Запланирован будильник ${trigger.alarm.alarm.id} на ${trigger.at}")
    }

    private fun cancelPending() {
        // FLAG_NO_CREATE: если PendingIntent не существует, вернётся null
        // и отменять нечего. Создавать его ради отмены бессмысленно.
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
        }
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /**
     * PendingIntent с фиксированным requestCode: так его всегда можно
     * перезаписать или отменить, не зная предыдущего состояния.
     */
    private fun firePendingIntent(alarmId: Long, triggerMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerMillis)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    /** Отложить текущий будильник на N минут. */
    fun scheduleSnooze(alarmId: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZE, true)
        }
        val pi = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE = 1001
        private const val SNOOZE_REQUEST_CODE = 1002
    }
}
