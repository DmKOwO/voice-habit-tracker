package com.voicehabit.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.voicehabit.tracker.MainActivity
import com.voicehabit.tracker.R
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.domain.model.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuroHabitCardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            DuroWidgetConfigureActivity.deleteWidgetPref(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            DuroWidgetConfigureActivity.loadHabitIdForWidget(context, oldId)?.let { habitId ->
                DuroWidgetConfigureActivity.saveHabitForWidget(context, newId, habitId)
            }
            DuroWidgetConfigureActivity.deleteWidgetPref(context, oldId)
        }
        updateAllCardWidgets(context)
    }

    companion object {
        fun updateAllCardWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DuroHabitCardWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateSingleWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateSingleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val repo = HabitRepositoryImpl(db.habitDao())
                val allHabits = repo.getAllHabitsList().filter { it.deletedAt == null && !it.archived }

                val configuredHabitId = DuroWidgetConfigureActivity.loadHabitIdForWidget(context, appWidgetId)
                val habit = (if (configuredHabitId != null) allHabits.find { it.id == configuredHabitId } else null)
                    ?: allHabits.firstOrNull()

                val views = RemoteViews(context.packageName, R.layout.widget_duro_card_medium)
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    3000 + appWidgetId,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.tv_card_title, appPendingIntent)
                views.setOnClickPendingIntent(R.id.card_root, appPendingIntent)

                if (habit == null) {
                    views.setTextViewText(R.id.tv_card_category, "DURO")
                    views.setTextViewText(R.id.tv_card_title, "No habits yet")
                    views.setViewVisibility(R.id.iv_card_grid, View.GONE)
                    views.setViewVisibility(R.id.layout_card_streak, View.GONE)
                    views.setViewVisibility(R.id.layout_card_today, View.GONE)
                    views.setTextViewText(R.id.tv_card_quote, "Tap + in app")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                // 1. Text & Headers
                views.setTextViewText(R.id.tv_card_category, habit.category.uppercase())
                views.setTextViewText(R.id.tv_card_title, habit.title)

                val accentColorInt = try {
                    android.graphics.Color.parseColor(habit.colorHex)
                } catch (e: Exception) {
                    android.graphics.Color.parseColor("#FF6B35")
                }

                // 2. Quick Mic Intent (tap mic -> immediately record speech)
                val quickRecordIntent = Intent(context, QuickRecordActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val quickRecordPendingIntent = PendingIntent.getActivity(
                    context,
                    1000 + appWidgetId,
                    quickRecordIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_card_mic, quickRecordPendingIntent)

                // 3. 1-Tap Check/Complete Intent (tap check -> toggle completion right from home screen)
                val checkIntent = Intent(context, HabitWidgetReceiver::class.java).apply {
                    action = HabitWidgetReceiver.ACTION_TOGGLE_HABIT
                    data = Uri.Builder()
                        .scheme("duro")
                        .authority("widget")
                        .appendPath(appWidgetId.toString())
                        .appendPath("toggle")
                        .appendPath(habit.id)
                        .build()
                    putExtra(HabitWidgetReceiver.EXTRA_HABIT_ID, habit.id)
                }
                val checkPendingIntent = PendingIntent.getBroadcast(
                    context,
                    2000 + appWidgetId,
                    checkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_card_check, checkPendingIntent)

                // 4. Check State (Frame 26: "Checked!")
                if (habit.isCompletedToday) {
                    views.setImageViewResource(R.id.btn_card_check, R.drawable.ic_widget_circle_done)
                    views.setViewVisibility(R.id.tv_card_checked_badge, View.VISIBLE)
                    views.setViewVisibility(R.id.tv_card_quote, View.GONE)
                } else {
                    views.setImageViewResource(R.id.btn_card_check, R.drawable.ic_widget_circle_empty)
                    views.setViewVisibility(R.id.tv_card_checked_badge, View.GONE)
                    views.setViewVisibility(R.id.tv_card_quote, View.VISIBLE)
                    views.setTextViewText(R.id.tv_card_quote, habit.quote.ifEmpty { "Stay\nHungry" })
                }

                // 5. Visualization rendering
                when (habit.displayType.uppercase()) {
                    "STREAKS" -> {
                        views.setViewVisibility(R.id.iv_card_grid, View.GONE)
                        views.setViewVisibility(R.id.layout_card_today, View.GONE)
                        views.setViewVisibility(R.id.layout_card_streak, View.VISIBLE)
                        views.setTextViewText(R.id.tv_card_streak_count, "${habit.currentStreak} Streaks")
                    }
                    "DAILY_CHECK" -> {
                        views.setViewVisibility(R.id.iv_card_grid, View.GONE)
                        views.setViewVisibility(R.id.layout_card_streak, View.GONE)
                        views.setViewVisibility(R.id.layout_card_today, View.VISIBLE)
                        if (habit.isCompletedToday) {
                            views.setImageViewResource(R.id.iv_card_today_circle, R.drawable.ic_widget_circle_done)
                        } else {
                            views.setImageViewResource(R.id.iv_card_today_circle, R.drawable.ic_widget_circle_empty)
                        }
                    }
                    "BAR_GRAPH" -> {
                        views.setViewVisibility(R.id.layout_card_streak, View.GONE)
                        views.setViewVisibility(R.id.layout_card_today, View.GONE)
                        views.setViewVisibility(R.id.iv_card_grid, View.VISIBLE)
                        val barBitmap = createBarGraphBitmap(habit, accentColorInt)
                        views.setImageViewBitmap(R.id.iv_card_grid, barBitmap)
                    }
                    else -> { // "GRID" or "MINIMAL"
                        views.setViewVisibility(R.id.layout_card_streak, View.GONE)
                        views.setViewVisibility(R.id.layout_card_today, View.GONE)
                        views.setViewVisibility(R.id.iv_card_grid, View.VISIBLE)
                        val gridBitmap = createHeatmapBitmap(habit, accentColorInt)
                        views.setImageViewBitmap(R.id.iv_card_grid, gridBitmap)
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun createHeatmapBitmap(habit: Habit, accentColor: Int): Bitmap {
            val width = 160
            val height = 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val history = habit.historyDaysCompleted

            val rows = 4
            val cols = 7
            val cellSize = 16f
            val spacing = 5f
            val cornerRadius = 4f

            val totalW = cols * cellSize + (cols - 1) * spacing
            val totalH = rows * cellSize + (rows - 1) * spacing
            val startX = (width - totalW) / 2f
            val startY = (height - totalH) / 2f

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val isDone = history.getOrElse(idx) { false }
                    val isToday = idx == 27

                    val x = startX + c * (cellSize + spacing)
                    val y = startY + r * (cellSize + spacing)
                    val rect = RectF(x, y, x + cellSize, y + cellSize)

                    if (isToday && habit.isCompletedToday) {
                        paint.color = accentColor
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    } else if (isDone) {
                        paint.color = accentColor
                        paint.alpha = 210
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    } else {
                        paint.color = android.graphics.Color.parseColor("#2A2A38")
                        paint.alpha = 255
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    }
                }
            }

            return bitmap
        }

        private fun createBarGraphBitmap(habit: Habit, accentColor: Int): Bitmap {
            val width = 160
            val height = 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val weekly = habit.weeklyCompletions

            val barCount = 7
            val barWidth = 12f
            val spacing = 8f
            val cornerRadius = 5f

            val totalW = barCount * barWidth + (barCount - 1) * spacing
            val startX = (width - totalW) / 2f
            val baselineY = height - 12f

            for (i in 0 until barCount) {
                val isDone = weekly.getOrElse(i) { false }
                val barHeight = if (isDone) 56f else 18f
                val x = startX + i * (barWidth + spacing)
                val y = baselineY - barHeight
                val rect = RectF(x, y, x + barWidth, baselineY)

                if (isDone) {
                    paint.color = accentColor
                    paint.alpha = 240
                } else {
                    paint.color = android.graphics.Color.parseColor("#2A2A38")
                    paint.alpha = 255
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            }

            return bitmap
        }
    }
}
