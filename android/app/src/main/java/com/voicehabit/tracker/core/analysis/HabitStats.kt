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
        // Окно пустое — не «выполнено на 100%», а «пока нечего выполнять».
        if (end < start) return WindowResult(0, 0, 0)
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
        // `scheduled == 0` раньше давало 100%, и еженедельная привычка, заведённая в
        // день отдыха, показывала «100%» ещё до первого тренировочного дня: человек
        // видел будто бы выполненный план, которого ещё не было. Ничего не запланировано —
        // значит и процент ненулевой быть не может.
        val pct = if (scheduled == 0) 0 else minOf(100, (completed * 100) / scheduled)
        return WindowResult(scheduled, completed, pct)
    }

    /**
     * P1. Привычки, которые **сегодня** вообще должны выполняться.
     *
     * ## Что было не так
     *
     * «Пульс продуктивности» показывал `2/4` и 50%: в знаменателе стояли все четыре
     * привычки, включая две с расписанием на другие дни. При четырёх привычках, из
     * которых сегодня запланированы две, честный ответ — 100% после их выполнения,
     * а не 50%, которая вечно висит и не двигается.
     *
     * ## Почему одна функция, а не проверка в трёх местах
     *
     * Тот же счёт стоял в сводке дня и в экспорте для Obsidian. Три копии одной
     * строки — три шанса снова разъехаться, поэтому «что запланировано на сегодня»
     * определено здесь и используется всеми тремя.
     *
     * Пустое расписание (`setOf()`) означает «каждый день» — так его трактует
     * [com.voicehabit.tracker.domain.model.Habit.isRestDay], и здесь он тоже не
     * считается днём отдыха, иначе у привычек без расписания процент был бы нулём.
     */
    fun <T> scheduledToday(habits: List<T>, todayDow: Int, isRestDay: (T) -> Boolean): List<T> =
        habits.filterNot(isRestDay)

    /** Знаменатель для процента «сегодня». Ноль означает «сегодня нечего делать». */
    fun <T> todayCounts(
        habits: List<T>,
        todayDow: Int,
        isRestDay: (T) -> Boolean,
        isCompleted: (T) -> Boolean
    ): Pair<Int, Int> {
        val scheduled = scheduledToday(habits, todayDow, isRestDay)
        return scheduled.count(isCompleted) to scheduled.size
    }
}
