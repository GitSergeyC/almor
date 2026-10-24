package com.example.alarmclock.tasks

import android.content.Context
import com.example.alarmclock.domain.TaskType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реестр заданий. Регистрация в одном месте — новые типы добавляются сюда.
 */
@Singleton
class TaskRegistry @Inject constructor(
    private val tasks: Set<@JvmSuppressWildcards WakeUpTask>
) {
    fun get(type: TaskType): WakeUpTask? = tasks.firstOrNull { it.type == type }

    fun availableTypes(context: Context): List<TaskType> =
        listOf(TaskType.NONE) + tasks.filter { it.isAvailable(context) }.map { it.type }
}
