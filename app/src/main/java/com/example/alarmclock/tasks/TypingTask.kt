package com.example.alarmclock.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.alarmclock.domain.TaskType
import javax.inject.Inject

/**
 * Набор фразы вручную. Автоподстановка и автокоррекция отключены —
 * иначе задание решается одним тапом по подсказке клавиатуры.
 */
class TypingTask @Inject constructor() : WakeUpTask {

    override val type = TaskType.TYPING

    @Composable
    override fun Render(state: TaskSessionState, callbacks: TaskCallbacks) {
        val phrase = remember(state.index, state.difficulty) {
            PHRASES[state.difficulty.coerceIn(1, 3) - 1].random()
        }
        var input by remember(phrase) { mutableStateOf("") }
        val matches = input.trim().equals(phrase, ignoreCase = true)

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Наберите фразу", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    phrase,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = false,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (matches) callbacks.onSolved() else callbacks.onFailed() },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Готово") }
        }
    }

    companion object {
        val PHRASES = listOf(
            listOf("Доброе утро", "Пора вставать", "Новый день"),
            listOf("Сегодня будет хороший день", "Я встаю прямо сейчас"),
            listOf(
                "Утро начинается не с кофе, а с решения встать",
                "Каждый день — это новая возможность начать заново"
            )
        )
    }
}
