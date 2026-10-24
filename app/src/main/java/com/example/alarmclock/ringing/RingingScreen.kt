package com.example.alarmclock.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.alarmclock.domain.TaskType
import com.example.alarmclock.tasks.TaskCallbacks
import com.example.alarmclock.tasks.TaskRegistry
import com.example.alarmclock.tasks.TaskSessionState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun RingingScreen(
    registry: TaskRegistry,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val session by RingingService.session.collectAsState()
    val s = session ?: return

    var solvedCount by remember { mutableIntStateOf(0) }
    var failures by remember { mutableIntStateOf(0) }
    // Аварийный выход появляется через 5 минут: приложение не должно
    // превращаться в блокировщик устройства, если задание не решается.
    var emergencyAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(EMERGENCY_DELAY_MS)
        emergencyAvailable = true
    }

    val total = s.effectiveTaskCount
    val task = registry.get(s.alarm.taskType)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                Text(
                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.displayLarge
                )
                if (s.alarm.label.isNotBlank()) {
                    Text(s.alarm.label, style = MaterialTheme.typography.titleMedium)
                }
                if (s.escalated) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Обнаружена перезагрузка во время звонка — задание усложнено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (s.alarm.taskType == TaskType.NONE || task == null) {
                    // Без задания середина экрана пустая: обе кнопки внизу.
                } else {
                    val difficulty = baseDifficulty(s.escalated, failures)
                    task.Render(
                        state = TaskSessionState(
                            index = solvedCount,
                            total = total,
                            difficulty = difficulty,
                            failures = failures,
                            escalated = s.escalated
                        ),
                        callbacks = object : TaskCallbacks {
                            override fun onSolved() {
                                solvedCount++
                                if (solvedCount >= total) onDismiss()
                            }
                            override fun onFailed() { failures++ }
                        }
                    )
                }
            }

            // Две крупные кнопки внизу, как в системных часах.
            // «Выключить» недоступна, пока не выполнено задание, —
            // иначе задание не имело бы смысла.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val hasTask = s.alarm.taskType != TaskType.NONE && task != null
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (s.alarm.snoozeMinutes > 0) {
                        OutlinedButton(
                            onClick = onSnooze,
                            modifier = Modifier.weight(1f).height(64.dp)
                        ) {
                            Text(
                                "Отложить\n${s.alarm.snoozeMinutes} мин",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        enabled = !hasTask,
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Text(
                            if (hasTask) "Выполните\nзадание" else "Выключить",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (emergencyAvailable) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Экстренное выключение")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Сложность растёт при провалах и после обнаруженной перезагрузки. */
private fun baseDifficulty(escalated: Boolean, failures: Int): Int {
    val base = if (escalated) 2 else 1
    return (base + failures / 3).coerceAtMost(3)
}

private const val EMERGENCY_DELAY_MS = 5 * 60 * 1000L
