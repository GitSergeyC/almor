package com.example.alarmclock.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.alarmclock.ringing.RingingActivity
import com.example.alarmclock.ringing.RingingService

/**
 * Короткое нажатие кнопки питания гасит экран. Ловим это и включаем обратно.
 * Работает только на уровне MAXIMUM и только во время активной сессии.
 */
class ScreenOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        if (RingingService.session.value == null) return

        Log.i(TAG, "Экран погашен во время звонка, включаю обратно")

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "alarmclock:screenon"
        )
        wl.acquire(5000L)

        val relaunch = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        runCatching { context.startActivity(relaunch) }

        if (wl.isHeld) wl.release()
    }

    companion object { private const val TAG = "ScreenOffReceiver" }
}
