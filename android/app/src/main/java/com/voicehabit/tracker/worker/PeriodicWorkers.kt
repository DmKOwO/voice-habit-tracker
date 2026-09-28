package com.voicehabit.tracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.voicehabit.tracker.core.backup.BackupManager
import kotlinx.coroutines.launch
import com.voicehabit.tracker.core.di.AppContainer
import com.voicehabit.tracker.core.notifications.Notify
import com.voicehabit.tracker.core.update.github.CheckResult
import com.voicehabit.tracker.core.update.github.GithubReleaseChecker
import com.voicehabit.tracker.core.update.github.GithubUpdateController
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** G36: еженедельный автобэкап в фоне. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val file = BackupManager(applicationContext).export()
            com.voicehabit.tracker.core.logging.AppLogger.instance()
                .i("backup", "Автобэкап готов: ${file.name}")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "weekly_backup"

        fun scheduleWeekly(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .addTag("backup")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

/** G25: итог недели в воскресенье вечером. */
class WeeklyReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.get(applicationContext)
            val today = LocalDate.now()
            if (today.dayOfWeek != DayOfWeek.SUNDAY) return Result.success()
            if (container.settings.lastWeeklyReviewEpochDay == today.toEpochDay()) return Result.success()

            val habits = container.habitRepository.getAllHabitsList().filter { it.deletedAt == null && !it.archived }
            val doneToday = habits.count { it.isCompletedToday }
            val best = habits.maxOfOrNull { it.bestStreak } ?: 0
            val tasks = container.taskRepository.getAllTasksList()
            val openCount = tasks.count { !it.isCompleted && it.deletedAt == null }

            container.settings.lastWeeklyReviewEpochDay = today.toEpochDay()
            if (!Notify.inQuietHours(container.settings)) {
                Notify.show(
                    applicationContext, Notify.CHANNEL_DIGEST, 9101,
                    "Итог недели",
                    "Привычек выполнено сегодня: $doneToday из ${habits.size}. Лучший стрик: $best. Открытых задач: $openCount. Откройте вечерний разбор!"
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun enqueueCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeeklyReviewWorker>()
                .addTag("weekly_review")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("weekly_review_check", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Перепланирование напоминаний и периодики после перезагрузки. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(context)
                ReminderRescheduler.rescheduleAll(context, container)
                BackupWorker.scheduleWeekly(context)
                UpdateCheckWorker.scheduleDaily(context)
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** OTA: ежедневная фоновая проверка GitHub-релизов.
 *
 * Только проверяет и уведомляет: само скачивание идёт при открытом приложении
 * (там виден прогресс и кнопка установки). Тихая установка без ведома
 * пользователя на Android невозможна — финальный тап всегда за пользователем
 * в системном инсталлере.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.get(applicationContext)
            if (!container.settings.autoUpdateCheck) return Result.success()
            val checker = GithubReleaseChecker(
                currentVersion = { GithubUpdateController.currentSemVer(applicationContext) }
            )
            when (val result = checker.check(
                com.voicehabit.tracker.core.update.UPDATE_GITHUB_OWNER,
                com.voicehabit.tracker.core.update.UPDATE_GITHUB_REPO
            )) {
                is CheckResult.UpdateAvailable -> {
                    val release = result.release
                    if (container.settings.lastGithubAutoTag != release.tagName) {
                        val downloader = com.voicehabit.tracker.core.update.github.GithubUpdateDownloader()
                        val dlResult = downloader.download(
                            context = applicationContext,
                            url = release.apkUrl,
                            expectedSizeBytes = release.apkSizeBytes
                        )
                        if (dlResult is com.voicehabit.tracker.core.update.github.GithubUpdateDownloader.DownloadResult.Done) {
                            container.settings.lastGithubAutoTag = release.tagName
                            if (!Notify.inQuietHours(container.settings)) {
                                Notify.show(
                                    context = applicationContext,
                                    channel = Notify.CHANNEL_UPDATES,
                                    id = 9201,
                                    title = "⬇ Обновление v${release.version} готово к установке",
                                    text = "Нажмите для установки новой версии.",
                                    actions = listOf(Notify.updateInstallAction(applicationContext)),
                                    customContentIntent = Notify.updateInstallIntent(applicationContext)
                                )
                            }
                        }
                    }
                    Result.success()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            // Фоновая проверка не должна долбить API ретраями: следующий шанс — завтра.
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "daily_update_check"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag("ota")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

/** G29: виджеты отдают команды через этот receiver — обновление по смене даты. */
class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )
        ) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                WidgetRefreshHelper.refreshAll(context)
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
