package com.example.alarmclock.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.alarmclock.domain.GuardLevel
import com.example.alarmclock.ringing.RingingActivity
import com.example.alarmclock.ringing.RingingService

/**
 * Единственный законный способ для обычного приложения перехватить
 * кнопку Home, "недавние" и системное меню питания.
 *
 * ВАЖНО: служба реагирует ТОЛЬКО когда guardLevel != NONE, то есть
 * исключительно во время срабатывания будильника. В остальное время
 * она полностью пассивна и ничего не читает. Это и нужно писать
 * в обосновании для Google Play.
 */
class AlarmAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val level = guardLevel
        if (level.level < GuardLevel.STRONG.level) return
        if (event == null) return
        if (RingingService.session.value == null) return

        val pkg = event.packageName?.toString() ?: return

        // Меню питания рисуется системным пакетом. Закрываем его.
        if (level.level >= GuardLevel.MAXIMUM.level && isSystemPowerDialog(pkg, event)) {
            Log.i(TAG, "Перехвачено системное меню питания")
            performGlobalAction(GLOBAL_ACTION_BACK)
            bringRingingToFront()
            return
        }

        // Ушли с экрана задания (Home, недавние, другое приложение).
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != packageName && !RingingActivity.isVisible
        ) {
            Log.i(TAG, "Уход с экрана задания в $pkg, возвращаю")
            bringRingingToFront()
        }
    }

    private fun isSystemPowerDialog(pkg: String, event: AccessibilityEvent): Boolean {
        if (pkg != "android" && !pkg.contains("systemui")) return false
        val cls = event.className?.toString().orEmpty()
        return cls.contains("Dialog", ignoreCase = true) ||
            cls.contains("GlobalActions", ignoreCase = true)
    }

    private fun bringRingingToFront() {
        val intent = Intent(this, RingingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Служба защиты подключена (пассивна вне звонка)")
    }

    companion object {
        private const val TAG = "AlarmA11y"
        /** Управляется GuardController. NONE = служба полностью пассивна. */
        @Volatile var guardLevel: GuardLevel = GuardLevel.NONE
    }
}
