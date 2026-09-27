package com.voicehabit.tracker.domain.repository

import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskRecurrence
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

interface TaskRepository {
    fun getOpenTasksFlow(): Flow<List<Task>>
    fun getAllTasksFlow(): Flow<List<Task>>
    suspend fun getAllTasksList(): List<Task>
    suspend fun insertTask(task: Task)
    suspend fun completeTask(taskId: String)
    suspend fun reopenTask(taskId: String)
    /**
     * Отметка/снятие отметки за конкретную локальную дату — источник правды
     * для «выполнено сегодня». Плоский флаг в таблице при этом обновляется
     * как кэш для виджетов и сортировок.
     */
    suspend fun completeTaskOn(
        taskId: String,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    )
    suspend fun reopenTaskOn(taskId: String, date: LocalDate)
    suspend fun getHistory(taskId: String): List<LocalDate>
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(taskId: String)
    suspend fun getById(taskId: String): Task?
    suspend fun setArchived(taskId: String, archived: Boolean)
    suspend fun moveToTrash(taskId: String)
    suspend fun restoreFromTrash(taskId: String)
    suspend fun purgeTrash(olderThanMillis: Long): Int
    suspend fun setReminder(taskId: String, minutesBefore: Int?)
    suspend fun setRecurrence(taskId: String, recurrence: TaskRecurrence)
    suspend fun setTags(taskId: String, tags: List<String>)
    suspend fun setCalendarEventId(taskId: String, eventId: Long?)
    suspend fun setPinned(taskId: String, pinned: Boolean)
}
