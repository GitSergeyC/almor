package com.example.alarmclock.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.alarmclock.domain.TaskType
import javax.inject.Inject
import kotlin.random.Random

/**
 * Арифметика с тремя уровнями сложности.
 * Генератор гарантирует целочисленный ответ и исключает тривиальные
 * примеры вроде "x * 1" или "x + 0" — иначе задание не будит.
 */
class MathTask @Inject constructor() : WakeUpTask {

    override val type = TaskType.MATH

    @Composable
    override fun Render(state: TaskSessionState, callbacks: TaskCallbacks) {
        val problem = remember(state.index, state.failures) {
            generate(state.difficulty)
        }
        var input by remember(problem) { mutableStateOf("") }
        var wrong by remember(problem) { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${state.index + 1} из ${state.total}",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(24.dp))
            Text(problem.question, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { new ->
                    if (new.all { it.isDigit() || it == '-' } && new.length <= 6) {
                        input = new
                        wrong = false
                    }
                },
                isError = wrong,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text("Ответ") },
                modifier = Modifier.fillMaxWidth()
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text("Неверно, попробуйте ещё раз", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val answer = input.toIntOrNull()
                    if (answer == problem.answer) {
                        callbacks.onSolved()
                    } else {
                        wrong = true
                        input = ""
                        callbacks.onFailed()
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить") }
        }
    }

    data class Problem(val question: String, val answer: Int)

    companion object {
        fun generate(difficulty: Int, rng: Random = Random.Default): Problem = when (difficulty) {
            1 -> {
                val a = rng.nextInt(10, 50)
                val b = rng.nextInt(10, 50)
                if (rng.nextBoolean()) Problem("$a + $b", a + b)
                else Problem("${a + b} − $b", a)
            }
            2 -> {
                val a = rng.nextInt(3, 20)
                val b = rng.nextInt(3, 13)
                val c = rng.nextInt(5, 40)
                Problem("$a × $b + $c", a * b + c)
            }
            else -> {
                val a = rng.nextInt(11, 40)
                val b = rng.nextInt(11, 30)
                val c = rng.nextInt(2, 12)
                // Делимость гарантирована конструкцией: сначала произведение.
                val product = a * c
                Problem("$product ÷ $c + $a × $b", a + a * b)
            }
        }
    }
}
