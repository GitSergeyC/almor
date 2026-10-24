package com.example.alarmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarmclock.domain.GuardLevel
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
    val alarm = state.alarm

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
                        enabled = supported,
                        onClick = { viewModel.setGuardLevel(level) },
                        label = { Text(guardTitle(level)) }
                    )
                }
            }
            if (!state.guardAvailability.supports(alarm.guardLevel)) {
                Text(
                    "Для этого уровня нужны дополнительные разрешения — откройте раздел разрешений.",
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

            Text("Отложить на: ${alarm.snoozeMinutes} мин")
            Slider(
                value = alarm.snoozeMinutes.toFloat(),
                onValueChange = { viewModel.setSnooze(it.toInt()) },
                valueRange = 1f..30f
            )

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
}

private fun guardTitle(level: GuardLevel) = when (level) {
    GuardLevel.NONE -> "Нет"
    GuardLevel.BASIC -> "Базовая"
    GuardLevel.STRONG -> "Усиленная"
    GuardLevel.MAXIMUM -> "Максимальная"
}
