package com.voicehabit.tracker.domain.usecase

import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskRecurrence
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.domain.repository.HabitRepository
import com.voicehabit.tracker.domain.repository.TaskRepository
import java.util.UUID

class ApplyVoiceActionsUseCase(
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository
) {
    private val displayTypes = listOf("GRID", "DAILY_CHECK", "STREAKS", "BAR_GRAPH")
    private val colors = listOf("#FF6B35", "#A855F7", "#0EA5E9", "#84CC16", "#EC4899")

    suspend operator fun invoke(action: VoiceNoteAction) {
        // 1. Применяем отмеченные привычки
        var index = 0
        for (habitAction in action.habitsCompleted) {
            if (!habitAction.isSelected) continue

            val habitId = habitAction.habitId ?: run {
                val newId = "habit_" + UUID.randomUUID().toString().take(8)
                val assignedDisplay = displayTypes[index % displayTypes.size]
                val assignedColor = colors[index % colors.size]
                index++

                habitRepository.insertOrUpdateHabit(
                    Habit(
                        id = newId,
                        title = habitAction.habitTitle,
                        category = "Voice",
                        displayType = assignedDisplay,
                        colorHex = assignedColor,
                        quote = "Tracked via AI",
                        targetValue = habitAction.incrementValue ?: 1.0,
                        unit = if (habitAction.incrementValue != null && habitAction.incrementValue > 10) "мл" else "раз"
                    )
                )
                newId
            }

            habitRepository.logHabitCompletion(
                habitId = habitId,
                value = habitAction.incrementValue ?: 1.0,
                comment = habitAction.comment
            )
        }

        // 1б. Новые привычки голосом — создаём без отметки выполнения.
        // Отметка появится, когда человек реально сделает (или скажет об этом).
        for (create in action.habitsToCreate) {
            if (!create.isSelected || create.title.isBlank()) continue
            val assignedDisplay = displayTypes[index % displayTypes.size]
            val assignedColor = colors[index % colors.size]
            index++
            habitRepository.insertOrUpdateHabit(
                Habit(
                    id = "habit_" + UUID.randomUUID().toString().take(8),
                    title = create.title,
                    category = "Voice",
                    displayType = assignedDisplay,
                    colorHex = assignedColor,
                    quote = "Заведено голосом",
                    targetValue = create.targetValue,
                    unit = create.unit
                )
            )
        }

        // 2. Создаем новые задачи
        for (taskAction in action.tasksToAdd) {
            if (!taskAction.isSelected) continue

            val priority = try {
                Priority.valueOf(taskAction.priority.uppercase())
            } catch (e: Exception) {
                Priority.MEDIUM
            }
            val taskType = try {
                TaskType.valueOf(taskAction.taskType.uppercase())
            } catch (e: Exception) {
                TaskType.QUICK
            }

            val task = Task(
                id = "task_" + UUID.randomUUID().toString().take(8),
                title = taskAction.title,
                dueDateIso = taskAction.dueDate,
                priority = priority,
                category = taskAction.category,
                type = taskType
            )
            taskRepository.insertTask(task)
        }

        // 3. Отмечаем завершенные задачи
        for (completeAction in action.tasksToComplete) {
            if (!completeAction.isSelected || completeAction.taskId == null) continue
            taskRepository.completeTask(completeAction.taskId)
            spawnRecurrenceIfNeeded(completeAction.taskId)
        }

        // 4. G2: удаление — в корзину (восстановимо), а не навсегда.
        for (deleteAction in action.tasksToDelete) {
            if (!deleteAction.isSelected || deleteAction.taskId == null) continue
            taskRepository.moveToTrash(deleteAction.taskId)
        }

        // 5. G3: перенос срока.
        for (reschedule in action.tasksToReschedule) {
            if (!reschedule.isSelected || reschedule.taskId == null) continue
            val current = taskRepository.getById(reschedule.taskId) ?: continue
            taskRepository.updateTask(current.copy(dueDateIso = reschedule.newDueDate))
        }
    }

    /** F4: выполненная повторяющаяся задача порождает следующий экземпляр. */
    private suspend fun spawnRecurrenceIfNeeded(taskId: String) {
        val task = taskRepository.getById(taskId) ?: return
        val nextIso = when (task.recurrence) {
            TaskRecurrence.DAILY -> shiftIsoDays(task.dueDateIso, 1)
            TaskRecurrence.WEEKLY -> shiftIsoDays(task.dueDateIso, 7)
            TaskRecurrence.NONE -> return
        }
        taskRepository.insertTask(
            task.copy(
                id = "task_" + UUID.randomUUID().toString().take(8),
                isCompleted = false,
                completedAt = null,
                dueDateIso = nextIso,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun shiftIsoDays(dueDateIso: String?, days: Long): String? {
        if (dueDateIso.isNullOrBlank()) {
            return java.time.LocalDate.now().plusDays(days).atTime(9, 0)
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
        return try {
            java.time.LocalDateTime.parse(dueDateIso.take(19)).plusDays(days)
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            dueDateIso
        }
    }
}
