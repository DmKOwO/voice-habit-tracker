package com.voicehabit.tracker.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.voicehabit.tracker.MainActivity
import com.voicehabit.tracker.R

/**
 * Единый финишный сигнал фокуса: вибро + звук + уведомление.
 * Вынесен отдельно, чтобы его могли вызвать и сервис, и системный будильник,
 * не дублируя код. Идемпотентен через ключи prefs: кто первый — тот и сигналит.
 */
object FocusFinishSignal {

    fun fire(context: Context, label: String) {
        val prefs = FocusTimerService.prefs(context)
        // Если сервис уже отработал, второй сигнал не нужен.
        val alreadyDone = prefs.getString(FocusTimerService.KEY_FINISHED_LABEL, null) != null
        val totalMin = prefs.getInt(FocusTimerService.KEY_TOTAL_MIN, 0)
        if (!alreadyDone) {
            prefs.edit()
                .remove(FocusTimerService.KEY_END_AT)
                .putString(FocusTimerService.KEY_FINISHED_LABEL, label)
                .putInt(FocusTimerService.KEY_FINISHED_MIN, totalMin)
                .putLong(FocusTimerService.KEY_FINISHED_AT, System.currentTimeMillis())
                .apply()
        }
        vibrate(context)
        playSound(context)
        notify(context, label)
    }

    private fun vibrate(context: Context) {
        runCatching {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 800)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        }
    }

    private fun playSound(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            ringtone.play()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { runCatching { ringtone.play() } }, 2500L
            )
        }
    }

    private fun notify(context: Context, label: String) {
        runCatching {
            val openIntent = Intent(context, MainActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            val pending = PendingIntent.getActivity(
                context, 702, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val done = NotificationCompat.Builder(context, "focus_finish")
                .setContentTitle("Фокус завершён")
                .setContentText("$label — можно подвести итог")
                .setSmallIcon(R.drawable.ic_mic_minimal)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            context.getSystemService(NotificationManager::class.java)?.notify(43, done)
        }
    }
}
