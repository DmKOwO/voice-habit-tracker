package com.voicehabit.tracker.presentation.home

import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskSectioningTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val todayAtNoon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val yesterdayAtNoon = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun quickTask(
        id: String,
        completed: Boolean,
        completedAt: Long? = null,
        due: String? = null
    ) = Task(
        id = id,
        title = id,
        dueDateIso = due,
        priority = Priority.MEDIUM,
        type = TaskType.QUICK,
        isCompleted = completed,
        completedAt = completedAt,
        createdAt = 0L
    )

    @Test
    fun `completed quick task lands in today section instead of vanishing`() {
        val sections = TaskSectioning.split(
            tasks = listOf(quickTask("t1", completed = true, completedAt = todayAtNoon)),
            nowMillis = todayAtNoon,
            zoneId = zone
        )

        assertTrue("Выполненная задача не должна теряться", sections.openTasks.isEmpty())
        assertEquals(1, sections.completedToday.size)
        assertEquals("t1", sections.completedToday.first().id)
    }

    @Test
    fun `long task stays in the open list after completion`() {
        val sections = TaskSectioning.split(
            tasks = listOf(
                Task(
                    id = "long_1",
                    title = "Английский каждый день",
                    type = TaskType.LONG,
                    isCompleted = true,
                    completedAt = todayAtNoon
                )
            ),
            nowMillis = todayAtNoon,
            zoneId = zone
        )

        assertEquals(1, sections.openTasks.size)
        assertTrue("Долгая цель остаётся в списке как привычка", sections.openTasks.first().isCompleted)
        assertTrue(sections.completedToday.isEmpty())
    }

    @Test
    fun `tasks completed on earlier days are only counted`() {
        val sections = TaskSectioning.split(
            tasks = listOf(quickTask("t1", completed = true, completedAt = yesterdayAtNoon)),
            nowMillis = todayAtNoon,
            zoneId = zone
        )

        assertTrue(sections.openTasks.isEmpty())
        assertTrue(sections.completedToday.isEmpty())
        assertEquals(1, sections.completedEarlierCount)
    }

    @Test
    fun `open tasks are sorted by due date then newest first`() {
        val sections = TaskSectioning.split(
            tasks = listOf(
                quickTask("no_due_new", completed = false, due = null),
                quickTask("later", completed = false, due = "2030-02-01T10:00:00"),
                quickTask("sooner", completed = false, due = "2030-01-01T10:00:00")
            ),
            nowMillis = todayAtNoon,
            zoneId = zone
        )

        assertEquals(listOf("sooner", "later", "no_due_new"), sections.openTasks.map { it.id })
    }

    @Test
    fun `mixed list keeps every task visible somewhere`() {
        val all = listOf(
            quickTask("open", completed = false),
            quickTask("done_today", completed = true, completedAt = todayAtNoon),
            quickTask("done_old", completed = true, completedAt = yesterdayAtNoon)
        )

        val sections = TaskSectioning.split(tasks = all, nowMillis = todayAtNoon, zoneId = zone)

        assertEquals(3, sections.openTasks.size + sections.completedToday.size + sections.completedEarlierCount)
    }
}
