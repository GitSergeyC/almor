package com.example.alarmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarmclock.domain.GuardLevel
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.guard.GuardAvailability
import com.example.alarmclock.domain.TaskType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long,
    onDone: () -> Unit,
    viewModel: AlarmEditViewModel = hiltViewModel()
) {
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    // Вернулись из системных настроек — уровни защиты могли стать доступны.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshGuardAvailability()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Форма не строится, пока будильник не прочитан из базы.
    // Иначе часы создавались бы со значением по умолчанию (07:00),
    // уже не обновлялись при загрузке — и сохранение затирало бы
    // настоящее время будильника.
    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val alarm = state.alarm

    val context = LocalContext.current
    // Уровень защиты, который пользователь выбрал без нужных разрешений.
    var pendingGuard by remember { mutableStateOf<GuardLevel?>(null) }

    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == 0L) "Новый будильник" else "Изменить") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.save(timeState.hour, timeState.minute, onDone)
                    }) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimePicker(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))

            Text("Повтор", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip("Один раз", alarm.daysMask == 0) { viewModel.setDays(0) }
                PresetChip("Ежедневно", alarm.daysMask == Alarm.EVERY_DAY) {
                    viewModel.setDays(Alarm.EVERY_DAY)
                }
                PresetChip("Пн–Пт", alarm.daysMask == Alarm.WEEKDAYS) {
                    viewModel.setDays(Alarm.WEEKDAYS)
                }
                PresetChip("Выходные", alarm.daysMask == Alarm.WEEKEND) {
                    viewModel.setDays(Alarm.WEEKEND)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = alarm.isDayEnabled(day),
                        onClick = { viewModel.toggleDay(day) },
                        label = {
                            Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                        }
                    )
                }
            }

            OutlinedTextField(
                value = alarm.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Группа", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = alarm.groupId == null,
                    onClick = { viewModel.setGroup(null) },
                    label = { Text("Без группы") }
                )
                state.groups.forEach { g ->
                    FilterChip(
                        selected = alarm.groupId == g.id,
                        onClick = { viewModel.setGroup(g.id) },
                        label = { Text(g.name) }
                    )
                }
            }

            Text("Задание для пробуждения", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableTasks.forEach { t ->
                    FilterChip(
                        selected = alarm.taskType == t,
                        onClick = { viewModel.setTaskType(t) },
                        label = { Text(t.title) }
                    )
                }
            }

            if (alarm.taskType != TaskType.NONE) {
                Text("Повторов: ${alarm.taskCount}")
                Slider(
                    value = alarm.taskCount.toFloat(),
                    onValueChange = { viewModel.setTaskCount(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            Text("Защита от отключения", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuardLevel.entries.forEach { level ->
                    val supported = state.guardAvailability.supports(level)
                    FilterChip(
                        selected = alarm.guardLevel == level,
                        // Уровень можно выбрать всегда: если разрешений нет,
                        // предлагаем их выдать, а не прячем возможность.
                        onClick = {
                            if (supported) viewModel.setGuardLevel(level)
                            else pendingGuard = level
                        },
                        label = { Text(guardTitle(level)) },
                        trailingIcon = if (!supported) {
                            { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            if (!state.guardAvailability.supports(alarm.guardLevel)) {
                Text(
                    "Выбранный уровень пока не действует — не выданы разрешения. " +
                        "Нажмите на него, чтобы это исправить.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Вибрация")
                Switch(checked = alarm.vibrate, onCheckedChange = viewModel::setVibrate)
            }

            Text("Громкость: ${alarm.volume}%")
            Slider(
                value = alarm.volume.toFloat(),
                onValueChange = { viewModel.setVolume(it.toInt()) },
                valueRange = 10f..100f
            )

            Text("Нарастание громкости: ${alarm.volumeRampSec} сек")
            Slider(
                value = alarm.volumeRampSec.toFloat(),
                onValueChange = { viewModel.setRamp(it.toInt()) },
                valueRange = 0f..120f
            )

            Text("Отложить на", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Alarm.SNOOZE_PRESETS.forEach { minutes ->
                    PresetChip(
                        text = if (minutes == 0) "Выкл" else "$minutes мин",
                        selected = alarm.snoozeMinutes == minutes
                    ) { viewModel.setSnooze(minutes) }
                }
            }

            if (alarmId != 0L) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.delete(onDone) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить будильник") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingGuard?.let { level ->
        GuardPermissionDialog(
            level = level,
            availability = state.guardAvailability,
            onOpenAccessibility = { context.startActivity(viewModel.accessibilityIntent()) },
            onOpenDeviceAdmin = { context.startActivity(viewModel.deviceAdminIntent()) },
            onApplyAnyway = {
                viewModel.setGuardLevel(level)
                pendingGuard = null
            },
            onDismiss = { pendingGuard = null }
        )
    }
}

/**
 * Объясняет, чего не хватает выбранному уровню, и ведёт прямо в нужные
 * настройки. Уровень можно применить и заранее — он начнёт действовать,
 * как только разрешение будет выдано.
 */
@Composable
private fun GuardPermissionDialog(
    level: GuardLevel,
    availability: GuardAvailability,
    onOpenAccessibility: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onApplyAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val needsAccessibility = !availability.accessibilityEnabled
    val needsAdmin = level == GuardLevel.MAXIMUM && !availability.deviceAdminEnabled

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Уровень «${guardTitle(level)}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (level) {
                        GuardLevel.MAXIMUM ->
                            "Перехватывает кнопку «Домой», меню питания и не даёт удалить " +
                                "приложение во время звонка."
                        else ->
                            "Перехватывает кнопку «Домой» и список недавних приложений " +
                                "во время звонка."
                    }
                )
                Text("Для этого нужно выдать:", style = MaterialTheme.typography.titleSmall)
                if (needsAccessibility) {
                    TextButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                        Text("Служба доступности")
                    }
                }
                if (needsAdmin) {
                    TextButton(onClick = onOpenDeviceAdmin, modifier = Modifier.fillMaxWidth()) {
                        Text("Админ устройства")
                    }
                }
                if (needsAccessibility) {
                    Text(
                        "Если в списке служб приложение недоступно, откройте его страницу " +
                            "в настройках и включите «Разрешить ограниченные настройки».",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApplyAnyway) { Text("Выбрать всё равно") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Чип-пресет: выбор одного значения из нескольких. */
@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

private fun guardTitle(level: GuardLevel) = when (level) {
    GuardLevel.NONE -> "Нет"
    GuardLevel.BASIC -> "Базовая"
    GuardLevel.STRONG -> "Усиленная"
    GuardLevel.MAXIMUM -> "Максимальная"
}
