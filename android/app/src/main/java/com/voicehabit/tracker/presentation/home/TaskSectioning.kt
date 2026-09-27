package com.voicehabit.tracker.presentation.home

import com.voicehabit.tracker.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Разделение задач на секции главного экрана.
 *
 * Раньше UI подписывался только на `getOpenTasksFlow()`, поэтому выполненная задача
 * исчезала из списка вместо того, чтобы остаться в нём окрашенной.
 *
 * Правила:
 * - QUICK: выполненная сегодня задача уезжает в секцию «Выполнено сегодня»,
 *   более старые выполненные учитываются только счётчиком.
 * - LONG: ведёт себя как привычка — остаётся в рабочем списке и лишь помечается
 *   галочкой «сегодня», поэтому прогресс не теряется после одного отметки.
 */
object TaskSectioning {

    fun split(
        tasks: List<Task>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TaskSections = split(tasks, toLocalDate(nowMillis, zoneId), zoneId)

    /**
     * Явная дата «сегодня» — для midnight-reset и тестов границы суток.
     * Статус «выполнено сегодня» вычисляется только по журналу дат задачи.
     */
    fun split(
        tasks: List<Task>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TaskSections {

        val open = mutableListOf<Task>()
        val completedToday = mutableListOf<Task>()
        var completedEarlierCount = 0

        for (task in tasks) {
            // Архив и корзина не участвуют в рабочих секциях — у них свои экраны.
            if (task.isArchived || task.deletedAt != null) continue
            val doneToday = task.isCompleted && task.completedAt != null &&
                toLocalDate(task.completedAt!!, zoneId) == today

            when {
                !task.isCompleted -> open += task
                task.type.isLong -> open += task
                doneToday -> completedToday += task
                else -> completedEarlierCount++
            }
        }

        return TaskSections(
            openTasks = open.sortedWith(openComparator()),
            completedToday = completedToday.sortedByDescending { it.completedAt ?: 0L },
            completedEarlierCount = completedEarlierCount
        )
    }

    fun isCompletedOn(task: Task, day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val completedAt = task.completedAt ?: return false
        return toLocalDate(completedAt, zoneId) == day
    }

    private fun toLocalDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    private fun openComparator(): Comparator<Task> = Comparator { left, right ->
        // G9: закреплённые всегда вверху.
        val byPin = right.pinned.compareTo(left.pinned)
        if (byPin != 0) return@Comparator byPin

        val byCompletion = left.isCompleted.compareTo(right.isCompleted)
        if (byCompletion != 0) return@Comparator byCompletion

        val leftDue = left.dueDateIso
        val rightDue = right.dueDateIso
        val byDue = when {
            leftDue == null && rightDue == null -> 0
            leftDue == null -> 1
            rightDue == null -> -1
            else -> leftDue.compareTo(rightDue)
        }
        if (byDue != 0) return@Comparator byDue

        right.createdAt.compareTo(left.createdAt)
    }
}

data class TaskSections(
    val openTasks: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList(),
    val completedEarlierCount: Int = 0
)
