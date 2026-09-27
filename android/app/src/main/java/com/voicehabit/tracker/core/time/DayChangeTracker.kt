package com.voicehabit.tracker.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Отслеживание смены календарных суток.
 *
 * Room Flow переизлучается только при записи в БД, поэтому приложение, открытое
 * через полночь, показывало вчерашнее состояние «выполнено сегодня» до первого
 * тапа. Трекер слушает системные бродкасты смены даты/времени/пояса и дёргает
 * колбэк; дополнительно ViewModel перепроверяет день при возврате на экран.
 */
class DayChangeTracker(
    private val context: Context,
    private val onDayChanged: () -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onDayChanged()
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
    }
}
