package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.data.local.entity.HabitEntity

/**
 * Кодировка расширенной семантики привычек в поле tags (CSV).
 * Отдельных колонок в БД нет специально: миграция ради трёх флагов на личном
 * этапе — лишний риск потерять данные. Когда продукт пойдёт в массы —
 * перенести в колонки миграцией.
 * Теги: "avoid" = привычка-воздержание, "perweek:N" = цель N раз в неделю.
 */
object HabitTagCodec {
    private const val AVOID = "avoid"
    private const val PERWEEK_PREFIX = "perweek:"

    fun isAvoid(habit: HabitEntity): Boolean = isAvoidTags(habit.tags)

    fun weeklyTarget(habit: HabitEntity): Int = weeklyTargetTags(habit.tags)

    /** Та же логика для доменной модели (tags списком). */
    fun isAvoidTags(tagsCsv: String): Boolean =
        tagsCsv.split(",").map { it.trim().lowercase() }.contains(AVOID)

    fun weeklyTargetTags(tagsCsv: String): Int =
        tagsCsv.split(",").map { it.trim().lowercase() }
            .firstOrNull { it.startsWith(PERWEEK_PREFIX) }
            ?.removePrefix(PERWEEK_PREFIX)?.toIntOrNull()?.coerceIn(1, 7) ?: 0

    fun withAvoid(tags: String, avoid: Boolean): String {
        val parts = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() != AVOID }.toMutableList()
        if (avoid) parts.add(AVOID)
        return parts.joinToString(",")
    }

    fun withWeeklyTarget(tags: String, perWeek: Int): String {
        val parts = tags.split(",").map { it.trim() }
            .filter { it.isNotEmpty() && !it.lowercase().startsWith(PERWEEK_PREFIX) }.toMutableList()
        if (perWeek in 1..7) parts.add("$PERWEEK_PREFIX$perWeek")
        return parts.joinToString(",")
    }
}
