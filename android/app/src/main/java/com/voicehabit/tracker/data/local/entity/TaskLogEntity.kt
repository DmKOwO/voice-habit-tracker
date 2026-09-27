package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись выполнения задачи за конкретную локальную дату (`YYYY-MM-DD`).
 *
 * Раньше выполнение было плоским флагом `tasks.isCompleted` + один `completedAt`:
 * повторная отметка в другой день перезаписывала дату, а «снять отметку» стирало
 * историю полностью. Теперь источник правды — журнал: задача выполнена сегодня,
 * только если есть запись за текущий локальный день устройства.
 *
 * `zoneId` фиксирует часовой пояс момента записи: при смене пояса (перелёт)
 * видно, что запись относится к другому дню в новом поясе, а не «пропадает».
 */
@Entity(
    tableName = "task_logs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId"),
        Index(value = ["taskId", "localDate"], unique = true)
    ]
)
data class TaskLogEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val localDate: String,
    val completedAt: Long,
    val zoneId: String
)
