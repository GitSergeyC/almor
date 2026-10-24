package com.example.alarmclock.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmclock.data.repo.AlarmRepository
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.domain.AlarmGroup
import com.example.alarmclock.domain.GuardLevel
import com.example.alarmclock.domain.TaskType
import com.example.alarmclock.guard.GuardAvailability
import com.example.alarmclock.guard.GuardController
import com.example.alarmclock.tasks.TaskRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class AlarmEditUiState(
    /** Пока false — будильник ещё читается из базы, показывать форму нельзя. */
    val loaded: Boolean = false,
    val alarm: Alarm = Alarm(hour = 7, minute = 0),
    val groups: List<AlarmGroup> = emptyList(),
    val availableTasks: List<TaskType> = listOf(TaskType.NONE),
    val guardAvailability: GuardAvailability = GuardAvailability(false, false, false)
)

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlarmRepository,
    private val registry: TaskRegistry,
    private val guard: GuardController
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditUiState())
    val state = _state.asStateFlow()

    fun load(id: Long) = viewModelScope.launch {
        val groups = repository.observeGroups().first()
        val alarm = if (id == 0L) Alarm(hour = 7, minute = 0)
        else repository.getAlarm(id)?.alarm ?: Alarm(hour = 7, minute = 0)

        _state.value = AlarmEditUiState(
            loaded = true,
            alarm = alarm,
            groups = groups,
            availableTasks = registry.availableTypes(context),
            guardAvailability = guard.availability()
        )
    }

    /** Перечитать доступность уровней защиты (после выдачи разрешений). */
    fun refreshGuardAvailability() {
        _state.value = _state.value.copy(guardAvailability = guard.availability())
    }

    private fun edit(block: (Alarm) -> Alarm) {
        _state.value = _state.value.copy(alarm = block(_state.value.alarm))
    }

    fun toggleDay(day: DayOfWeek) = edit { it.withDayToggled(day) }
    /** Быстрый пресет повтора: ежедневно, будни, выходные, один раз. */
    fun setDays(mask: Int) = edit { it.copy(daysMask = mask) }
    fun setLabel(v: String) = edit { it.copy(label = v) }
    fun setGroup(id: Long?) = edit { it.copy(groupId = id) }
    fun setTaskType(t: TaskType) = edit { it.copy(taskType = t) }
    fun setTaskCount(v: Int) = edit { it.copy(taskCount = v.coerceIn(1, 5)) }
    fun setGuardLevel(l: GuardLevel) = edit { it.copy(guardLevel = l) }
    fun setVibrate(v: Boolean) = edit { it.copy(vibrate = v) }
    fun setVolume(v: Int) = edit { it.copy(volume = v.coerceIn(10, 100)) }
    fun setRamp(v: Int) = edit { it.copy(volumeRampSec = v.coerceIn(0, 120)) }
    fun setSnooze(v: Int) = edit { it.copy(snoozeMinutes = v.coerceIn(0, 60)) }

    fun accessibilityIntent() = guard.accessibilitySettingsIntent()
    fun deviceAdminIntent() = guard.deviceAdminIntent()

    fun save(hour: Int, minute: Int, onDone: () -> Unit) = viewModelScope.launch {
        repository.save(_state.value.alarm.copy(hour = hour, minute = minute))
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repository.delete(_state.value.alarm.id)
        onDone()
    }
}
