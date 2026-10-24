package com.example.alarmclock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
    // Какие секции свёрнуты. По умолчанию все развёрнуты.
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.exactAlarmsAllowed) {
                item {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenPermissions)) {
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

            state.sections.forEach { section ->
                val isCollapsed = collapsed[section.key] == true

                section.group?.let { group ->
                    item(key = "header-${section.key}") {
                        GroupHeader(
                            name = group.name,
                            colorArgb = group.colorArgb,
                            enabled = group.enabled,
                            total = section.alarms.size,
                            active = section.enabledCount,
                            collapsed = isCollapsed,
                            onToggleCollapse = { collapsed[section.key] = !isCollapsed },
                            onToggleEnabled = { viewModel.toggleGroup(group.id, it) }
                        )
                    }
                }

                if (!isCollapsed) {
                    items(section.alarms, key = { "alarm-${it.alarm.id}" }) { item ->
                        AlarmRow(
                            item = item,
                            indented = section.group != null,
                            onToggle = { viewModel.toggleAlarm(item, it) },
                            onClick = { onEditAlarm(item.alarm.id) }
                        )
                    }
                    if (section.group != null && section.alarms.isEmpty()) {
                        item(key = "empty-${section.key}") {
                            Text(
                                "В группе пока нет будильников",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            if (state.isEmpty) {
                item {
                    Text(
                        "Будильников пока нет. Нажмите «плюс», чтобы создать первый.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    colorArgb: Int,
    enabled: Boolean,
    total: Int,
    active: Int,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "arrow"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (collapsed) "Развернуть" else "Свернуть",
            modifier = Modifier.rotate(arrowRotation)
        )
        Spacer(Modifier.size(8.dp))
        Spacer(Modifier.size(12.dp).clip(CircleShape).background(Color(colorArgb)))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) "$active из $total активны" else "группа выключена",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggleEnabled)
    }
}

@Composable
private fun AlarmRow(
    item: AlarmWithGroup,
    indented: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val alarm = item.alarm
    Card(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 16.dp else 0.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
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
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.mutedByGroup) {
                    Text(
                        "Отключён группой «${item.group?.name}»",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            // Тумблер отражает реальное состояние: выключенная группа
            // гасит его, даже если личный флаг будильника включён.
            Switch(checked = item.effectivelyEnabled, onCheckedChange = onToggle)
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
