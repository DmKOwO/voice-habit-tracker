package com.voicehabit.tracker

import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.support.FakeHabitDao
import com.voicehabit.tracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitHistoryAndWidgetTest {

    @Test
    fun `toggleHabitCompletion toggles completion and updates streak`() = runTest {
        val dao = FakeHabitDao()
        val repo = HabitRepositoryImpl(dao)

        val habit = Habit(
            id = "habit_workout",
            title = "Workout",
            category = "Fitness",
            displayType = "GRID",
            colorHex = "#A855F7",
            quote = "Stay Hungry",
            targetValue = 1.0,
            currentStreak = 0,
            isCompletedToday = false
        )
        repo.insertOrUpdateHabit(habit)

        // 1. Toggle ON
        val checked = repo.toggleHabitCompletion("habit_workout")
        assertTrue("Expected habit to be completed after first toggle", checked)

        val updated = repo.getHabitById("habit_workout")
        assertNotNull(updated)
        assertTrue(updated!!.isCompletedToday)
        assertEquals(1, updated.currentStreak)

        // 2. Toggle OFF (uncheck)
        val unchecked = repo.toggleHabitCompletion("habit_workout")
        assertFalse("Expected habit to be uncompleted after second toggle", unchecked)

        val updated2 = repo.getHabitById("habit_workout")
        assertNotNull(updated2)
        assertFalse(updated2!!.isCompletedToday)
        assertEquals(0, updated2.currentStreak)
    }

    @Test
    fun `history 28 days computes correct heatmap mapping`() = runTest {
        val dao = FakeHabitDao()
        val repo = HabitRepositoryImpl(dao)

        val habit = Habit(
            id = "habit_bed",
            title = "Make Bed",
            category = "Morning",
            displayType = "STREAKS",
            colorHex = "#FF6B35",
            quote = "Win morning",
            targetValue = 1.0
        )
        repo.insertOrUpdateHabit(habit)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterdayMillis = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val twoDaysAgoMillis = today.minusDays(2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        dao.insertHabitLog(HabitLogEntity("log_1", "habit_bed", 1.0, null, yesterdayMillis))
        dao.insertHabitLog(HabitLogEntity("log_2", "habit_bed", 1.0, null, twoDaysAgoMillis))

        val list = repo.getAllHabitsList()
        assertEquals(1, list.size)
        val h = list.first()

        assertEquals(28, h.historyDaysCompleted.size)
        // Index 27 is today (not logged yet)
        assertFalse(h.historyDaysCompleted[27])
        // Index 26 is yesterday (logged)
        assertTrue(h.historyDaysCompleted[26])
        // Index 25 is 2 days ago (logged)
        assertTrue(h.historyDaysCompleted[25])
        // Index 24 is 3 days ago (not logged)
        assertFalse(h.historyDaysCompleted[24])
    }
}
