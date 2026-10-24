package com.example.alarmclock.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.alarmclock.R
import java.time.format.DateTimeFormatter

/** Ненавязчивое постоянное уведомление о ближайшем будильнике. */
object UpcomingNotification {

    const val CHANNEL_ID = "upcoming_alarm"
    private const val NOTIFICATION_ID = 2001
    private val fmt = DateTimeFormatter.ofPattern("EEE, HH:mm")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_upcoming),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, trigger: AlarmTrigger) {
        ensureChannel(context)
        val label = trigger.alarm.alarm.label.ifBlank { "Будильник" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText(trigger.at.format(fmt))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Нет POST_NOTIFICATIONS - не критично для самого будильника.
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
