package com.voicehabit.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.voicehabit.tracker.MainActivity
import com.voicehabit.tracker.core.di.AppContainer
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Обработка голосовой записи в фоне: расшифровка и применение действий.
 *
 * Раньше `enqueue()` не вызывался нигде, поэтому «очередь при отсутствии сети»
 * из README не существовала: без ключей и без сети запись просто терялась.
 * Теперь работа ставится в очередь явно, уникальна по записи, имеет backoff,
 * лимит попыток и пишет статус в `voice_logs`, поэтому экран истории перестаёт
 * показывать выдуманное «обработано».
 */
class VoiceUploadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val audioPath = inputData.getString(KEY_AUDIO_PATH)
        val logId = inputData.getString(KEY_LOG_ID) ?: ("voice_" + UUID.randomUUID().toString())
        if (audioPath.isNullOrBlank()) return Result.failure()

        val container = AppContainer.get(applicationContext)
        val audioFile = File(audioPath)

        if (!audioFile.exists()) {
            markStatus(container, logId, VOICE_STATUS_FAILED, "Аудиофайл не найден")
            showNotification(
                title = "Запись не обработана",
                message = "Аудиофайл не найден: ${audioFile.name}"
            )
            return Result.failure()
        }

        val attempt = runAttemptCount + 1
        return try {
            markStatus(container, logId, VOICE_STATUS_PENDING_UPLOAD, "Обработка #$attempt")

            val result = container.processVoiceUseCase(audioFile)
            val action = result.getOrNull()

            if (action == null) {
                val message = result.exceptionOrNull()?.message ?: "Неизвестная ошибка расшифровки"
                if (attempt >= MAX_ATTEMPTS) {
                    markStatus(container, logId, VOICE_STATUS_FAILED, message)
                    showNotification(
                        title = "Запись не распознана",
                        message = "$message. Попыток: $attempt. Откройте историю и повторите вручную."
                    )
                    Result.failure()
                } else {
                    markStatus(container, logId, VOICE_STATUS_PENDING_UPLOAD, "$message · попытка $attempt")
                    Result.retry()
                }
            } else {
                container.applyVoiceActionsUseCase(action)
                markStatus(container, logId, VOICE_STATUS_APPLIED, action.summary)
                showNotification(
                    title = "Голосовая заметка обработана",
                    message = buildString {
                        append(action.summary)
                        if (attempt > 1) append(" (попыток: $attempt)")
                    }
                )
                audioFile.delete()
                Result.success()
            }
        } catch (error: Exception) {
            if (attempt >= MAX_ATTEMPTS) {
                markStatus(container, logId, VOICE_STATUS_FAILED, error.message ?: error.javaClass.simpleName)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun markStatus(
        container: AppContainer,
        logId: String,
        status: String,
        summary: String
    ) {
        container.database.voiceLogDao().upsertVoiceLog(
            com.voicehabit.tracker.data.local.entity.VoiceLogEntity(
                id = logId,
                audioPath = inputData.getString(KEY_AUDIO_PATH).orEmpty(),
                rawTranscript = inputData.getString(KEY_TRANSCRIPT).orEmpty(),
                summary = summary,
                status = status,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun showNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Голосовые заметки",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_AUDIO_PATH = "key_audio_path"
        const val KEY_TRANSCRIPT = "key_transcript"
        const val KEY_LOG_ID = "key_log_id"

        const val VOICE_STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
        const val VOICE_STATUS_PROCESSED = "PROCESSED"
        const val VOICE_STATUS_APPLIED = "APPLIED"
        const val VOICE_STATUS_FAILED = "FAILED"

        const val MAX_ATTEMPTS = 3
        const val CHANNEL_ID = "voice_notes_channel"
        private const val NOTIFICATION_ID = 4711

        /**
         * Ставит запись в очередь. Уникальное имя по пути файла: повторный вызов
         * не создаёт вторую работу и не применяет одну запись дважды.
         */
        fun enqueue(context: Context, audioPath: String, transcript: String? = null, logId: String? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<VoiceUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .setInputData(
                    workDataOf(
                        KEY_AUDIO_PATH to audioPath,
                        KEY_TRANSCRIPT to transcript.orEmpty(),
                        KEY_LOG_ID to (logId ?: "voice_" + UUID.randomUUID().toString())
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(audioPath),
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun uniqueWorkName(audioPath: String): String = "voice_upload_$audioPath"

        const val WORK_TAG = "voice_upload"
    }
}
