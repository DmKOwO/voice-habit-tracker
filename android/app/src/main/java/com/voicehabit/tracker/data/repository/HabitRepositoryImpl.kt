package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.local.TransactionRunner
import com.voicehabit.tracker.data.local.dao.HabitDao
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.repository.HabitRepository
import com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Репозиторий привычек.
 *
 * Ключевое отличие от прежней версии: `upsertHabit` (@Upsert) + `updateHabitStreak`
 * вместо `INSERT OR REPLACE`. В SQLite `INSERT OR REPLACE` — это `DELETE` + `INSERT`,
 * а у `habit_logs` объявлен `ON DELETE CASCADE`, поэтому каждый чекбокс стирал всю
 * историю привычки (тепловая карта, проценты, графики виджетов всегда были пустыми).
 *
 * Время и зона инъецируются: иначе невозможно тестировать смену суток и DST.
 */
class HabitRepositoryImpl(
    private val habitDao: HabitDao,
    private val calculateStreakUseCase: CalculateStreakUseCase = CalculateStreakUseCase(),
    private val transactionRunner: TransactionRunner = TransactionRunner.NoOp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** G6: замороженные дни не разрывают стрик. По умолчанию — пусто (тесты). */
    private val frozenDaysProvider: (suspend () -> Set<Long>)? = null
) : HabitRepository {

    private val streak: CalculateStreakUseCase = calculateStreakUseCase

    override fun getAllHabitsFlow(): Flow<List<Habit>> =
        habitDao.getAllHabitsFlow().map { entities -> buildHabits(entities) }

    override suspend fun getAllHabitsList(): List<Habit> = buildHabits(habitDao.getAllHabitsList())

    override suspend fun getHabitById(id: String): Habit? {
        val entity = habitDao.getHabitById(id) ?: return null
        val todayValue = habitDao.getTodayLogsForHabit(id, startOfDayTimestamp())
            .sumOf { it.valueLogged }
        return entity.toHabit(
            todayValue = todayValue,
            isCompletedToday = todayValue >= entity.targetValue,
            history = emptyList(),
            weekly = emptyList(),
            completionPercentage = 0
        )
    }

    override suspend fun insertOrUpdateHabit(habit: Habit) {
        habitDao.upsertHabit(habit.toEntity())
    }

    override suspend fun logHabitCompletion(habitId: String, value: Double, comment: String?) {
        transactionRunner {
            habitDao.insertHabitLog(
                HabitLogEntity(
                    id = newLogId(),
                    habitId = habitId,
                    valueLogged = value,
                    comment = comment,
                    completedAt = clock()
                )
            )
            recomputeStreak(habitId)
        }
    }

    override suspend fun toggleHabitCompletion(habitId: String): Boolean =
        transactionRunner {
            val startOfDay = startOfDayTimestamp()
            val habit = habitDao.getHabitById(habitId) ?: return@transactionRunner false
            val todayValue = habitDao.getTodayLogsForHabit(habitId, startOfDay)
                .sumOf { it.valueLogged }
            val isCompleted = todayValue >= habit.targetValue

            if (isCompleted) {
                habitDao.deleteTodayLogsForHabit(habitId, startOfDay)
                recomputeStreak(habitId)
                false
            } else {
                habitDao.insertHabitLog(
                    HabitLogEntity(
                        id = newLogId(),
                        habitId = habitId,
                        valueLogged = habit.targetValue,
                        comment = "Отмечено в приложении",
                        completedAt = clock()
                    )
                )
                recomputeStreak(habitId)
                true
            }
        }

    override suspend fun deleteHabit(id: String) {
        habitDao.deleteHabit(id)
    }

    /** Пересчитывает стрик и пишет его отдельным UPDATE — история логов не затрагивается. */
    private suspend fun recomputeStreak(habitId: String) {
        val logs = habitDao.getLogsForHabit(habitId)
        val neutral = frozenDaysProvider?.invoke() ?: emptySet()
        val newStreak = calculateStreakUseCase(logs.map { it.completedAt }, neutral)
        habitDao.updateHabitStreak(habitId, newStreak)
        habitDao.updateBestStreak(habitId, newStreak)
    }

    private suspend fun buildHabits(entities: List<HabitEntity>): List<Habit> {
        if (entities.isEmpty()) return emptyList()

        val now = clock()
        val startOfDay = startOfDayTimestamp(now)
        val todayLogsGrouped = habitDao.getTodayLogs(startOfDay).groupBy { it.habitId }

        val windowStart = startOfDay - (HISTORY_DAYS - 1L) * MILLIS_PER_DAY
        val pastLogsGrouped = habitDao.getLogsBetween(windowStart, now).groupBy { it.habitId }

        return entities.map { entity ->
            val todayValue = todayLogsGrouped[entity.id]?.sumOf { it.valueLogged } ?: 0.0
            val history = computeHistory(entity.id, pastLogsGrouped, now)
            entity.toHabit(
                todayValue = todayValue,
                isCompletedToday = todayValue >= entity.targetValue,
                history = history,
                weekly = computeWeekly(entity.id, pastLogsGrouped, now),
                completionPercentage = if (history.isEmpty()) 0 else (history.count { it } * 100) / history.size
            )
        }
    }

    private fun computeHistory(
        habitId: String,
        pastLogsGrouped: Map<String, List<HabitLogEntity>>,
        now: Long
    ): List<Boolean> {
        val epochs = logDayEpochs(pastLogsGrouped[habitId], now)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startEpoch = today.toEpochDay() - (HISTORY_DAYS - 1)
        return (0 until HISTORY_DAYS).map { offset -> epochs.contains(startEpoch + offset) }
    }

    private fun computeWeekly(
        habitId: String,
        pastLogsGrouped: Map<String, List<HabitLogEntity>>,
        now: Long
    ): List<Boolean> {
        val epochs = logDayEpochs(pastLogsGrouped[habitId], now)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val mondayEpoch = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        return (0 until 7).map { offset -> epochs.contains(mondayEpoch + offset) }
    }

    private fun logDayEpochs(logs: List<HabitLogEntity>?, now: Long): Set<Long> =
        logs.orEmpty().map { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate().toEpochDay() }.toSet()

    private fun startOfDayTimestamp(now: Long = clock()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun newLogId(): String = "log_" + UUID.randomUUID().toString()

    private fun HabitEntity.toHabit(
        todayValue: Double,
        isCompletedToday: Boolean,
        history: List<Boolean>,
        weekly: List<Boolean>,
        completionPercentage: Int
    ) = Habit(
        id = id,
        title = title,
        category = category,
        displayType = displayType,
        colorHex = colorHex,
        quote = quote,
        targetValue = targetValue,
        unit = unit,
        frequency = frequency,
        currentStreak = streak,
        isCompletedToday = isCompletedToday,
        todayValue = todayValue,
        historyDaysCompleted = history,
        weeklyCompletions = weekly,
        completionPercentage = completionPercentage,
        archived = archived == 1,
        bestStreak = bestStreak,
        pinned = pinned == 1,
        reminderMin = reminderMin,
        scheduleDays = scheduleDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet().ifEmpty { (1..7).toSet() },
        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        deletedAt = deletedAt,
        createdAt = createdAt
    )

    private fun Habit.toEntity() = HabitEntity(
        id = id,
        title = title,
        category = category,
        displayType = displayType,
        colorHex = colorHex,
        quote = quote,
        targetValue = targetValue,
        unit = unit,
        frequency = frequency,
        streak = currentStreak,
        bestStreak = maxOf(bestStreak, currentStreak),
        pinned = if (pinned) 1 else 0,
        reminderMin = reminderMin,
        archived = if (archived) 1 else 0,
        scheduleDays = scheduleDays.sorted().joinToString(","),
        tags = tags.joinToString(","),
        deletedAt = deletedAt,
        createdAt = createdAt
    )

    override suspend fun setArchived(id: String, archived: Boolean) {
        habitDao.setArchived(id, if (archived) 1 else 0)
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        habitDao.setPinned(id, if (pinned) 1 else 0)
    }

    override suspend fun setReminderMin(id: String, minutes: Int?) {
        habitDao.setReminderMin(id, minutes)
    }

    override suspend fun updateBestStreak(id: String, best: Int) {
        habitDao.updateBestStreak(id, best)
    }

    override suspend fun moveToTrash(id: String) {
        habitDao.setDeletedAt(id, clock())
    }

    override suspend fun restoreFromTrash(id: String) {
        habitDao.setDeletedAt(id, null)
    }

    override suspend fun purgeTrash(olderThanMillis: Long): Int {
        return habitDao.purgeTrash(clock() - olderThanMillis)
    }

    override suspend fun updateScheduleAndTags(id: String, days: Set<Int>, tags: List<String>) {
        val safeDays = days.ifEmpty { (1..7).toSet() }
        habitDao.updateScheduleAndTags(id, safeDays.sorted().joinToString(","), tags.joinToString(","))
    }

    private companion object {
        const val HISTORY_DAYS = 28
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
