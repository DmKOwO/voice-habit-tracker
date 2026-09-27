package com.voicehabit.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.voicehabit.tracker.core.logging.AppLogger
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.voicehabit.tracker.R
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DuroHabitWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
        AppLogger.instance().d("widget", "Обновляю все виджеты обзора")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DuroHabitWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            }
        }

        private fun updateAllWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val repo = HabitRepositoryImpl(db.habitDao())
                val habits = repo.getAllHabitsList().filter { it.deletedAt == null && !it.archived }

                val yearProgressText = calculateYearProgress()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_duro_habits)
                    views.setTextViewText(R.id.tv_widget_year_progress, yearProgressText)

                    // Quick Voice Recording Intent
                    val quickRecordIntent = Intent(context, QuickRecordActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val quickRecordPendingIntent = PendingIntent.getActivity(
                        context,
                        101,
                        quickRecordIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_quick_record, quickRecordPendingIntent)

                    // App launch when clicking widget header or habit row
                    val appIntent = Intent(context, com.voicehabit.tracker.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val appPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        appIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

                    // Bind Habit 1
                    if (habits.isNotEmpty()) {
                        val h1 = habits[0]
                        views.setViewVisibility(R.id.layout_habit_1, View.VISIBLE)
                        views.setTextViewText(R.id.tv_habit_1_title, h1.title)
                        views.setTextViewText(R.id.tv_habit_1_streak, "${h1.category} • 🔥 ${h1.currentStreak} Streaks")
                        views.setOnClickPendingIntent(R.id.layout_habit_1, appPendingIntent)
                        
                        val checkIntent1 = Intent(context, HabitWidgetReceiver::class.java).apply {
                            action = HabitWidgetReceiver.ACTION_TOGGLE_HABIT
                            data = Uri.parse("duro://widget/$appWidgetId/toggle/${h1.id}")
                            putExtra(HabitWidgetReceiver.EXTRA_HABIT_ID, h1.id)
                        }
                        val checkPendingIntent1 = PendingIntent.getBroadcast(
                            context,
                            201 + appWidgetId,
                            checkIntent1,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_check_habit_1, checkPendingIntent1)
                        views.setImageViewResource(
                            R.id.btn_check_habit_1,
                            if (h1.isCompletedToday) R.drawable.ic_widget_circle_done else R.drawable.ic_widget_circle_empty
                        )
                    } else {
                        views.setViewVisibility(R.id.layout_habit_1, View.GONE)
                    }

                    // Bind Habit 2
                    if (habits.size > 1) {
                        val h2 = habits[1]
                        views.setViewVisibility(R.id.layout_habit_2, View.VISIBLE)
                        views.setTextViewText(R.id.tv_habit_2_title, h2.title)
                        views.setTextViewText(R.id.tv_habit_2_streak, "${h2.category} • 🔥 ${h2.currentStreak} Streaks")
                        views.setOnClickPendingIntent(R.id.layout_habit_2, appPendingIntent)

                        val checkIntent2 = Intent(context, HabitWidgetReceiver::class.java).apply {
                            action = HabitWidgetReceiver.ACTION_TOGGLE_HABIT
                            data = Uri.parse("duro://widget/$appWidgetId/toggle/${h2.id}")
                            putExtra(HabitWidgetReceiver.EXTRA_HABIT_ID, h2.id)
                        }
                        val checkPendingIntent2 = PendingIntent.getBroadcast(
                            context,
                            202 + appWidgetId,
                            checkIntent2,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_check_habit_2, checkPendingIntent2)
                        views.setImageViewResource(
                            R.id.btn_check_habit_2,
                            if (h2.isCompletedToday) R.drawable.ic_widget_circle_done else R.drawable.ic_widget_circle_empty
                        )
                    } else {
                        views.setViewVisibility(R.id.layout_habit_2, View.GONE)
                    }

                    // Bind Habit 3
                    if (habits.size > 2) {
                        val h3 = habits[2]
                        views.setViewVisibility(R.id.layout_habit_3, View.VISIBLE)
                        views.setTextViewText(R.id.tv_habit_3_title, h3.title)
                        views.setTextViewText(R.id.tv_habit_3_streak, "${h3.category} • 🔥 ${h3.currentStreak} Streaks")
                        views.setOnClickPendingIntent(R.id.layout_habit_3, appPendingIntent)

                        val checkIntent3 = Intent(context, HabitWidgetReceiver::class.java).apply {
                            action = HabitWidgetReceiver.ACTION_TOGGLE_HABIT
                            data = Uri.parse("duro://widget/$appWidgetId/toggle/${h3.id}")
                            putExtra(HabitWidgetReceiver.EXTRA_HABIT_ID, h3.id)
                        }
                        val checkPendingIntent3 = PendingIntent.getBroadcast(
                            context,
                            203 + appWidgetId,
                            checkIntent3,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.btn_check_habit_3, checkPendingIntent3)
                        views.setImageViewResource(
                            R.id.btn_check_habit_3,
                            if (h3.isCompletedToday) R.drawable.ic_widget_circle_done else R.drawable.ic_widget_circle_empty
                        )
                    } else {
                        views.setViewVisibility(R.id.layout_habit_3, View.GONE)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private fun calculateYearProgress(): String {
            val cal = Calendar.getInstance()
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val year = cal.get(Calendar.YEAR)
            val totalDays = if (cal.getActualMaximum(Calendar.DAY_OF_YEAR) > 365) 366 else 365
            val pct = (dayOfYear * 100) / totalDays
            return "Reached $pct% of $year"
        }
    }
}
