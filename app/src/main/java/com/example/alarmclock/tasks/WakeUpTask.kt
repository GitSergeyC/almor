package com.example.alarmclock.tasks

import androidx.compose.runtime.Composable
import com.example.alarmclock.domain.TaskType

/**
 * Контракт задания пробуждения.
 *
 * Добавление нового типа = новый класс + одна строка в TaskRegistry.
 * Остальной код приложения не меняется.
 */
interface WakeUpTask {
    val type: TaskType

    /** Разрешения, без которых задание невозможно (например, CAMERA). */
    val requiredPermissions: List<String> get() = emptyList()

    /** Доступно ли на этом устройстве (наличие датчика, камеры и т.п.). */
    fun isAvailable(context: android.content.Context): Boolean = true

    @Composable
    fun Render(state: TaskSessionState, callbacks: TaskCallbacks)
}

/**
 * @param index номер текущего повтора (0-based)
 * @param total сколько всего нужно выполнить
 * @param difficulty 1..3, при провалах и после перезагрузки повышается
 */
data class TaskSessionState(
    val index: Int,
    val total: Int,
    val difficulty: Int,
    val failures: Int,
    val escalated: Boolean
)

interface TaskCallbacks {
    /** Текущий повтор выполнен верно. */
    fun onSolved()
    /** Ошибка. Увеличивает счётчик провалов, может поднять сложность. */
    fun onFailed()
}
