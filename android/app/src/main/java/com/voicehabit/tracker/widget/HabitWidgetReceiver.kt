package com.voicehabit.tracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HabitWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_HABIT) {
            val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = HabitRepositoryImpl(db.habitDao())
                    val isChecked = repo.toggleHabitCompletion(habitId)
                    val habit = repo.getHabitById(habitId)

                    withContext(Dispatchers.Main) {
                        val habitName = habit?.title ?: "Привычка"
                        val msg = if (isChecked) "✓ $habitName выполнено!" else "○ $habitName снято"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }

                    DuroHabitWidgetProvider.updateAllWidgets(context)
                    DuroHabitCardWidgetProvider.updateAllCardWidgets(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_HABIT = "com.voicehabit.tracker.ACTION_TOGGLE_HABIT"
        const val EXTRA_HABIT_ID = "extra_habit_id"
    }
}
