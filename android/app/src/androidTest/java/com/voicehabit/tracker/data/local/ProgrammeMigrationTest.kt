package com.voicehabit.tracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1. Миграция 7→8: тренировочные программы.
 *
 * ## Почему это нельзя проверить юнит-тестом
 *
 * Миграция выполняется на настоящем SQLite устройства, а на JVM нет
 * `android.database`. В `AppDatabase` включён `fallbackToDestructiveMigration()`:
 * если `MIGRATION_7_8` исчезнет или соберётся с ошибкой, Room **не упадёт** — он
 * просто сотрит базу. Человек потеряет все привычки, задачи и конспекты молча.
 *
 * ## Что именно проверяется
 *
 * - Четыре новые таблицы созданы, индексы на месте.
 * - Данные v7 пережили миграцию: программа добавляется, а не заменяет список.
 * - `habitId` читается как `TEXT` и остаётся `NULL`: связь с привычкой появляется
 *   только при импорте, а сама миграция не должна придумывать её.
 * - Каскад `programme_exercises → programme_logs` на месте: удаление упражнения
 *   уносит его прогресс, иначе в `programme_logs` остаются логи без родителя.
 */
@RunWith(AndroidJUnit4::class)
class ProgrammeMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migratesFrom7To8_creatingProgrammeTables() {
        helper.createDatabase(DB_NAME, 7).apply {
            // Привычка и её лог: миграция добавляет таблицы и не имеет права
            // трогать то, что уже было.
            execSQL(
                """INSERT INTO habits (id, title, category, displayType, colorHex, quote,
                    targetValue, unit, frequency, streak, bestStreak, pinned, reminderMin,
                    archived, scheduleDays, tags, deletedAt, createdAt)
                   VALUES ('h1', 'Тренировка', 'Fitness', 'STREAKS', '#DE6B48', '', 1.0, NULL,
                    'WEEKLY', 3, 5, 0, NULL, 0, '1,3,5', 'prog:prg_1', NULL, 0)"""
            )
            execSQL(
                """INSERT INTO habit_logs (id, habitId, valueLogged, comment, completedAt)
                   VALUES ('hl_1', 'h1', 1.0, NULL, 0)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 8, true, AppDatabase.MIGRATION_7_8)

        val tables = mutableSetOf<String>()
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
        listOf("programmes", "programme_days", "programme_exercises", "programme_logs").forEach {
            assertTrue("Таблица $it обязана существовать. Есть: $tables", tables.contains(it))
        }

        // Привычки и их логи не должны пострадать при добавлении новых таблиц.
        migrated.query("SELECT title, scheduleDays, tags FROM habits WHERE id = 'h1'")
            .use {
                assertTrue("Привычка обязана пережить миграцию", it.moveToFirst())
                assertEquals("Тренировка", it.getString(0))
                assertEquals("Расписание Пн/Ср/Пт не должно меняться", "1,3,5", it.getString(1))
                assertEquals("Метка программы не должна теряться", "prog:prg_1", it.getString(2))
            }
        migrated.query("SELECT COUNT(*) FROM habit_logs WHERE habitId = 'h1'")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals("Лог привычки обязан сохраниться", 1, it.getInt(0))
            }

        // Запись программы целиком: без неё таблицы есть, но не работают.
        migrated.execSQL(
            """INSERT INTO programmes (id, title, athleteNote, goals, sourceText,
                startEpochDay, isActive, createdAt)
               VALUES ('prg_1', 'Калистеника', '173 см', 'Набор массы', 'исходный текст', 20733, 1, 0)"""
        )
        migrated.execSQL(
            """INSERT INTO programme_days (id, programmeId, position, weekday, title,
                focusNote, isRest, habitId)
               VALUES ('pday_1', 'prg_1', 0, 1, 'Тяга', 'Спина', 0, NULL)"""
        )
        migrated.execSQL(
            """INSERT INTO programme_exercises (id, dayId, position, title, outdoor, home,
                sets, repsMin, repsMax, measure, tempo, restSec, note, targetValue)
               VALUES ('pex_1', 'pday_1', 0, 'Подтягивания', 'Турник', '', 4, 6, 8, 'повт', '2-0-3', 150, '', 0)"""
        )
        migrated.execSQL(
            """INSERT INTO programme_logs (id, exerciseId, programmeId, localDate, value, setsDone, completedAt)
               VALUES ('plog_1', 'pex_1', 'prg_1', '2026-09-30', 24.0, 4, 0)"""
        )

        migrated.query("SELECT isRest, habitId FROM programme_days WHERE id = 'pday_1'")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals("isRest читается как 0", 0, it.getInt(0))
                assertNull("До импорта связи с привычкой нет", it.getString(1))
            }
        migrated.query("SELECT sets, repsMin, repsMax, restSec FROM programme_exercises WHERE id = 'pex_1'")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(4, it.getInt(0))
                assertEquals(6, it.getInt(1))
                assertEquals(8, it.getInt(2))
                assertEquals(150, it.getInt(3))
            }
        migrated.query("SELECT value, setsDone FROM programme_logs WHERE id = 'plog_1'")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(24.0, it.getDouble(0), 0.001)
                assertEquals(4, it.getInt(1))
            }

        // Каскад: удаление упражнения уносит его прогресс, а не оставляет сироту.
        //
        // SQLite держит foreign_keys выключенным по умолчанию, а Room включает их
        // только на своих соединениях. Здесь соединение сырое, поэтому pragma
        // обязана быть включена руками — иначе проверка каскада проходит на
        // базе, где каскадов попросту нет.
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("DELETE FROM programme_exercises WHERE id = 'pex_1'")
        migrated.query("SELECT COUNT(*) FROM programme_logs")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals("Лог удалённого упражнения обязан исчезнуть", 0, it.getInt(0))
            }

        // Каскад от программы к дням и упражнениям.
        migrated.execSQL("DELETE FROM programmes WHERE id = 'prg_1'")
        migrated.query("SELECT COUNT(*) FROM programme_days")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals("День удалённой программы обязан исчезнуть", 0, it.getInt(0))
            }

        migrated.close()
    }

    companion object {
        private const val DB_NAME = "programme-migration-test.db"
    }
}
