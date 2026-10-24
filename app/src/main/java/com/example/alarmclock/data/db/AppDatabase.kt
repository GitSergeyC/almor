package com.example.alarmclock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, GroupEntity::class, AlarmEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun groupDao(): GroupDao
    abstract fun eventDao(): AlarmEventDao

    companion object {
        const val NAME = "alarm.db"
        // Миграции добавлять сюда. Никогда не используйте
        // fallbackToDestructiveMigration в приложении-будильнике:
        // потеря будильников при обновлении = проспанная работа.
        // val MIGRATION_1_2 = object : Migration(1, 2) { ... }
    }
}
