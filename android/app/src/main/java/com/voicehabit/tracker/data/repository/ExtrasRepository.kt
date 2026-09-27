package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.local.entity.AchievementEntity
import com.voicehabit.tracker.data.local.entity.ChallengeEntity
import com.voicehabit.tracker.data.local.entity.DayMarkEntity
import com.voicehabit.tracker.data.local.entity.FocusSessionEntity
import com.voicehabit.tracker.data.local.entity.ReviewLogEntity
import com.voicehabit.tracker.data.local.entity.RoutineEntity
import com.voicehabit.tracker.data.local.entity.SubtaskEntity
import com.voicehabit.tracker.domain.model.ALL_ACHIEVEMENTS
import com.voicehabit.tracker.domain.model.AppStats
import com.voicehabit.tracker.domain.model.Challenge
import com.voicehabit.tracker.domain.model.FocusSession
import com.voicehabit.tracker.domain.model.Routine
import com.voicehabit.tracker.domain.model.Subtask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Репозиторий «второго контура» (F2–F8, F15–F17): подзадачи, фокус-сессии, метки дней,
 * достижения, челленджи, рутины, вечерние разборы и сводная статистика.
 */
class ExtrasRepository(private val db: AppDatabase) {

    private val zone: ZoneId = ZoneId.systemDefault()

    // --- Подзадачи (F5) ---
    fun subtasksFlow(taskId: String): Flow<List<Subtask>> =
        db.subtaskDao().getForTaskFlow(taskId).map { list -> list.map { it.toDomain() } }

    suspend fun subtasksFor(taskId: String): List<Subtask> =
        db.subtaskDao().getForTask(taskId).map { it.toDomain() }

    suspend fun upsertSubtask(taskId: String, subtask: Subtask) {
        db.subtaskDao().upsert(
            SubtaskEntity(subtask.id, taskId, subtask.title, subtask.isDone, subtask.position)
        )
    }

    suspend fun setSubtaskDone(id: String, done: Boolean) = db.subtaskDao().setDone(id, done)
    suspend fun deleteSubtask(id: String) = db.subtaskDao().delete(id)
    fun newSubtaskId(): String = "sub_" + UUID.randomUUID().toString()

    /** Доля выполненных подзадач 0..1; null — подзадач нет. */
    suspend fun subtaskProgress(taskId: String): Double? {
        val list = subtasksFor(taskId)
        if (list.isEmpty()) return null
        return list.count { it.isDone }.toDouble() / list.size
    }

    // --- Фокус (F8) ---
    fun focusRecentFlow(): Flow<List<FocusSession>> =
        db.focusDao().recentFlow().map { list -> list.map { it.toDomain() } }

    suspend fun saveFocusSession(session: FocusSession) {
        db.focusDao().insert(
            FocusSessionEntity(session.id, session.taskId, session.habitId, session.label, session.startedAt, session.durationMin, session.completed)
        )
    }

    fun newFocusId(): String = "focus_" + UUID.randomUUID().toString()

    // --- Метки дней / freeze (F6) ---
    suspend fun marksInRange(fromEpochDay: Long, toEpochDay: Long): Set<Long> =
        db.dayMarkDao().range(fromEpochDay, toEpochDay).map { it.dateEpochDay }.toSet()

    suspend fun freezeCountThisWeek(): Int {
        val today = LocalDate.now(zone)
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        return db.dayMarkDao().freezeCountInWeek(monday, monday + 6)
    }

    /** Заморозить день: не более [MAX_FREEZE_PER_WEEK] в неделю. */
    suspend fun freezeDay(day: LocalDate): FreezeResult {
        if (freezeCountThisWeek() >= MAX_FREEZE_PER_WEEK) return FreezeResult.LIMIT
        db.dayMarkDao().upsert(DayMarkEntity(day.toEpochDay(), "FREEZE"))
        return FreezeResult.OK
    }

    suspend fun unfreezeDay(day: LocalDate) = db.dayMarkDao().delete(day.toEpochDay())

    // --- Достижения (F16) ---
    fun achievementsFlow(): Flow<Set<String>> =
        db.achievementDao().allFlow().map { list -> list.map { it.id }.toSet() }

    /**
     * Проверяет правила и разблокирует новые достижения.
     * Возвращает список только что разблокированных id (для тоста/конфетти).
     */
    suspend fun evaluateAchievements(snapshot: AchievementSnapshot): List<String> {
        val unlocked = db.achievementDao().all().map { it.id }.toSet()
        val newly = mutableListOf<String>()
        for (def in ALL_ACHIEVEMENTS) {
            if (def.id in unlocked) continue
            val earned = when (def.id) {
                "first_habit" -> snapshot.habitCount >= 1
                "first_voice" -> snapshot.voiceNotesProcessed >= 1
                "streak_7" -> snapshot.bestStreak >= 7
                "streak_30" -> snapshot.bestStreak >= 30
                "tasks_10" -> snapshot.tasksCompleted >= 10
                "tasks_100" -> snapshot.tasksCompleted >= 100
                "focus_5" -> snapshot.focusCompleted >= 5
                "early_bird" -> snapshot.earlyBirdToday
                "night_owl" -> snapshot.nightOwlToday
                "perfect_week" -> snapshot.perfectWeek
                "routine_master" -> snapshot.routineExecuted
                "challenge_done" -> snapshot.challengeDone
                else -> false
            }
            if (earned) {
                if (db.achievementDao().unlock(AchievementEntity(def.id)) != -1L) newly += def.id
            }
        }
        return newly
    }

