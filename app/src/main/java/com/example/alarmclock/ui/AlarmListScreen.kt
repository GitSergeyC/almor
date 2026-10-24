package com.example.alarmclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.domain.AlarmWithGroup
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Будильники")
                        state.nextSummary?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenGroups) {
                        Icon(Icons.Default.Folder, contentDescription = "Группы")
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Default.Settings, contentDescription = "Разрешения")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.exactAlarmsAllowed) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenPermissions)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.size(12.dp))
                            Text("Нет разрешения на точные будильники — сработают неточно. Нажмите, чтобы исправить.")
                        }
                    }
                }
            }

            // Переключатели групп одной кнопкой
            if (state.groups.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.groups.forEach { group ->
                            FilterChip(
                                selected = group.enabled,
                                onClick = { viewModel.toggleGroup(group.id, !group.enabled) },
                                label = { Text(group.name) },
                                leadingIcon = {
                                    Spacer(
                                        Modifier.size(12.dp).clip(CircleShape)
                                            .background(Color(group.colorArgb))
                                    )
                                }
                            )
                        }
                    }
                }
            }

            items(state.alarms, key = { it.alarm.id }) { item ->
                AlarmRow(
                    item = item,
                    onToggle = { viewModel.toggleAlarm(item.alarm.id, it) },
                    onClick = { onEditAlarm(item.alarm.id) }
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(
    item: AlarmWithGroup,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val alarm = item.alarm
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    buildString {
                        append(formatDays(alarm))
                        if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                        item.group?.let { append(" · ${it.name}") }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                // Честно показываем, что будильник заглушён группой,
                // а не выключен лично — иначе пользователь путается.
                if (item.mutedByGroup) {
                    Text(
                        "Отключён группой «${item.group?.name}»",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun formatDays(alarm: Alarm): String = when {
    !alarm.isRepeating -> "Один раз"
    alarm.daysMask == Alarm.EVERY_DAY -> "Ежедневно"
    alarm.daysMask == Alarm.WEEKDAYS -> "Пн–Пт"
    alarm.daysMask == Alarm.WEEKEND -> "Сб, Вс"
    else -> DayOfWeek.entries
        .filter { alarm.isDayEnabled(it) }
        .joinToString(", ") {
            it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
}
