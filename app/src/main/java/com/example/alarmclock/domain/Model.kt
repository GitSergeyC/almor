package com.example.alarmclock.domain

import java.time.DayOfWeek
import java.time.LocalTime

/** Уровень защиты от отключения. Каждый следующий включает предыдущие. */
enum class GuardLevel(val level: Int) {
    NONE(0),
    /** Назад + громкость. Работает всегда, без доп. разрешений. */
    BASIC(1),
    /** + Home и "недавние". Требует службы доступности. */
    STRONG(2),
    /** + экран питания и защита от удаления. Доступность + админ устройства. */
    MAXIMUM(3);

    companion object {
        fun of(v: Int) = entries.firstOrNull { it.level == v } ?: NONE
    }
}

enum class TaskType(val id: String, val title: String) {
    NONE("NONE", "Без задания"),
    MATH("MATH", "Примеры"),
    SHAKE("SHAKE", "Встряхивание"),
    TYPING("TYPING", "Набрать фразу"),
    MEMORY("MEMORY", "Запомнить последовательность");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Битовая маска: бит 0 = понедельник ... бит 6 = воскресенье. 0 = разовый. */
    val daysMask: Int = 0,
    /** Личный флаг пользователя. НЕ перезаписывается переключением группы. */
    val enabled: Boolean = true,
    val groupId: Long? = null,
    val label: String = "",
    val soundUri: String? = null,
    val volume: Int = 100,
    val volumeRampSec: Int = 30,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val snoozeLimit: Int = 3,
    val taskType: TaskType = TaskType.NONE,
    val taskConfig: String = "{}",
    val taskCount: Int = 1,
    val guardLevel: GuardLevel = GuardLevel.NONE,
    val skipNextOccurrence: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)
    val isRepeating: Boolean get() = daysMask != 0

    fun isDayEnabled(day: DayOfWeek): Boolean = daysMask and bit(day) != 0

    fun withDayToggled(day: DayOfWeek): Alarm =
        copy(daysMask = daysMask xor bit(day))

    companion object {
        /** Варианты откладывания, как в системных приложениях-часах. 0 = выключено. */
        val SNOOZE_PRESETS = listOf(0, 5, 10, 15, 20, 30)

        fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
        val WEEKDAYS = 0b0011111
        val WEEKEND = 0b1100000
        val EVERY_DAY = 0b1111111
    }
}

data class AlarmGroup(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val enabled: Boolean = true,
    /** Группы с одинаковым непустым setId взаимоисключающие. */
    val exclusiveSetId: Long? = null,
    val sortOrder: Int = 0
)

/**
 * Будильник вместе с разрешённым состоянием группы.
 * Именно [effectivelyEnabled] определяет, ставить ли его в AlarmManager.
 */
data class AlarmWithGroup(
    val alarm: Alarm,
    val group: AlarmGroup?
) {
    val effectivelyEnabled: Boolean
        get() = alarm.enabled && (group?.enabled ?: true)

    /** Выключен лично, а не из-за группы — для корректной иконки в списке. */
    val mutedByGroup: Boolean
        get() = alarm.enabled && group != null && !group.enabled
}
