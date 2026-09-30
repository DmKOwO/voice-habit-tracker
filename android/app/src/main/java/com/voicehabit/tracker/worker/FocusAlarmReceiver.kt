package com.voicehabit.tracker.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Системный будильник окончания фокуса.
 *
 * Foreground-сервис защищает от Low Memory Killer, но не от Doze и агрессивных
 * прошивок (OriginOS и подобные усыпляют процессор вместе с Handler-тиками).
 * setAlarmClock будит устройство гарантированно: это тот же механизм, что
 * пользуют часы-будильники, и ему не нужно разрешение SCHEDULE_EXACT_ALARM.
 * При срабатывании — тот же финишный сигнал, что у сервиса: вибро + звук +
 * уведомление. Дубли со стороны сервиса безопасны: финиш идемпотентен
 * (ключи prefs чистятся при первом же срабатывании).
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FOCUS_ALARM) return
        val label = intent.getStringExtra(FocusTimerService.EXTRA_LABEL) ?: "Фокус-сессия"
        FocusFinishSignal.fire(context.applicationContext, label)
    }

    companion object {
        const val ACTION_FOCUS_ALARM = "com.voicehabit.tracker.action.FOCUS_ALARM"
        private const val REQUEST_CODE = 701

        fun schedule(context: Context, endAtMillis: Long, label: String) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, FocusAlarmReceiver::class.java)
                .setAction(ACTION_FOCUS_ALARM)
                .putExtra(FocusTimerService.EXTRA_LABEL, label)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // setAlarmClock: срабатывает даже в Doze, показывает иконку будильника.
            val info = AlarmManager.AlarmClockInfo(endAtMillis, pending)
            runCatching { alarm.setAlarmClock(info, pending) }
                .onFailure { e ->
                    com.voicehabit.tracker.core.logging.AppLogger.instance().e(
                        "focus", "setAlarmClock не встал",
                        mapOf("err" to (e.message ?: e.javaClass.simpleName))
                    )
                }
        }

        fun cancel(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, FocusAlarmReceiver::class.java).setAction(ACTION_FOCUS_ALARM)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching { alarm.cancel(pending) }
        }
    }
}
