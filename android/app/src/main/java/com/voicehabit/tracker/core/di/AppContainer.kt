package com.voicehabit.tracker.core.di

import android.content.Context
import com.voicehabit.tracker.core.audio.AudioRecorderManager
import com.voicehabit.tracker.core.audio.SpeechRecognizerHelper
import com.voicehabit.tracker.core.coroutines.AppDispatchers
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.data.local.TransactionRunner
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.data.repository.TaskRepositoryImpl
import com.voicehabit.tracker.data.repository.VoiceRepositoryImpl
import com.voicehabit.tracker.domain.usecase.ApplyVoiceActionsUseCase
import com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase
import com.voicehabit.tracker.domain.usecase.ProcessVoiceUseCase
import androidx.room.withTransaction

/**
 * Единственный граф зависимостей приложения.
 *
 * Раньше один и тот же граф собирался вручную в трёх местах
 * (HomeViewModel, QuickRecordActivity, VoiceUploadWorker) — правка одной зависимости
 * требовала правки трёх копий, и они расходились.
 */
class AppContainer(
    context: Context,
    val dispatchers: AppDispatchers = AppDispatchers.Default,
    val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    val settings: SettingsManager = SettingsManager.getInstance(context.applicationContext)
) {
    private val appContext = context.applicationContext

    val habitRepository by lazy {
        HabitRepositoryImpl(
            database.habitDao(),
            CalculateStreakUseCase(),
            transactionRunner,
            frozenDaysProvider = {
                val today = java.time.LocalDate.now().toEpochDay()
                database.dayMarkDao().range(today - 400, today).map { it.dateEpochDay }.toSet()
            }
        )
    }
    val taskRepository by lazy { TaskRepositoryImpl(database.taskDao()) }
    val voiceRepository by lazy {
        VoiceRepositoryImpl(
            habitDao = database.habitDao(),
            taskDao = database.taskDao(),
            voiceLogDao = database.voiceLogDao(),
            settingsManager = settings,
            digestRepository = digestRepository
        )
    }

    val processVoiceUseCase by lazy { ProcessVoiceUseCase(voiceRepository) }
    val applyVoiceActionsUseCase by lazy { ApplyVoiceActionsUseCase(habitRepository, taskRepository) }

    val audioRecorder by lazy { AudioRecorderManager(appContext) }
    val speechRecognizer by lazy { SpeechRecognizerHelper(appContext) }

    val extrasRepository by lazy { com.voicehabit.tracker.data.repository.ExtrasRepository(database) }

    /** H1: конспекты свободного потока — «диктофон с выжимкой». */
    val digestRepository by lazy { com.voicehabit.tracker.data.repository.DigestRepository(database) }

    val githubUpdater by lazy {
        com.voicehabit.tracker.core.update.github.GithubUpdateController(appContext, settings)
    }
    val tts by lazy { com.voicehabit.tracker.core.audio.TtsHelper(appContext) }
    val vosk by lazy { com.voicehabit.tracker.core.audio.VoskManager(appContext) }
    val health by lazy { com.voicehabit.tracker.core.health.HealthManager(appContext) }
    val backup by lazy { com.voicehabit.tracker.core.backup.BackupManager(appContext) }
    val obsidianVaultManager by lazy {
        com.voicehabit.tracker.core.obsidian.ObsidianVaultManager(appContext, settings)
    }

    /** Транзакции Room для многошаговых записей. */
    val transactionRunner: TransactionRunner = object : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T =
            database.withTransaction { block() }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun get(context: Context): AppContainer =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context).also { INSTANCE = it }
            }
    }
}