    // --- Челленджи (F17) ---
    fun challengesFlow(): Flow<List<Challenge>> =
        db.challengeDao().allFlow().map { list -> list.map { it.toDomain() } }

    suspend fun upsertChallenge(challenge: Challenge) {
        db.challengeDao().upsert(
            ChallengeEntity(challenge.id, challenge.title, challenge.habitId, challenge.startEpochDay, challenge.lengthDays, challenge.note, challenge.isActive, challenge.createdAt)
        )
    }

    suspend fun deleteChallenge(id: String) = db.challengeDao().delete(id)
    fun newChallengeId(): String = "chl_" + UUID.randomUUID().toString()

    /** Сколько дней челленджа закрыто по логам привычки (начиная со startEpochDay). */
    suspend fun challengeProgress(challenge: Challenge, logDays: Set<Long>): Pair<Int, Int> {
        if (challenge.habitId == null) return 0 to challenge.lengthDays
        var done = 0
        for (offset in 0 until challenge.lengthDays) {
            if (challenge.startEpochDay + offset in logDays) done++
        }
        return done to challenge.lengthDays
    }

    // --- Рутины (F2) ---
    fun routinesFlow(): Flow<List<Routine>> =
        db.routineDao().allFlow().map { list -> list.map { it.toDomain() } }

    suspend fun allRoutines(): List<Routine> = db.routineDao().all().map { it.toDomain() }

    suspend fun upsertRoutine(routine: Routine) {
        db.routineDao().upsert(
            RoutineEntity(routine.id, routine.title, routine.triggerPhrase, routine.habitIds.joinToString(","))
        )
    }

    suspend fun deleteRoutine(id: String) = db.routineDao().delete(id)
    fun newRoutineId(): String = "rtn_" + UUID.randomUUID().toString()

    // --- Вечерний разбор (F3) ---
    fun reviewFlow(): Flow<List<Pair<Long, String>>> =
        db.reviewDao().recentFlow().map { list -> list.map { it.dateEpochDay to it.summary } }

    suspend fun saveReview(day: LocalDate, summary: String, tomorrowPlan: String) {
        db.reviewDao().upsert(ReviewLogEntity(day.toEpochDay(), summary, tomorrowPlan))
    }

    // --- Статистика (F15) ---
    suspend fun computeStats(): AppStats {
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val yearAgo = today.minusDays(364).toEpochDay()

        val logs = db.habitDao().getLogsBetween(
            LocalDate.ofEpochDay(yearAgo).atStartOfDay(zone).toInstant().toEpochMilli(), now
        )
        val days = logs.map { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
        val byWeekday = IntArray(7)
        var best = 0
        for (day in days) {
            val idx = (day.dayOfWeek.value - 1) % 7
            byWeekday[idx]++
            if (byWeekday[idx] > best) best = idx
        }
        val byCategory = logs.groupBy { log ->
            db.habitDao().getHabitById(log.habitId)?.category ?: "General"
        }.map { (cat, list) -> cat to list.size }.sortedByDescending { it.second }.take(6)

        val grid = BooleanArray(365) { false }
        for (day in days) {
            val offset = (day.toEpochDay() - yearAgo).toInt()
            if (offset in 0..364) grid[offset] = true
        }
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val focus = db.focusDao().since(0L)
        val focusTotal = focus.filter { it.completed }.sumOf { it.durationMin }
        val focusWeek = focus.filter { it.completed && it.startedAt >= weekStart }.sumOf { it.durationMin }
        val allTasks = db.taskDao().getAllTasksList()
        val doneTasks = allTasks.count { it.isCompleted }

        return AppStats(
            totalCompletions = logs.size,
            completionsByWeekday = byWeekday.toList(),
            bestWeekday = best,
            completionByCategory = byCategory,
            yearGrid = grid.toList(),
            yearGridStartEpochDay = yearAgo,
            focusMinutesTotal = focusTotal,
            focusMinutesWeek = focusWeek,
            currentBestStreak = db.habitDao().getAllHabitsList().maxOfOrNull { it.streak } ?: 0,
            tasksCompleted = doneTasks
        )
    }

    private fun SubtaskEntity.toDomain() = Subtask(id, taskId, title, isDone, position)
    private fun FocusSessionEntity.toDomain() = FocusSession(id, taskId, habitId, label, startedAt, durationMin, completed)
    private fun ChallengeEntity.toDomain() = Challenge(id, title, habitId, startEpochDay, lengthDays, note, isActive, createdAt)
    private fun RoutineEntity.toDomain() = Routine(id, title, triggerPhrase, habitIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }, createdAt)

    companion object {
        const val MAX_FREEZE_PER_WEEK = 2
    }
}

enum class FreezeResult { OK, LIMIT }

/** Снимок для движка достижений — собирается из БД вызывающим кодом. */
data class AchievementSnapshot(
    val habitCount: Int = 0,
    val voiceNotesProcessed: Int = 0,
    val bestStreak: Int = 0,
    val tasksCompleted: Int = 0,
    val focusCompleted: Int = 0,
    val earlyBirdToday: Boolean = false,
    val nightOwlToday: Boolean = false,
    val perfectWeek: Boolean = false,
    val routineExecuted: Boolean = false,
    val challengeDone: Boolean = false
)
