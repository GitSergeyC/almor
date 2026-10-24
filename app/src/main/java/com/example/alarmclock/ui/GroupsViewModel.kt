package com.example.alarmclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmclock.data.repo.AlarmRepository
import com.example.alarmclock.domain.AlarmGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class GroupRow(val group: AlarmGroup, val alarmCount: Int)
data class GroupsUiState(val groups: List<GroupRow> = emptyList())

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: AlarmRepository
) : ViewModel() {

    val state = combine(
        repository.observeGroups(),
        repository.observeAlarms()
    ) { groups, alarms ->
        GroupsUiState(
            groups = groups.map { g ->
                GroupRow(g, alarms.count { it.alarm.groupId == g.id })
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupsUiState())

    fun createGroup(name: String) = viewModelScope.launch {
        repository.saveGroup(
            AlarmGroup(
                name = name.trim(),
                colorArgb = PALETTE[Random.nextInt(PALETTE.size)],
                enabled = true
            )
        )
    }

    fun toggle(groupId: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setGroupEnabled(groupId, enabled)
    }

    fun deleteGroup(groupId: Long) = viewModelScope.launch {
        repository.deleteGroup(groupId)
    }

    companion object {
        private val PALETTE = listOf(
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
            0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt()
        )
    }
}
