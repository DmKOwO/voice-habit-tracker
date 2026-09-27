package com.voicehabit.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDateIso ASC, createdAt DESC")
    fun getOpenTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDateIso ASC, createdAt DESC")
    suspend fun getOpenTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = 1, completedAt = :completedAt WHERE id = :taskId")
    suspend fun markTaskCompleted(taskId: String, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET isCompleted = 0, completedAt = NULL WHERE id = :taskId")
    suspend fun markTaskOpen(taskId: String)

    @Query(
        """
        UPDATE tasks SET
            title = :title,
            dueDateIso = :dueDateIso,
            priority = :priority,
            category = :category,
            taskType = :taskType,
            description = :description,
            url = :url,
            pinned = :pinned,
            estimatedMin = :estimatedMin,
            tags = :tags,
            reminderMinutesBefore = :reminderMinutesBefore,
            recurrence = :recurrence
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskFields(
        taskId: String,
        title: String,
        dueDateIso: String?,
        priority: String,
        category: String,
        taskType: String,
        description: String = "",
        url: String? = null,
        pinned: Int = 0,
        estimatedMin: Int? = null,
        tags: String = "",
        reminderMinutesBefore: Int? = null,
        recurrence: String = "NONE"
    )

    @Query("UPDATE tasks SET pinned = :pinned WHERE id = :taskId")
    suspend fun setPinned(taskId: String, pinned: Int)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    // F7 архив/корзина, F9 напоминание, F10 календарь, F4 повтор, F14 теги
    @Query("UPDATE tasks SET isArchived = :archived WHERE id = :taskId")
    suspend fun setArchived(taskId: String, archived: Int)

    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :taskId")
    suspend fun setDeletedAt(taskId: String, deletedAt: Long?)

    @Query("UPDATE tasks SET reminderMinutesBefore = :minutes WHERE id = :taskId")
    suspend fun setReminder(taskId: String, minutes: Int?)

    @Query("UPDATE tasks SET recurrence = :recurrence WHERE id = :taskId")
    suspend fun setRecurrence(taskId: String, recurrence: String)

    @Query("UPDATE tasks SET calendarEventId = :eventId WHERE id = :taskId")
    suspend fun setCalendarEventId(taskId: String, eventId: Long?)

    @Query("UPDATE tasks SET tags = :tags WHERE id = :taskId")
    suspend fun setTags(taskId: String, tags: String)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :olderThan")
    suspend fun purgeTrash(olderThan: Long): Int

    // Журнал выполнения по датам: источник правды для «выполнено сегодня».

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskLog(log: TaskLogEntity): Long

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY localDate DESC")
    suspend fun getLogsForTask(taskId: String): List<TaskLogEntity>

    @Query("SELECT taskId, localDate FROM task_logs")
    fun getAllLogDaysFlow(): Flow<List<TaskLogDay>>

    @Query("SELECT * FROM task_logs")
    fun getAllTaskLogsFlow(): Flow<List<TaskLogEntity>>

    @Query("SELECT taskId FROM task_logs WHERE localDate = :date")
    suspend fun getCompletedTaskIdsOn(date: String): List<String>

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND localDate = :date")
    suspend fun deleteLogForTaskOn(taskId: String, date: String): Int

    @Query("DELETE FROM task_logs WHERE taskId = :taskId")
    suspend fun deleteLogsForTask(taskId: String)
}

/** Лёгкая проекция журнала для соединения задач с их датами выполнения. */
data class TaskLogDay(
    val taskId: String,
    val localDate: String
)
