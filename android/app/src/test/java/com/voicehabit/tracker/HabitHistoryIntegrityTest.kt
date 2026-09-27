package com.voicehabit.tracker

import com.voicehabit.tracker.data.local.TransactionRunner
import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.support.FakeHabitDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Регрессии целостности данных привычек.
 *
 * Главный регресс: `INSERT OR REPLACE` + `ON DELETE CASCADE` стирали всю историю
 * привычки при каждом чекбоксе (тепловая карта и проценты всегда были пустыми).
 */
class HabitHistoryIntegrityTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val todayNoon: Long = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val dayAt: (Long) -> Long = { daysAgo ->
        today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun repository(
        dao: FakeHabitDao,
        clock: () -> Long = { todayNoon },
        zoneId: ZoneId = zone,
        transactions: TransactionRunner = TransactionRunner.NoOp
    ) = HabitRepositoryImpl(
        dao,
        com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase(zoneId, clock),
        transactions,
        clock,
        zoneId
    )

    private val habit = Habit(id = "habit_bed", title = "Make Bed", targetValue = 1.0)

    @Test
    fun `toggling a habit never deletes its past logs`() = runTest {
        val dao = FakeHabitDao()
        val repo = repository(dao)

        repo.insertOrUpdateHabit(habit)
        repeat(6) { daysAgo -> dao.insertHabitLog(HabitLogEntity("log_$daysAgo", "habit_bed", 1.0, null, dayAt(daysAgo + 1L))) }
        assertEquals(6, dao.countLogsForHabit("habit_bed"))

        repeat(3) {
            val checked = repo.toggleHabitCompletion("habit_bed")
            assertTrue(checked)
            assertEquals("История не должна теряться при отметке", 7, dao.countLogsForHabit("habit_bed"))
            assertEquals(6, dao.logs.count { it.completedAt < todayNoon })

            val unchecked = repo.toggleHabitCompletion("habit_bed")
            assertFalse(unchecked)
            assertEquals("История не должна теряться при снятии отметки", 6, dao.countLogsForHabit("habit_bed"))
        }
    }

    @Test
    fun `upsert does not go through destructive replace path`() = runTest {
        val dao = FakeHabitDao()
        val repo = repository(dao)
        repo.insertOrUpdateHabit(habit)
        dao.insertHabitLog(HabitLogEntity("log_x", "habit_bed", 1.0, null, dayAt(1)))

        repo.logHabitCompletion("habit_bed", 1.0, "голосом")

        assertEquals(0, dao.destructiveUpsertCalls)
        assertEquals(2, dao.countLogsForHabit("habit_bed"))
    }

    @Test
    fun `history keeps marks for all last days and computes percentage`() = runTest {
        val dao = FakeHabitDao()
        val repo = repository(dao)
        repo.insertOrUpdateHabit(habit)
        dao.insertHabitLog(HabitLogEntity("log_1", "habit_bed", 1.0, null, dayAt(1)))
        dao.insertHabitLog(HabitLogEntity("log_2", "habit_bed", 1.0, null, dayAt(3)))

        val result = repo.getAllHabitsList().single()

        assertEquals(28, result.historyDaysCompleted.size)
        assertTrue(result.historyDaysCompleted[26])
        assertTrue(result.historyDaysCompleted[24])
        assertFalse(result.historyDaysCompleted[27])
        // 2 отмеченных дня из 28
        assertEquals(2 * 100 / 28, result.completionPercentage)
    }

    @Test
    fun `streak is persisted and history survives across day boundary`() = runTest {
        val dao = FakeHabitDao()
        val fixedZone = ZoneOffset.UTC
        val clockNow = { dayAt(0) }
        val repo = HabitRepositoryImpl(
            dao,
            com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase(fixedZone, clockNow),
            TransactionRunner.NoOp,
            clockNow,
            fixedZone
        )

        repo.insertOrUpdateHabit(habit)
        repo.logHabitCompletion("habit_bed", 1.0, null)
        repo.logHabitCompletion("habit_bed", 1.0, null)

        // Второй лог за тот же день не должен удваивать стрик и не должен стирать первый
        assertEquals(2, dao.countLogsForHabit("habit_bed"))
        assertEquals(1, dao.getHabitById("habit_bed")!!.streak)
    }

    @Test
    fun `multi step writes run inside a transaction`() = runTest {
        val calls = mutableListOf<String>()
        val recordingRunner = object : TransactionRunner {
            override suspend fun <T> invoke(block: suspend () -> T): T {
                calls.add("begin")
                return try {
                    block()
                } finally {
                    calls.add("commit")
                }
            }
        }

        val dao = FakeHabitDao()
        val repo = repository(dao, transactions = recordingRunner)
        repo.insertOrUpdateHabit(habit)

        calls.clear()
        repo.toggleHabitCompletion("habit_bed")

        assertEquals(listOf("begin", "commit"), calls)
    }

    @Test
    fun `history is computed in the injected time zone`() = runTest {
        val dao = FakeHabitDao()
        val utc = ZoneOffset.UTC
        // Полночь по UTC: в зоне UTC+5 это уже следующий день
        val instant = LocalDate.of(2026, 3, 10).atTime(21, 30).atZone(utc).toInstant().toEpochMilli()
        val repo = repository(dao, clock = { instant }, zoneId = utc)
        repo.insertOrUpdateHabit(habit)
        dao.insertHabitLog(HabitLogEntity("log_utc", "habit_bed", 1.0, null, instant))

        val result = repo.getAllHabitsList().single()
        assertTrue("Отметка должна попасть в последний день окна", result.historyDaysCompleted.last())
    }
}
