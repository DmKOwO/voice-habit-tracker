package com.voicehabit.tracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * H1. Миграция 6→7: таблица `digests` — конспекты свободного потока.
 *
 * ## Почему это нельзя проверить юнит-тестом
 *
 * Миграция выполняется на настоящем SQLite устройства, а на JVM нет `android.database`.
 * При этом в `AppDatabase` включён `fallbackToDestructiveMigration()`: если
 * `MIGRATION_6_7` когда-нибудь исчезнет или будет собрана с ошибкой, Room **не
 * упадёт** — он просто сотрит базу целиком. Пользователь потеряет все конспекты
 * молча, и узнает об этом по пустому экрану.
 *
 * ## Что именно проверяется
 *
 * - Таблица и оба индекса созданы.
 * - `modeConfidence` читается как `Float` (тип `REAL`): иначе Room не смог бы
 *   прочитать существующие строки и упал бы на первом же чтении.
 * - Данные v6 пережили миграцию: у конспектов появился собственный срок жизни,
 *   но он не имеет права ломать чужое.
 */
@RunWith(AndroidJUnit4::class)
class DigestMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migratesFrom6To7_creatingDigestsTable() {
        helper.createDatabase(DB_NAME, 6).apply {
            // Привычка и её лог: данные, которые миграция обязана не тронуть.
            execSQL(
                """INSERT INTO habits (id, title, category, displayType, colorHex, quote,
                    targetValue, unit, frequency, streak, bestStreak, pinned, reminderMin,
                    archived, scheduleDays, tags, deletedAt, createdAt)
                   VALUES ('h1', 'Пить воду', 'Health', 'STREAKS', '#84CC16', '', 8.0, 'стаканов',
                    'DAILY', 3, 5, 0, NULL, 0, '1,2,3,4,5,6,7', '', NULL, 0)"""
            )
            execSQL(
                """INSERT INTO voice_logs (id, audioPath, rawTranscript, summary, status, createdAt)
                   VALUES ('v1', '/cache/a.m4a', 'завтра надо купить хлеб', 'Задача', 'APPLIED', 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 7, true, AppDatabase.MIGRATION_6_7)

        // Таблица на месте и читается.
        migrated.query("SELECT count(*) FROM digests").use { c ->
            assertTrue("Запрос к новой таблице обязан вернуть строку", c.moveToFirst())
            assertEquals("Пустая миграция не должна оставлять конспектов", 0, c.getInt(0))
        }

        // Данные v6 на месте.
        migrated.query("SELECT title FROM habits WHERE id = 'h1'").use {
            assertTrue("Привычка не должна исчезнуть при миграции", it.moveToFirst())
            assertEquals("Пить воду", it.getString(0))
        }
        migrated.query("SELECT rawTranscript FROM voice_logs WHERE id = 'v1'").use {
            assertTrue("Запись голоса не должна исчезнуть", it.moveToFirst())
            assertEquals("завтра надо купить хлеб", it.getString(0))
        }

        // Индексы созданы: без них список конспектов сортируется полным сканом.
        val indexes = mutableSetOf<String>()
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'digests'")
            .use { c -> while (c.moveToNext()) indexes.add(c.getString(0)) }
        assertTrue("Индекс по дате обязателен: $indexes", indexes.contains("index_digests_createdAt"))
        assertTrue("Индекс по режиму обязателен: $indexes", indexes.contains("index_digests_mode"))

        // Запись в новую таблицу: колонки и типы обязаны совпасть с сущностью.
        migrated.execSQL(
            """INSERT INTO digests (id, voiceLogId, title, gist, keyPoints, decisions,
                openQuestions, nextSteps, people, numbers, tone, mode, modeConfidence,
                transcript, wordCount, speechSeconds, pinned, createdAt)
               VALUES ('d1', 'v1', 'Релиз', 'перенесли на пятницу', 'сроки горят', 'перенос',
                'как быть с обучением', 'предупредить команду', 'Олег', '15 процентов',
                'Усталость', 'MIXED', 0.57, 'сырой текст', 74, 95, 0, 0)"""
        )
        migrated.query("SELECT mode, modeConfidence, tone, wordCount, speechSeconds FROM digests WHERE id = 'd1'")
            .use {
                assertTrue("Конспект должен читаться после миграции", it.moveToFirst())
                assertEquals("MIXED", it.getString(0))
                assertEquals("REAL читается как Float", 0.57f, it.getFloat(1), 0.0001f)
                assertEquals("Усталость", it.getString(2))
                assertEquals(74, it.getInt(3))
                assertEquals(95, it.getInt(4))
            }
        migrated.close()
    }

    companion object {
        private const val DB_NAME = "digest-migration-test.db"
    }
}
