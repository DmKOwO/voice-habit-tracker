package com.voicehabit.tracker.core.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.voicehabit.tracker.core.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Одна запись бэкапа: таблица + строки. */
data class BackupTable(val name: String, val rows: List<Map<String, Any?>>)

data class BackupData(
    val app: String = "voice-habit-tracker",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tables: List<BackupTable> = emptyList()
)

data class ImportPreview(
    val tables: Map<String, Int>,
    val warnings: List<String>
) {
    val totalRows: Int get() = tables.values.sum()
}

/**
 * F13/G34–G36: экспорт/импорт JSON и еженедельный автобэкап.
 *
 * Формат — плоский JSON `{tables: [{name, rows}]}`: читается глазами, правится
 * руками, не зависит от версии Room. Импорт валидирует структуру до записи.
 */
class BackupManager(private val appContext: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(): File = withContext(Dispatchers.IO) {
        val container = AppContainer.get(appContext)
        val db = container.database
        val tables = mutableListOf<BackupTable>()
        tables += table(db, "habits", "SELECT * FROM habits")
        tables += table(db, "habit_logs", "SELECT * FROM habit_logs")
        tables += table(db, "tasks", "SELECT * FROM tasks")
        tables += table(db, "task_logs", "SELECT * FROM task_logs")
        tables += table(db, "subtasks", "SELECT * FROM subtasks")
        tables += table(db, "focus_sessions", "SELECT * FROM focus_sessions")
        tables += table(db, "day_marks", "SELECT * FROM day_marks")
        tables += table(db, "achievements", "SELECT * FROM achievements")
        tables += table(db, "challenges", "SELECT * FROM challenges")
        tables += table(db, "routines", "SELECT * FROM routines")
        tables += table(db, "review_log", "SELECT * FROM review_log")
        tables += table(db, "mood_log", "SELECT * FROM mood_log")
        tables += table(db, "voice_logs", "SELECT * FROM voice_logs")
        tables += table(db, "digests", "SELECT * FROM digests")

        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dir = File(appContext.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val file = File(dir, "duro-backup-$stamp.json")
        file.writeText(gson.toJson(BackupData(tables = tables)))
        container.settings.lastAutoBackupMillis = System.currentTimeMillis()
        file
    }

    private fun table(db: com.voicehabit.tracker.data.local.AppDatabase, name: String, sql: String): BackupTable {
        // Room не отдаёт сырые курсоры из DAO без @RawQuery — читаем через supportDb.
        val rows = mutableListOf<Map<String, Any?>>()
        val cursor = db.openHelper.readableDatabase.query(sql)
        cursor.use {
            val cols = (0 until it.columnCount).map { i -> it.getColumnName(i) }
            while (it.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (i in cols.indices) {
                    row[cols[i]] = when (it.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> it.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> it.getDouble(i)
                        else -> it.getString(i)
                    }
                }
                rows += row
            }
        }
        return BackupTable(name, rows)
    }

    suspend fun previewImport(json: String): ImportPreview = withContext(Dispatchers.Default) {
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            return@withContext ImportPreview(emptyMap(), listOf("Файл не похож на бэкап: ${e.message}"))
        }
        val warnings = mutableListOf<String>()
        if (data.app != "voice-habit-tracker") warnings += "Чужой формат: ${data.app}"
        val known = setOf(
            "habits", "habit_logs", "tasks", "task_logs", "subtasks", "focus_sessions",
            "day_marks", "achievements", "challenges", "routines", "review_log",
            "mood_log", "voice_logs", "digests"
        )
        val tables = mutableMapOf<String, Int>()
        for (table in data.tables) {
            if (table.name !in known) {
                warnings += "Неизвестная таблица пропущена: ${table.name}"
                continue
            }
            tables[table.name] = table.rows.size
        }
        ImportPreview(tables, warnings)
    }

    /** Импорт поверх текущих данных (upsert по id). Возвращает число строк. */
    suspend fun importValidated(json: String): Int = withContext(Dispatchers.IO) {
        val data = gson.fromJson(json, BackupData::class.java)
        val container = AppContainer.get(appContext)
        var count = 0
        container.transactionRunner {
            for (table in data.tables) {
                for (row in table.rows) {
                    if (insertRow(container, table.name, row)) count++
                }
            }
        }
        count
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun insertRow(container: AppContainer, table: String, row: Map<String, Any?>): Boolean {
        return try {
            val str = { key: String -> row[key]?.toString() }
            val long = { key: String -> (row[key] as? Number)?.toLong() }
            val int = { key: String -> (row[key] as? Number)?.toInt() }
            val dbl = { key: String -> (row[key] as? Number)?.toDouble() }
            val boolInt = { key: String -> if ((row[key] as? Number)?.toInt() == 1) 1 else 0 }
            when (table) {
                "habits" -> container.database.habitDao().upsertHabit(
                    com.voicehabit.tracker.data.local.entity.HabitEntity(
                        id = str("id") ?: return false,
                        title = str("title") ?: "",
                        category = str("category") ?: "Routine",
                        displayType = str("displayType") ?: "DAILY_CHECK",
                        colorHex = str("colorHex") ?: "#FF6B35",
                        quote = str("quote") ?: "",
                        targetValue = dbl("targetValue") ?: 1.0,
                        unit = str("unit"),
                        frequency = str("frequency") ?: "DAILY",
                        streak = int("streak") ?: 0,
                        bestStreak = int("bestStreak") ?: 0,
                        pinned = int("pinned") ?: 0,
                        reminderMin = int("reminderMin"),
                        archived = int("archived") ?: 0,
                        scheduleDays = str("scheduleDays") ?: "1,2,3,4,5,6,7",
                        tags = str("tags") ?: "",
                        deletedAt = long("deletedAt"),
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                "habit_logs" -> container.database.habitDao().insertHabitLog(
                    com.voicehabit.tracker.data.local.entity.HabitLogEntity(
                        id = str("id") ?: return false,
                        habitId = str("habitId") ?: return false,
                        valueLogged = dbl("valueLogged") ?: 1.0,
                        comment = str("comment"),
                        completedAt = long("completedAt") ?: System.currentTimeMillis()
                    )
                )
                "tasks" -> container.database.taskDao().insertTask(
                    com.voicehabit.tracker.data.local.entity.TaskEntity(
                        id = str("id") ?: return false,
                        title = str("title") ?: "",
                        dueDateIso = str("dueDateIso"),
                        priority = str("priority") ?: "MEDIUM",
                        category = str("category") ?: "General",
                        taskType = str("taskType") ?: "QUICK",
                        isCompleted = boolInt("isCompleted") == 1,
                        completedAt = long("completedAt"),
                        tags = str("tags") ?: "",
                        parentTaskId = str("parentTaskId"),
                        reminderMinutesBefore = int("reminderMinutesBefore"),
                        isArchived = int("isArchived") ?: 0,
                        deletedAt = long("deletedAt"),
                        recurrence = str("recurrence") ?: "NONE",
                        calendarEventId = long("calendarEventId"),
                        description = str("description") ?: "",
                        url = str("url"),
                        pinned = int("pinned") ?: 0,
                        estimatedMin = int("estimatedMin"),
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                "task_logs" -> container.database.taskDao().insertTaskLog(
                    com.voicehabit.tracker.data.local.entity.TaskLogEntity(
                        id = str("id") ?: return false,
                        taskId = str("taskId") ?: return false,
                        localDate = str("localDate") ?: return false,
                        completedAt = long("completedAt") ?: System.currentTimeMillis(),
                        zoneId = str("zoneId") ?: ""
                    )
                )
                "subtasks" -> container.database.subtaskDao().upsert(
                    com.voicehabit.tracker.data.local.entity.SubtaskEntity(
                        id = str("id") ?: return false,
                        taskId = str("taskId") ?: return false,
                        title = str("title") ?: "",
                        isDone = boolInt("isDone") == 1,
                        position = int("position") ?: 0
                    )
                )
                "focus_sessions" -> container.database.focusDao().insert(
                    com.voicehabit.tracker.data.local.entity.FocusSessionEntity(
                        id = str("id") ?: return false,
                        taskId = str("taskId"),
                        habitId = str("habitId"),
                        label = str("label") ?: "",
                        startedAt = long("startedAt") ?: System.currentTimeMillis(),
                        durationMin = int("durationMin") ?: 25,
                        completed = boolInt("completed") == 1
                    )
                )
                "day_marks" -> container.database.dayMarkDao().upsert(
                    com.voicehabit.tracker.data.local.entity.DayMarkEntity(
                        dateEpochDay = long("dateEpochDay") ?: return false,
                        mark = str("mark") ?: "FREEZE"
                    )
                )
                "achievements" -> container.database.achievementDao().unlock(
                    com.voicehabit.tracker.data.local.entity.AchievementEntity(
                        id = str("id") ?: return false,
                        unlockedAt = long("unlockedAt") ?: System.currentTimeMillis()
                    )
                )
                "challenges" -> container.database.challengeDao().upsert(
                    com.voicehabit.tracker.data.local.entity.ChallengeEntity(
                        id = str("id") ?: return false,
                        title = str("title") ?: "",
                        habitId = str("habitId"),
                        startEpochDay = long("startEpochDay") ?: 0L,
                        lengthDays = int("lengthDays") ?: 30,
                        note = str("note") ?: "",
                        isActive = boolInt("isActive") == 1,
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                "routines" -> container.database.routineDao().upsert(
                    com.voicehabit.tracker.data.local.entity.RoutineEntity(
                        id = str("id") ?: return false,
                        title = str("title") ?: "",
                        triggerPhrase = str("triggerPhrase") ?: "",
                        habitIdsCsv = str("habitIdsCsv") ?: "",
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                "review_log" -> container.database.reviewDao().upsert(
                    com.voicehabit.tracker.data.local.entity.ReviewLogEntity(
                        dateEpochDay = long("dateEpochDay") ?: return false,
                        summary = str("summary") ?: "",
                        tomorrowPlan = str("tomorrowPlan") ?: "",
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                "mood_log" -> container.database.moodDao().upsert(
                    com.voicehabit.tracker.data.local.entity.MoodEntity(
                        dateEpochDay = long("dateEpochDay") ?: return false,
                        mood = (int("mood") ?: 3).coerceIn(1, 5),
                        note = str("note") ?: ""
                    )
                )
                "voice_logs" -> container.database.voiceLogDao().upsertVoiceLog(
                    com.voicehabit.tracker.data.local.entity.VoiceLogEntity(
                        id = str("id") ?: return false,
                        audioPath = str("audioPath") ?: "",
                        rawTranscript = str("rawTranscript") ?: "",
                        summary = str("summary") ?: "",
                        status = str("status") ?: "PROCESSED",
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                // H1: конспект восстанавливается целиком. Режим и уверенность
                // кладутся в БД строками, поэтому нераспознанное значение должно
                // падать в DICTATE, а не ронять весь импорт бэкапа.
                "digests" -> container.database.digestDao().upsert(
                    com.voicehabit.tracker.data.local.entity.DigestEntity(
                        id = str("id") ?: return false,
                        voiceLogId = str("voiceLogId"),
                        title = str("title") ?: "",
                        gist = str("gist") ?: "",
                        keyPoints = str("keyPoints") ?: "",
                        decisions = str("decisions") ?: "",
                        openQuestions = str("openQuestions") ?: "",
                        nextSteps = str("nextSteps") ?: "",
                        people = str("people") ?: "",
                        numbers = str("numbers") ?: "",
                        tone = str("tone") ?: "",
                        mode = str("mode") ?: "DICTATE",
                        modeConfidence = (dbl("modeConfidence") ?: 0.0).toFloat(),
                        transcript = str("transcript") ?: "",
                        wordCount = int("wordCount") ?: 0,
                        speechSeconds = int("speechSeconds") ?: 0,
                        pinned = (int("pinned") ?: 0) != 0,
                        createdAt = long("createdAt") ?: System.currentTimeMillis()
                    )
                )
                else -> return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
