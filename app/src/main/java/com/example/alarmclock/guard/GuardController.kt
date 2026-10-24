package com.example.alarmclock.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.example.alarmclock.domain.GuardLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что реально доступно на этом устройстве прямо сейчас.
 * UI обязан показывать это честно: обещать защиту, которой нет, хуже,
 * чем не обещать ничего.
 */
data class GuardAvailability(
    val accessibilityEnabled: Boolean,
    val deviceAdminEnabled: Boolean,
    val overlayGranted: Boolean
) {
    fun maxAvailableLevel(): GuardLevel = when {
        accessibilityEnabled && deviceAdminEnabled -> GuardLevel.MAXIMUM
        accessibilityEnabled -> GuardLevel.STRONG
        else -> GuardLevel.BASIC
    }

    fun supports(level: GuardLevel) = level.level <= maxAvailableLevel().level
}

@Singleton
class GuardController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var screenOffReceiver: ScreenOffReceiver? = null
    @Volatile var activeLevel: GuardLevel = GuardLevel.NONE
        private set

    fun availability(): GuardAvailability = GuardAvailability(
        accessibilityEnabled = isAccessibilityEnabled(),
        deviceAdminEnabled = isDeviceAdminActive(),
        overlayGranted = Settings.canDrawOverlays(context)
    )

    /** Включить охрану. Молча деградирует до доступного уровня. */
    fun engage(requested: GuardLevel) {
        val effective = if (availability().supports(requested)) requested
        else availability().maxAvailableLevel()

        activeLevel = effective
        Log.i(TAG, "Охрана включена: запрошен $requested, применён $effective")

        AlarmAccessibilityService.guardLevel = effective

        if (effective.level >= GuardLevel.MAXIMUM.level) {
            registerScreenOffReceiver()
        }
    }

    fun release() {
        Log.i(TAG, "Охрана снята")
        activeLevel = GuardLevel.NONE
        AlarmAccessibilityService.guardLevel = GuardLevel.NONE
        unregisterScreenOffReceiver()
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = ScreenOffReceiver().also {
            context.registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        screenOffReceiver = null
    }

    // ---- Проверки разрешений ----

    fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, AlarmAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, DeviceAdminReceiver::class.java))
    }

    // ---- Интенты для онбординга ----

    fun accessibilitySettingsIntent() =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(context, DeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Позволяет приложению защищать себя от удаления во время звонка будильника."
            )
        }

    companion object { private const val TAG = "GuardController" }
}
