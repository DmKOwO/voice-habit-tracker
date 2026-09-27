package com.voicehabit.tracker.support

import com.voicehabit.tracker.data.local.dao.HabitDao
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory заглушка [HabitDao], повторяющая поведение настоящей SQLite.
 *
 * Ключевая деталь: `insertOrUpdateHabit` эмулирует `ON DELETE CASCADE`, то есть
 * реализацию через DELETE + INSERT. Именно из-за этого старый
 * `INSERT OR REPLACE` стирал всю историю привычки при каждом чекбоксе. Заглушка
 * специально воспроизводит это поведение, чтобы регрессия ловилась тестом,
 * а не пользователем на устройстве.
 */
class FakeHabitDao : HabitDao {

    val habits = mutableListOf<HabitEntity>()
    val logs = mutableListOf<HabitLogEntity>()

    /** Счётчик обращений к «опасной» операции: 0 = используется безопасный путь. */
    var destructiveUpsertCalls: Int = 0
        private set

    override fun getAllHabitsFlow(): Flow<List<HabitEntity>> = flowOf(habits.toList())

    override suspend fun getAllHabitsList(): List<HabitEntity> = habits.toList()

    override suspend fun getHabitById(id: String): HabitEntity? = habits.find { it.id == id }

    override suspend fun upsertHabit(habit: HabitEntity) {
        val index = habits.indexOfFirst { it.id == habit.id }
        if (index >= 0) {
            habits[index] = habit
        } else {
            habits.add(habit)
        }
    }

    /**
     * Реализация «как раньше»: DELETE + INSERT с каскадным удалением логов.
     * Используется в тестах как ловушка регрессии.
     */
    fun destructiveReplaceHabit(habit: HabitEntity) {
        destructiveUpsertCalls++
        habits.removeAll { it.id == habit.id }
        logs.removeAll { it.habitId == habit.id }
        habits.add(habit)
    }

    override suspend fun updateHabitStreak(id: String, streak: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(streak = streak)
    }

    override suspend fun insertHabitLog(log: HabitLogEntity) {
        logs.add(log)
    }

    override suspend fun getLogsForHabit(habitId: String): List<HabitLogEntity> =
        logs.filter { it.habitId == habitId }.sortedByDescending { it.completedAt }

    override suspend fun getTodayLogs(startOfDayTimestamp: Long): List<HabitLogEntity> =
        logs.filter { it.completedAt >= startOfDayTimestamp }

    override suspend fun getTodayLogsForHabit(habitId: String, startOfDayTimestamp: Long): List<HabitLogEntity> =
        logs.filter { it.habitId == habitId && it.completedAt >= startOfDayTimestamp }

    override suspend fun deleteTodayLogsForHabit(habitId: String, startOfDayTimestamp: Long) {
        logs.removeAll { it.habitId == habitId && it.completedAt >= startOfDayTimestamp }
    }

    override suspend fun getLogsBetween(startTimestamp: Long, endTimestamp: Long): List<HabitLogEntity> =
        logs.filter { it.completedAt in startTimestamp..endTimestamp }

    override suspend fun countLogsForHabit(habitId: String): Int = logs.count { it.habitId == habitId }

    override suspend fun deleteHabit(id: String) {
        habits.removeAll { it.id == id }
        logs.removeAll { it.habitId == id }
    }

    override suspend fun setArchived(id: String, archived: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(archived = archived)
    }

    override suspend fun setDeletedAt(id: String, deletedAt: Long?) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(deletedAt = deletedAt)
    }

    override suspend fun updateScheduleAndTags(id: String, days: String, tags: String) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(scheduleDays = days, tags = tags)
    }

    override suspend fun purgeTrash(olderThan: Long): Int {
        val doomed = habits.filter { it.deletedAt != null && it.deletedAt < olderThan }.map { it.id }
        doomed.forEach { deleteHabit(it) }
        return doomed.size
    }

    override suspend fun setPinned(id: String, pinned: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(pinned = pinned)
    }

    override suspend fun setReminderMin(id: String, minutes: Int?) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0) habits[index] = habits[index].copy(reminderMin = minutes)
    }

    override suspend fun updateBestStreak(id: String, best: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index >= 0 && habits[index].bestStreak < best) {
            habits[index] = habits[index].copy(bestStreak = best)
        }
    }
}
