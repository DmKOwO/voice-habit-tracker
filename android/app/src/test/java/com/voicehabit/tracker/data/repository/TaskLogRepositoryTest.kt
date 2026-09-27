package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.TaskLogEntity
import com.voicehabit.tracker.support.FakeTaskDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Источник правды «выполнено сегодня» — журнал дат, а не плоский флаг.
 *
 * Регрессии, которые ловятся здесь:
 * - задача с `isCompleted=1` в таблице, но без записи за сегодня → НЕ выполнена;
 * - reopen удаляет только сегодняшнюю запись, история остаётся;
 * - смена часового пояса не «воскрешает» и не «хоронит» вчерашние отметки молча.
 */
class TaskLogRepositoryTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 27)
    private val yesterday: LocalDate = today.minusDays(1)

    private fun repoAt(
        dao: FakeTaskDao,
        noonTodayMillis: Long,
        zone: ZoneId = utc
    ) = TaskRepositoryImpl(dao, zone) { noonTodayMillis }

    private fun task(id: String, completed: Boolean = false, completedAt: Long? = null) =
        TaskEntity(
            id = id, title = id, dueDateIso = null, priority = "MEDIUM", category = "General",
            taskType = "QUICK", isCompleted = completed, completedAt = completedAt, createdAt = 0L
        )

    private fun log(taskId: String, date: LocalDate, atMillis: Long) =
        TaskLogEntity("l_${taskId}_$date", taskId, date.toString(), atMillis, "UTC")

    @Test
    fun `flat flag without today log does not count as completed`() = runTest {
        val dao = FakeTaskDao()
        // Плоский флаг выставлен (наследие старой версии), журнала за сегодня нет.
        dao.seedTasks(task("t1", completed = true, completedAt = yesterday.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000))
        val repo = TaskRepositoryImpl(dao, utc) { today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }

        val tasks = repo.getAllTasksFlow().first()
        assertFalse(tasks.single().isCompleted)
        assertTrue(tasks.single().history.isEmpty())
    }

    @Test
    fun `log today marks completed and keeps denormalized cache in sync`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        val noon = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val repo = TaskRepositoryImpl(dao, utc) { noon }

        repo.completeTask("t1")

        val tasks = repo.getAllTasksFlow().first()
        val domain = tasks.single()
        assertTrue(domain.isCompleted)
        assertEquals(listOf(today), domain.history)
        assertEquals(noon, domain.completedAt)
        // Кэш в таблице тоже обновлён (его читают виджеты).
        assertEquals(true, dao.getById("t1")!!.isCompleted)
    }

    @Test
    fun `reopen removes only today log and restores latest cache`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        val repo = repoAt(dao, today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

        repo.completeTaskOn("t1", yesterday, utc, yesterday.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        repo.completeTaskOn("t1", today, utc, today.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        assertEquals(listOf(yesterday, today), repo.getHistory("t1"))

        repo.reopenTask("t1")

        // Сегодняшняя запись ушла, вчерашняя осталась; кэш указывает на вчера.
        assertEquals(listOf(yesterday), repo.getHistory("t1"))
        val cached = dao.getById("t1")!!
        assertEquals(true, cached.isCompleted)
        assertEquals(
            yesterday.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            cached.completedAt
        )
    }

    @Test
    fun `reopen with empty history clears the cache flag`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        val repo = repoAt(dao, today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

        repo.completeTask("t1")
        assertTrue(repo.getAllTasksFlow().first().single().isCompleted)
        repo.reopenTask("t1")

        val domain = repo.getAllTasksFlow().first().single()
        assertFalse(domain.isCompleted)
        assertTrue(domain.history.isEmpty())
        assertEquals(false, dao.getById("t1")!!.isCompleted)
    }

    @Test
    fun `duplicate completion same day is idempotent`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        val repo = repoAt(dao, today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

        repo.completeTask("t1")
        repo.completeTask("t1")

        assertEquals(1, dao.currentLogs().size)
        assertEquals(listOf(today), repo.getHistory("t1"))
    }

    @Test
    fun `timezone travel does not resurrect yesterday log as today`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        // Запись сделана 27.09 в UTC…
        dao.seedLogs(log("t1", today, today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()))

        // …а смотрим из пояса, где уже 28.09: запись за 27.09 — не «сегодня».
        val plus14 = ZoneId.of("Pacific/Kiritimati") // UTC+14
        val repo = TaskRepositoryImpl(dao, plus14) {
            today.atTime(12, 0).atZone(utc).withZoneSameInstant(plus14).toInstant().toEpochMilli()
        }

        val domain = repo.getAllTasksFlow().first().single()
        assertFalse("В новом поясе уже другой день — отметка не должна считаться сегодняшней", domain.isCompleted)
        // История при этом не потеряна.
        assertEquals(listOf(today), domain.history)
    }

    @Test
    fun `history is sorted and broken dates are ignored`() = runTest {
        val dao = FakeTaskDao()
        dao.seedTasks(task("t1"))
        dao.seedLogs(
            TaskLogEntity("l3", "t1", today.toString(), 3L, "UTC"),
            TaskLogEntity("lx", "t1", "not-a-date", 2L, "UTC"),
            TaskLogEntity("l1", "t1", yesterday.toString(), 1L, "UTC")
        )
        val repo = repoAt(dao, today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

        assertEquals(listOf(yesterday, today), repo.getHistory("t1"))
    }
}
