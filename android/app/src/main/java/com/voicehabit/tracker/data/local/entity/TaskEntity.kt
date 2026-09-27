package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["isCompleted", "dueDateIso"])]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val dueDateIso: String?,
    val priority: String,
    val category: String,
    val taskType: String = "QUICK",
    val isCompleted: Boolean,
    val completedAt: Long?,
    /** F14/F4/F9/F10/F7: теги, родитель (подзадачи живут отдельно), напоминание, архив/корзина, повтор, событие календаря. */
    val tags: String = "",
    val parentTaskId: String? = null,
    val reminderMinutesBefore: Int? = null,
    val isArchived: Int = 0,
    val deletedAt: Long? = null,
    val recurrence: String = "NONE",
    val calendarEventId: Long? = null,
    /** G7/G8/G9/G10: описание, ссылка, закреп, оценка минут. */
    val description: String = "",
    val url: String? = null,
    val pinned: Int = 0,
    val estimatedMin: Int? = null,
    val createdAt: Long
)
