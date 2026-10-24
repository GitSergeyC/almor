package com.example.alarmclock.data.repo

import com.example.alarmclock.data.db.AlarmDao
import com.example.alarmclock.data.db.AlarmEventDao
import com.example.alarmclock.data.db.AlarmEventEntity
import com.example.alarmclock.data.db.GroupDao
import com.example.alarmclock.data.db.toDomain
import com.example.alarmclock.data.db.toEntity
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.domain.AlarmGroup
import com.example.alarmclock.domain.AlarmWithGroup
import com.example.alarmclock.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственная точка записи будильников. Любое изменение обязательно
 * заканчивается пересчётом расписания — иначе БД и AlarmManager разъедутся.
 */
@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
    private val groupDao: GroupDao,
    private val eventDao: AlarmEventDao,
    private val scheduler: AlarmScheduler
) {

    fun observeAlarms(): Flow<List<AlarmWithGroup>> =
        alarmDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeGroups(): Flow<List<AlarmGroup>> =
        groupDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAlarms(): List<AlarmWithGroup> = alarmDao.getAll().map { it.toDomain() }

    suspend fun getAlarm(id: Long): AlarmWithGroup? = alarmDao.getById(id)?.toDomain()

    suspend fun save(alarm: Alarm): Long {
        val id = if (alarm.id == 0L) {
            alarmDao.insert(alarm.toEntity())
        } else {
            alarmDao.update(alarm.toEntity()); alarm.id
        }
        scheduler.rescheduleAll()
        return id
    }

    suspend fun delete(id: Long) {
        alarmDao.delete(id)
        scheduler.rescheduleAll()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        alarmDao.setEnabled(id, enabled)
        // Включение будильника снимает разовый пропуск - иначе пользователь
        // включает тумблер, а звонка нет.
        if (enabled) alarmDao.setSkipNext(id, false)
        scheduler.rescheduleAll()
    }

    suspend fun setSkipNext(id: Long, skip: Boolean) {
        alarmDao.setSkipNext(id, skip)
        scheduler.rescheduleAll()
    }

    // ---- Группы ----

    suspend fun saveGroup(group: AlarmGroup): Long {
        val id = if (group.id == 0L) groupDao.insert(group.toEntity())
        else { groupDao.update(group.toEntity()); group.id }
        scheduler.rescheduleAll()
        return id
    }

    /**
     * Переключение группы одной кнопкой.
     * Личные флаги будильников НЕ трогаются: при обратном включении
     * группы восстанавливается ровно то состояние, что было до выключения.
     * Если группа состоит во взаимоисключающем наборе — остальные гасим.
     */
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean) {
        val group = groupDao.getById(groupId) ?: return
        groupDao.setEnabled(groupId, enabled)

        if (enabled && group.exclusiveSetId != null) {
            groupDao.siblingsInSet(group.exclusiveSetId, groupId)
                .forEach { groupDao.setEnabled(it.id, false) }
        }
        scheduler.rescheduleAll()
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.delete(groupId) // FK ON DELETE SET NULL освободит будильники
        scheduler.rescheduleAll()
    }

    suspend fun assignToGroup(alarmId: Long, groupId: Long?) {
        alarmDao.setGroup(alarmId, groupId)
        scheduler.rescheduleAll()
    }

    // ---- Журнал срабатываний ----

    suspend fun openEvent(alarmId: Long): Long =
        eventDao.insert(AlarmEventEntity(alarmId = alarmId, firedAt = System.currentTimeMillis(), dismissedAt = null))

    suspend fun getEvent(id: Long) = eventDao.getById(id)

    suspend fun updateEvent(event: AlarmEventEntity) = eventDao.update(event)

    suspend fun closeEvent(eventId: Long) =
        eventDao.markDismissed(eventId, System.currentTimeMillis())

    suspend fun unfinishedEventsSince(millis: Long) = eventDao.unfinishedSince(millis)

    suspend fun closeAllStaleEvents() = eventDao.closeAllStale(System.currentTimeMillis())
}
