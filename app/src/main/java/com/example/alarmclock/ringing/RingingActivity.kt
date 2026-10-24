package com.example.alarmclock.ringing

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.alarmclock.domain.GuardLevel
import com.example.alarmclock.guard.GuardController
import com.example.alarmclock.tasks.TaskRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Экран задания. Показывается поверх экрана блокировки.
 *
 * Все перехваты клавиш живут здесь — это уровень BASIC, работающий
 * без каких-либо специальных разрешений.
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    @Inject lateinit var guard: GuardController
    @Inject lateinit var registry: TaskRegistry
    @Inject lateinit var player: AlarmPlayer

    private var guardLevel: GuardLevel = GuardLevel.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val session = RingingService.session.value
        if (session == null) { finish(); return }

        guardLevel = session.alarm.guardLevel
        guard.engage(guardLevel)
        RingingService.markGuardActive(guardLevel != GuardLevel.NONE)

        // Кнопка "Назад" не выпускает с экрана, пока охрана активна.
        onBackPressedDispatcher.addCallback(this) {
            if (guardLevel == GuardLevel.NONE) finish()
            // иначе просто игнорируем
        }

        setContent {
            RingingScreen(
                registry = registry,
                onSnooze = { sendServiceAction(RingingService.ACTION_SNOOZE) },
                onDismiss = { sendServiceAction(RingingService.ACTION_DISMISS) }
            )
        }
    }

    private fun showOverLockScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Перехват аппаратных клавиш громкости — уровень BASIC.
     * Возвращаем true, чтобы система не обработала нажатие,
     * и заодно восстанавливаем громкость, если её успели убавить.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (guardLevel == GuardLevel.NONE) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                RingingService.session.value?.let { player.restoreVolumeIfLowered(it.alarm.volume) }
                true
            }
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home нажали. Без службы доступности вернуть себя нельзя,
        // но сервис-сторож поднимет экран через 2 секунды.
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, RingingService::class.java).apply { this.action = action })
        guard.release()
        finish()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    override fun onDestroy() {
        isVisible = false
        if (RingingService.session.value == null) guard.release()
        super.onDestroy()
    }

    companion object {
        /** Читается сторожем сервиса и службой доступности. */
        @Volatile var isVisible: Boolean = false
            private set
    }
}
