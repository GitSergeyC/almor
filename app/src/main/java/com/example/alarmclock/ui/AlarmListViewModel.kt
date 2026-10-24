package com.example.alarmclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmclock.data.repo.AlarmRepository
import com.example.alarmclock.domain.AlarmGroup
import com.example.alarmclock.domain.AlarmWithGroup
import com.example.alarmclock.scheduler.AlarmScheduler
import com.example.alarmclock.scheduler.NextTriggerCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Секция списка. [group] == null — будильники, не входящие ни в одну группу;
 * такая секция показывается без заголовка и без переключателя.
 */
data class AlarmSection(
    val group: AlarmGroup?,
    val alarms: List<AlarmWithGroup>
) {
    val key: String get() = group?.id?.toString() ?: "ungrouped"
    val enabledCount: Int get() = alarms.count { it.effectivelyEnabled }
}

data class AlarmListUiState(
    val sections: List<AlarmSection> = emptyList(),
    val nextSummary: String? = null,
    val exactAlarmsAllowed: Boolean = true
) {
    val isEmpty: Boolean get() = sections.all { it.alarms.isEmpty() }
}

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    val state = combine(
        repository.observeAlarms(),
        repository.observeGroups()
    ) { alarms, groups ->
        val now = ZonedDateTime.now()
        val next = alarms.mapNotNull { awg ->
            NextTriggerCalculator.next(awg.alarm, awg.effectivelyEnabled, now)
        }.minOrNull()

        val grouped = groups.map { g ->
            AlarmSection(g, alarms.filter { it.alarm.groupId == g.id })
        }
        val ungrouped = alarms.filter { it.alarm.groupId == null }

        AlarmListUiState(
            // Секция без группы идёт последней, чтобы группы были на виду.
            sections = grouped + listOfNotNull(
                AlarmSection(null, ungrouped).takeIf { ungrouped.isNotEmpty() }
            ),
            nextSummary = next?.let { NextTriggerCalculator.humanizeRemaining(now, it) },
            exactAlarmsAllowed = scheduler.canScheduleExact()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmListUiState())

    /**
     * Переключатель у будильника показывает РЕАЛЬНОЕ состояние с учётом группы.
     * Если будильник заглушён выключенной группой, включение тумблера
     * поднимает и группу — иначе нажатие не давало бы видимого результата.
     */
    fun toggleAlarm(item: AlarmWithGroup, enabled: Boolean) = viewModelScope.launch {
        if (enabled && item.mutedByGroup) {
            repository.setGroupEnabled(item.alarm.groupId!!, true)
            return@launch
        }
        repository.setEnabled(item.alarm.id, enabled)
    }

    /** Включение/выключение всей группы одной кнопкой. */
    fun toggleGroup(groupId: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setGroupEnabled(groupId, enabled)
    }

    fun deleteAlarm(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun skipNext(id: Long, skip: Boolean) = viewModelScope.launch {
        repository.setSkipNext(id, skip)
    }
}
