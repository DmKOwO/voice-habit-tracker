package com.voicehabit.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

object WidgetPinHelper {

    fun isPinningSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return appWidgetManager.isRequestPinAppWidgetSupported
        }
        return false
    }

    fun pinHabitCardWidget(context: Context) {
        pinWidget(context, DuroHabitCardWidgetProvider::class.java, "Карточка привычки Duro")
    }

    fun pinOverviewWidget(context: Context) {
        pinWidget(context, DuroHabitWidgetProvider::class.java, "Список привычек Duro")
    }

    fun pinVoiceWidget(context: Context) {
        pinWidget(context, QuickVoiceWidgetProvider::class.java, "Микрофон быстрой записи Duro")
    }

    private fun pinWidget(context: Context, providerClass: Class<*>, widgetName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = ComponentName(context, providerClass)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, HabitWidgetReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                Toast.makeText(context, "Подтвердите добавление виджета «$widgetName»", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(
            context,
            "Зажмите свободное место на домашнем экране -> «Виджеты» -> найдите Duro",
            Toast.LENGTH_LONG
        ).show()
    }
}
