package com.example.alarmclock.di

import android.content.Context
import androidx.room.Room
import com.example.alarmclock.data.db.AlarmDao
import com.example.alarmclock.data.db.AlarmEventDao
import com.example.alarmclock.data.db.AppDatabase
import com.example.alarmclock.data.db.GroupDao
import com.example.alarmclock.tasks.MathTask
import com.example.alarmclock.tasks.ShakeTask
import com.example.alarmclock.tasks.TypingTask
import com.example.alarmclock.tasks.WakeUpTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // ВНИМАНИЕ: никакого fallbackToDestructiveMigration.
            // Потерянные при обновлении будильники = проспанная работа.
            .build()

    @Provides fun provideAlarmDao(db: AppDatabase): AlarmDao = db.alarmDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun provideEventDao(db: AppDatabase): AlarmEventDao = db.eventDao()

    /** Регистрация заданий. Новый тип — добавить сюда одну строку. */
    @Provides
    @ElementsIntoSet
    fun provideTasks(
        math: MathTask,
        shake: ShakeTask,
        typing: TypingTask
    ): Set<@JvmSuppressWildcards WakeUpTask> = setOf(math, shake, typing)
}
