package com.example.alarmclock.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Чеклист разрешений с ФАКТИЧЕСКОЙ проверкой состояния, а не разовым
 * запросом при первом запуске. Пользователь должен видеть, что именно
 * не выдано и чем это грозит.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Пользователь уходит в системные настройки и возвращается — состояние
    // разрешений надо перечитать, иначе галочки врут.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разрешения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionCard(
                title = "Точные будильники",
                description = "Без него будильник может сработать с задержкой до часа.",
                granted = remember(refreshKey) { exactAlarmsGranted(context) },
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            PermissionCard(
                title = "Отключить оптимизацию батареи",
                description = "Иначе система может усыпить приложение и пропустить срабатывание.",
                granted = remember(refreshKey) { batteryOptimizationIgnored(context) },
                action = {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            PermissionCard(
                title = "Служба доступности",
                description = "Нужна только для усиленной защиты: перехват кнопки «Домой» и меню питания во время звонка.",
                granted = remember(refreshKey) { accessibilityEnabled(context) },
                action = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            PermissionCard(
                title = "Админ устройства",
                description = "Нужен для максимальной защиты: не даёт удалить приложение во время звонка. Отключается в любой момент в настройках безопасности.",
                granted = remember(refreshKey) { deviceAdminActive(context) },
                action = {
                    val component = android.content.ComponentName(
                        context, com.example.alarmclock.guard.DeviceAdminReceiver::class.java
                    )
                    context.startActivity(
                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Позволяет приложению защищать себя от удаления во время звонка будильника."
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )

            PermissionCard(
                title = "Поверх других приложений",
                description = "Помогает показать экран будильника, если система не отдаёт передний план.",
                granted = remember(refreshKey) { Settings.canDrawOverlays(context) },
                action = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            VendorHint()
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    action: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                TextButton(onClick = action) { Text("Настроить") }
            }
        }
    }
}

/**
 * Прошивки Xiaomi, Huawei, Oppo и подобные агрессивно убивают фоновые
 * процессы. Без ручной настройки автозапуска будильник может не сработать.
 */
@Composable
private fun VendorHint() {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val aggressive = listOf("xiaomi", "huawei", "honor", "oppo", "vivo", "realme", "oneplus", "meizu")
    if (aggressive.none { manufacturer.contains(it) }) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Настройки производителя", style = MaterialTheme.typography.titleMedium)
            Text(
                "На устройствах ${Build.MANUFACTURER} система может закрывать приложение в фоне. " +
                    "Разрешите автозапуск и закрепите приложение в списке недавних, " +
                    "иначе будильник может не прозвонить.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Читаем реальное состояние из Settings.Secure, а не предполагаем. */
private fun accessibilityEnabled(context: android.content.Context): Boolean {
    val expected = android.content.ComponentName(
        context, com.example.alarmclock.guard.AlarmAccessibilityService::class.java
    ).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun deviceAdminActive(context: android.content.Context): Boolean {
    val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
    return dpm.isAdminActive(
        android.content.ComponentName(
            context, com.example.alarmclock.guard.DeviceAdminReceiver::class.java
        )
    )
}

private fun exactAlarmsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(android.app.AlarmManager::class.java)
    return am.canScheduleExactAlarms()
}

private fun batteryOptimizationIgnored(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
