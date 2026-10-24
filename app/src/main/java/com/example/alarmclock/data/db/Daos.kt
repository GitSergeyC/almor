package com.example.alarmclock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Transaction
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmWithGroupEntity>>

    @Transaction
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAll(): List<AlarmWithGroupEntity>

    @Transaction
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmWithGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET skipNextOccurrence = :skip WHERE id = :id")
    suspend fun setSkipNext(id: Long, skip: Boolean)

    @Query("UPDATE alarms SET groupId = :groupId WHERE id = :id")
    suspend fun setGroup(id: Long, groupId: Long?)

    @Query("SELECT COUNT(*) FROM alarms WHERE groupId = :groupId")
    suspend fun countInGroup(groupId: Long): Int
}

@Dao
interface GroupDao {

    @Query("SELECT * FROM alarm_groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM alarm_groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    @Query("SELECT * FROM alarm_groups WHERE exclusiveSetId = :setId AND id != :exceptId")
    suspend fun siblingsInSet(setId: Long, exceptId: Long): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("UPDATE alarm_groups SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarm_groups WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AlarmEventDao {

    @Insert
    suspend fun insert(event: AlarmEventEntity): Long

    @Update
    suspend fun update(event: AlarmEventEntity)

    @Query("SELECT * FROM alarm_events WHERE id = :id")
    suspend fun getById(id: Long): AlarmEventEntity?

    /** Незакрытые события за последние [sinceMillis] — признак принудительной перезагрузки. */
    @Query("SELECT * FROM alarm_events WHERE dismissedAt IS NULL AND firedAt >= :sinceMillis ORDER BY firedAt DESC")
    suspend fun unfinishedSince(sinceMillis: Long): List<AlarmEventEntity>

    @Query("UPDATE alarm_events SET dismissedAt = :now WHERE id = :id")
    suspend fun markDismissed(id: Long, now: Long)

    @Query("UPDATE alarm_events SET dismissedAt = :now WHERE dismissedAt IS NULL")
    suspend fun closeAllStale(now: Long)

    @Query("SELECT * FROM alarm_events ORDER BY firedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AlarmEventEntity>>
}
