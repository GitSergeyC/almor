package com.example.alarmclock.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.alarmclock.domain.Alarm
import com.example.alarmclock.domain.AlarmGroup
import com.example.alarmclock.domain.AlarmWithGroup
import com.example.alarmclock.domain.GuardLevel
import com.example.alarmclock.domain.TaskType

@Entity(tableName = "alarm_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val enabled: Boolean,
    val exclusiveSetId: Long?,
    val sortOrder: Int
)

@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId"), Index("enabled")]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val groupId: Long?,
    val label: String,
    val soundUri: String?,
    val volume: Int,
    val volumeRampSec: Int,
    val vibrate: Boolean,
    val snoozeMinutes: Int,
    val snoozeLimit: Int,
    val taskType: String,
    val taskConfig: String,
    val taskCount: Int,
    val guardLevel: Int,
    val skipNextOccurrence: Boolean,
    val createdAt: Long
)

/**
 * Журнал срабатываний. Запись с dismissedAt == null означает, что будильник
 * не был закрыт штатно — основа для восстановления после перезагрузки.
 */
@Entity(
    tableName = "alarm_events",
    indices = [Index("alarmId", "firedAt"), Index("dismissedAt")]
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val firedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val dismissedAt: Long?,
    val snoozeCount: Int = 0,
    val taskAttempts: Int = 0,
    val taskFailures: Int = 0,
    val rebootDetected: Boolean = false,
    val escalated: Boolean = false
)

data class AlarmWithGroupEntity(
    @Embedded val alarm: AlarmEntity,
    @Relation(parentColumn = "groupId", entityColumn = "id")
    val group: GroupEntity?
)

// ---- Мапперы ----

fun AlarmEntity.toDomain() = Alarm(
    id = id, hour = hour, minute = minute, daysMask = daysMask, enabled = enabled,
    groupId = groupId, label = label, soundUri = soundUri, volume = volume,
    volumeRampSec = volumeRampSec, vibrate = vibrate, snoozeMinutes = snoozeMinutes,
    snoozeLimit = snoozeLimit, taskType = TaskType.of(taskType), taskConfig = taskConfig,
    taskCount = taskCount, guardLevel = GuardLevel.of(guardLevel),
    skipNextOccurrence = skipNextOccurrence, createdAt = createdAt
)

fun Alarm.toEntity() = AlarmEntity(
    id = id, hour = hour, minute = minute, daysMask = daysMask, enabled = enabled,
    groupId = groupId, label = label, soundUri = soundUri, volume = volume,
    volumeRampSec = volumeRampSec, vibrate = vibrate, snoozeMinutes = snoozeMinutes,
    snoozeLimit = snoozeLimit, taskType = taskType.id, taskConfig = taskConfig,
    taskCount = taskCount, guardLevel = guardLevel.level,
    skipNextOccurrence = skipNextOccurrence, createdAt = createdAt
)

fun GroupEntity.toDomain() = AlarmGroup(id, name, colorArgb, enabled, exclusiveSetId, sortOrder)

fun AlarmGroup.toEntity() = GroupEntity(id, name, colorArgb, enabled, exclusiveSetId, sortOrder)

fun AlarmWithGroupEntity.toDomain() = AlarmWithGroup(alarm.toDomain(), group?.toDomain())
