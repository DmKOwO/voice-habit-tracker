package com.voicehabit.tracker.worker

import android.content.Context
import com.voicehabit.tracker.core.di.AppContainer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Перепланирование напоминаний: после перезагрузки и при старте приложения.
 * WorkManager one-time работы переживают перезагрузку сами, но напоминания,
 * привязанные к задачам, проще пересоздать из источника правды (БД).
 */
object ReminderRescheduler {

    suspend fun rescheduleAll(context: Context, container: AppContainer) {
        try {
            val now = System.currentTimeMillis()
            val tasks = container.taskRepository.getAllTasksList()
            for (task in tasks) {
                val minutes = task.reminderMinutesBefore ?: continue
                if (task.isCompleted || task.deletedAt != null || task.isArchived) continue
                val dueIso = task.dueDateIso ?: continue
                val dueMillis = parseIsoMillis(dueIso) ?: continue
                val fireAt = dueMillis - minutes * 60_000L
                val delayMin = (fireAt - now) / 60_000L
                if (delayMin < 0) continue
                ReminderWorker.enqueue(context, task.id, null, task.title, delayMin)
            }
            val habits = container.habitRepository.getAllHabitsList()
            for (habit in habits) {
                val reminderMin = habit.reminderMin ?: continue
                if (habit.deletedAt != null || habit.archived) continue
                val delayMin = minutesUntilTodayAt(reminderMin)
                ReminderWorker.enqueue(context, null, habit.id, habit.title, delayMin)
            }
        } catch (e: Exception) {
        }
    }

    /** Планирует одно напоминание задачи (вызывается из ViewModel при сохранении). */
    fun scheduleTaskReminder(context: Context, taskId: String, title: String, dueIso: String?, minutesBefore: Int?) {
        if (dueIso.isNullOrBlank() || minutesBefore == null) return
        val dueMillis = parseIsoMillis(dueIso) ?: return
        val delayMin = (dueMillis - minutesBefore * 60_000L - System.currentTimeMillis()) / 60_000L
        if (delayMin < 0) return
        ReminderWorker.enqueue(context, taskId, null, title, delayMin)
    }

    private fun parseIsoMillis(dueIso: String): Long? {
        return try {
            LocalDateTime.parse(dueIso.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDate.parse(dueIso.take(10)).atTime(9, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun minutesUntilTodayAtPublic(minuteOfDay: Int): Long = minutesUntilTodayAt(minuteOfDay)

    private fun minutesUntilTodayAt(minuteOfDay: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(java.util.Calendar.MINUTE, minuteOfDay % 60)
            set(java.util.Calendar.SECOND, 0)
        }
        var diff = (target.timeInMillis - now.timeInMillis) / 60_000L
        if (diff <= 0) diff += 24 * 60
        return diff
    }
}
