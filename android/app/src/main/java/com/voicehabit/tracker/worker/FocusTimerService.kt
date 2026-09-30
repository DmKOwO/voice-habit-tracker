package com.voicehabit.tracker.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.voicehabit.tracker.MainActivity
import com.voicehabit.tracker.R

/**
 * Фокус-таймер, который доживает до конца.
 *
 * Раньше таймер был голой корутиной во ViewModel: сворачивание приложения или
 * смерть процесса молча убивали сессию, а об окончании не сигналило ничего.
 * Теперь источник правды — этот foreground-сервис: он тикает в фоне и по
 * окончании будит вибро + звуком + уведомлением, даже если приложения уже нет.
 * ViewModel при возврате сверяется с ним (см. reconcileFocusTimer).
 */
class FocusTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                FocusAlarmReceiver.cancel(this)
                prefs().edit().remove(KEY_END_AT).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val totalSec = intent?.getIntExtra(EXTRA_TOTAL_SEC, 0) ?: 0
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Фокус-сессия"
        // Минуты едут отдельно: при рестарте totalSec — остаток, а длительность сессии исходная.
        val totalMin = intent?.getIntExtra(EXTRA_TOTAL_MIN, 0)?.takeIf { it > 0 } ?: (totalSec / 60)
        if (totalSec <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val endAt = System.currentTimeMillis() + totalSec * 1000L
        prefs().edit()
            .putLong(KEY_END_AT, endAt)
            .putString(KEY_LABEL, label)
            .putInt(KEY_TOTAL_MIN, totalMin)
            .remove(KEY_FINISHED_LABEL)
            .apply()
        // Второй рубеж: системный будильник добьёт даже в Doze.
        FocusAlarmReceiver.schedule(this, endAt, label)
        startForeground(NOTIFICATION_ID, buildOngoing(label, totalSec))
        scheduleTick(label)
        return START_STICKY
    }

    private fun scheduleTick(label: String) {
        tick?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val remaining = ((prefs().getLong(KEY_END_AT, 0L) - System.currentTimeMillis()) / 1000L)
                    .coerceAtLeast(0L)
                if (remaining <= 0) {
                    onFinish(label)
                    return
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildOngoing(label, remaining.toInt()))
                handler.postDelayed(this, 1000L)
            }
        }
        tick = r
        handler.postDelayed(r, 1000L)
    }

    private fun onFinish(label: String) {
        tick?.let { handler.removeCallbacks(it) }
        FocusAlarmReceiver.cancel(this)
        FocusFinishSignal.fire(this, label)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOngoing(label: String, remainingSec: Int): Notification {
        val mm = "%02d:%02d".format(remainingSec / 60, remainingSec % 60)
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("Фокус: $label")
            .setContentText("Осталось $mm")
            .setSmallIcon(R.drawable.ic_mic_minimal)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Таймер фокуса", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Остаток фокус-сессии" }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_FINISH) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FINISH, "Окончание фокуса", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Сигнал завершения фокус-сессии" }
            )
        }
    }

    override fun onDestroy() {
        tick?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.voicehabit.tracker.action.FOCUS_START"
        const val ACTION_CANCEL = "com.voicehabit.tracker.action.FOCUS_CANCEL"
        const val EXTRA_TOTAL_SEC = "extra_total_sec"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TOTAL_MIN = "extra_total_min"

        const val PREFS = "focus_timer"
        const val KEY_END_AT = "end_at"
        const val KEY_LABEL = "label"
        const val KEY_TOTAL_MIN = "total_min"
        const val KEY_FINISHED_LABEL = "finished_label"
        const val KEY_FINISHED_MIN = "finished_min"
        const val KEY_FINISHED_AT = "finished_at"

        private const val CHANNEL_ONGOING = "focus_timer"
        private const val CHANNEL_FINISH = "focus_finish"
        private const val NOTIFICATION_ID = 42
        private const val FINISH_NOTIFICATION_ID = 43

        fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun prefs() = prefs(this)
}
