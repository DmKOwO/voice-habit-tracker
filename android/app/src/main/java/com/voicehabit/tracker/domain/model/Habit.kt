package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Habit(
    val id: String,
    val title: String,
    val category: String = "Routine",
    val displayType: String = "DAILY_CHECK", // STREAKS, GRID, DAILY_CHECK, BAR_GRAPH, MINIMAL
    val colorHex: String = "#FF6B35",
    val quote: String = "",
    val targetValue: Double = 1.0,
    val unit: String? = null,
    val frequency: String = "DAILY",
    val currentStreak: Int = 0,
    val isCompletedToday: Boolean = false,
    val todayValue: Double = 0.0,
    val historyDaysCompleted: List<Boolean> = emptyList(),
    val weeklyCompletions: List<Boolean> = emptyList(),
    val completionPercentage: Int = 0,
    val archived: Boolean = false,
    val bestStreak: Int = 0,
    val pinned: Boolean = false,
    val reminderMin: Int? = null,
    /** Дни недели 1..7 (1=пн), когда привычка активна. Пусто = все дни. */
    val scheduleDays: Set<Int> = (1..7).toSet(),
    val tags: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Сегодня — день отдыха по расписанию (не влияет на стрик). */
    fun isRestDay(todayDow: Int): Boolean = scheduleDays.isNotEmpty() && todayDow !in scheduleDays
}
