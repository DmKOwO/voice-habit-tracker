package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Subtask(val id: String, val taskId: String, val title: String, val isDone: Boolean = false, val position: Int = 0)

@Immutable
data class FocusSession(
    val id: String,
    val taskId: String? = null,
    val habitId: String? = null,
    val label: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val durationMin: Int = 25,
    val completed: Boolean = false
)

@Immutable
data class Challenge(
    val id: String,
    val title: String,
    val habitId: String? = null,
    val startEpochDay: Long = 0L,
    val lengthDays: Int = 30,
    val note: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class Routine(
    val id: String,
    val title: String,
    val triggerPhrase: String = "",
    val habitIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** Определение достижения (F16): правило проверяется движком по данным. */
@Immutable
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = ""
)

val ALL_ACHIEVEMENTS = listOf(
    AchievementDef("first_habit", "Первая привычка", "Создайте первую привычку", "habit"),
    AchievementDef("first_voice", "Голосовой старт", "Первая запись голосом разобрана", "voice"),
    AchievementDef("streak_7", "Неделя дисциплины", "Стрик 7 дней подряд", "streak7"),
    AchievementDef("streak_30", "Месяц дисциплины", "Стрик 30 дней подряд", "streak30"),
    AchievementDef("tasks_10", "Десятка", "Закрыто 10 задач", "task10"),
    AchievementDef("tasks_100", "Сотня", "Закрыто 100 задач", "task100"),
    AchievementDef("focus_5", "Глубокая работа", "5 завершённых фокус-сессий", "focus5"),
    AchievementDef("early_bird", "Ранний подъём", "Отметка до 7:00 утра", "early"),
    AchievementDef("night_owl", "Поздний фокус", "Активность после 23:00", "night"),
    AchievementDef("perfect_week", "Идеальная неделя", "7 дней подряд без пропусков", "week"),
    AchievementDef("routine_master", "Мастер рутин", "Выполнена голосовая рутина", "routine"),
    AchievementDef("challenge_done", "Челлендж пройден", "Завершён 30-дневный челлендж", "challenge")
)

/** Сводная статистика для экрана (F15). */
@Immutable
data class AppStats(
    val totalCompletions: Int = 0,
    val completionsByWeekday: List<Int> = List(7) { 0 },
    val bestWeekday: Int = 0,
    val completionByCategory: List<Pair<String, Int>> = emptyList(),
    val yearGrid: List<Boolean> = emptyList(),
    val yearGridStartEpochDay: Long = 0L,
    val focusMinutesTotal: Int = 0,
    val focusMinutesWeek: Int = 0,
    val currentBestStreak: Int = 0,
    val tasksCompleted: Int = 0,
    val stepsToday: Int? = null,
    val sleepHoursLastNight: Double? = null,
    val healthAvailable: Boolean = false
)
