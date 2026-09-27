package com.voicehabit.tracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Миграция 4→5: таблица `task_logs` + backfill из плоского флага.
 *
 * Проверяется на реальном SQLite устройства: юнит-тесты на JVM Room
 * прогнать не могут (нужен `android.database`), а миграцию без проверки
 * выпускать нельзя — откат через destructive migration стёр бы данные.
 */
@RunWith(AndroidJUnit4::class)
class TaskLogMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun insertTask(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        isCompleted: Int,
        completedAt: Long?
    ) {
        db.execSQL(
            """INSERT INTO tasks (id, title, dueDateIso, priority, category, taskType,
                isCompleted, completedAt, tags, parentTaskId, reminderMinutesBefore,
                isArchived, deletedAt, recurrence, calendarEventId, createdAt)
               VALUES (?, ?, NULL, 'MEDIUM', 'General', 'QUICK', ?, ?, '', NULL, NULL,
                0, NULL, 'NONE', NULL, 0)""",
            arrayOf<Any?>(id, "Task $id", isCompleted, completedAt)
        )
    }

    @Test
    fun migrate4To5_backfillsSingleCompletionIntoLog() {
        // completedAt = 2026-09-27 12:00 UTC; ожидаемая localDate считается
        // в том же device-local поясе, что и backfill через 'localtime'.
        val completedAt = 1_758_897_600_000L
        val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(completedAt))

        helper.createDatabase(TEST_DB, 4).apply {
            insertTask(this, "done", 1, completedAt)
            insertTask(this, "open", 0, null)
            // Флаг без timestamp в backfill не попадает (нечего датировать).
            insertTask(this, "flag_no_ts", 1, null)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).apply {
            query("SELECT taskId, localDate, completedAt FROM task_logs").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("done", cursor.getString(0))
                assertEquals(expectedDate, cursor.getString(1))
                assertEquals(completedAt, cursor.getLong(2))
            }
            // Старые данные целы.
            query("SELECT COUNT(*) FROM tasks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(3, cursor.getInt(0))
            }
            // Таблица и уникальность (taskId, localDate) на месте.
            query(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='task_logs'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertTrue(cursor.getString(0).contains("FOREIGN KEY"))
            }
            close()
        }
    }

    @Test
    fun migrate4To5_isIdempotentForOpenTasks() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertTask(this, "open1", 0, null)
            insertTask(this, "open2", 0, null)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).apply {
            query("SELECT COUNT(*) FROM task_logs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    companion object {
        private const val TEST_DB = "migration-task-log-test"
    }
}
