package com.example.alarmclock.scheduler

import com.example.alarmclock.domain.Alarm
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerCalculatorTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun alarm(h: Int, m: Int, mask: Int = 0) =
        Alarm(id = 1, hour = h, minute = m, daysMask = mask)

    @Test
    fun `one shot alarm today when time not passed`() {
        val now = ZonedDateTime.of(2026, 9, 7, 6, 0, 0, 0, moscow)
        val next = NextTriggerCalculator.next(alarm(7, 30), true, now)!!
        assertThat(next.hour).isEqualTo(7)
        assertThat(next.dayOfMonth).isEqualTo(7)
    }

    @Test
    fun `one shot alarm rolls to tomorrow when time passed`() {
        val now = ZonedDateTime.of(2026, 9, 7, 8, 0, 0, 0, moscow)
        val next = NextTriggerCalculator.next(alarm(7, 30), true, now)!!
        assertThat(next.dayOfMonth).isEqualTo(8)
    }

    @Test
    fun `exact same minute does not fire twice`() {
        val now = ZonedDateTime.of(2026, 9, 7, 7, 30, 0, 0, moscow)
        val next = NextTriggerCalculator.next(alarm(7, 30), true, now)!!
        assertThat(next.dayOfMonth).isEqualTo(8)
    }

    @Test
    fun `weekday alarm on friday jumps to monday`() {
        // 11 сентября 2026 - пятница, 19:00
        val now = ZonedDateTime.of(2026, 9, 11, 19, 0, 0, 0, moscow)
        val next = NextTriggerCalculator.next(alarm(7, 0, Alarm.WEEKDAYS), true, now)!!
        assertThat(next.dayOfWeek.value).isEqualTo(1)
        assertThat(next.dayOfMonth).isEqualTo(14)
    }

    @Test
    fun `skip next occurrence skips exactly one`() {
        val now = ZonedDateTime.of(2026, 9, 7, 8, 0, 0, 0, moscow)
        val a = alarm(7, 0, Alarm.EVERY_DAY).copy(skipNextOccurrence = true)
        val next = NextTriggerCalculator.next(a, true, now)!!
        assertThat(next.dayOfMonth).isEqualTo(9) // 8-е пропущено
    }

    @Test
    fun `disabled alarm returns null`() {
        val now = ZonedDateTime.of(2026, 9, 7, 6, 0, 0, 0, moscow)
        assertThat(NextTriggerCalculator.next(alarm(7, 0), false, now)).isNull()
    }

    @Test
    fun `spring dst gap shifts alarm forward, never skips a day`() {
        // Берлин: 29 марта 2026, 02:00 -> 03:00. Будильник на 02:30 не существует.
        val now = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, berlin)
        val next = NextTriggerCalculator.next(alarm(2, 30, Alarm.EVERY_DAY), true, now, berlin)!!
        assertThat(next.dayOfMonth).isEqualTo(29)
        assertThat(next.hour).isEqualTo(3) // сдвинут вперёд, а не пропущен
    }

    @Test
    fun `autumn dst overlap picks earlier offset`() {
        // Берлин: 25 октября 2026, 03:00 -> 02:00. 02:30 бывает дважды.
        val now = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, berlin)
        val next = NextTriggerCalculator.next(alarm(2, 30, Alarm.EVERY_DAY), true, now, berlin)!!
        assertThat(next.hour).isEqualTo(2)
        assertThat(next.offset).isEqualTo(java.time.ZoneOffset.ofHours(2)) // летнее, раннее
    }

    @Test
    fun `leap day alarm works`() {
        val now = ZonedDateTime.of(2028, 2, 29, 5, 0, 0, 0, moscow)
        val next = NextTriggerCalculator.next(alarm(7, 0, Alarm.EVERY_DAY), true, now)!!
        assertThat(next.dayOfMonth).isEqualTo(29)
    }
}
