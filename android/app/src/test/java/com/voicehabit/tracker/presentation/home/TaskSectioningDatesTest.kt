package com.voicehabit.tracker.presentation.home

import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Midnight-reset: секции считаются от явной даты, а не от «сейчас».
 *
 * Главный сценарий: задача отмечена в 23:59, приложение открыто через полночь
 * без единой записи в БД — задача обязана вернуться в активные, а история
 * сохраниться. Без dayTick старый код показывал бы вчерашнее состояние.
 */
class TaskSectioningDatesTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 9, 27)

    private fun task(id: String, completed: Boolean, completedAt: Long? = null): Task =
        Task(
            id = id, title = id, priority = Priority.MEDIUM, type = TaskType.QUICK,
            isCompleted = completed, completedAt = completedAt, createdAt = 0L
        )

    private fun millis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `completion at 23-59 leaves today section after midnight`() {
        val lateNight = task("t1", completed = true, completedAt = millis(day, 23, 59))

        val beforeMidnight = TaskSectioning.split(listOf(lateNight), today = day, zoneId = utc)
        assertEquals(1, beforeMidnight.completedToday.size)

        // Полночь прошла, записей в БД не было — секции пересчитаны по новой дате.
        // (Плоский флаг isCompleted=true без сегодняшнего журнала уходит в счётчик
        // старых; живой репозиторий в такой ситуации уже выставил бы isCompleted=false
        // по журналу, и задача лежала бы в openTasks.)
        val afterMidnight = TaskSectioning.split(listOf(lateNight), today = day.plusDays(1), zoneId = utc)
        assertTrue(afterMidnight.completedToday.isEmpty())
        assertEquals(1, afterMidnight.completedEarlierCount)
    }

    @Test
    fun `yesterday completion is counted not shown`() {
        val doneYesterday = task("t1", completed = true, completedAt = millis(day.minusDays(1), 12, 0))
        val sections = TaskSectioning.split(listOf(doneYesterday), today = day, zoneId = utc)

        assertTrue(sections.openTasks.isEmpty())
        assertTrue(sections.completedToday.isEmpty())
        assertEquals(1, sections.completedEarlierCount)
    }

    @Test
    fun `explicit today overload matches implicit now overload`() {
        val tasks = listOf(
            task("open", completed = false),
            task("done", completed = true, completedAt = millis(day, 9, 30))
        )
        val nowMillis = millis(day, 18, 0)
        val implicit = TaskSectioning.split(tasks, nowMillis = nowMillis, zoneId = utc)
        val explicit = TaskSectioning.split(tasks, today = day, zoneId = utc)

        assertEquals(implicit.openTasks.map { it.id }, explicit.openTasks.map { it.id })
        assertEquals(implicit.completedToday.map { it.id }, explicit.completedToday.map { it.id })
        assertEquals(implicit.completedEarlierCount, explicit.completedEarlierCount)
    }

    @Test
    fun `timezone shift moves boundary consistently`() {
        // Отметка 27.09 23:30 UTC = 28.09 01:30 в UTC+2.
        val completedAt = millis(day, 23, 30)
        val t = task("t1", completed = true, completedAt = completedAt)

        val inUtc = TaskSectioning.split(listOf(t), today = day, zoneId = utc)
        assertEquals(1, inUtc.completedToday.size)

        val plus2 = ZoneId.of("Europe/Helsinki")
        // Тот же момент в Хельсинки — уже 28.09: при «сегодня = 27.09» отметка
        // в сегодня не попадает и уходит в счётчик старых, а не теряется.
        val helsinkiYesterday = TaskSectioning.split(listOf(t), today = day, zoneId = plus2)
        assertTrue(helsinkiYesterday.completedToday.isEmpty())
        assertEquals(1, helsinkiYesterday.completedEarlierCount)

        // А при «сегодня = 28.09» она же — сегодняшняя.
        val helsinkiToday = TaskSectioning.split(
            listOf(t),
            today = LocalDate.of(2026, 9, 28),
            zoneId = plus2
        )
        assertEquals(1, helsinkiToday.completedToday.size)
    }

    @Test
    fun `isCompletedOn respects the given zone`() {
        val completedAt = millis(day, 23, 30)
        val t = task("t1", completed = true, completedAt = completedAt)

        assertTrue(TaskSectioning.isCompletedOn(t, day, utc))
        // Та же метка в UTC+2 — уже следующий день.
        val plus2 = ZoneId.of("Europe/Helsinki")
        val helsinkiDay = java.time.Instant.ofEpochMilli(completedAt).atZone(plus2).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 28), helsinkiDay)
        assertTrue(TaskSectioning.isCompletedOn(t, helsinkiDay, plus2))
        assertTrue(!TaskSectioning.isCompletedOn(t, day, plus2))
    }
}
