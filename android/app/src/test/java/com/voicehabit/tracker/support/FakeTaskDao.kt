package com.voicehabit.tracker.support

import com.voicehabit.tracker.data.local.dao.TaskDao
import com.voicehabit.tracker.data.local.dao.TaskLogDay
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory заглушка [TaskDao] с реактивными Flow: эмитит при каждой записи,
 * как настоящий Room. Нужна для проверки, что `isCompleted` вычисляется
 * из журнала дат, а не из плоского флага таблицы.
 */
class FakeTaskDao : TaskDao {
    private val tasksFlow = MutableStateFlow<List<TaskEntity>>(emptyList())
    private val logsFlow = MutableStateFlow<List<TaskLogEntity>>(emptyList())

    private val tasks: List<TaskEntity> get() = tasksFlow.value
    private fun setTasks(next: List<TaskEntity>) { tasksFlow.value = next }
    private fun setLogs(next: List<TaskLogEntity>) { logsFlow.value = next }

    override fun getOpenTasksFlow(): Flow<List<TaskEntity>> =
        tasksFlow.map { list -> list.filter { !it.isCompleted } }

    override suspend fun getOpenTasksList(): List<TaskEntity> =
        tasks.filter { !it.isCompleted }

    override fun getAllTasksFlow(): Flow<List<TaskEntity>> = tasksFlow

    override suspend fun getAllTasksList(): List<TaskEntity> = tasks.toList()

    override suspend fun insertTask(task: TaskEntity) {
        setTasks(tasks.filterNot { it.id == task.id } + task)
    }

    override suspend fun markTaskCompleted(taskId: String, completedAt: Long) {
        setTasks(tasks.map {
            if (it.id == taskId) it.copy(isCompleted = true, completedAt = completedAt) else it
        })
    }

    override suspend fun markTaskOpen(taskId: String) {
        setTasks(tasks.map {
            if (it.id == taskId) it.copy(isCompleted = false, completedAt = null) else it
        })
    }

    override suspend fun updateTaskFields(
        taskId: String,
        title: String,
        dueDateIso: String?,
        priority: String,
        category: String,
        taskType: String,
        description: String,
        url: String?,
        pinned: Int,
        estimatedMin: Int?,
        tags: String,
        reminderMinutesBefore: Int?,
        recurrence: String
    ) {
        setTasks(tasks.map {
            if (it.id == taskId) {
                it.copy(
                    title = title, dueDateIso = dueDateIso, priority = priority,
                    category = category, taskType = taskType, description = description,
                    url = url, pinned = pinned, estimatedMin = estimatedMin, tags = tags,
                    reminderMinutesBefore = reminderMinutesBefore, recurrence = recurrence
                )
            } else it
        })
    }

    override suspend fun deleteTask(taskId: String) {
        setTasks(tasks.filterNot { it.id == taskId })
        setLogs(logsFlow.value.filterNot { it.taskId == taskId })
    }

    override suspend fun getById(id: String): TaskEntity? = tasks.find { it.id == id }

    override suspend fun setArchived(taskId: String, archived: Int) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(isArchived = archived) else it })
    }

    override suspend fun setDeletedAt(taskId: String, deletedAt: Long?) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(deletedAt = deletedAt) else it })
    }

    override suspend fun setReminder(taskId: String, minutes: Int?) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(reminderMinutesBefore = minutes) else it })
    }

    override suspend fun setRecurrence(taskId: String, recurrence: String) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(recurrence = recurrence) else it })
    }

    override suspend fun setCalendarEventId(taskId: String, eventId: Long?) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(calendarEventId = eventId) else it })
    }

    override suspend fun setTags(taskId: String, tags: String) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(tags = tags) else it })
    }

    override suspend fun setPinned(taskId: String, pinned: Int) {
        setTasks(tasks.map { if (it.id == taskId) it.copy(pinned = pinned) else it })
    }

    override suspend fun purgeTrash(olderThan: Long): Int {
        val doomed = tasks.filter { it.deletedAt != null && it.deletedAt < olderThan }.map { it.id }
        doomed.forEach { deleteTask(it) }
        return doomed.size
    }

    override suspend fun insertTaskLog(log: TaskLogEntity): Long {
        val exists = logsFlow.value.any { it.taskId == log.taskId && it.localDate == log.localDate }
        if (!exists) setLogs(logsFlow.value + log)
        return if (exists) -1 else 1
    }

    override suspend fun getLogsForTask(taskId: String): List<TaskLogEntity> =
        logsFlow.value.filter { it.taskId == taskId }.sortedByDescending { it.localDate }

    override fun getAllLogDaysFlow(): Flow<List<TaskLogDay>> =
        logsFlow.map { list -> list.map { TaskLogDay(it.taskId, it.localDate) } }

    override fun getAllTaskLogsFlow(): Flow<List<TaskLogEntity>> = logsFlow

    override suspend fun getCompletedTaskIdsOn(date: String): List<String> =
        logsFlow.value.filter { it.localDate == date }.map { it.taskId }

    override suspend fun deleteLogForTaskOn(taskId: String, date: String): Int {
        val before = logsFlow.value.size
        setLogs(logsFlow.value.filterNot { it.taskId == taskId && it.localDate == date })
        return before - logsFlow.value.size
    }

    override suspend fun deleteLogsForTask(taskId: String) {
        setLogs(logsFlow.value.filterNot { it.taskId == taskId })
    }

    /** Прямой доступ для подготовки предусловий в тестах. */
    fun seedTasks(vararg entities: TaskEntity) = setTasks(entities.toList())

    fun seedLogs(vararg entities: TaskLogEntity) = setLogs(entities.toList())

    fun currentLogs(): List<TaskLogEntity> = logsFlow.value.toList()
}
