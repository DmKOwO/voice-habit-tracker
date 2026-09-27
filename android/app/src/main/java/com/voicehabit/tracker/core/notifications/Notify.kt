package com.voicehabit.tracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voicehabit.tracker.MainActivity
import com.voicehabit.tracker.data.local.SettingsManager
import java.time.LocalTime

/** Каналы и хелперы уведомлений (F9/G22/G25): напоминания, итоги, обновления. */
object Notify {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DIGEST = "digest"
    const val CHANNEL_UPDATES = "updates"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, "Итоги дня и недели", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Обновления", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** G22: ночью не будим, даже если worker сработал. */
    fun inQuietHours(settings: SettingsManager): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = LocalTime.now().hour
        return hour >= 23 || hour < 7
    }

    fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateInstallIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
        }
        return PendingIntent.getActivity(
            context, 9201, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateInstallAction(context: Context): NotificationCompat.Action {
        return NotificationCompat.Action(
            android.R.drawable.stat_sys_download_done,
            "Установить",
            updateInstallIntent(context)
        )
    }

    fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        actions: List<NotificationCompat.Action> = emptyList(),
        customContentIntent: PendingIntent? = null
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(customContentIntent ?: contentIntent(context))
            .setAutoCancel(true)
        actions.forEach { builder.addAction(it) }
        try {
            manager.notify(id, builder.build())
        } catch (e: SecurityException) {
            // Нет POST_NOTIFICATIONS — молча пропускаем, данные не теряются.
        }
    }

    fun action(
        context: Context,
        requestCode: Int,
        title: String,
        intent: Intent
    ): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, title, pending)
    }
}
