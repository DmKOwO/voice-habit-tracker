package com.voicehabit.tracker.domain.repository

import com.voicehabit.tracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitRepository {
    fun getAllHabitsFlow(): Flow<List<Habit>>
    suspend fun getAllHabitsList(): List<Habit>
    suspend fun getHabitById(id: String): Habit?
    suspend fun insertOrUpdateHabit(habit: Habit)
    suspend fun logHabitCompletion(habitId: String, value: Double, comment: String?)
    suspend fun toggleHabitCompletion(habitId: String): Boolean
    suspend fun toggleHabitDate(habitId: String, date: LocalDate): Boolean
    suspend fun deleteHabit(id: String)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun moveToTrash(id: String)
    suspend fun restoreFromTrash(id: String)
    suspend fun purgeTrash(olderThanMillis: Long): Int
    suspend fun updateScheduleAndTags(id: String, days: Set<Int>, tags: List<String>)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setReminderMin(id: String, minutes: Int?)
    suspend fun updateBestStreak(id: String, best: Int)
}
