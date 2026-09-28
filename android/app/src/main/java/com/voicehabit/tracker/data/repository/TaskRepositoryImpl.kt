package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.local.dao.TaskDao
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.TaskLogEntity
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskRecurrence
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis
) : TaskRepository {

    override fun getOpenTasksFlow(): Flow<List<Task>> {
        return withHistory(taskDao.getOpenTasksFlow())
    }

    override fun getAllTasksFlow(): Flow<List<Task>> {
        return withHistory(taskDao.getAllTasksFlow())
    }

    /**
     * Соединяет задачи с журналом: `isCompleted` вычисляется как «есть запись
     * за сегодня», `completedAt` — как последний timestamp журнала. Без этого
     * UI показывал бы плоский флаг из таблицы, и midnight-reset не работал бы.
     */
    private fun withHistory(source: Flow<List<TaskEntity>>): Flow<List<Task>> {
        return source.combine(taskDao.getAllTaskLogsFlow()) { entities, logs ->
            val byTask = logs.groupBy { it.taskId }
            val today = todayString()
            entities.map { entity ->
                val taskLogs = byTask[entity.id].orEmpty()
                val history = taskLogs
                    .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
                    .sorted()
                entity.toDomain(
                    history = history,
                    doneToday = taskLogs.any { it.localDate == today },
                    latestLogAt = taskLogs.maxOfOrNull { it.completedAt }
                )
            }
        }
    }

    override suspend fun getAllTasksList(): List<Task> {
        val entities = taskDao.getAllTasksList()
        val byTask = taskDao.getAllTaskLogsFlow().first().groupBy { it.taskId }
        val today = todayString()
        return entities.map { entity ->
            val logs = byTask[entity.id].orEmpty()
            entity.toDomain(
                history = logs.mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }.sorted(),
                doneToday = logs.any { it.localDate == today },
                latestLogAt = logs.maxOfOrNull { it.completedAt }
            )
        }
    }

    override suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.toEntity())
    }

    private fun todayLocalDate(): LocalDate =
        Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    override suspend fun completeTask(taskId: String) {
        completeTaskOn(taskId, todayLocalDate(), zone, clock())
    }

    override suspend fun reopenTask(taskId: String) {
        reopenTaskOn(taskId, todayLocalDate())
    }

    override suspend fun completeTaskOn(
        taskId: String,
        date: LocalDate,
        zone: ZoneId,
        nowMillis: Long
    ) {
        taskDao.insertTaskLog(
            TaskLogEntity(
                id = "tlog_" + UUID.randomUUID(),
                taskId = taskId,
                localDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                completedAt = nowMillis,
                zoneId = zone.id
            )
        )
        // Денормализованный кэш для виджетов/сортировок; источником правды остаётся журнал.
        taskDao.markTaskCompleted(taskId, nowMillis)
    }

    override suspend fun reopenTaskOn(taskId: String, date: LocalDate) {
        val removed = taskDao.deleteLogForTaskOn(taskId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        if (removed > 0) {
            val remaining = taskDao.getLogsForTask(taskId)
            if (remaining.isEmpty()) {
                taskDao.markTaskOpen(taskId)
            } else {
                val latest = remaining.maxByOrNull { it.completedAt }
                if (latest != null) taskDao.markTaskCompleted(taskId, latest.completedAt)
            }
        }
    }

    override suspend fun getHistory(taskId: String): List<LocalDate> {
        return taskDao.getLogsForTask(taskId)
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .sorted()
    }

    private fun todayString(): String =
        todayLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    override suspend fun updateTask(task: Task) {
        taskDao.updateTaskFields(
            taskId = task.id,
            title = task.title,
            dueDateIso = task.dueDateIso,
            priority = task.priority.name,
            category = task.category,
            taskType = task.type.name,
            description = task.description,
            url = task.url,
            pinned = if (task.pinned) 1 else 0,
            estimatedMin = task.estimatedMin,
            tags = task.tags.joinToString(","),
            reminderMinutesBefore = task.reminderMinutesBefore,
            recurrence = task.recurrence.name
        )
    }

    override suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }

    override suspend fun getById(taskId: String): Task? = taskDao.getById(taskId)?.toDomain()

    override suspend fun setArchived(taskId: String, archived: Boolean) {
        taskDao.setArchived(taskId, if (archived) 1 else 0)
    }

    override suspend fun moveToTrash(taskId: String) {
        taskDao.setDeletedAt(taskId, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(taskId: String) {
        taskDao.setDeletedAt(taskId, null)
    }

    override suspend fun purgeTrash(olderThanMillis: Long): Int {
        return taskDao.purgeTrash(System.currentTimeMillis() - olderThanMillis)
    }

    override suspend fun setReminder(taskId: String, minutesBefore: Int?) {
        taskDao.setReminder(taskId, minutesBefore)
    }

    override suspend fun setRecurrence(taskId: String, recurrence: TaskRecurrence) {
        taskDao.setRecurrence(taskId, recurrence.name)
    }

    override suspend fun setTags(taskId: String, tags: List<String>) {
        taskDao.setTags(taskId, tags.joinToString(","))
    }

    override suspend fun setCalendarEventId(taskId: String, eventId: Long?) {
        taskDao.setCalendarEventId(taskId, eventId)
    }

    private fun TaskEntity.toDomain(
        history: List<LocalDate> = emptyList(),
        doneToday: Boolean = isCompleted,
        latestLogAt: Long? = null
    ): Task {
        val prio = try {
            Priority.valueOf(priority.uppercase())
        } catch (e: Exception) {
            Priority.MEDIUM
        }
        val type = try {
            TaskType.valueOf(taskType.uppercase())
        } catch (e: Exception) {
            TaskType.QUICK
        }
        val recurrence = try {
            TaskRecurrence.valueOf(recurrence.uppercase())
        } catch (e: Exception) {
            TaskRecurrence.NONE
        }
        return Task(
            id = id,
            title = title,
            dueDateIso = dueDateIso,
            priority = prio,
            category = category,
            type = type,
            isCompleted = doneToday,
            completedAt = latestLogAt ?: completedAt,
            history = history,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            parentTaskId = parentTaskId,
            reminderMinutesBefore = reminderMinutesBefore,
            isArchived = isArchived == 1,
            deletedAt = deletedAt,
            recurrence = recurrence,
            calendarEventId = calendarEventId,
            description = description,
            url = url,
            pinned = pinned == 1,
            estimatedMin = estimatedMin,
            createdAt = createdAt
        )
    }

    private fun Task.toEntity() = TaskEntity(
        id = id,
        title = title,
        dueDateIso = dueDateIso,
        priority = priority.name,
        category = category,
        taskType = type.name,
        isCompleted = isCompleted,
        completedAt = completedAt,
        tags = tags.joinToString(","),
        parentTaskId = parentTaskId,
        reminderMinutesBefore = reminderMinutesBefore,
        isArchived = if (isArchived) 1 else 0,
        deletedAt = deletedAt,
        recurrence = recurrence.name,
        calendarEventId = calendarEventId,
        description = description,
        url = url,
        pinned = if (pinned) 1 else 0,
        estimatedMin = estimatedMin,
        createdAt = createdAt
    )

    override suspend fun setPinned(taskId: String, pinned: Boolean) {
        taskDao.setPinned(taskId, if (pinned) 1 else 0)
    }
}
