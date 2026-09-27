package com.voicehabit.tracker.worker

import android.content.Context
import com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider
import com.voicehabit.tracker.widget.DuroHabitWidgetProvider

/** G29: единая точка обновления всех виджетов (вызывают UI, receiver даты, worker). */
object WidgetRefreshHelper {
    fun refreshAll(context: Context) {
        try {
            DuroHabitWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
        }
        try {
            DuroHabitCardWidgetProvider.updateAllCardWidgets(context)
        } catch (e: Exception) {
        }
    }
}
