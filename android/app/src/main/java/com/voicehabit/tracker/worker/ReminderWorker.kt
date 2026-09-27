package com.voicehabit.tracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.voicehabit.tracker.core.di.AppContainer
import kotlinx.coroutines.launch
import com.voicehabit.tracker.core.notifications.Notify
import java.util.concurrent.TimeUnit

/**
 * F9/G22: напоминание о задаче/привычке. Кнопки: «Готово» (закрывает сразу из
 * шторки) и «+10 мин» (snooze). Ночью (тихие часы) уведомление не показывается,
 * но задача остаётся в списке — данные не теряются.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
        val habitId = inputData.getString(KEY_HABIT_ID)
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val container = AppContainer.get(applicationContext)

        if (Notify.inQuietHours(container.settings)) {
            // Переносим на 7:00 вместо показа ночью.
            val now = java.util.Calendar.getInstance()
            var delayMin = ((7 - now.get(java.util.Calendar.HOUR_OF_DAY) + 24) % 24) * 60L
            if (delayMin <= 0) delayMin = 60
            enqueue(applicationContext, taskId, habitId, title, delayMin)
            return Result.success()
        }

        val doneIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra(KEY_TASK_ID, taskId)
            putExtra(KEY_HABIT_ID, habitId)
        }
        val snoozeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(KEY_TASK_ID, taskId)
            putExtra(KEY_HABIT_ID, habitId)
            putExtra(KEY_TITLE, title)
        }
        Notify.show(
            applicationContext,
            Notify.CHANNEL_REMINDERS,
            (taskId ?: habitId ?: title).hashCode(),
            "⏰ $title",
            "Пора выполнить — нажмите «Готово» прямо из уведомления",
            listOf(
                Notify.action(applicationContext, 1, "Готово", doneIntent),
                Notify.action(applicationContext, 2, "+10 мин", snoozeIntent)
            )
        )
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_HABIT_ID = "habit_id"
        const val KEY_TITLE = "title"

        fun enqueue(context: Context, taskId: String?, habitId: String?, title: String, delayMin: Long) {
            if (delayMin < 0) return
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_TASK_ID to taskId,
                        KEY_HABIT_ID to habitId,
                        KEY_TITLE to title
                    )
                )
                .addTag("reminder")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/** Кнопки уведомления-напоминания: выполнить сразу или отложить на 10 минут. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                val taskId = intent.getStringExtra(ReminderWorker.KEY_TASK_ID)
                val habitId = intent.getStringExtra(ReminderWorker.KEY_HABIT_ID)
                when (intent.action) {
                    ACTION_DONE -> {
                        if (taskId != null) container.taskRepository.completeTask(taskId)
                        if (habitId != null) container.habitRepository.logHabitCompletion(habitId, 1.0, "Из уведомления")
                    }
                    ACTION_SNOOZE -> {
                        ReminderWorker.enqueue(
                            context, taskId, habitId,
                            intent.getStringExtra(ReminderWorker.KEY_TITLE) ?: "Напоминание", 10
                        )
                    }
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.voicehabit.tracker.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.voicehabit.tracker.REMINDER_SNOOZE"
    }
}
