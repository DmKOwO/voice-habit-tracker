package com.voicehabit.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getAllHabitsList(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: String): HabitEntity?

    /**
     * Важно: раньше здесь стоял `@Insert(REPLACE)`. В SQLite `INSERT OR REPLACE` —
     * это `DELETE` + `INSERT`, а у `habit_logs` объявлен `ON DELETE CASCADE`,
     * поэтому каждый чекбокс стирал всю историю привычки.
     * `@Upsert` выполняет `UPDATE`/`INSERT` и историю не трогает.
     */
    @Upsert
    suspend fun upsertHabit(habit: HabitEntity)

    @Query("UPDATE habits SET streak = :streak WHERE id = :id")
    suspend fun updateHabitStreak(id: String, streak: Int)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY completedAt DESC")
    suspend fun getLogsForHabit(habitId: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE completedAt >= :startOfDayTimestamp")
    suspend fun getTodayLogs(startOfDayTimestamp: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND completedAt >= :startOfDayTimestamp")
    suspend fun getTodayLogsForHabit(habitId: String, startOfDayTimestamp: Long): List<HabitLogEntity>

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND completedAt >= :startOfDayTimestamp")
    suspend fun deleteTodayLogsForHabit(habitId: String, startOfDayTimestamp: Long)

    @Query("SELECT * FROM habit_logs WHERE completedAt >= :startTimestamp AND completedAt <= :endTimestamp")
    suspend fun getLogsBetween(startTimestamp: Long, endTimestamp: Long): List<HabitLogEntity>

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId")
    suspend fun countLogsForHabit(habitId: String): Int

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: String)

    // F7 архив/корзина, F4 расписание, F14 теги
    @Query("UPDATE habits SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Int)

    @Query("UPDATE habits SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: String, deletedAt: Long?)

    @Query("UPDATE habits SET scheduleDays = :days, tags = :tags WHERE id = :id")
    suspend fun updateScheduleAndTags(id: String, days: String, tags: String)

    @Query("DELETE FROM habits WHERE deletedAt IS NOT NULL AND deletedAt < :olderThan")
    suspend fun purgeTrash(olderThan: Long): Int

    @Query("UPDATE habits SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Int)

    @Query("UPDATE habits SET reminderMin = :minutes WHERE id = :id")
    suspend fun setReminderMin(id: String, minutes: Int?)

    @Query("UPDATE habits SET bestStreak = :best WHERE id = :id AND bestStreak < :best")
    suspend fun updateBestStreak(id: String, best: Int)
}
