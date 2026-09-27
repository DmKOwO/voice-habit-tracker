package com.voicehabit.tracker

import com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CalculateStreakUseCaseTest {

    private val calculateStreak = CalculateStreakUseCase()

    @Test
    fun `empty logs return 0 streak`() {
        assertEquals(0, calculateStreak(emptyList()))
    }

    @Test
    fun `single log today returns 1 streak`() {
        val today = System.currentTimeMillis()
        assertEquals(1, calculateStreak(listOf(today)))
    }

    @Test
    fun `three consecutive days including today returns 3 streak`() {
        val cal = Calendar.getInstance()
        val d0 = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d1 = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d2 = cal.timeInMillis

        assertEquals(3, calculateStreak(listOf(d0, d1, d2)))
    }

    @Test
    fun `three consecutive days ending yesterday returns 3 streak`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d1 = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d2 = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d3 = cal.timeInMillis

        // Yesterday, day before, 3 days ago -> active streak of 3 waiting for today
        assertEquals(3, calculateStreak(listOf(d1, d2, d3)))
    }

    @Test
    fun `gap breaks streak`() {
        val cal = Calendar.getInstance()
        val d0 = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -2) // skipped yesterday
        val d2 = cal.timeInMillis

        assertEquals(1, calculateStreak(listOf(d0, d2)))
    }

    @Test
    fun `multiple logs on same day count as single streak day`() {
        // Время фиксируется явно: вариант с `now - 2 часа` был нестабилен —
        // между 00:00 и 02:00 две отметки попадали в разные сутки.
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val morning = today.atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        val evening = today.atTime(21, 40).atZone(zone).toInstant().toEpochMilli()

        assertEquals(1, calculateStreak(listOf(morning, evening)))
    }

    @Test
    fun `timezone boundary preserves streak in UTC+5 zone`() {
        val zone = java.time.ZoneId.of("Asia/Yekaterinburg")
        val useCase = CalculateStreakUseCase(zone)

        val todayDate = java.time.LocalDate.now(zone)
        val todayLateNight = todayDate.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val yesterdayEarlyMorning = todayDate.minusDays(1).atTime(1, 10).atZone(zone).toInstant().toEpochMilli()

        assertEquals(2, useCase(listOf(todayLateNight, yesterdayEarlyMorning)))
    }
}

class StreakFreezeTest {

    private val zone = java.time.ZoneId.systemDefault()

    private fun millisAt(daysAgo: Int, hour: Int = 12): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun epochDay(millis: Long): Long {
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
    }

    @Test
    fun `frozen day does not break streak`() {
        val useCase = CalculateStreakUseCase()
        // Сегодня и позавчера есть, вчера — заморозка: серия не рвётся (2),
        // но замороженный день в счётчик не идёт (не 3).
        val logs = listOf(millisAt(0), millisAt(2))
        val neutral = setOf(epochDay(millisAt(1)))
        org.junit.Assert.assertEquals(2, useCase(logs, neutral))
    }

    @Test
    fun `missing day without freeze still breaks streak`() {
        val useCase = CalculateStreakUseCase()
        val logs = listOf(millisAt(0), millisAt(2))
        org.junit.Assert.assertEquals(1, useCase(logs))
    }
}
