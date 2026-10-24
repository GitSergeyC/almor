package com.example.alarmclock.scheduler

import com.example.alarmclock.domain.Alarm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Чистая функция расчёта следующего срабатывания. Без Android-зависимостей,
 * покрывается юнит-тестами.
 *
 * Обработка перехода на летнее время делегируется java.time:
 *  - несуществующее локальное время (весенний скачок) сдвигается вперёд
 *    на величину разрыва;
 *  - неоднозначное время (осенний откат) берётся по РАННЕМУ смещению,
 *    т.е. будильник звонит первым из двух совпадающих моментов.
 * Это соответствует поведению системных часов Android.
 */
object NextTriggerCalculator {

    /**
     * @param from момент, начиная с которого ищем (строго больше).
     * @return следующее срабатывание или null, если будильник разовый и
     *         помечен на пропуск, либо выключен.
     */
    fun next(
        alarm: Alarm,
        enabled: Boolean,
        from: ZonedDateTime,
        zone: ZoneId = from.zone
    ): ZonedDateTime? {
        if (!enabled) return null

        val time = alarm.time

        if (!alarm.isRepeating) {
            // Разовый: сегодня, если время ещё не прошло, иначе завтра.
            var candidate = resolve(from.toLocalDate(), alarm, zone)
            if (!candidate.isAfter(from)) {
                candidate = resolve(from.toLocalDate().plusDays(1), alarm, zone)
            }
            return if (alarm.skipNextOccurrence) null else candidate
        }

        // Повторяющийся: перебираем до 8 дней вперёд (8, а не 7, чтобы
        // корректно вернуться к тому же дню недели через неделю).
        var skipped = false
        var date: LocalDate = from.toLocalDate()
        for (i in 0..8) {
            val day: DayOfWeek = date.dayOfWeek
            if (alarm.isDayEnabled(day)) {
                val candidate = resolve(date, alarm, zone)
                if (candidate.isAfter(from)) {
                    if (alarm.skipNextOccurrence && !skipped) {
                        skipped = true // пропускаем ровно одно срабатывание
                    } else {
                        return candidate
                    }
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun resolve(date: LocalDate, alarm: Alarm, zone: ZoneId): ZonedDateTime {
        // ZonedDateTime.of сам разрешает разрывы и наложения DST.
        return ZonedDateTime.of(date, alarm.time, zone)
    }

    /** Человекочитаемый остаток до срабатывания: "через 7 ч 20 мин". */
    fun humanizeRemaining(from: ZonedDateTime, target: ZonedDateTime): String {
        val minutes = java.time.Duration.between(from, target).toMinutes().coerceAtLeast(0)
        val days = minutes / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60
        return buildString {
            append("через ")
            if (days > 0) append("$days дн ")
            if (hours > 0) append("$hours ч ")
            if (days == 0L) append("$mins мин")
        }.trim()
    }
}
