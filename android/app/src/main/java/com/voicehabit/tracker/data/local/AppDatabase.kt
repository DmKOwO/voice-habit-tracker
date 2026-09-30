package com.voicehabit.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voicehabit.tracker.data.local.dao.AchievementDao
import com.voicehabit.tracker.data.local.dao.ChallengeDao
import com.voicehabit.tracker.data.local.dao.DayMarkDao
import com.voicehabit.tracker.data.local.dao.DigestDao
import com.voicehabit.tracker.data.local.dao.FocusDao
import com.voicehabit.tracker.data.local.dao.HabitDao
import com.voicehabit.tracker.data.local.dao.MoodDao
import com.voicehabit.tracker.data.local.dao.ProgrammeDao
import com.voicehabit.tracker.data.local.dao.ReviewDao
import com.voicehabit.tracker.data.local.dao.RoutineDao
import com.voicehabit.tracker.data.local.dao.SubtaskDao
import com.voicehabit.tracker.data.local.dao.TaskDao
import com.voicehabit.tracker.data.local.dao.VoiceLogDao
import com.voicehabit.tracker.data.local.entity.AchievementEntity
import com.voicehabit.tracker.data.local.entity.ChallengeEntity
import com.voicehabit.tracker.data.local.entity.DayMarkEntity
import com.voicehabit.tracker.data.local.entity.DigestEntity
import com.voicehabit.tracker.data.local.entity.FocusSessionEntity
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.HabitLogEntity
import com.voicehabit.tracker.data.local.entity.MoodEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeDayEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeExerciseEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeLogEntity
import com.voicehabit.tracker.data.local.entity.ReviewLogEntity
import com.voicehabit.tracker.data.local.entity.RoutineEntity
import com.voicehabit.tracker.data.local.entity.SubtaskEntity
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.TaskLogEntity
import com.voicehabit.tracker.data.local.entity.VoiceLogEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        TaskEntity::class,
        VoiceLogEntity::class,
        SubtaskEntity::class,
        FocusSessionEntity::class,
        DayMarkEntity::class,
        AchievementEntity::class,
        ChallengeEntity::class,
        RoutineEntity::class,
        ReviewLogEntity::class,
        TaskLogEntity::class,
        MoodEntity::class,
        DigestEntity::class,
        ProgrammeEntity::class,
        ProgrammeDayEntity::class,
        ProgrammeExerciseEntity::class,
        ProgrammeLogEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun voiceLogDao(): VoiceLogDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun focusDao(): FocusDao
    abstract fun dayMarkDao(): DayMarkDao
    abstract fun achievementDao(): AchievementDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun routineDao(): RoutineDao
    abstract fun reviewDao(): ReviewDao
    abstract fun moodDao(): MoodDao
    abstract fun digestDao(): DigestDao
    abstract fun programmeDao(): ProgrammeDao

    companion object {
        /** Различает «быструю» задачу и «долгую» цель. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN taskType TEXT NOT NULL DEFAULT 'QUICK'"
                )
            }
        }

        /**
         * Индексы под реальные запросы + разовая чистка логов старого сида.
         *
         * Раньше чистка была `DELETE FROM habit_logs WHERE id LIKE 'log_%_0'`, где `_`
         * в SQL LIKE означает «любой один символ», а не подчёркивание. Реальные id
         * (`log_` + 8 символов UUID) попадали под паттерн с вероятностью 1/16 —
         * точные логи пользователя удалялись при каждом запуске приложения.
         * Здесь используется GLOB с точными префиксами сида, которые могли быть
         * созданы только старой версией.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_habit_logs_completedAt ON habit_logs (completedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tasks_isCompleted_dueDateIso ON tasks (isCompleted, dueDateIso)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_voice_logs_status_createdAt ON voice_logs (status, createdAt)"
                )

                val startOfToday = java.time.LocalDate.now()
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                db.execSQL(
                    "DELETE FROM habit_logs WHERE completedAt >= $startOfToday AND (" +
                        "id GLOB 'log_bed_*' OR id GLOB 'log_w_*' OR " +
                        "id GLOB 'log_post_*' OR id GLOB 'log_vit_*')",
                    arrayOf<Any>()
                )
            }
        }

        /**
         * v4: архив/корзина/расписание/теги привычек; теги/родитель/напоминание/архив/
         * корзина/повтор/событие-календаря задач; новые таблицы подзадач, фокуса,
         * меток дней, достижений, челленджей, рутин и вечерних разборов.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN scheduleDays TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
                db.execSQL("ALTER TABLE habits ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderMinutesBefore INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS subtasks (
                        id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, title TEXT NOT NULL,
                        isDone INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_taskId ON subtasks (taskId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS focus_sessions (
                        id TEXT NOT NULL PRIMARY KEY, taskId TEXT, habitId TEXT, label TEXT NOT NULL DEFAULT '',
                        startedAt INTEGER NOT NULL DEFAULT 0, durationMin INTEGER NOT NULL DEFAULT 25,
                        completed INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_startedAt ON focus_sessions (startedAt)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS day_marks (
                        dateEpochDay INTEGER NOT NULL PRIMARY KEY, mark TEXT NOT NULL DEFAULT 'FREEZE')"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT NOT NULL PRIMARY KEY, unlockedAt INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS challenges (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, habitId TEXT,
                        startEpochDay INTEGER NOT NULL DEFAULT 0, lengthDays INTEGER NOT NULL DEFAULT 30,
                        note TEXT NOT NULL DEFAULT '', isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS routines (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, triggerPhrase TEXT NOT NULL DEFAULT '',
                        habitIdsCsv TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS review_log (
                        dateEpochDay INTEGER NOT NULL PRIMARY KEY, summary TEXT NOT NULL DEFAULT '',
                        tomorrowPlan TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0)"""
                )
            }
        }

        /**
         * v5: журнал выполнения задач по датам (`task_logs`).
         *
         * Backfill переносит единственную известную отметку из плоского флага:
         * `isCompleted = 1 AND completedAt IS NOT NULL` → запись с локальной датой
         * этого timestamp. Флаг и колонка остаются как денормализованный кэш
         * (их читают виджеты и сортировки), но источником правды становится журнал.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS task_logs (
                        id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, localDate TEXT NOT NULL,
                        completedAt INTEGER NOT NULL, zoneId TEXT NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_logs_taskId ON task_logs (taskId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_task_logs_taskId_localDate " +
                        "ON task_logs (taskId, localDate)"
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO task_logs (id, taskId, localDate, completedAt, zoneId)
                        SELECT 'backfill_' || id, id,
                            date(completedAt / 1000, 'unixepoch', 'localtime'),
                            completedAt, ''
                        FROM tasks WHERE isCompleted = 1 AND completedAt IS NOT NULL"""
                )
            }
        }

        /**
         * v6: G7–G10/G18/G19 — описание/ссылка/закреп/оценка задач, лучший стрик/
         * закреп/напоминание привычек, журнал настроения.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN url TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMin INTEGER")
                db.execSQL("ALTER TABLE habits ADD COLUMN bestStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMin INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS mood_log (
                        dateEpochDay INTEGER NOT NULL PRIMARY KEY, mood INTEGER NOT NULL DEFAULT 3,
                        note TEXT NOT NULL DEFAULT '')"""
                )
                // bestStreak инициализируем текущим стриком, чтобы не потерять историю.
                db.execSQL("UPDATE habits SET bestStreak = streak WHERE bestStreak < streak")
            }
        }

        /**
         * v7 (H1): конспекты свободного потока.
         *
         * Списки секций лежат в CSV-колонках. Схема объявляет их `TEXT NOT NULL DEFAULT ''`,
         * потому что записи, созданные до v7, не должны ломать миграцию, а `modeConfidence`
         * — `REAL`, иначе Room не смог бы прочитать существующие строки и упал бы на
         * первом же чтении при `Float`-свойстве.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS digests (
                        id TEXT NOT NULL PRIMARY KEY, voiceLogId TEXT, title TEXT NOT NULL DEFAULT '',
                        gist TEXT NOT NULL DEFAULT '', keyPoints TEXT NOT NULL DEFAULT '',
                        decisions TEXT NOT NULL DEFAULT '', openQuestions TEXT NOT NULL DEFAULT '',
                        nextSteps TEXT NOT NULL DEFAULT '', people TEXT NOT NULL DEFAULT '',
                        numbers TEXT NOT NULL DEFAULT '', tone TEXT NOT NULL DEFAULT '',
                        mode TEXT NOT NULL DEFAULT 'DICTATE', modeConfidence REAL NOT NULL DEFAULT 0,
                        transcript TEXT NOT NULL DEFAULT '', wordCount INTEGER NOT NULL DEFAULT 0,
                        speechSeconds INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_digests_createdAt ON digests (createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_digests_mode ON digests (mode)")
            }
        }

        /**
         * v8 (P1): программы тренировок — `programmes`, `programme_days`,
         * `programme_exercises`, `programme_logs`.
         *
         * Таблицы новые, чужие данные не трогаются. Важны два решения:
         *
         * 1. `programme_days.habitId` — **без** внешнего ключа на `habits`. Связь
         *    логическая, а не физическая: удаление привычки не должно каскадом
         *    снести день программы (и наоборот). Привычка и день программы живут
         *    своей жизнью, а `habitId` — просто указатель для обратной связи.
         * 2. `programme_logs.localDate` — TEXT в формате `yyyy-MM-dd`, как в
         *    `task_logs`. Дата, а не `completedAt`: отметка «сделал вчера» должна
         *    попасть во вчерашний день, а локальная зона у пользователя одна.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS programmes (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL DEFAULT '',
                        athleteNote TEXT NOT NULL DEFAULT '', sourceText TEXT NOT NULL DEFAULT '',
                        goals TEXT NOT NULL DEFAULT '', startEpochDay INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL DEFAULT 0)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS programme_days (
                        id TEXT NOT NULL PRIMARY KEY, programmeId TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0,
                        weekday INTEGER NOT NULL DEFAULT 1, title TEXT NOT NULL DEFAULT '',
                        focusNote TEXT NOT NULL DEFAULT '', isRest INTEGER NOT NULL DEFAULT 0,
                        habitId TEXT,
                        FOREIGN KEY(programmeId) REFERENCES programmes(id) ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_days_programmeId ON programme_days (programmeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_days_habitId ON programme_days (habitId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS programme_exercises (
                        id TEXT NOT NULL PRIMARY KEY, dayId TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0,
                        title TEXT NOT NULL DEFAULT '', outdoor TEXT NOT NULL DEFAULT '',
                        home TEXT NOT NULL DEFAULT '', sets INTEGER NOT NULL DEFAULT 1,
                        repsMin INTEGER NOT NULL DEFAULT 0, repsMax INTEGER NOT NULL DEFAULT 0,
                        measure TEXT NOT NULL DEFAULT 'повт', tempo TEXT NOT NULL DEFAULT '',
                        restSec INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '',
                        targetValue REAL NOT NULL DEFAULT 0, currentValue REAL NOT NULL DEFAULT 0,
                        FOREIGN KEY(dayId) REFERENCES programme_days(id) ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_exercises_dayId ON programme_exercises (dayId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS programme_logs (
                        id TEXT NOT NULL PRIMARY KEY, exerciseId TEXT NOT NULL, programmeId TEXT NOT NULL,
                        localDate TEXT NOT NULL, value REAL NOT NULL DEFAULT 0,
                        setsDone INTEGER NOT NULL DEFAULT 0, completedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(exerciseId) REFERENCES programme_exercises(id) ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_logs_exerciseId ON programme_logs (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_logs_localDate ON programme_logs (localDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_programme_logs_programmeId ON programme_logs (programmeId)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_habit_tracker.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
