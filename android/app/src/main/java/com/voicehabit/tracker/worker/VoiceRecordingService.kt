package com.voicehabit.tracker.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicehabit.tracker.R

/**
 * Запись с выключенным экраном. Без foreground-сервиса Android убивает процесс
 * через несколько минут после блокировки — для голосового дневника это провал
 * ядра продукта (Фаза C была 0/5).
 *
 * Сервис не пишет звук сам: он держит процесс живым и показывает честное
 * уведомление, пока [com.voicehabit.tracker.core.audio.AudioRecorderManager]
 * пишет файл. Старт/стоп — из HomeViewModel.startRecording/stopRecording.
 */
class VoiceRecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Запись голоса",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Идёт запись голосовой заметки" }
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("dairy записывает")
            .setContentText("Нажмите «стоп» в приложении, чтобы закончить")
            .setSmallIcon(R.drawable.ic_mic_minimal)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_START = "com.voicehabit.tracker.action.RECORD_START"
        const val ACTION_STOP = "com.voicehabit.tracker.action.RECORD_STOP"
        private const val CHANNEL_ID = "voice_recording"
        private const val NOTIFICATION_ID = 41
    }
}
