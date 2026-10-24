package com.example.alarmclock.guard

import android.content.Context
import android.content.Intent

/**
 * Активный админ устройства не даёт удалить приложение обычным способом.
 * Политика заявлена минимальная (force-lock) — расширять её без
 * необходимости нельзя, Google Play проверяет обоснованность.
 */
class DeviceAdminReceiver : android.app.admin.DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Отключение снимет защиту будильника от удаления во время звонка."
    }
}
