package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voicehabit.tracker.domain.model.IntentMode

/** Подзадача (F5): чек-лист внутри задачи, авто-«выполнено» при 100%. */
@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isDone: Boolean = false,
    val position: Int = 0
)

/** Сессия фокуса (F8): Pomodoro, привязанная к задаче/привычке. */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAt")]
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val habitId: String? = null,
    val label: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val durationMin: Int = 25,
    val completed: Boolean = false
)

/** Метка дня (F6): FREEZE — пропуск без обнуления стрика. */
@Entity(tableName = "day_marks")
data class DayMarkEntity(
    @PrimaryKey val dateEpochDay: Long,
    val mark: String = "FREEZE"
)

/** Разблокированное достижение (F16). */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long = System.currentTimeMillis()
)

/** Челлендж на N дней (F17), обычно привязан к привычке. */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val habitId: String? = null,
    val startEpochDay: Long = 0L,
    val lengthDays: Int = 30,
    val note: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** Голосовой макрос (F2): одна фраза отмечает несколько привычек. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val triggerPhrase: String = "",
    val habitIdsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Настроение дня 1–5 (G26). */
@Entity(tableName = "mood_log")
data class MoodEntity(
    @PrimaryKey val dateEpochDay: Long,
    val mood: Int = 3,
    val note: String = ""
)

/** Запись вечернего разбора дня (F3). */
@Entity(tableName = "review_log")
data class ReviewLogEntity(
    @PrimaryKey val dateEpochDay: Long,
    val summary: String = "",
    val tomorrowPlan: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * H1. Конспект свободного потока — «диктофон, который сделал выжимку».
 *
 * Отдельная таблица, а не поле в `voice_logs`, потому что конспектов у пользователя
 * накапливается много, у каждого своя жизнь: он ищется, читается, удаляется отдельно,
 * и перезапись одной записи голоса не должна стирать весь разговор. Списки хранятся
 * в CSV-колонках по образцу [RoutineEntity]: составные секции короткие, длина строки
 * не мешает, а JOIN на семь таблиц ради одного экрана — мешает.
 */
@Entity(
    tableName = "digests",
    indices = [Index(value = ["createdAt"]), Index(value = ["mode"])]
)
data class DigestEntity(
    @PrimaryKey val id: String,
    /** Ссылка на запись в `voice_logs`, если конспект вырос из голоса. */
    val voiceLogId: String? = null,
    val title: String = "",
    val gist: String = "",
    val keyPoints: String = "",
    val decisions: String = "",
    val openQuestions: String = "",
    val nextSteps: String = "",
    val people: String = "",
    val numbers: String = "",
    val tone: String = "",
    /** [IntentMode.name] — режим, который определил детектор. */
    val mode: String = IntentMode.DICTATE.name,
    val modeConfidence: Float = 0f,
    val transcript: String = "",
    val wordCount: Int = 0,
    val speechSeconds: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
