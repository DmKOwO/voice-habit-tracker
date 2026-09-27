package com.voicehabit.tracker

import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.HabitCompletedAction
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskCreateAction
import com.voicehabit.tracker.domain.model.TaskRecurrence
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.domain.repository.HabitRepository
import com.voicehabit.tracker.domain.repository.TaskRepository
import com.voicehabit.tracker.domain.usecase.ApplyVoiceActionsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyVoiceActionsUseCaseTest {

    private class FakeHabitRepo : HabitRepository {
        val habits = mutableListOf<Habit>()
        val logs = mutableListOf<Triple<String, Double, String?>>()

        override fun getAllHabitsFlow(): Flow<List<Habit>> = flowOf(habits)
        override suspend fun getAllHabitsList(): List<Habit> = habits
        override suspend fun getHabitById(id: String): Habit? = habits.find { it.id == id }
        override suspend fun insertOrUpdateHabit(habit: Habit) {
            habits.removeAll { it.id == habit.id }
            habits.add(habit)
        }
        override suspend fun logHabitCompletion(habitId: String, value: Double, comment: String?) {
            logs.add(Triple(habitId, value, comment))
        }
        override suspend fun toggleHabitCompletion(habitId: String): Boolean = true
        override suspend fun deleteHabit(id: String) { habits.removeAll { it.id == id } }
        override suspend fun setArchived(id: String, archived: Boolean) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(archived = archived)
        }
        override suspend fun moveToTrash(id: String) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(deletedAt = System.currentTimeMillis())
        }
        override suspend fun restoreFromTrash(id: String) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(deletedAt = null)
        }
        override suspend fun purgeTrash(olderThanMillis: Long): Int = 0
        override suspend fun updateScheduleAndTags(id: String, days: Set<Int>, tags: List<String>) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(scheduleDays = days, tags = tags)
        }
        override suspend fun setPinned(id: String, pinned: Boolean) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(pinned = pinned)
        }
        override suspend fun setReminderMin(id: String, minutes: Int?) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1) habits[idx] = habits[idx].copy(reminderMin = minutes)
        }
        override suspend fun updateBestStreak(id: String, best: Int) {
            val idx = habits.indexOfFirst { it.id == id }
            if (idx != -1 && habits[idx].bestStreak < best) {
                habits[idx] = habits[idx].copy(bestStreak = best)
            }
        }
    }

    private class FakeTaskRepo : TaskRepository {
        val tasks = mutableListOf<Task>()
        override fun getOpenTasksFlow(): Flow<List<Task>> = flowOf(tasks)
        override fun getAllTasksFlow(): Flow<List<Task>> = flowOf(tasks)
        override suspend fun insertTask(task: Task) { tasks.add(task) }
        override suspend fun completeTask(id: String) {
            val idx = tasks.indexOfFirst { it.id == id }
            if (idx != -1) tasks[idx] = tasks[idx].copy(isCompleted = true)
        }
        override suspend fun reopenTask(id: String) {
            val idx = tasks.indexOfFirst { it.id == id }
            if (idx != -1) tasks[idx] = tasks[idx].copy(isCompleted = false, completedAt = null)
        }
        override suspend fun completeTaskOn(
            taskId: String,
            date: LocalDate,
            zone: ZoneId,
            nowMillis: Long
        ) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) {
                tasks[idx] = tasks[idx].copy(
                    isCompleted = true,
                    completedAt = nowMillis,
                    history = (tasks[idx].history + date).distinct().sorted()
                )
            }
        }
        override suspend fun reopenTaskOn(taskId: String, date: LocalDate) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) {
                val history = tasks[idx].history - date
                tasks[idx] = tasks[idx].copy(
                    isCompleted = false,
                    completedAt = null,
                    history = history
                )
            }
        }
        override suspend fun getHistory(taskId: String): List<LocalDate> =
            tasks.find { it.id == taskId }?.history.orEmpty()
        override suspend fun updateTask(task: Task) {
            val idx = tasks.indexOfFirst { it.id == task.id }
            if (idx != -1) tasks[idx] = task
        }
        override suspend fun deleteTask(id: String) { tasks.removeAll { it.id == id } }
        override suspend fun getById(taskId: String): Task? = tasks.find { it.id == taskId }
        override suspend fun setArchived(taskId: String, archived: Boolean) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(isArchived = archived)
        }
        override suspend fun moveToTrash(taskId: String) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(deletedAt = System.currentTimeMillis())
        }
        override suspend fun restoreFromTrash(taskId: String) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(deletedAt = null)
        }
        override suspend fun purgeTrash(olderThanMillis: Long): Int = 0
        override suspend fun setReminder(taskId: String, minutesBefore: Int?) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(reminderMinutesBefore = minutesBefore)
        }
        override suspend fun setRecurrence(taskId: String, recurrence: TaskRecurrence) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(recurrence = recurrence)
        }
        override suspend fun setTags(taskId: String, tags: List<String>) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(tags = tags)
        }
        override suspend fun setCalendarEventId(taskId: String, eventId: Long?) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(calendarEventId = eventId)
        }
        override suspend fun getAllTasksList(): List<Task> = tasks.toList()
        override suspend fun setPinned(taskId: String, pinned: Boolean) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(pinned = pinned)
        }
    }

    @Test
    fun `long voice task keeps its type`() = runTest {
        val taskRepo = FakeTaskRepo()
        val useCase = ApplyVoiceActionsUseCase(FakeHabitRepo(), taskRepo)

        useCase(
            VoiceNoteAction(
                rawTranscript = "каждый день пить воду",
                tasksToAdd = listOf(
                    TaskCreateAction(
                        title = "Пить воду",
                        dueDate = null,
                        priority = "MEDIUM",
                        category = "Health",
                        taskType = TaskType.LONG.name
                    )
                )
            )
        )

        assertEquals(1, taskRepo.tasks.size)
        assertEquals(TaskType.LONG, taskRepo.tasks.first().type)
    }

    @Test
    fun `apply voice action creates new habit with visual display type and logs completion`() = runTest {
        val habitRepo = FakeHabitRepo()
        val taskRepo = FakeTaskRepo()
        val useCase = ApplyVoiceActionsUseCase(habitRepo, taskRepo)

        val action = VoiceNoteAction(
            rawTranscript = "Выпил стакан воды и побегал",
            summary = "Побегал и попил воды",
            habitsCompleted = listOf(
                HabitCompletedAction(
                    habitId = null,
                    habitTitle = "Пить воду",
                    incrementValue = 250.0,
                    comment = "стакан воды",
                    isSelected = true
                )
            ),
            tasksToAdd = listOf(
                TaskCreateAction(
                    title = "Купить кроссовки",
                    dueDate = null,
                    priority = "HIGH",
                    category = "Shopping",
                    subtasks = emptyList(),
                    isSelected = true
                )
            )
        )

        useCase(action)

        assertEquals(1, habitRepo.habits.size)
        val createdHabit = habitRepo.habits.first()
        assertEquals("Пить воду", createdHabit.title)
        assertTrue(createdHabit.displayType.isNotEmpty())
        assertTrue(createdHabit.colorHex.startsWith("#"))
        assertEquals(1, habitRepo.logs.size)

        assertEquals(1, taskRepo.tasks.size)
        assertEquals("Купить кроссовки", taskRepo.tasks.first().title)
        assertEquals(Priority.HIGH, taskRepo.tasks.first().priority)
    }
}
