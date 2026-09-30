package com.voicehabit.tracker.core.analysis

import java.time.LocalDate

/**
 * Математика выполнения привычек по расписанию.
 *
 * Два правила, которые раньше нарушались и из-за которых процент врал:
 * 1. Считаются только запланированные дни (Пн/Чт/Вс, а не все 7).
 * 2. Окно начинается с даты создания привычки: дни до создания — не «прогулы».
 *    Привычка, заведённая 3 дня назад с расписанием Пн/Чт/Вс и выполненная
 *    в оба прошедших плановых дня, показывает 100%, а не 15%.
 * Будущие дни тоже не считаются: незакрытое «завтра» — не провал.
 */
object HabitStats {

    data class WindowResult(
        val scheduled: Int,
        val completed: Int,
        val percentage: Int
    )

    fun windowCompletion(
        logEpochDays: Set<Long>,
        scheduleDays: Set<Int>,
        windowStartEpochDay: Long,
        windowEndEpochDay: Long,
        createdEpochDay: Long,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): WindowResult {
        val start = maxOf(windowStartEpochDay, createdEpochDay)
        val end = minOf(windowEndEpochDay, todayEpochDay)
        if (end < start) return WindowResult(0, 0, 100)
        var scheduled = 0
        var completed = 0
        var day = start
        while (day <= end) {
            if (LocalDate.ofEpochDay(day).dayOfWeek.value in scheduleDays) {
                scheduled++
                if (day in logEpochDays) completed++
            }
            day++
        }
        val pct = if (scheduled == 0) 100 else minOf(100, (completed * 100) / scheduled)
        return WindowResult(scheduled, completed, pct)
    }
}
