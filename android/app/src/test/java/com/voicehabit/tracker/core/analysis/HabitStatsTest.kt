package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitStatsTest {

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test fun `только запланированные дни`() {
        // Пн/Чт/Вс: 2026-09-28 — понедельник
        val schedule = setOf(1, 4, 7)
        val logs = setOf(epoch(2026, 9, 28), epoch(2026, 10, 1))
        val r = HabitStats.windowCompletion(
            logEpochDays = logs,
            scheduleDays = schedule,
            windowStartEpochDay = epoch(2026, 9, 28),
            windowEndEpochDay = epoch(2026, 10, 4),
            createdEpochDay = epoch(2026, 9, 28),
            todayEpochDay = epoch(2026, 10, 4)
        )
        // Плановых дней: Пн 28.09, Чт 01.10, Вс 04.10 = 3; закрыты 2
        assertEquals(3, r.scheduled)
        assertEquals(2, r.completed)
        assertEquals(66, r.percentage)
    }

    @Test fun `дни до создания не считаются прогулами`() {
        val schedule = setOf(1, 4, 7)
        val logs = setOf(epoch(2026, 9, 28), epoch(2026, 10, 1))
        val r = HabitStats.windowCompletion(
            logEpochDays = logs,
            scheduleDays = schedule,
            windowStartEpochDay = epoch(2026, 9, 1),
            windowEndEpochDay = epoch(2026, 10, 4),
            createdEpochDay = epoch(2026, 9, 28),
            todayEpochDay = epoch(2026, 10, 4)
        )
        assertEquals(3, r.scheduled)
        assertEquals(2, r.completed)
    }

    @Test fun `всё закрыто после создания = 100 процентов`() {
        val schedule = setOf(1, 4, 7)
        val logs = setOf(epoch(2026, 9, 28), epoch(2026, 10, 1), epoch(2026, 10, 4))
        val r = HabitStats.windowCompletion(
            logEpochDays = logs,
            scheduleDays = schedule,
            windowStartEpochDay = epoch(2026, 9, 1),
            windowEndEpochDay = epoch(2026, 10, 4),
            createdEpochDay = epoch(2026, 9, 28),
            todayEpochDay = epoch(2026, 10, 4)
        )
        assertEquals(100, r.percentage)
    }

    @Test fun `будущее не считается`() {
        val schedule = (1..7).toSet()
        val r = HabitStats.windowCompletion(
            logEpochDays = emptySet(),
            scheduleDays = schedule,
            windowStartEpochDay = epoch(2026, 9, 28),
            windowEndEpochDay = epoch(2026, 12, 31),
            createdEpochDay = epoch(2026, 9, 28),
            todayEpochDay = epoch(2026, 9, 29)
        )
        assertEquals(2, r.scheduled)
        assertEquals(0, r.completed)
    }

    @Test fun `пустое окно = 100`() {
        val r = HabitStats.windowCompletion(
            logEpochDays = emptySet(),
            scheduleDays = setOf(1),
            windowStartEpochDay = epoch(2026, 10, 5),
            windowEndEpochDay = epoch(2026, 10, 4),
            createdEpochDay = epoch(2026, 9, 1)
        )
        assertEquals(100, r.percentage)
    }
}
