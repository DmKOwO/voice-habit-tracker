package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class Task(
    val id: String,
    val title: String,
    val dueDateIso: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "General",
    val type: TaskType = TaskType.QUICK,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val tags: List<String> = emptyList(),
    val parentTaskId: String? = null,
    val reminderMinutesBefore: Int? = null,
    val isArchived: Boolean = false,
    val deletedAt: Long? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val calendarEventId: Long? = null,
    /**
     * Даты выполнения из журнала (по возрастанию). `isCompleted` означает
     * «есть запись за сегодня», а не плоский флаг: reopen/history не теряются.
     */
    val history: List<LocalDate> = emptyList(),
    val description: String = "",
    val url: String? = null,
    val pinned: Boolean = false,
    val estimatedMin: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskRecurrence(val title: String) {
    NONE("Без повтора"),
    DAILY("Ежедневно"),
    WEEKLY("Еженедельно")
}

enum class Priority {
    LOW, MEDIUM, HIGH
}

/**
 * Разница между разовой задачей и долгой целью «как привычка».
 * QUICK — закрывается одним касанием и уходит в выполненные.
 * LONG — остаётся в списке как регулярная цель, у неё важен не сам факт,
 * а накопленный прогресс, поэтому карточка показывает прогресс и ведёт себя как привычка.
 */
enum class TaskType {
    QUICK, LONG;

    val isLong: Boolean get() = this == LONG
}
