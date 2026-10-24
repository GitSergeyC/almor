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

data class AlarmListUiState(
    val alarms: List<AlarmWithGroup> = emptyList(),
    val groups: List<AlarmGroup> = emptyList(),
    val nextSummary: String? = null,
    val exactAlarmsAllowed: Boolean = true
)

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

        AlarmListUiState(
            alarms = alarms,
            groups = groups,
            nextSummary = next?.let { NextTriggerCalculator.humanizeRemaining(now, it) },
            exactAlarmsAllowed = scheduler.canScheduleExact()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmListUiState())

    fun toggleAlarm(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(id, enabled)
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
