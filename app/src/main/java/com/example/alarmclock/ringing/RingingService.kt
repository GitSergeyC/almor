package com.example.alarmclock.ringing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.alarmclock.R
import com.example.alarmclock.data.repo.AlarmRepository
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.scheduler.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Владелец сессии звонка. Живёт независимо от Activity: если пользователь
 * убьёт экран задания, сервис продолжит звонить и поднимет его заново.
 *
 * Состояние сессии выставлено в статическом StateFlow, чтобы Activity
 * могла пересоздаваться сколько угодно раз без потери контекста.
 */
@AndroidEntryPoint
class RingingService : Service() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var player: AlarmPlayer
    @Inject lateinit var scheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var watchdog: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_SNOOZE -> handleSnooze()
            ACTION_DISMISS -> handleDismiss()
            ACTION_STOP_SOUND_ONLY -> player.stop()
        }
        // START_REDELIVER_INTENT: если систему прибьёт, звонок восстановится.
        return START_REDELIVER_INTENT
    }

    private fun handleStart(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val escalated = intent.getBooleanExtra(EXTRA_ESCALATED, false)
        if (alarmId <= 0) { stopSelf(); return }

        startForeground(NOTIFICATION_ID, buildNotification("Будильник"))
        acquireWakeLock()

        scope.launch {
            val awg = repository.getAlarm(alarmId)
            if (awg == null) { stopSelf(); return@launch }

            val alarm = awg.alarm
            _session.value = RingingSession(
                alarmId = alarmId,
                eventId = eventId,
                alarm = alarm,
                escalated = escalated,
                // После перезагрузки задание становится строже:
                // больше повторов и минимум средняя сложность.
                effectiveTaskCount = if (escalated) alarm.taskCount + 1 else alarm.taskCount
            )

            startForeground(NOTIFICATION_ID, buildNotification(alarm.label.ifBlank { "Будильник" }))
            player.start(alarm)
            launchRingingActivity()
            startWatchdog()
        }
    }

    /**
     * Сторож: раз в 2 секунды проверяет, что экран задания на переднем плане.
     * Если Activity убили свайпом или через "недавние" — поднимаем заново.
     */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (true) {
                delay(2000)
                val s = _session.value ?: break
                if (s.guardActive && !RingingActivity.isVisible) {
                    Log.w(TAG, "Экран задания пропал, поднимаю заново")
                    launchRingingActivity()
                }
            }
        }
    }

    private fun launchRingingActivity() {
        val intent = Intent(this, RingingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        startActivity(intent)
    }

    private fun handleSnooze() {
        val s = _session.value ?: return
        scope.launch {
            val event = repository.getEvent(s.eventId)
            if (event != null) {
                repository.updateEvent(event.copy(snoozeCount = event.snoozeCount + 1))
            }
            player.stop()
            scheduler.scheduleSnooze(s.alarmId, s.alarm.snoozeMinutes)
            finishSession()
        }
    }

    private fun handleDismiss() {
        val s = _session.value ?: return
        scope.launch {
            repository.closeEvent(s.eventId)
            player.stop()
            scheduler.rescheduleAll()
            finishSession()
        }
    }

    private fun finishSession() {
        watchdog?.cancel()
        _session.value = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "alarmclock:ringing"
        ).also { it.acquire(15 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_ringing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null) // звук ведёт AlarmPlayer, не уведомление
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String): android.app.Notification {
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Нажмите, чтобы выключить")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true) // ключ к экрану поверх блокировки
            .build()
    }

    override fun onDestroy() {
        player.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingingService"
        const val CHANNEL_ID = "ringing_alarm"
        private const val NOTIFICATION_ID = 3001

        const val ACTION_START = "com.example.alarmclock.RING_START"
        const val ACTION_SNOOZE = "com.example.alarmclock.RING_SNOOZE"
        const val ACTION_DISMISS = "com.example.alarmclock.RING_DISMISS"
        const val ACTION_STOP_SOUND_ONLY = "com.example.alarmclock.RING_MUTE"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_ESCALATED = "escalated"

        private val _session = MutableStateFlow<RingingSession?>(null)
        val session: StateFlow<RingingSession?> = _session.asStateFlow()

        fun markGuardActive(active: Boolean) {
            _session.value = _session.value?.copy(guardActive = active)
        }
    }
}

data class RingingSession(
    val alarmId: Long,
    val eventId: Long,
    val alarm: Alarm,
    val escalated: Boolean = false,
    val effectiveTaskCount: Int = 1,
    val guardActive: Boolean = true
)
